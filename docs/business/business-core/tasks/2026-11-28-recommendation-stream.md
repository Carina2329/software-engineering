# 正式任务卡：实时推荐 Kafka / Flink / Redis 链路

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-RECOMMENDATION-STREAM-001` |
| 认领人 | 组员 A |
| 业务域 | 竞赛中心相似竞赛推荐 / 实时行为特征 |
| 开发分支 | `codex/recommendation-stream` |
| PR 目标分支 | `codex/recommendation-foundation` |
| 基线 | `codex/recommendation-foundation@998290c`，合入前 rebase 到最新冻结基线 |
| 公共契约 | `recommendation-contract.md` 1.0.1、OpenAPI 1.7.0 |
| 当前状态 | 链路实现与 Docker 实测已完成，待推送和审查 |
| 最终确认人 | 段欣懿 |

## 一、任务目标

实现独立 Flink 作业，消费 Kafka 行为事件，按 5 分钟滚动窗口统计每个用户每个竞赛分类的浏览次数，写回 Redis 偏好计数桶，并做消费侧去重。

```text
Kafka contest-behavior
  → Flink 事件时间窗口聚合
  → Redis rec:user:{uid}:cat:{category}
  → 供 EP-101 读取偏好
```

## 二、前置依赖

- 从段欣懿冻结的 `codex/recommendation-foundation` 切出分支。
- 已阅读 `AGENTS.md`、`recommendation-contract.md` 和本任务卡。
- Kafka topic `contest-behavior`、Redis 键和事件 JSON 结构已由段欣懿冻结。
- 不等待后端上报接口和前端页面完成，可用测试事件独立联调。

## 三、本次范围

- 独立 `stream/` 模块和 Flink 作业，不修改 Spring Boot 主工程。
- Kafka 消费 `contest-behavior`，按 `user_id` 分区。
- 消费侧去重键 `rec:flink:dedup:{event_id}`，`SET NX EX`，TTL 8 天。
- 事件时间 `occurred_at`、水位线 1 分钟、5 分钟滚动窗口。
- 按 `user_id` + `category` 统计浏览次数。
- 写 Redis `rec:user:{uid}:cat:{category}`，field 为窗口起点 UTC epoch 秒字符串，value 为计数。
- Lua 原子清理 7 天前桶、刷新 8 天 TTL。
- 坏 JSON、非法事件类型丢弃；超水位线事件进 side output 后丢弃，作业不失败。
- 窗口、去重、Redis 写入和故障隔离的测试。

## 四、允许修改的文件

```text
stream/
```

可向段欣懿提交 `docker/docker-compose.yml` 的 Kafka/Flink 配置建议，不直接修改公共文件。

## 五、禁止修改

- `backend/` 主工程代码和 `backend/pom.xml`。
- 竞赛分类、报名资格、前端页面。
- RabbitMQ 拓扑与认证代码。
- 公共 SQL、OpenAPI、接口汇总、错误码、`AGENTS.md`、`README.md`、`docker/docker-compose.yml`。
- 不实现协同过滤、推荐模型、复杂召回排序或其他基础设施。

## 六、与其他人员的冻结协作接口

- 消费 topic `contest-behavior`，事件字段：`event_id`、`user_id`、`event_type`、`contest_id`、`category`、`occurred_at`。
- 写 Redis 偏好键 `rec:user:{uid}:cat:{category}`，field 为窗口起点 UTC epoch 秒，供组员 B 读取。
- 写消费去重键 `rec:flink:dedup:{event_id}`，TTL 8 天。
- 窗口口径固定：5 分钟滚动、事件时间、1 分钟水位线、最近 7 天特征。

## 七、具体交付物

- 可构建运行的独立 Flink 作业和 `stream/pom.xml`。
- Kafka 消费、窗口聚合、Redis 写入、消费侧去重实现。
- 窗口、水位线、去重、坏 JSON 处理测试。
- `stream/README.md`：启动命令、Docker 镜像和 topic 初始化说明。
- Kafka/Flink Docker Compose 配置建议。

## 八、验收场景

- Flink 消费事件后，Redis 出现该用户该分类的 5 分钟时间桶计数。
- 相同 `event_id` 重复事件在消费侧被去重，不重复累计。
- 超过 1 分钟水位线的迟到事件进 side output，不进入偏好。
- 坏 JSON 和非法事件类型被丢弃，作业持续运行。
- 旧时间桶超过 7 天后不再计入统计，Redis key TTL 约 8 天。
- Kafka 或 Flink 挂掉时不影响正常详情浏览。

## 九、需要提交的测试和运行证据

- `mvn clean package` 结果。
- Kafka topic 分区数、`retention.ms` 证据。
- Flink 作业运行状态和 checkpoint 证据。
- 重复 `event_id`、迟到事件、坏 JSON 的实测记录。
- Redis 键、桶计数和 TTL 截图。
- 分支名、提交哈希、修改文件清单、作业 JAR SHA256。

## 十、必须停止并询问段欣懿的情况

- Redis 键结构、field 编码、事件 JSON 或窗口口径与冻结契约不一致。
- 需要修改主工程 pom、配置、docker-compose 或公共 SQL。
- Kafka/Flink 镜像或版本与项目环境冲突且需要改变公共基线。
- 需要接入公共基础设施以外的组件。
