# 实时推荐流处理作业

该目录是组员 A 的独立 Flink 作业交付物，只负责：

1. 从 Kafka `contest-behavior` 消费学生浏览事件；
2. 使用 `rec:flink:dedup:{event_id}` 做消费侧去重；
3. 按事件时间做 5 分钟滚动窗口，水位线允许 1 分钟乱序；
4. 将用户分类浏览次数写入 Redis。

该作业不修改 Spring Boot 后端、前端、RabbitMQ 或公共配置。

## 输入事件

```json
{
  "event_id": "018f5f17-5c3e-7c9a-a4a4-2d2f38f49f9a",
  "user_id": "101",
  "event_type": "contest_detail_viewed",
  "contest_id": "8",
  "category": "MATH_MODELING",
  "occurred_at": "2026-11-28T18:30:00+08:00"
}
```

`event_type` 不是 `contest_detail_viewed`、字段缺失或 JSON 无法解析时，事件会被丢弃，不会导致作业因单条坏消息反复失败。

## Redis 写入口径

- 偏好键：`rec:user:{uid}:cat:{category}`
- Hash field：窗口开始时间的 UTC epoch 秒，例如 `1795861800`
- Hash value：该窗口浏览次数
- 键 TTL：8 天
- 每次写入通过 Lua 原子完成：删除 7 天前字段、保留同窗口较大的计数、写入当前窗口、刷新 TTL
- 去重键：`rec:flink:dedup:{event_id}`，`SET NX EX 8 天`

“同窗口保留较大计数”用于降低 Flink checkpoint 恢复后的重复副作用风险，不表示 exactly-once。契约仍然是最佳努力语义。

Hash field 的 epoch 秒编码是由 A 侧确定、供后端 B 读取的统一约定。若段欣懿要求修改 field 编码，只需要调整 `RedisRecommendationKeys.bucketField` 和 `RedisPreferenceCounterWriter`。

## 构建与测试

```powershell
cd stream
mvn clean test
mvn package
```

作业 JAR：`stream/target/recommendation-stream.jar`。Flink 核心依赖使用 `provided`，生产 JAR 不携带 Flink 运行时。

## 提交到现有 Flink

```powershell
docker cp stream/target/recommendation-stream.jar contest-flink-jobmanager:/opt/flink/usrlib/
docker exec -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 `
  -e REDIS_HOST=redis `
  contest-flink-jobmanager flink run -d `
  -c com.campus.contest.recommendation.stream.RecommendationStreamJob `
  /opt/flink/usrlib/recommendation-stream.jar
```

可覆盖的配置：

| 环境变量 | 默认值 |
| --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `RECOMMENDATION_KAFKA_TOPIC` | `contest-behavior` |
| `RECOMMENDATION_KAFKA_GROUP_ID` | `recommendation-stream` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `RECOMMENDATION_WINDOW_MINUTES` | `5` |
| `RECOMMENDATION_WATERMARK_MINUTES` | `1` |
| `RECOMMENDATION_RETENTION_DAYS` | `7` |
| `RECOMMENDATION_CHECKPOINT_INTERVAL_MS` | `30000` |
| `RECOMMENDATION_PARALLELISM` | `1` |

也支持 `--kafka-bootstrap-servers=...`、`--redis-host=...` 等同名命令行参数。

## 需要段欣懿调整的公共 Docker 基线

现有 `kafka` 服务只向客户端宣告 `localhost:9092`。宿主机后端可以连接，但容器内 Flink 会把 `localhost` 解释为自己，因此需增加内部 listener：

```yaml
kafka:
  ports:
    - "9092:9092"
  environment:
    KAFKA_CFG_LISTENERS: EXTERNAL://:9092,INTERNAL://:29092,CONTROLLER://:9093
    KAFKA_CFG_ADVERTISED_LISTENERS: EXTERNAL://localhost:9092,INTERNAL://kafka:29092
    KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
```

此外当前 `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=false`，需要初始化 topic。可在启动后执行：

```powershell
docker exec contest-kafka kafka-topics.sh `
  --bootstrap-server localhost:9092 `
  --create --if-not-exists `
  --topic contest-behavior `
  --partitions 3 `
  --replication-factor 1 `
  --config retention.ms=604800000
```

这两项属于公共 Compose/Kafka 基线，当前分支没有直接修改。
