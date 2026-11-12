# 认证高并发保护任务卡

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.1 |
| 日期 | 2026-11-12 |
| 状态 | 分工草案，等待确认；尚未开始开发 |
| 适用范围 | 登录、图片验证码、邮件 OTP 的高并发保护 |
| 共同契约 | `AGENTS.md`、`docs/auth-rbac/contract.md`、`docs/api/openapi-v1.json` 的合并后新版本 |

## 一、共同规则

每张任务卡开始开发前，必须先阅读：

1. 根目录 `AGENTS.md`；
2. 本任务卡；
3. `docs/auth-rbac/contract.md` 中与本任务相关的部分；
4. `docs/api/openapi-v1.json` 中对应的 EP 接口；
5. `docs/auth-rbac/high-concurrency-integration-plan.md`。

组员不得各自复制接口字段、错误码、Redis Key 规则或 OTP 生命周期定义。所有字段和规则以公共契约和 OpenAPI 为唯一事实源。

共同限制：

- 不新增 HTTP 接口；
- 不新增数据库表或字段；
- 不增加 Spring Cloud Gateway；
- 不开发抢赛题、教师抢名额或抢队员；
- 不修改登录方式、JWT、Refresh Token、RBAC 或 Auth Store；
- RabbitMQ 只允许用于认证邮件 OTP，其他模块不得使用；
- 不新造业务错误码；
- 不把密码、OTP、JWT、Refresh Token写入日志；
- 不修改公共文件，确需变更先向段欣懿提出。

## 二、公共文件唯一维护人

以下文件只能由段欣懿修改：

- `AGENTS.md`
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `.env.example`
- `docker/docker-compose.yml`
- `README.md`
- `docs/api/openapi-v1.json`
- `docs/api/接口汇总.md`
- `docs/auth-rbac/contract.md`
- `docs/auth-rbac/CHANGELOG.md`
- `docs/auth-rbac/team-plan.md`
- `docs/db/rbac-auth-delta.md`

前端公共文件继续只由原负责人维护：

- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`

## 三、跨模块唯一文件所有权

| 文件或能力 | 唯一负责人 | 约定 |
| --- | --- | --- |
| `EmailOtpServiceImpl.java` | 组员 B | 只由 B 修改，负责 challenge 生命周期和调用异步发布接口 |
| `CaptchaServiceImpl.java` | 组员 B | 只由 B 修改 |
| `LoginRateLimiter.java` | 组员 B | 只由 B 修改 |
| `VerificationRateLimiter.java` | 组员 B | 只由 B 修改 |
| RabbitMQ 拓扑、Producer、Consumer | 组员 C | C 不得修改 `EmailOtpServiceImpl` |
| Mail 投递状态和重复消费防护 | 组员 C | 不由 B 在 `EmailOtpServiceImpl` 中重复实现 |
| `EmailOtpDispatchCommand` 跨模块命令对象 | 段欣懿 | 只冻结签名和字段，B 调用、C 转换 |
| Nginx 配置 | 组员 A | C、B 不得修改 |
| 三张认证前端页面 | 组员 A | 其他组员不得修改 |

跨模块 Java 接口由段欣懿在集成分支先冻结接口签名，组员只实现各自版本，不得私自修改接口。

---

## 四、组员 A 任务卡：Nginx、前端提示和总体压测

> 开始开发前，先阅读根目录 `AGENTS.md`、本任务卡、`contract.md` 中与本任务相关的部分，以及 OpenAPI 中 EP-001、EP-067、EP-068、EP-071 的接口定义。

### 1. 分支

```text
feature/high-concurrency-nginx
```

从段欣懿创建并推送的 `feature/high-concurrency` 拉出，不得直接修改 `develop` 或 `feature/rbac-auth`。

### 2. 目标

1. 对敏感认证接口增加 IP 限流和连接限制。
2. Nginx 直接返回统一 429 JSON，包含 `trace_id` 和 `X-Trace-Id`。
3. 不影响普通页面、普通业务接口和文件上传下载。
4. 调整邮件 OTP 异步受理文案和错误提示。
5. 主导 JMeter 压测脚本和 Nginx 验收。

### 3. 前置依赖

- 段欣懿完成公共契约和配置；
- 段欣懿在 `docker-compose.yml` 中挂载 Nginx 配置；
- OpenAPI 已明确 429、40001 和异步 202 语义；
- B、C 提供后端验证条件和测试账号，A 不擅自修改后端。

### 4. 允许修改

- 新增 `docker/nginx/nginx.conf`
- 新增 `docker/nginx/conf.d/default.conf`
- `frontend/src/views/common/LoginPage.vue`
- `frontend/src/views/common/RegisterPage.vue`
- `frontend/src/views/common/PasswordResetPage.vue`
- 新增 `tests/jmeter/auth-high-concurrency.jmx`
- 新增 `scripts/high-concurrency/` 下的验证脚本

### 5. 禁止修改

- `backend/` 下所有代码
- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- `docker/docker-compose.yml`
- `.env.example`
- `README.md`
- OpenAPI、接口汇总、contract、CHANGELOG、数据库文档和 SQL
- 登录方式、错误码、JWT、RBAC 和数据库

### 6. Nginx 要求

只限制认证敏感路径：

- `/api/v1/auth/login`
- `/api/v1/auth/captcha`
- `/api/v1/auth/email-verification-codes`
- `/api/v1/auth/password-reset/request`
- 可选 `/api/v1/auth/register`

不得对以下路径设置相同的低额度限制：

- 静态页面；
- 普通 API；
- `/api/v1/files/**`
- `/api/v1/uploads/**`

建议初始值：

| 路径 | 平均速率 | burst | 连接限制 |
| --- | ---: | ---: | ---: |
| 登录 | `1r/s` | 10 | 每 IP 10 |
| 图片验证码 | `2r/s` | 20 | 每 IP 10 |
| 邮件 OTP | `30r/m` | 10 | 每 IP 5 |
| 找回密码申请 | `30r/m` | 10 | 每 IP 5 |

Nginx 429 必须返回：

```json
{
  "code": 40001,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "trace_id": "Nginx request_id"
}
```

同时设置：

- `Content-Type: application/json; charset=utf-8`
- `X-Trace-Id`
- `limit_req_status 429`
- `limit_conn_status 429`

### 7. 前端提示

- 成功文案改为“请求已受理，请稍后查收”；
- 不从“已发送”推断邮件已经送达；
- MQ 返回 503 时展示统一后端错误；
- 普通用户名密码登录页面不因 MQ 故障改变流程；
- 不新写 Axios、Auth Store 或路由守卫。

### 8. JMeter

- 压测只针对本地 Nginx、后端、Redis、RabbitMQ 和 Mailpit；
- 不对真实 SMTP 做并发压测；
- 主要验证 Nginx 429、普通页面不受影响、文件接口不受影响；
- B、C 提供 Redis 和 RabbitMQ 故障测试条件；
- 保存 JMX、结果 JTL、HTML报告和关键断言截图。

### 9. 验收场景

1. 限额内登录、验证码请求可以进入后端；
2. 超限请求由 Nginx 返回 HTTP 429；
3. 429 响应具有统一四个字段；
4. `trace_id` 非空，`X-Trace-Id` 存在；
5. 429 响应中的 `trace_id` 必须真实展开 Nginx `$request_id`，不能返回字面量 `$request_id`；
6. 至少抓取两次不同的 429 响应，确认不同请求的 `trace_id` 不相同，且每次响应体 `trace_id` 与对应 `X-Trace-Id` 响应头完全一致；
7. 普通静态页面和普通 API 不被误限流；
8. 文件上传、分片上传和下载不被误限流；
9. 登录初始化、注册、找回密码页面显示“已受理”文案；
10. 后端 503、40001 错误能正常展示；
11. 不对真实邮箱做压力测试。

### 10. 提交证据

- 分支名和提交哈希；
- 修改文件清单；
- `nginx -t` 结果；
- JMeter JMX、JTL 和 HTML报告；
- 429 响应样例；
- 至少两次不同 429 响应的 `trace_id` 与 `X-Trace-Id` 对照记录，证明二者一致且没有返回字面量 `$request_id`；
- 普通页面、普通 API、文件接口不受影响的验证记录；
- 前端 `pnpm build` 结果；
- 未解决问题。

### 11. 停止并询问段欣懿的条件

- 需要修改 `docker-compose.yml`、环境变量或 README；
- 需要修改 OpenAPI、错误码或后端代码；
- Nginx 限流导致普通业务或上传下载误伤；
- 无法得到统一 trace_id；
- 需要新增网关或其他中间件；
- 需要调整契约中的限流数值且影响其他组员。

---

## 五、组员 B 任务卡：Redis Lua 和 OTP 生命周期

> 开始开发前，先阅读根目录 `AGENTS.md`、本任务卡、`contract.md` 中与本任务相关的部分，以及 OpenAPI 中 EP-001、EP-067、EP-068、EP-069、EP-071、EP-072 的接口定义。

### 1. 分支

```text
feature/high-concurrency-redis-otp
```

### 2. 目标

1. 登录、验证码、OTP 限流改为 Redis Lua 原子操作。
2. 验证码和 OTP challenge 原子创建并设置 TTL。
3. 增加当前 challenge 索引，重发后旧 OTP 立即失效。
4. 提供当前 challenge 条件校验和原子比较回滚能力给 C 调用。
5. 保证请求 A 发布失败时不会删除请求 B 的 challenge 和索引。

### 3. 前置依赖

- 公共契约已更新；
- 段欣懿已冻结 `EmailOtpChallengeStore` 与 `EmailOtpPublisher` 接口；
- C 提供 `EmailOtpPublisher` 接口，不要求 C 先提供完整实现；
- B 不依赖 Nginx、前端或 RabbitMQ 实现。

### 4. 允许修改

- `backend/src/main/java/com/campus/contest/module/system/service/LoginRateLimiter.java`
- `backend/src/main/java/com/campus/contest/module/system/service/VerificationRateLimiter.java`
- `backend/src/main/java/com/campus/contest/module/system/service/impl/CaptchaServiceImpl.java`
- `backend/src/main/java/com/campus/contest/module/system/service/impl/EmailOtpServiceImpl.java`
- 新增 `EmailOtpChallengeStore` 的实现类
- 对应单元测试和 Redis 集成测试

### 5. 禁止修改

- RabbitMQ 拓扑、Producer、Consumer、Mail 投递状态；
- `EmailOtpMessage`；
- Nginx 配置；
- 登录 Controller、SecurityConfig、JWT、RBAC；
- 公共依赖、application 配置、docker-compose、OpenAPI、接口汇总、contract、CHANGELOG、SQL；
- 前端代码。

### 6. 必须提供的公共能力

实现冻结后的 `EmailOtpChallengeStore`：

- 判断指定 challenge 是否存在、未过期且仍是当前版本；
- 封装原子比较并回滚：
  - 只有当前索引等于本请求 challengeId 时才删除当前索引；
  - 始终只删除本请求自己的 challenge；
  - 不得误删其他请求创建的 challenge。

不得让 C 自行写 GET 后 DEL 的回滚逻辑。

### 7. Redis 原子要求

- 登录失败计数、IP 计数、锁定和 TTL 使用 Lua；
- 验证码 IP 计数和 TTL 使用 Lua；
- OTP 邮箱冷却、邮箱小时计数、IP小时计数使用 Lua；
- challenge 写入与 TTL 使用原子脚本；
- 当前 challenge 索引替换和旧 challenge 失效使用原子脚本；
- OTP 校验成功后一次性删除；
- 错误次数达到上限后删除 challenge；
- Redis 不可用返回现有 HTTP 503 + `50003`。

### 8. OTP 生命周期

- 6 位安全随机码；
- 5 分钟有效；
- 只保存哈希；
- 最多错误 5 次；
- 成功立即失效；
- 重新发送替换当前 challenge；
- 旧 OTP 即使仍在 TTL 内也不可验证；
- 邮箱、scene 和 challenge 必须完全匹配；
- synthetic challenge 不得具备可验证的有效 OTP。

### 9. 与组员 C 的边界

- B 修改 `EmailOtpServiceImpl.java`，C 不得修改；
- B 通过 `EmailOtpPublisher` 接口提交异步发送任务；
- C 负责 Publisher 实现、MQ 发布和 confirm；
- C 发布失败时调用 B 的原子回滚能力；
- 回滚由 Publisher 实现执行一次，B 的 `EmailOtpServiceImpl` 只负责传播 503/`50003`，不得再次回滚；
- B 不在 `EmailOtpServiceImpl` 中直接使用 RabbitTemplate；
- C 不重复实现 challenge 生命周期。

### 10. 验收场景

1. 并发请求验证码，计数和 TTL 正确；
2. 并发请求 OTP，60 秒冷却只有一个成功；
3. 邮箱和 IP 小时限制正确；
4. Redis Key 都有合理 TTL；
5. OTP 第 5 次错误后第 6 次被拒绝；
6. OTP 成功消费后再次使用失败；
7. 重发后旧 OTP 失效；
8. A/B 并发重发时，A 回滚不删除 B；
9. Redis 断开时返回 503 + `50003`；
10. 测试日志不包含 OTP 明文。

### 11. 提交证据

- 分支名和提交哈希；
- 修改文件清单；
- 单元测试和 Redis 门控测试命令、结果；
- A/B 并发回滚测试结果；
- 使用的 Lua Key 和 TTL摘要；
- 与 C 使用的接口签名；
- 未解决问题。

### 12. 停止并询问段欣懿的条件

- 需要新增或修改 Java 跨模块接口；
- 需要修改公共配置、依赖、OpenAPI 或错误码；
- 发现现有 OTP 字段需要改数据库；
- 需要 RabbitMQ 生产/消费代码；
- 无法在 Redis 内完成原子比较；
- 发现现有登录、注册或找回密码业务需要改变。

---

## 六、组员 C 任务卡：RabbitMQ 异步邮件

> 开始开发前，先阅读根目录 `AGENTS.md`、本任务卡、`contract.md` 中与本任务相关的部分，以及 OpenAPI 中 EP-068、EP-071 和 OTP 校验相关接口定义。

### 1. 分支

```text
feature/high-concurrency-rabbitmq
```

### 2. 目标

1. 建立认证邮件 OTP 专用 RabbitMQ 拓扑。
2. 实现 Publisher、Consumer、publisher confirm 和投递状态。
3. 限制重试次数、30 秒重试间隔，失败进入 DLQ。
4. 检查有效期、当前 challenge、suppress 和投递状态。
5. 正常重复消费不重复发送。

### 3. 前置依赖

- 段欣懿完成 RabbitMQ 依赖、环境配置和 docker-compose；
- 段欣懿冻结 `EmailOtpPublisher` 接口；
- B 提供 `EmailOtpChallengeStore` 接口及实现；
- C 不依赖 Nginx 和前端完成。

### 4. 允许修改

- 新增 `backend/src/main/java/com/campus/contest/config/RabbitMqConfig.java`
- 新增 `backend/src/main/java/com/campus/contest/module/system/messaging/EmailOtpMessage.java`
- 新增 `EmailOtpPublisher` 实现
- 新增 `EmailOtpConsumer`
- 新增 `MailDeliveryStateService`
- 对应 RabbitMQ 单元和集成测试

### 5. 禁止修改

- `EmailOtpServiceImpl.java`
- `CaptchaServiceImpl.java`
- `LoginRateLimiter.java`
- `VerificationRateLimiter.java`
- Nginx 配置和前端页面
- 公共依赖、application 配置、docker-compose、.env.example、README
- OpenAPI、接口汇总、contract、CHANGELOG、数据库和 SQL
- 登录方式、JWT、Refresh Token、RBAC

### 6. RabbitMQ 拓扑

- `auth.otp.exchange`：durable direct exchange
- `auth.otp.send.queue`：主队列
- `auth.otp.retry.queue`：30 秒延迟重试队列
- `auth.otp.dlq`：死信队列
- 消息持久化
- 消息 TTL 不超过 OTP 5 分钟有效期
- 手动 ACK
- prefetch 3～5
- 最大业务重试 3 次
- 超过次数进入 DLQ

### 7. 发布语义

- Producer 等待 RabbitMQ publisher confirm；
- confirm 成功才允许 B 的接口返回 202；
- confirm 失败时调用 B 的原子回滚；
- Publisher 在失败路径中只执行一次回滚，并把失败结果返回给 B；
- 不自行使用 GET 再 DEL；
- RabbitMQ 不可用时返回现有 HTTP 503 + `50003`；
- 不影响用户名密码登录和已有 OTP 校验。

### 8. 消费前检查

消费者发送邮件前必须检查：

- challenge 存在；
- 当前时间未超过 `expiresAt`；
- challenge 仍是当前 email+scene 版本；
- `suppress != true`；
- 投递状态未标记为 `SENT`；
- 没有其他消费者持有有效发送租约。

不满足条件时按规则 ACK，不实际发信，不无限重入。

### 9. 投递语义

- RabbitMQ 采用至少一次投递；
- Redis 投递状态用于正常情况下的重复消费防护；
- 不承诺所有崩溃窗口下绝对只发一封邮件；
- 测试措辞必须写“正常状态下重复消息不重复发送”，不能写“永远 exactly-once”。

### 10. 验收场景

1. 202 后任务进入主队列；
2. publisher confirm 成功后接口成功；
3. Mailpit 收到邮件；
4. 重复消息在 SENT 状态下不重复发送；
5. 过期 challenge 消息不发送；
6. 已被新 challenge 替代的旧消息不发送；
7. suppress 消息不发送；
8. Mailpit 不可用时 30 秒后重试；
9. 最多 3 次后进入 DLQ；
10. RabbitMQ 不可用时新发送返回 503 + `50003`；
11. RabbitMQ 不可用时用户名密码登录正常；
12. RabbitMQ 不可用时已有 OTP 仍可校验。

### 11. 提交证据

- 分支名和提交哈希；
- 修改文件清单；
- RabbitMQ 拓扑声明结果；
- Publisher confirm、重试和 DLQ 测试结果；
- 重复消费、旧消息和 suppress 测试结果；
- Mailpit 收件数量或脱敏截图，不展示 OTP；
- 故障验证命令和结果；
- 未解决问题。

### 12. 停止并询问段欣懿的条件

- 需要修改 `EmailOtpServiceImpl` 或其他组员文件；
- 需要修改 Java 公共接口；
- 需要修改 pom、application 配置、compose 或错误码；
- 需要新增 MQ 使用场景；
- B 的 challenge 接口无法支持当前版本或原子回滚；
- 发现 HTTP 状态、202/503 或 trace_id 契约不一致。
