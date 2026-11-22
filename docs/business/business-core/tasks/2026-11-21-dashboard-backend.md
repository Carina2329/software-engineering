# 正式任务卡：数字大屏一期后端与 WebSocket

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-DASHBOARD-BACKEND-001` |
| 认领人 | 何同学 |
| 业务域 | 平台运行总览 / 数据统计与实时推送 |
| 开发分支 | `feature/business-dashboard-backend` |
| PR 目标分支 | `feature/business-core` |
| 公共契约 | 最新 `feature/business-core` 和 `dashboard-contract.md` |
| 当前状态 | 已完成并合并（PR #20，合并提交 `50e5f00815272b30ccd1948cacdebfe8c4b8f351`） |
| 最终确认人 | 段欣懿 |

## 一、任务目标

为数字大屏一期提供平台管理员可用的真实统计快照和 WebSocket 实时更新通道。

完成后必须形成：

```text
平台管理员请求统计快照
  → 页面拿到真实数据
  → 后端建立一次性 WebSocket ticket
  → WebSocket 每 30 秒推送最新快照
  → 权限失效时主动断开
```

接口字段、统计口径、消息结构和错误语义以
[`dashboard-contract.md`](../dashboard-contract.md) 为唯一事实源。

## 二、前置依赖

- 从最新 `feature/business-core` 创建分支。
- 已阅读 `AGENTS.md`、公共业务契约、本任务卡和 `dashboard-contract.md`。
- PR #13 和 PR #17 已合并。
- 使用现有 MySQL、Redis、Spring Security、MyBatis-Plus 和统一审计能力。
- 不等待队伍、作品和评审模块完成。

## 三、本次范围

- `GET /api/v1/dashboard/overview` 全量快照。
- `POST /api/v1/dashboard/ws-ticket` 一次性连接票据。
- `/ws/dashboard` 原生 WebSocket。
- 30 秒聚合与广播。
- `dashboard:view` 功能权限和 GLOBAL 数据范围校验。
- 统计 SQL、DTO、VO、Service、Mapper 和测试。
- 空数据、Redis 不可用、票据失效、权限撤销和重复连接处理。

## 四、允许修改的文件

后端新增目录：

```text
backend/src/main/java/com/campus/contest/module/dashboard/
backend/src/test/java/com/campus/contest/module/dashboard/
```

如 WebSocket 配置必须放在公共配置目录，只能新增独立配置类，并在 PR 中说明原因，不得修改认证、RBAC、CORS、SecurityConfig 和现有业务模块。

## 五、禁止修改

- `docs/api/openapi-v1.json`
- `docs/api/接口汇总.md`
- `docs/sql/`
- `docs/db/`
- `docs/business/business-core/dashboard-contract.md`
- `AGENTS.md`
- `backend/pom.xml`
- `backend/src/main/resources/application*.yml`
- 现有 `module/contest`、`module/organization`、`module/system` 业务实现
- 前端 `frontend/src/router/index.ts`
- 前端 `frontend/src/api/request.ts`
- 前端 `frontend/src/stores/auth.ts`
- 前端 `frontend/src/layouts/`

需要增加依赖或修改公共配置时，在 PR 描述中给出准确改动，由段欣懿处理。

## 六、后端实现要求

1. REST 和 WebSocket 使用同一个快照构造服务，禁止复制两套统计逻辑。
2. 接口同时校验 `dashboard:view` 和 `GLOBAL` 数据范围。
3. 统计查询必须在服务端完成，不把全量明细返回前端再计算。
4. 所有统计查询使用参数绑定，禁止字符串拼接用户输入。
5. 有效学校、用户、角色、届次和省份字典口径必须与共享契约一致。
6. 当前没有报名、队伍、作品和评审指标，不得在代码中伪造。
7. WebSocket 门票使用 Redis 原子消费，有效期 30 秒且只能使用一次。
8. WebSocket URL 中只允许出现一次性 ticket，禁止出现 JWT 或 Refresh Token。
9. WebSocket 每 30 秒广播一次，不为每个连接重复执行整套 SQL。
10. 连接建立、关闭、异常和广播失败必须有受控日志，不记录 ticket。
11. 权限变更使当前 WebSocket 失效时主动关闭连接。
12. Redis 不可用导致 ticket 无法创建时返回 HTTP 503 和公共 `50003`。
13. 空数据返回真实的 `0` 或空数组。
14. 所有时间按 `Asia/Shanghai` 返回为带 `+08:00` 的 RFC 3339。

## 七、建议实现结构

```text
DashboardController
DashboardWebSocketConfig
DashboardWebSocketHandler
DashboardTicketService
DashboardOverviewService
DashboardMapper
DashboardOverviewVO
DashboardSummaryVO
DashboardContestOperationsVO
DashboardProvinceSchoolVO
DashboardUserRoleVO
DashboardDeadlineVO
DashboardWebSocketMessage
```

结构可以按实际需要精简，但 REST 与 WebSocket 不得产生两套口径。

## 八、测试要求

必须覆盖：

1. 管理员可以获得完整快照；
2. 非管理员或非 GLOBAL 用户返回 403；
3. 有效学校只统计启用且省份字典有效的学校；
4. 报名中届次只统计报名未截止的数据；
5. 省份学校分布和角色统计去重正确；
6. 空数据返回 0 和空数组；
7. ticket 有效期内可以使用一次；
8. ticket 过期、伪造和重复使用时握手失败；
9. WebSocket 连接成功后收到 `CONNECTED`；
10. 定时广播的 JSON 与 REST 快照结构一致；
11. Redis 不可用时 ticket 接口返回 503 和 `50003`；
12. 权限撤销后连接关闭；
13. WebSocket URL 和日志不出现 JWT 或 Refresh Token。

建议测试命令：

```powershell
mvn -B -Dtest=Dashboard*Test test
mvn -B test
```

## 九、提交证据

PR 描述必须包含：

1. 分支、HEAD 和基于公共基线的提交；
2. 修改文件清单；
3. REST 快照实际响应；
4. WebSocket ticket 请求和响应；
5. `CONNECTED` 和 `DASHBOARD_OVERVIEW` 消息样例；
6. 真实 MySQL 指标与页面显示值核对；
7. Redis ticket 单次消费和过期证据；
8. 权限拒绝和断线关闭证据；
9. 后端专项测试和完整测试结果；
10. 未解决问题及需要的公共改动。

## 十、停止点

- PR 完成后保持 Ready，不自行合并。
- 不开发数字大屏前端页面。
- 不修改公共路由、菜单、OpenAPI、SQL 或权限种子。
- 完成后把 HEAD、测试结果和接口样例交给段欣懿。
