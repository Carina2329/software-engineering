# contest-agent-platform

AI 大学生学科竞赛全流程智能服务平台。

## 技术栈

- 后端：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis + RabbitMQ
- 前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia

## 环境要求

| 软件 | 版本 |
| --- | --- |
| JDK | 17（在 IDEA 项目结构中选择，命令行 java 版本不影响） |
| Node.js | 20 或更高（开发机实测 24 可用） |
| pnpm | 12.x（`npm i -g pnpm` 安装，全组统一同一大版本） |
| Maven | 3.8+（IDEA 自带亦可） |
| Docker Desktop | 最新 |

## 启动步骤

```bash
# 1. 启动 MySQL、Redis、Mailpit 和认证邮件 RabbitMQ
cd docker && docker compose up -d

# 2. 按顺序初始化数据库
mysql -uroot -proot123 contest_db < ../docs/sql/2026-11-08-contest-db-base.sql
mysql -uroot -proot123 contest_db < ../docs/sql/2026-10-29-file-sha256-storage-unique.sql
mysql -uroot -proot123 contest_db < ../docs/sql/2026-10-30-upload-task-expected-sha256.sql
mysql -uroot -proot123 contest_db < ../docs/sql/2026-11-03-rbac-auth-foundation.sql

# 3. 启动后端：用 IDEA 打开 backend/，运行 ContestApplication
#    或命令行：cd backend && mvn spring-boot:run

# 4. 启动前端
cd frontend && pnpm install && pnpm dev
```

## 验证

1. 按“启动步骤”的顺序执行 `docs/sql` 下的完整基础结构和增量脚本；
2. 浏览器访问 `http://localhost:8080/api/v1/auth/captcha?scene=LOGIN`，返回图片验证码和 `trace_id`；
3. 接口文档：`http://localhost:8080/doc.html`；
4. 打开 `http://localhost:5173/#/login`，登录页应能加载真实验证码。
5. RabbitMQ 管理页面：`http://localhost:15672`，本地默认账号见 `.env.example`；
6. Nginx 基础入口：`http://localhost:8088`，健康检查为 `/nginx-health`。认证接口限流由组员 A 在基础配置上实现。

## 认证高并发保护

- 仅认证邮件 OTP 允许使用 RabbitMQ；其他业务不得自行接入 MQ。
- 邮件 OTP 接口返回 HTTP 202 只表示任务已成功入队，不代表邮件已送达。
- RabbitMQ 不可用只影响新的 OTP 发送任务；用户名密码登录和已有 OTP 校验不依赖 MQ。
- Nginx 只限制登录、图片验证码、邮件 OTP 和找回密码申请等敏感认证路径，不影响普通页面、普通 API 和文件上传下载。
- Nginx 直接返回 429 时使用统一 JSON，trace_id 必须真实展开 `$request_id` 并与 `X-Trace-Id` 一致。
- 高并发任务边界和集成顺序见 `docs/auth-rbac/high-concurrency-task-briefs.md` 与 `docs/auth-rbac/high-concurrency-integration-plan.md`。

高并发公共基线验证命令：

```powershell
cd docker
docker compose config
docker compose up -d nginx rabbitmq redis
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8088/nginx-health

cd ..\backend
$env:RUN_REDIS_TESTS='true'
$env:RUN_AUTH_HTTP_TESTS='true'
$env:RUN_RBAC_HTTP_TESTS='true'
mvn -B test
```

## 真实邮箱（可选）

开发环境默认使用 Mailpit，不会向外部邮箱发信。需要在演示时发送真实邮件时，在启动后端前设置 SMTP 环境变量。QQ 邮箱示例：

```powershell
$env:MAIL_HOST='smtp.qq.com'
$env:MAIL_PORT='465'
$env:MAIL_USERNAME='你的QQ邮箱'
$env:MAIL_PASSWORD='QQ邮箱生成的SMTP授权码'
$env:MAIL_FROM='你的QQ邮箱'
$env:MAIL_SMTP_AUTH='true'
$env:MAIL_SMTP_STARTTLS='false'
$env:MAIL_SMTP_SSL_ENABLE='true'
```

`MAIL_PASSWORD` 必须是邮箱服务商生成的授权码，不是 QQ 登录密码。授权码只应通过本机环境变量或服务器密钥提供，不得提交 Git。

## 目录说明

| 目录 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 后端 |
| `frontend/` | Vue 3 前端 |
| `docs/` | 需求、设计、规范等课程文档 |
| `scripts/` | 种子数据 / AI 测试集生成脚本 |
| `docker/` | Docker Compose 与数据库初始化 SQL |

## 开发约定

- 分支模型与提交规范见 `docs/AI学科竞赛系统-前后端工程规划与命名规范-V0.1.md`；
- 禁止直接提交 `main`；功能开发从 `develop` 拉 `feature/xxx` 分支；
- 密钥、真实数据、`.env` 禁止进仓库。

## 更新记录
