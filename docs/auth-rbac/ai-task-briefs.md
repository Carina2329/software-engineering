# 认证与 RBAC AI 任务卡

本文件为五位组员提供可直接交给各自 AI 的任务边界。公共字段、流程和安全规则统一引用现有事实源，不在本文件重复定义。

下文中的 `contract.md` 均指：

```text
docs/auth-rbac/contract.md
```

下文中的 OpenAPI 均指：

```text
docs/api/openapi-v1.json
```

数据库规则均以 `docs/db/rbac-auth-delta.md` 和 `docs/sql/2026-11-03-rbac-auth-foundation.sql` 为准，不在任务卡中复制字段定义。

公共数字错误码沿用完整既有清单；新增 `50003` 仅表示“认证或幂等依赖暂不可用”，HTTP 503，可重试。不得占用或改变 `50001`、`50002`、`20001`、`40002` 及其他错误码含义。

## 通用提交要求

每位组员都必须从远程集成分支拉出自己的分支：

```text
git fetch origin
git switch -c <自己的分支> origin/feature/rbac-auth
```

所有 PR 目标为：

```text
feature/rbac-auth
```

不得直接合并到 `develop`。

提交时必须提供：

1. 分支名和提交哈希；
2. 修改文件清单；
3. 实际运行命令与结果；
4. 测试截图或接口响应；
5. 使用的 EP 编号和公共能力；
6. 未解决问题及需要段欣懿协调的事项。

## 公共接口优先合入检查点

跨模块接口定义必须先于业务实现合入集成分支：

1. 同学 2 提供 `EmailOtpScene` 和 `EmailOtpVerifier`；
2. 同学 1 提供 `SessionRevocationService` 和 `CurrentUserProvider`；
3. 段欣懿将接口定义合入 `feature/rbac-auth`；
4. 后端完成编译检查；
5. 各组再基于同一接口基线并行实现。

已合入的接口签名不得由模块实现分支擅自修改。

## 同学 1：认证核心后端

> 开始开发前，先阅读根目录 AGENTS.md、本任务卡、contract.md 中与本任务相关的部分，以及 OpenAPI 中对应的接口定义。

### 分支名

```text
feature/auth-core
```

### 任务目标

- 搭建 Spring Security 认证主干；
- 实现统一 `/api/v1/auth/login` 入口；
- 实现用户名密码登录；
- 接入邮箱 OTP 登录验证结果；
- 签发短期 Access JWT；
- 实现 Redis 可吊销会话；
- 实现 Refresh Token 轮换；
- 实现退出登录；
- 向其他模块提供统一的会话查询和会话撤销能力。

### 允许修改或新增

- `backend/src/main/java/com/campus/contest/security/`
- `backend/src/main/java/com/campus/contest/module/system/controller/` 下只属于登录、退出、续期的 Controller
- `backend/src/main/java/com/campus/contest/module/system/` 下只属于认证核心的 Service、DTO 和 VO
- `backend/src/test/java/com/campus/contest/` 下对应的认证测试

可以新增认证所需的 Service 和 Controller，但不得创建第二个项目、第二个登录入口或重复的数据访问层。

### 禁止修改

- 同学 2 的图片验证码、邮件 OTP、注册和找回密码实现
- 同学 3 的角色、权限资源和角色授权实现
- 前端文件
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- 公共 `Result`、`BizException`、`GlobalExceptionHandler`、`CorsConfig`
- 段欣懿提供的六张系统表 Entity、Mapper、权限快照、审计和幂等服务
- OpenAPI、接口汇总、公共 SQL 和其他冻结文档

如需要 Spring Security、OAuth2 Resource Server 或其他新依赖，在任务结果中写明依赖坐标和用途，交给段欣懿处理。

### 依赖的公共能力

- 同学 2：已合入的 `EmailOtpVerifier` 和 `EmailOtpScene`；
- 段欣懿：六张系统表 Entity/Mapper、`AuthorizationSnapshotService`、审计和幂等服务；
- 同学 3：权限编码和 `@PreAuthorize` 接入所需资源清单。

不得自行创建第二套用户、会话或 Email OTP 逻辑。

### 对应接口

- `EP-001`：用户名密码和邮箱 OTP 的统一登录入口；
- `EP-002`：退出登录；
- `EP-070`：刷新 Access JWT。

### 验收场景

- 用户名密码登录成功并能建立 Redis 会话；
- 密码错误与账号不存在返回统一失败提示；
- Access JWT 只包含非敏感字段；
- JWT 被篡改或过期时返回 HTTP 401；
- 邮箱 OTP 适配只调用同学 2 的验证能力；
- Refresh Cookie 能换取新 Access JWT；
- 退出后当前 Access JWT 和 Refresh Token 立即失效；
- Redis 不可用时受保护接口失败关闭；
- 公共会话撤销能力可被同学 2、3 调用。

### 提交证据

- `mvn -B -DskipTests clean compile` 的结果；
- 登录、密码错误、JWT 篡改、Refresh、Logout 的接口响应；
- Redis 会话存在和退出后删除的截图或命令输出；
- 修改文件清单和依赖变更说明。

### 必须停止并询问段欣懿

- 需要修改公共 `Result`、错误码或环境配置；
- 需要新增依赖；
- 发现 OpenAPI 的登录、退出或续期字段不能实现；
- 需要修改数据库表；
- 需要自行创建六张系统表的 Entity 或 Mapper；
- 需要改变统一登录入口的路径或请求结构；
- 同学 2 的 OTP 接口尚未提供且准备自行重写。

## 同学 2：验证码、邮件与账号流程后端

> 开始开发前，先阅读根目录 AGENTS.md、本任务卡、contract.md 中与本任务相关的部分，以及 OpenAPI 中对应的接口定义。

### 分支名

```text
feature/auth-verification
```

### 任务目标

- 实现服务端图片人机验证码；
- 接入 Spring Mail 和 Mailpit 邮件发送；
- 实现带 `challengeId` 的邮件 OTP；
- 实现注册；
- 提供邮箱 OTP 校验能力；
- 实现找回密码申请和确认；
- 遵守验证码、OTP 和限流规则。

### 允许修改或新增

- `backend/src/main/java/com/campus/contest/module/system/controller/` 下验证码、注册和找回密码 Controller
- `backend/src/main/java/com/campus/contest/module/system/` 下验证码、邮件 OTP、注册和找回密码 Service、DTO 和 VO
- `backend/src/test/java/com/campus/contest/` 下对应的验证码、注册和找回密码测试

### 禁止修改

- `/api/v1/auth/login` 的统一登录 Controller
- Spring Security 核心配置、JWT 和 Redis 会话签发逻辑
- 角色、权限资源和角色授权实现
- 同学 1 的会话撤销实现
- 段欣懿提供的六张系统表 Entity、Mapper、权限快照、审计和幂等服务
- 前端文件
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- 公共 `Result`、错误码和异常处理
- OpenAPI、接口汇总、公共 SQL 和其他冻结文档

### 依赖的公共能力

- 同学 1：实现已合入的 `SessionRevocationService` 和当前用户能力；
- 段欣懿：Spring Mail 依赖、Mailpit 配置、数据库字段、共享 Mapper、审计和幂等服务；
- 同学 3：角色关联能力，用于注册后默认分配 `STUDENT`。

如 Spring Mail 依赖或 Mailpit 配置尚不存在，先提交所需配置说明，不得自行修改公共构建和环境文件。

### 对应接口

- `EP-067`：图片人机验证码；
- `EP-068`：发送邮件 OTP；
- `EP-069`：注册；
- `EP-071`：找回密码申请；
- `EP-072`：找回密码确认。

### 验收场景

- 图片验证码成功一次后立即失效；
- 图片验证码错误 5 次后作废；
- Mailpit 能收到 OTP，接口和普通日志不返回 OTP；
- OTP 按 `REGISTER`、`LOGIN`、`RESET_PASSWORD` 隔离；
- OTP 60 秒发送间隔和小时级限流生效；
- 注册成功默认分配 `STUDENT`，不读取前端角色；
- 找回密码必须同时匹配 `challengeId`、邮箱和 `RESET_PASSWORD`；
- 重置密码成功后调用同学 1 的会话撤销能力；
- 限流返回 HTTP 429 和数字业务码 `40001`。

### 提交证据

- Maven 编译结果；
- 验证码、OTP、注册和找回密码接口响应；
- Mailpit 收件截图；
- Redis 验证码/OTP 一次性删除结果；
- 依赖和配置需求清单。

### 必须停止并询问段欣懿

- 需要修改 `pom.xml` 或 `application*.yml`；
- 需要新增数据库表或字段；
- 需要改变邮件 OTP、验证码或找回密码的契约字段；
- 需要修改 `/auth/login` Controller；
- 需要自行实现会话撤销；
- 需要自行创建六张系统表的 Entity 或 Mapper；
- Mailpit 配置或邮件依赖尚未提供。

## 同学 3：RBAC 与越权后端

> 开始开发前，先阅读根目录 AGENTS.md、本任务卡、contract.md 中与本任务相关的部分，以及 OpenAPI 中对应的接口定义。

### 分支名

```text
feature/rbac-backend
```

### 任务目标

- 实现角色列表、创建和修改；
- 实现权限资源树；
- 实现角色权限查询和替换；
- 完成用户角色分配；
- 接入 `@PreAuthorize`；
- 实现文件资源归属检查；
- 完成垂直越权和水平越权测试。

### 允许修改或新增

- `backend/src/main/java/com/campus/contest/module/system/` 下角色、权限资源和用户角色分配的 Controller、Service、DTO 和 VO
- `backend/src/main/java/com/campus/contest/module/work/controller/FileController.java`
- `backend/src/main/java/com/campus/contest/module/work/service/` 下文件权限和归属检查相关实现
- `backend/src/test/java/com/campus/contest/` 下 RBAC 和越权测试

### 禁止修改

- Spring Security 登录流程、JWT 和 Redis 会话实现
- 同学 1 的会话撤销实现
- 段欣懿提供的六张系统表 Entity、Mapper、权限快照、审计和幂等服务
- 同学 2 的验证码、OTP、注册和找回密码实现
- 前端文件
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- 公共 `Result`、错误码和异常处理
- OpenAPI、接口汇总、公共 SQL 和权限编码清单

### 依赖的公共能力

- 同学 1：当前登录用户信息和统一会话撤销能力；
- 段欣懿：冻结权限编码、数据库增量、种子数据、共享 Mapper、权限快照、审计和幂等服务；
- 现有文件模块：`FileController` 和 `FileService` 中的上传人信息。

### 对应接口

- `EP-005`：查看用户；
- `EP-007`：修改用户状态和角色；
- `EP-008`：当前用户菜单；
- `EP-045` 至 `EP-051`：文件权限和资源归属保护；
- `EP-073` 至 `EP-078`：角色、权限资源树和角色授权。

### 验收场景

- `ADMIN` 能查看用户、角色和权限资源；
- 非管理员访问角色管理接口返回 HTTP 403；
- 角色创建、修改和角色授权使用 `Idempotency-Key` 和 `version`；
- 修改角色权限后，拥有该角色的用户旧会话失效；
- 修改用户角色后，该用户旧会话失效；
- 学生 A 不能下载学生 B 的文件；
- 菜单权限和 `@PreAuthorize` 使用同一权限编码。

### 提交证据

- Maven 编译结果；
- 角色、权限树、角色授权和用户角色分配接口响应；
- 垂直越权 HTTP 403 响应；
- 水平越权 HTTP 403/404 响应；
- 角色变化后的旧会话失败响应；
- 会话撤销失败时的 HTTP 503 和 `trace_id` 记录说明。

### 必须停止并询问段欣懿

- 需要新增或修改权限编码；
- 需要修改 OpenAPI、公共 SQL 或错误码；
- 需要新增数据库表；
- 找不到同学 1 提供的当前用户或会话撤销能力；
- 需要自行创建六张系统表的 Entity 或 Mapper；
- 需要复制另一套 Redis 会话撤销；
- 需要改变冻结的 RBAC 接口路径或请求字段。

## 同学 4：认证前端与公共骨架

> 开始开发前，先阅读根目录 AGENTS.md、本任务卡、contract.md 中与本任务相关的部分，以及 OpenAPI 中对应的接口定义。

### 分支名

```text
feature/auth-frontend
```

### 任务目标

- 实现登录页、注册页和找回密码页；
- 实现两种登录方式的界面；
- 实现 Access Token 内存状态；
- 实现 Refresh 自动续期；
- 实现 CSRF Token 处理；
- 实现统一 401 处理；
- 实现路由登录守卫；
- 为同学 5 提供公共请求和认证 Store。

### 允许修改或新增

- `frontend/src/api/request.ts`
- `frontend/src/api/` 下认证接口文件
- `frontend/src/api/` 下公共请求辅助文件
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- `frontend/src/views/common/` 下登录、注册、找回密码页面
- `frontend/src/types/` 下认证公共类型
- 前端认证相关测试或手工验收脚本

### 禁止修改

- 后端业务代码
- `frontend/package.json` 和锁文件
- `frontend/vite.config.ts`
- 同学 5 的用户、角色、权限资源页面
- OpenAPI、接口汇总和数据库文件
- 第二套 Axios、Auth Store 或路由守卫

如需新增前端依赖，在任务结果中说明，交给段欣懿处理。

### 依赖的公共能力

- 同学 1：登录、退出和续期接口；
- 同学 2：验证码、邮件 OTP、注册和找回密码接口；
- 段欣懿：冻结的 OpenAPI 和错误码；
- 同学 5：管理页面路由清单，由同学 4 统一接入路由。

### 对应接口

- `EP-001`：两种登录方式；
- `EP-002`：退出；
- `EP-003`：当前用户信息；
- `EP-067` 至 `EP-072`：验证码、邮件 OTP、注册、续期和找回密码。

### 验收场景

- 登录页可以切换用户名密码和邮箱 OTP；
- 图片验证码刷新正常；
- Mailpit OTP 输入流程正常；
- Access Token 只保存在内存，不写入 `localStorage`；
- 页面刷新后能通过 Refresh Cookie 恢复登录；
- 401 只触发一次刷新，刷新失败后跳转登录页；
- Refresh 和 Logout 请求携带 CSRF Token；
- 路由守卫阻止未登录用户访问受保护页面；
- 同学 5 的页面能直接使用公共请求和 Auth Store。

### 提交证据

- `pnpm build` 结果；
- 登录、注册、找回密码、Refresh 和 401 页面截图；
- 浏览器 Network 中的 Cookie、CSRF 和 Authorization 证据；
- 修改文件清单和公共路由说明。

### 必须停止并询问段欣懿

- 需要修改后端接口字段；
- 需要新增前端依赖；
- 需要改变 Cookie、CSRF 或 Token 处理方案；
- OpenAPI 与实际接口冲突；
- 同学 5 需要增加路由但要求修改另一套路由文件。

## 同学 5：权限管理前端

> 开始开发前，先阅读根目录 AGENTS.md、本任务卡、contract.md 中与本任务相关的部分，以及 OpenAPI 中对应的接口定义。

### 分支名

```text
feature/admin-frontend
```

### 任务目标

- 实现用户管理页面；
- 实现角色管理页面；
- 实现权限资源树；
- 实现角色权限勾选；
- 实现动态菜单展示；
- 实现 403 页面。

### 允许修改或新增

- `frontend/src/views/admin/`
- `frontend/src/views/common/ForbiddenPage.vue`
- `frontend/src/api/adminRbac.ts`
- `frontend/src/types/rbac.ts`
- `frontend/src/layouts/` 下动态菜单和后台布局组件
- 本模块相关前端测试或手工验收脚本

### 禁止修改

- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- `frontend/package.json` 和锁文件
- 后端代码
- OpenAPI、接口汇总和数据库文件
- 第二套 Axios、Auth Store 或权限判断实现

### 依赖的公共能力

- 同学 4：Axios、Access Token、Refresh、CSRF、Auth Store 和路由守卫；
- 同学 1：`/users/me` 和当前登录状态；
- 同学 3：RBAC 后端接口；
- 段欣懿：冻结权限编码和 OpenAPI。

### 对应接口

- `EP-005`：用户列表；
- `EP-007`：用户角色分配；
- `EP-008`：当前用户菜单；
- `EP-073` 至 `EP-078`：角色、权限资源和角色授权。

### 验收场景

- 管理员能查看用户并分配角色；
- 非管理员看不到管理菜单；
- 角色列表、创建和修改页面可操作；
- 权限资源树正确显示目录、菜单和操作；
- 角色权限勾选使用 `Idempotency-Key` 和 `version`；
- 授权成功后角色版本更新；
- 修改角色权限后用户旧会话失效；
- 无权限页面返回并展示 403；
- 页面不自行保存 Token 或创建 Axios 实例。

### 提交证据

- `pnpm build` 结果；
- 用户管理、角色管理、权限树和授权页面截图；
- 角色授权请求和响应；
- 无权限页面截图；
- 修改文件清单和所需路由清单。

### 必须停止并询问段欣懿

- 需要修改公共 Axios、Auth Store 或路由守卫；
- 需要修改后端接口字段；
- 需要新增权限编码或数据库字段；
- RBAC 接口与 OpenAPI 或任务卡冲突；
- 需要在前端自行判断用户角色以获得后端权限。
