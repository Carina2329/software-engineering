# 认证高并发保护集成顺序

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.1 |
| 日期 | 2026-11-12 |
| 状态 | 集成草案，等待确认；尚未开始开发 |
| 集成负责人 | 段欣懿 |
| 目标分支 | `feature/high-concurrency` |

## 一、范围

本周只完成：

- 登录敏感接口的 Nginx 限流；
- Redis Lua 原子限流；
- 图片验证码和邮件 OTP 生命周期加固；
- RabbitMQ 异步发送邮件 OTP；
- JMeter 并发验收和故障验收。

不包含：

- Spring Cloud Gateway；
- 抢赛题、抢指导教师、抢队员；
- AI 预审消息队列；
- 新 HTTP 接口、新数据库表或新业务错误码；
- 新登录方式、SSO、第三方登录或 MFA。

## 二、分支和公共基线顺序

### 第 0 步：提交上一轮遗留文档

先在当前 `feature/rbac-auth` 正式基线中，把上一轮已经确认的文档单独提交并推送：

- 需求 V0.1、V0.2、V0.3 的版本留存；
- 工程规划 V0.1、V0.2；
- 数据库增量说明；
- OpenAPI 与接口汇总的历史归档和当前版本；
- 相关变更记录。

提交后必须满足：

- 工作区干净；
- `feature/rbac-auth` 与远端同步；
- 确认没有原型分支内容混入；
- 不把文件中心 WIP 原型合并回来。

### 第 1 步：创建公共集成分支

从最新的 `feature/rbac-auth` 创建：

```text
feature/high-concurrency
```

只由段欣懿维护该公共分支。

### 第 2 步：公共契约和配置

段欣懿在新分支先完成一个独立的公共提交，内容为：

- `AGENTS.md`：本周只允许认证 OTP 使用 RabbitMQ；
- `contract.md`：异步受理、202、503、Nginx 429、OTP 生命周期和至少一次语义；
- OpenAPI 和接口汇总：只更新现有 EP 的语义，不新增接口；
- CHANGELOG：记录公共变更；
- `rbac-auth-delta.md`：说明不新增 MySQL 表；
- `pom.xml`：Spring AMQP 依赖；
- `application.yml` / `application-dev.yml`：RabbitMQ 和队列参数；
- `docker-compose.yml`：RabbitMQ、Nginx；
- `.env.example`；
- `README.md`；
- 三张任务卡和本集成顺序文档。

公共提交完成并推送后，组员才能拉分支。

## 三、跨模块接口冻结

B 和 C 存在双向协作，不能同时修改同一个业务文件。

段欣懿在公共分支中先冻结以下 Java 接口，只提交签名，不写 RabbitMQ 或 OTP 业务实现：

1. `EmailOtpChallengeStore`
   - 供 B 实现；
   - 供 C 在发布失败回滚和消费前检查时调用。

2. `EmailOtpPublisher`
   - 供 B 的 `EmailOtpServiceImpl` 调用；
   - 供 C 实现。

3. `EmailOtpDispatchCommand`
   - 供 B 创建异步发送命令；
   - 由段欣懿维护字段和签名；
   - C 可将其转换为自己的 RabbitMQ 消息，不要求 B 依赖 C 的 MQ 专用类；
   - 不在多个文档复制字段定义。

接口冻结后：

- B 不得修改接口签名，只实现 `EmailOtpChallengeStore`；
- C 不得修改接口签名，只实现 `EmailOtpPublisher`；
- B 独占 `EmailOtpServiceImpl`；
- C 独占 MQ Config、Producer、Consumer、投递状态；
- 任何接口调整必须停止开发并请求段欣懿协调。

推荐的最小职责边界：

```text
EmailOtpServiceImpl（B）
  → 创建/替换 challenge
  → 调用 EmailOtpPublisher

EmailOtpPublisher（C 实现）
  → RabbitMQ publisher confirm
  → 失败时调用 EmailOtpChallengeStore 原子回滚
  → 将失败结果返回 EmailOtpServiceImpl（B 不再二次回滚）

EmailOtpConsumer（C）
  → 检查 challenge 当前状态
  → 检查消息有效性和投递状态
  → SMTP 发送、重试、DLQ
```

## 四、组员分支

组员从 `origin/feature/high-concurrency` 拉出：

| 组员 | 分支 | 目标 |
| --- | --- | --- |
| A | `feature/high-concurrency-nginx` | Nginx、前端提示、JMeter |
| B | `feature/high-concurrency-redis-otp` | Redis Lua、OTP 生命周期、原子回滚 |
| C | `feature/high-concurrency-rabbitmq` | RabbitMQ、重试、DLQ、投递状态 |

所有 PR 目标都是：

```text
feature/high-concurrency
```

不得直接合并到 `feature/rbac-auth` 或 `develop`。

## 五、推荐开发顺序和依赖

### 阶段 1：公共基础

负责人：段欣懿

- 第 0 步遗留文档提交；
- 创建 `feature/high-concurrency`；
- 更新公共契约、配置、依赖、部署和跨模块接口；
- 编译后端、构建前端、校验 OpenAPI；
- 通知组员同步。

完成条件：

- `feature/high-concurrency` 可编译；
- 旧登录、RBAC 和文件测试不回归；
- 组员拿到同一份契约和接口。

### 阶段 2：Redis 与 challenge 基础

负责人：组员 B

第一批只提交：

- `LoginRateLimiter`、`VerificationRateLimiter` 原子 Lua；
- `CaptchaServiceImpl` 原子创建；
- `EmailOtpChallengeStore` 实现及测试；
- 当前 challenge 索引；
- A/B 并发回滚测试。

此阶段不要求完成 `EmailOtpServiceImpl` 的邮件发布接线。

B 本阶段的提交先合并到 `feature/high-concurrency`，供 C 使用。

### 阶段 3：RabbitMQ

负责人：组员 C

在阶段 2 合并后：

- 同步 `feature/high-concurrency`；
- 实现 RabbitMQ 拓扑；
- `EmailOtpPublisher`；
- Consumer、投递状态、有限重试、DLQ、prefetch；
- 使用 B 的 `EmailOtpChallengeStore` 做回滚和发送前检查；
- 完成 RabbitMQ 和 Mailpit 集成测试。

C 不得修改 `EmailOtpServiceImpl`。

### 阶段 4：OTP 服务完成接线

负责人：组员 B

在 C 合并后：

- 同步 `feature/high-concurrency`；
- 修改 `EmailOtpServiceImpl`，只负责 challenge 生命周期和调用 `EmailOtpPublisher`；
- 保留 anti-enumeration；
- 完成 RabbitMQ 故障只影响新发送任务的测试；
- 确保已有 OTP 校验不依赖 RabbitMQ。

阶段 4 完成后，OTP 主链路才视为可用。

### 阶段 5：Nginx、前端和 JMeter

负责人：组员 A

可与阶段 2 部分并行开发配置和页面，但最终压测在阶段 3、阶段 4 合并后进行。

- Nginx 敏感接口限流；
- 自定义 429 JSON 和 trace_id；
- 普通页面、业务接口、文件接口不受影响；
- 三张页面的异步受理文案；
- JMeter 压测和故障场景。

## 六、建议合并顺序

1. 段欣懿：上一轮遗留文档提交；
2. 段欣懿：创建 `feature/high-concurrency`；
3. 段欣懿：高并发公共契约、配置、接口冻结；
4. 组员 B：阶段 2 Redis 与 challenge 基础；
5. 段欣懿：检查并合并阶段 2；
6. 组员 C：RabbitMQ 完整实现；
7. 段欣懿：检查并合并 C；
8. 组员 B：阶段 4 OTP 服务接线；
9. 段欣懿：检查并合并阶段 4；
10. 组员 A：Nginx、前端和 JMeter；
11. 段欣懿：检查并合并 A；
12. 段欣懿：全量回归、故障验收和最终记录。

如果 A 的 Nginx 配置与 B/C 不冲突，可以提前合并配置部分，但最终 JMeter 验收必须等 OTP 主链路完成。

## 七、每个 PR 的合并门禁

组员提交 PR 前必须提供：

- 分支名和提交哈希；
- 修改文件清单；
- 实际运行命令和结果；
- 公共接口名称和对应 EP 编号；
- 测试截图或脱敏接口响应；
- 未解决风险；
- 确认未修改禁止文件。

段欣懿合并前检查：

- 是否越过唯一文件所有权；
- 是否修改公共契约；
- 是否出现第二套 Redis、MQ、OTP 或请求逻辑；
- 是否引入新的错误码或字符串业务码；
- 是否保持 202、429、503 和 trace_id 契约；
- 是否影响用户名密码登录、RBAC、文件上传下载；
- 是否出现敏感信息日志。

## 八、最终联调清单

### 1. 构建和基础回归

- `mvn -B clean test`
- Redis 门控测试
- RabbitMQ 门控测试
- `pnpm build`
- OpenAPI JSON 解析校验
- 原用户名密码登录
- 原 RBAC 页面和权限
- 原文件上传下载

### 2. Nginx

- `nginx -t`
- 敏感接口正常请求
- 超限请求 429
- 429 JSON 四个字段
- `trace_id` 已真实展开 `$request_id`，不能是字面量
- 至少两次不同 429 的 `trace_id` 不同，并与对应 `X-Trace-Id` 一致
- 普通页面、普通 API、文件接口不受影响

### 3. Redis

- 登录、验证码、OTP 计数原子
- 所有计数键有 TTL
- 重发后旧 OTP 失效
- A/B 并发回滚不误删
- Redis 停止时 503 + `50003`
- Redis 停止时不会继续发布邮件

### 4. RabbitMQ

- publisher confirm
- 202 只表示已入队
- 手动 ACK
- 30 秒延迟重试
- 最多 3 次
- DLQ
- 过期/旧版本/suppress 消息不发信
- 正常状态下重复消息防护

### 5. 故障影响

- RabbitMQ 停止：新 OTP 发送返回 503；
- RabbitMQ 停止：用户名密码登录仍成功；
- RabbitMQ 停止：已有且未过期 OTP 仍可校验；
- Mailpit 停止：消费者重试后进入 DLQ；
- Redis 停止：不能绕过验证码或限流；
- Redis 恢复后业务可继续。

### 6. 投递语义

验收报告必须使用：

```text
RabbitMQ 为至少一次投递。
Redis 投递状态用于正常情况下的重复消费防护。
不承诺所有崩溃窗口下绝对只发送一封邮件。
```

不得写成无条件的 exactly-once。

## 九、最终禁止项

- 不新增接口、数据库表或网关；
- 不新增业务错误码；
- 不使用真实 SMTP 做压测；
- 不让多个组员拥有同一个文件的修改权；
- 不在任务卡中复制另一份接口字段或错误码；
- 不把 RabbitMQ 扩散到认证 OTP 之外的业务；
- 不把普通页面和文件接口纳入认证接口的低额度限流；
- 不承诺 RabbitMQ exactly-once；
- 不在日志中输出 OTP、JWT、Refresh Token 或密码。
