# 认证与 RBAC 契约冻结稿

- 文档版本：0.2.1
- 初版日期：2026-11-03
- 本次修订：2026-11-06
- 适用分支：`feature/rbac-auth`
- 接口前缀：`/api/v1`
- 状态：公共基础已冻结，供组内并行开发

## 一、范围

本周只实现以下认证与授权能力：

1. 用户名＋密码＋图片人机验证码登录；
2. 邮箱＋邮件 OTP＋图片人机验证码登录；
3. 注册、找回密码、退出和会话续期；
4. Spring Security 认证；
5. 用户分配角色；
6. 角色分配菜单、按钮和 API 权限；
7. 垂直越权和水平越权防护。

不包含手机短信登录、第三方登录、SSO、多因素认证、跨站部署、新的权限表或其他新基础设施。

## 二、公共响应和错误规则

所有接口继续使用：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "trace_id": "..."
}
```

- `code` 必须是数字；
- 成功业务码为 `0`；
- HTTP 状态码和数字业务码继续沿用现有接口规范；
- 请求频率超限使用 HTTP `429` 和业务码 `40001`；
- 参数不合法使用 HTTP `400`；
- 未登录、令牌过期或会话吊销使用 HTTP `401`；
- 功能权限或数据范围不足使用 HTTP `403`；
- 资源不存在或当前用户不可见使用 HTTP `404`；
- 业务冲突或 `version` 冲突使用 HTTP `409`；
- 认证或幂等依赖不可用时使用 HTTP `503` 和业务码 `50003`；
- 每个响应都返回服务端生成的 `trace_id`。

禁止引入字符串形式的新业务错误码。

既有错误码的含义和 HTTP 映射保持不变：

- `20001`：业务状态或乐观锁冲突，HTTP 409；
- `40002`：重复请求、重放或幂等冲突，HTTP 409；
- `50001`：AI 服务暂不可用，HTTP 503；
- `50002`：对象存储暂不可用，HTTP 503；
- `50003`：认证或幂等依赖暂不可用，HTTP 503，可重试。

## 三、登录方式

统一登录入口：

```text
POST /api/v1/auth/login
```

### 1. 用户名密码登录

请求：

```json
{
  "login_type": "USERNAME_PASSWORD",
  "username": "student_a",
  "password": "StudentA#2026",
  "captcha_id": "...",
  "captcha_code": "..."
}
```

服务端依次校验图片验证码、账号状态和 BCrypt 密码。账号不存在时执行一次固定假哈希比对。失败统一返回“账号或凭证不正确”。

### 2. 邮箱 OTP 登录

第一步请求邮件 OTP：

```json
{
  "email": "student_a@example.edu",
  "scene": "LOGIN",
  "captcha_id": "...",
  "captcha_code": "..."
}
```

服务端校验图片验证码后发送 OTP，并返回与邮箱、场景绑定的 `challenge_id`。

第二步登录：

```json
{
  "login_type": "EMAIL_OTP",
  "email": "student_a@example.edu",
  "challenge_id": "...",
  "email_code": "123456"
}
```

只有 `challenge_id`、邮箱、`LOGIN` 场景、OTP、有效期和一次性全部匹配时才允许登录。

## 四、注册和找回密码

### 注册

```text
POST /api/v1/auth/register
```

请求包含 `username`、`real_name`、`password`、`email`、`challenge_id` 和 `email_code`。

- `challenge_id` 必须绑定同一邮箱和 `REGISTER` 场景；
- 用户名和邮箱必须唯一；
- 密码使用 BCrypt 保存；
- 默认角色固定为 `STUDENT`；
- 不接受前端提交的角色编码。

### 找回密码申请

```text
POST /api/v1/auth/password-reset/request
```

请求包含 `email`、`captcha_id` 和 `captcha_code`。无论账号是否存在，都返回相同结构；账号存在且邮箱有效时发送 `RESET_PASSWORD` 场景 OTP。

### 找回密码确认

```text
POST /api/v1/auth/password-reset/confirm
```

请求：

```json
{
  "email": "student_a@example.edu",
  "challenge_id": "...",
  "email_code": "123456",
  "new_password": "NewPassword#2026"
}
```

服务端必须验证：

- `challenge_id` 存在且未过期；
- `challenge_id` 绑定的邮箱与请求邮箱一致；
- 场景严格等于 `RESET_PASSWORD`；
- OTP 未使用且未超过错误次数。

通过后更新 BCrypt 哈希、删除 OTP 和挑战、提交数据库事务，再撤销该用户全部 Redis 会话。

## 五、图片验证码和邮件 OTP

### 图片验证码

- 服务端生成 4 位字符 PNG；
- 答案只保存在 Redis；
- 有效 2 分钟；
- 最多错误 5 次；
- 验证成功后立即删除；
- 不返回答案，不写普通日志；
- 统一使用 `java.security.SecureRandom` 生成挑战。

### 邮件 OTP

- 6 位安全随机数字；
- 有效 5 分钟；
- 只保存哈希；
- 成功后立即删除，只能使用一次；
- 同一邮箱 60 秒内不能重复发送；
- 同一邮箱最多 5 次/小时；
- 同一 IP 最多 20 次/小时；
- 每个 OTP 最多错误 5 次；
- 场景仅允许 `REGISTER`、`LOGIN`、`RESET_PASSWORD`；
- 开发环境通过 Spring Mail 发送到 Mailpit；
- 邮件发件人通过 `app.mail.from` 读取，环境变量为 `MAIL_FROM`；
- EP-068 仅在 `REGISTER`（邮箱未占用）或 `LOGIN`（账号存在且启用）时实际发信；`RESET_PASSWORD` 只允许通过 EP-071 发送；
- 不实际发信时仍返回结构一致的 `challengeId`，避免账号枚举；
- OTP 不写普通日志，不通过接口返回。

## 六、登录限流

| 对象 | 限制 |
|---|---|
| 用户名密码登录 | 同一账号 5 次失败/5 分钟 |
| 用户名密码登录 | 同一 IP 10 次失败/5 分钟 |
| 账号临时锁定 | 达到限制后锁定 15 分钟 |
| 图片验证码 | 最多错误 5 次，有效 2 分钟 |
| 邮件 OTP 发送 | 60 秒间隔，同邮箱 5 次/小时，同 IP 20 次/小时 |
| 邮件 OTP 校验 | 最多错误 5 次，超过后作废 |

限流响应：

```json
{
  "code": 40001,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "trace_id": "..."
}
```

## 七、JWT 和 Redis 会话

Access JWT 有效 15 分钟，只包含：

```text
sub
sid
iat
exp
iss
```

不包含密码、邮箱、角色、权限或其他敏感业务数据。

Redis 会话：

```text
auth:session:{sid}
auth:user-sessions:{userId}
```

会话保存用户 ID、角色和权限快照、Refresh Token 哈希和过期时间。服务端每次请求验证 JWT 后都必须确认 Redis 会话仍有效。

Refresh Token 是不透明安全随机值，保存在 `HttpOnly` Cookie 中。Refresh 时比较 Redis 中的哈希并轮换 Refresh Token。

JWT 密钥通过环境变量 `AUTH_JWT_SECRET` 提供，不硬编码、不提交 Git。密钥泄露后必须更换环境变量、重启后端、清除认证会话并强制重新登录。

## 八、会话撤销公共能力

同学 1 提供唯一的会话撤销能力：

- 撤销当前会话；
- 撤销指定用户全部会话；
- 查询用户会话索引并批量撤销。

同学 2 在重置密码后调用该能力。同学 3 在用户角色、角色权限或数据范围变化后调用该能力。其他模块不能自行实现第二套 Redis 撤销逻辑。

## 九、数据库和 Redis 边界

MySQL 事务只保证数据库表操作的一致性，不包含 Redis。

角色或用户权限变更的顺序：

1. 在 MySQL 事务中更新数据库；
2. 提交 MySQL 事务；
3. 撤销 Redis 中受影响的会话；
4. 撤销成功后才报告完整成功。

Redis 撤销失败时：

1. 进行有限次数同步重试；
2. 返回 HTTP `503` 和现有数字业务错误码；
3. 保留 `trace_id`；
4. 在 `sys_oper_log` 记录 `SESSION_REVOCATION_FAILED`；
5. Redis 恢复后依据审计记录重新执行撤销。

不引入消息队列、Outbox 或其他新基础设施。

## 十、RBAC 权限模型

继续使用：

```text
sys_user → sys_user_role → sys_role
          ↓
sys_role_menu → sys_menu
```

`sys_menu` 同时承载目录、菜单和按钮/API 权限资源。`perms` 是稳定权限编码，不新增重复权限资源表。

本周权限编码：

| 权限编码 | 用途 |
|---|---|
| `system:user:list` | 查看用户 |
| `system:user:create` | 创建用户 |
| `system:user:update` | 修改用户状态和角色 |
| `system:role:list` | 查看角色 |
| `system:role:create` | 创建角色 |
| `system:role:update` | 修改角色 |
| `system:role:authorize` | 修改角色权限 |
| `system:menu:list` | 查看权限资源树 |
| `file:upload` | 上传文件 |
| `file:download` | 下载文件 |

### 角色权限矩阵

| 权限 | ADMIN | STUDENT |
|---|---:|---:|
| 查看用户 | 有 | 无 |
| 修改用户角色 | 有 | 无 |
| 查看角色 | 有 | 无 |
| 创建和修改角色 | 有 | 无 |
| 角色授权 | 有 | 无 |
| 查看权限资源树 | 有 | 无 |
| 文件上传 | 有 | 有 |
| 文件下载 | 有 | 有，仅本人文件 |

`TEACHER` 和 `REVIEWER` 保留种子角色，本周不扩展业务页面。

前端菜单和按钮只根据权限做展示控制，后端必须通过 `@PreAuthorize` 和资源归属检查完成真正授权。

## 十一、角色和权限接口规则

- `POST /api/v1/roles`：必须携带 `Idempotency-Key`，`role_code` 唯一；
- `PATCH /api/v1/roles/{role_id}`：必须携带 `role.version` 和 `Idempotency-Key`；
- `PUT /api/v1/roles/{role_id}/menus`：必须携带 `role.version` 和 `Idempotency-Key`；
- `PATCH /api/v1/users/{user_id}`：沿用原有规则，必须携带 `user.version`，前端同时提供 `Idempotency-Key`。

`PUT /api/v1/roles/{role_id}/menus` 必须在 MySQL 事务中替换 `sys_role_menu` 并递增 `sys_role.version`。

审计日志记录操作者、目标用户或角色、操作前后内容、结果、IP、时间和 `trace_id`，不记录密码、OTP、JWT 或 Refresh Token。

### 幂等作用域和并发规则

幂等键必须包含用户或当前会话作用域，不能只在全局使用 `operation + idempotencyKey`：

- 登录用户的写操作使用 `USER + userId`；
- Logout 等依赖当前会话的操作使用 `SESSION + sid`。

相同作用域、相同 operation、相同幂等键和相同请求：

- 第一次请求执行写操作并保存完整结果；
- 并发请求不得重复执行；
- 非 owner 请求最多等待约 2 秒读取完整结果；
- 结果尚未完成时返回 `40002`，HTTP 409，不读取半成品。

相同幂等键但请求内容不同，也返回 `40002`。`20001` 仍只表示业务状态或乐观锁冲突。

业务执行失败时删除进行中记录，不缓存失败结果，也不留下可被误读为成功的半成品。

Redis 不可用时停止执行原始业务，返回 `50003`，HTTP 503。

## 十二、开发测试账号

以下账号只用于本地开发，禁止用于生产：

| 用户名 | 邮箱 | 角色 | 本地演示密码 |
|---|---|---|---|
| `admin_demo` | `admin_demo@example.edu` | `ADMIN` | `Admin#2026Demo` |
| `student_a` | `student_a@example.edu` | `STUDENT` | `StudentA#2026` |
| `student_b` | `student_b@example.edu` | `STUDENT` | `StudentB#2026` |

种子 SQL 只保存 BCrypt 哈希，不保存明文密码。

## 十三、公共依赖边界

### 邮箱 OTP 校验

同学 2 提供验证能力，返回验证结果和错误信息。同学 1 在 `/api/v1/auth/login` 中调用，不创建第二个邮箱登录入口。

### 会话撤销

同学 1 提供公共撤销能力。同学 2 和同学 3 只能调用，不直接写 Redis 会话键。

### 前端公共请求和认证状态

同学 4 统一维护 Axios、Access Token 内存状态、Refresh、CSRF、401 和路由守卫。同学 5 使用这些能力，不创建第二套请求实例或认证 Store。

## 十四、冻结规则

- 公共契约变更先向段欣懿提出；
- 不允许个人直接修改 OpenAPI、接口汇总、公共 SQL、错误码或权限编码；
- 代码、SQL、OpenAPI、Apifox 和测试必须使用同一字段名和数字错误码；
- 接口路径统一以 `/api/v1` 为前缀；
- 本文件冻结后，各模块通过独立分支实现，集成前不改变公共契约。
