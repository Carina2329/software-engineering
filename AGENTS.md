# 项目 AI 协作约束

本文件适用于本仓库中参与本周认证与 RBAC 开发的 AI 助手。

## 开发前必须阅读

开始任何实现前，阅读：

- `AGENTS.md`
- 对应角色的 `docs/auth-rbac/ai-task-briefs.md` 任务卡
- `docs/auth-rbac/contract.md` 中与本任务相关的部分
- `docs/api/openapi-v1.json` 中对应的接口定义

如任务涉及数据库，只能阅读：

- `docs/db/rbac-auth-delta.md`
- `docs/sql/2026-11-03-rbac-auth-foundation.sql`

这些文件是事实源。不要在任务卡、代码注释或另一份文档中复制一套接口字段或数据库定义。

公共契约版本记录见 `docs/auth-rbac/CHANGELOG.md`。

## 已冻结范围

本周只实现：

- 用户名密码和邮箱 OTP 两种登录；
- 服务端图片人机验证码；
- Spring Mail 与 Mailpit 邮件 OTP；
- 注册、找回密码、退出和会话续期；
- BCrypt 密码哈希；
- Spring Security 认证；
- 短期 Access JWT；
- Refresh Token 与 Redis 可吊销会话；
- 现有表上的 RBAC；
- 用户角色和角色权限变更后的会话撤销；
- 垂直越权与水平越权防护。

禁止扩展短信登录、第三方登录、SSO、多因素认证、新的权限表、消息队列或 Outbox。

## 公共文件边界

以下文件由段欣懿统一维护。其他成员不得直接修改；若需要变更，应在任务结果中说明原因、期望改动和影响，由段欣懿处理：

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/com/campus/contest/common/Result.java`
- `backend/src/main/java/com/campus/contest/common/BizException.java`
- `backend/src/main/java/com/campus/contest/common/GlobalExceptionHandler.java`
- `backend/src/main/java/com/campus/contest/config/CorsConfig.java`
- `docs/api/openapi-v1.json`
- `docs/api/接口汇总.md`
- `docs/auth-rbac/contract.md`
- `docs/auth-rbac/team-plan.md`
- `docs/db/rbac-auth-delta.md`
- `docs/sql/` 下的公共数据库脚本
- 根目录 `AGENTS.md`

前端公共骨架只由同学 4 维护：

- `frontend/src/api/request.ts`
- `frontend/src/router/index.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`

同学 5 不得创建第二套 Axios 实例、Auth Store、Token 持久化或路由守卫。

## 认证与授权约束

- 认证和授权必须使用 Spring Security，不手写一套拦截器替代。
- `/api/v1/auth/login` 只能有同学 1 维护的统一入口。同学 2 只提供验证码和邮箱 OTP 能力。
- 用户角色、角色权限或数据范围发生变化后，必须调用同学 1 提供的统一会话撤销能力。
- 不得在同学 2、3 的业务代码中另写一套 Redis 会话撤销。
- Access JWT 只能包含 `sub`、`sid`、`iat`、`exp`、`iss` 等非敏感字段，不得包含密码、邮箱、角色、权限或其他敏感业务数据。
- 后端权限必须通过 Spring Security 和 `@PreAuthorize` 校验。前端隐藏菜单和按钮仅用于界面展示。
- 带资源 ID 的查询必须校验资源归属，不能只凭 ID 返回数据。

## 安全约束

- 密码仅使用 BCrypt 哈希保存，不得明文保存或返回。
- 图片验证码、邮件 OTP、会话 ID 和 Refresh Token 使用 `java.security.SecureRandom`。
- 图片验证码和邮件 OTP 只能一次性使用，并遵守 `contract.md` 中的次数、时效和限流规则。
- 密码、邮件 OTP、Access JWT、Refresh Token 不得进入 URL、普通日志或错误响应。
- 种子数据只保存 BCrypt 哈希。
- JWT 密钥只能通过环境变量或外部配置提供，不得硬编码或提交 Git。
- Refresh 和 Logout 等自动携带 Cookie 的路径使用 Spring Security CSRF 防护。
- 生产环境使用 HTTPS、HSTS、安全 Cookie 和受限 CORS；本地 HTTP 只用于开发。

## 数据库约束

- 不新增数据库表。
- 继续使用 `sys_user`、`sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_oper_log`。
- `sys_menu` 是目录、菜单、按钮和 API 权限的统一资源树。
- 不得新增 `sys_permission_resource` 或 `sys_role_permission`。
- 图片验证码、邮件 OTP、会话、Refresh Token、幂等记录和限流保存在 Redis。
- MySQL 事务不包含 Redis。数据库事务提交后再撤销 Redis 会话；撤销失败不得报告完全成功。

## 公共接口规范

- 路径统一以 `/api/v1` 为前缀。
- 使用统一响应 `code`、`message`、`data`、`trace_id`。
- 业务错误码必须是数字并沿用现有 HTTP 状态映射。
- 限流返回 HTTP 429 和业务码 `40001`。
- `50001` 专指 AI 服务不可用；`50002` 专指对象存储不可用。
- `50003` 专指认证或幂等依赖暂不可用，HTTP 503，可重试。
- 服务端必须做参数校验。
- 适用的写接口使用 `Idempotency-Key`。
- 更新接口使用 `version` 乐观锁。
- 角色变更、角色授权和用户角色分配必须写审计日志，但不得记录敏感数据。

公共基础现已提供统一 `trace_id`、数字错误码映射、六张系统表的 Entity/Mapper、权限快照、审计和幂等服务。
同学 1、2、3 使用这些公共能力，不再各自创建同名数据访问或审计逻辑。

公共 `Result`、异常处理、Mapper 和公共配置仍由段欣懿统一维护。

## 编码与目录规范

- 后端包根路径为 `backend/src/main/java/com/campus/contest/`。
- 后端模块内部按 Controller、Service、Mapper、Entity、DTO、VO 分层。
- 前端页面按角色或公共用途放在 `frontend/src/views/` 下。
- Vue 使用 `<script setup lang="ts">`，业务代码不得使用 `any`。
- 页面请求必须通过 `frontend/src/api/request.ts`。
- 不修改与本任务无关的文件。
- 不创建另一个项目、演示副本或独立后端。

## 完成要求

提交前必须实际运行相应构建或测试，并在任务结果中提供：

1. 分支名和提交哈希；
2. 修改文件清单；
3. 实际运行命令与结果；
4. 接口响应或测试截图；
5. 使用的公共接口和 EP 编号；
6. 未解决问题及需要段欣懿协调的事项。

只有 AI 生成的代码、没有实际运行，不算完成。

遇到以下情况立即停止并询问段欣懿：

- 需要修改 OpenAPI、公共 SQL、公共错误码或权限编码；
- 需要修改 `pom.xml` 或环境配置；
- 找不到其他同学应提供的公共能力；
- 发现接口字段、数据库字段或权限模型冲突；
- 需要新增表、登录方式、外部服务或基础设施。
