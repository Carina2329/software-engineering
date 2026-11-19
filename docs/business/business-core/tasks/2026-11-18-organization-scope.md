# 正式任务卡：组织、学校与范围权限

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-ORG-SCOPE-001` |
| 认领人 | 罗同学 |
| 当前状态 | 第 1～4 阶段已验收；第 5 阶段待开始 |
| 业务域 | 平台公共基础 / 组织与范围权限 |
| 建议分支 | `feature/business-organization-scope` |
| PR 目标分支 | `feature/business-core` |
| 公共契约基线 | 最新 `feature/business-core`，业务契约 v1.4.3 |
| 协调与合入 | 段欣懿 |

本任务只允许从最新 `origin/feature/business-core` 开发，分支 `HEAD` 必须能追溯到上述基线。不得把尚未完成的个人档案、竞赛与届次或高并发分支作为依赖。

## 一、目标与本次小闭环

平台管理员可以维护组织和学校；登录用户可以从已启用、已审核的组织中选择学校；学校管理员可以确认本校用户；带范围的管理员角色在后端真正限制查询和写操作。

完成后必须形成：

```text
平台管理员创建学校/主办组织
  → 平台管理员授予首个范围管理员
  → 范围管理员在授权范围内授予赛事负责人或评委
  → 用户选择学校并提交归属
  → 学校管理员确认或驳回本校归属
  → 其他学校或省份无法读取、修改该范围数据
```

个人档案页面和竞赛页面不在本任务内。本任务只提供它们后续要复用的学校归属服务和范围判断能力。

## 二、冻结契约

接口字段、枚举、错误语义和数据库事实源以现有文件为准，不在本任务卡重复定义：

- `docs/api/openapi-v1.json`
- `docs/db/multitenant-foundation-v0.1.md`
- `docs/business/business-core/contract.md`

本期实现以下冻结接口：

| EP | 方法与路径 | 能力 | 权限 | 数据范围 | 审计 |
| --- | --- | --- | --- | --- | --- |
| EP-085 | `GET /schools` | 查询启用学校 | `school:list` | 登录用户 | 否 |
| EP-086 | `GET /organizations` | 分页查询组织 | `organization:list` | `GLOBAL / ORGANIZATION` | 否 |
| EP-087 | `POST /organizations` | 创建组织 | `organization:create` | `GLOBAL` | 是 |
| EP-088 | `GET /organizations/{organization_id}` | 查询组织详情 | `organization:list` | `GLOBAL / ORGANIZATION` | 否 |
| EP-089 | `PATCH /organizations/{organization_id}` | 修改组织 | `organization:update` | `GLOBAL` | 是 |
| EP-090 | `POST /role-assignments` | 授予范围角色 | `organization:role:assign` | `GLOBAL / ORGANIZATION / PROVINCE` | 是 |
| EP-091 | `PATCH /role-assignments/{assignment_id}` | 修改或撤销范围角色 | `organization:role:assign` | `GLOBAL / ORGANIZATION / PROVINCE` | 是 |
| EP-092 | `GET /school-affiliations` | 查询学校归属申请 | `school:affiliation:list` | `GLOBAL / ORGANIZATION` | 否 |
| EP-093 | `PATCH /school-affiliations/{user_id}` | 审核学校归属 | `school:affiliation:review` | `GLOBAL / ORGANIZATION` | 是 |

EP-087 和 EP-090 使用 `Idempotency-Key`。EP-089 和 EP-091 必须使用数据库中的真实 `version` 做乐观锁校验；旧版本返回冲突，不得用客户端值直接覆盖。

## 三、范围授权矩阵

| 操作人 | 可授予角色 | 可授予范围 |
| --- | --- | --- |
| 平台 `ADMIN` | 除全局 `ADMIN` 外的业务角色 | 指定组织、省份或赛事 |
| 学校管理员 | `EVENT_MANAGER`、`REVIEWER` | 本校或本校赛事 |
| 省级管理员 | `EVENT_MANAGER`、`REVIEWER` | 授权省份或该省赛事 |
| 国家级管理员 | `EVENT_MANAGER`、`REVIEWER` | 所属国家级组织或其赛事 |
| 赛事负责人 | 第 1 阶段不能继续授权 | 无 |

必须执行以下安全规则：

1. `SCHOOL_ADMIN`、`PROVINCE_ADMIN`、`NATIONAL_ADMIN` 只能由平台管理员授予。
2. EP-090、EP-091 永远不能授予全局 `ADMIN`。
3. 任何管理员都不能授予同级或更高级管理员。
4. `scope_id` 对应的组织、省份或赛事必须真实存在、处于有效状态，并在调用人的授权范围内。
5. 禁止信任客户端传入的任意 `scope_id`；数据范围和功能权限必须同时校验。
6. 过期、停用或已删除授权不得进入权限快照，也不得用于数据查询。
7. 角色或范围修改后复用现有会话撤销服务，不另写 Redis 撤销逻辑。
8. 数据库业务修改、角色授权和审计必须同事务提交；审计失败时整体回滚。
9. 事务提交成功后再撤销会话；会话撤销失败不能返回完全成功。

## 四、允许修改的文件

### 1. 新增组织与范围模块

允许在以下目录新增与本任务直接相关的 Controller、Service、Mapper、Entity、DTO、VO 和测试：

```text
backend/src/main/java/com/campus/contest/module/organization/
backend/src/test/java/com/campus/contest/module/organization/
```

组织、赛事范围 Mapper、范围权限判断和学校归属审核的实现都放在该模块内。个人档案和竞赛模块后续调用本模块服务，不复制范围判断。

### 2. 字典公共能力

允许在 `module/system` 新增：

```text
backend/src/main/java/com/campus/contest/module/system/entity/SysDictTypeDO.java
backend/src/main/java/com/campus/contest/module/system/entity/SysDictItemDO.java
backend/src/main/java/com/campus/contest/module/system/mapper/SysDictTypeMapper.java
backend/src/main/java/com/campus/contest/module/system/mapper/SysDictItemMapper.java
backend/src/main/java/com/campus/contest/module/system/service/DictionaryQueryService.java
backend/src/main/java/com/campus/contest/module/system/service/impl/DictionaryQueryServiceImpl.java
```

`DictionaryQueryService` 至少提供：

- 查询启用状态的字典项；
- 判断编码是否属于指定字典；
- 根据编码获取展示名称。

组织类型和省份字典只能从 `sys_dict_type`、`sys_dict_item` 读取，禁止在业务代码中硬编码省份列表。

### 3. 明确允许修改的共享文件

```text
backend/src/main/java/com/campus/contest/module/system/entity/SysUserDO.java
backend/src/main/java/com/campus/contest/module/system/entity/SysUserRoleDO.java
backend/src/main/java/com/campus/contest/module/system/service/impl/AuthorizationSnapshotServiceImpl.java
backend/src/test/java/com/campus/contest/module/system/service/impl/AuthorizationSnapshotServiceImplTest.java
```

共享文件只允许增加本任务冻结字段、映射和范围授权逻辑。修改后必须单独列出差异，说明为何不能放在 `module/organization` 内。

### 4. 可选前端文件

如本阶段需要最小管理页面，只允许新增组织业务自己的 API 类型和页面组件。`router/index.ts`、`layouts/`、`request.ts`、`auth.ts`、`main.ts`、`App.vue` 仍由段欣懿统一合入，罗同学只能提交路由和菜单接入建议。

## 五、禁止修改

罗同学不得直接修改：

```text
backend/src/main/java/com/campus/contest/module/system/controller/UserController.java
backend/src/main/java/com/campus/contest/module/system/service/ProfileService.java
backend/src/main/java/com/campus/contest/module/system/service/impl/ProfileServiceImpl.java
backend/src/main/java/com/campus/contest/module/system/vo/CurrentUserVO.java
backend/src/main/java/com/campus/contest/module/system/service/UserAccessService.java
backend/src/main/java/com/campus/contest/module/system/service/impl/UserAccessServiceImpl.java
frontend/src/router/index.ts
frontend/src/layouts/
```

同样不得修改：

- `docs/api/openapi-v1.json`、接口汇总和归档；
- 公共 SQL、数据库增量、回滚脚本、权限种子、菜单种子和开发种子；
- 认证登录、JWT、Refresh Token、Redis 会话撤销核心；
- `pom.xml`、application 配置、RabbitMQ、AI 和无关业务模块；
- EP-003、EP-004 或个人档案 Controller、DTO、VO。

EP-003/EP-004 仍由刘同学的档案任务维护。罗同学提供中立的 `SchoolAffiliationService`，由档案任务后续调用；不能直接修改 EP-004 偷跑联调。

## 六、开发分阶段与门禁

### 第 1 阶段：数据映射和字典层

本阶段只完成：

- `SysUserDO`、`SysUserRoleDO` 与现有数据库字段准确映射；
- `AuthorizationSnapshotServiceImpl` 能读取范围授权所需字段；
- 组织、赛事范围查询所需 Mapper；
- 字典 Entity、Mapper、Service；
- 为上述映射、字典查询和不进入快照的失效授权写单元测试或集成测试。

第 1 阶段结束时，罗同学必须汇报文件清单、实际测试命令与结果、字段映射核对结果。未经段欣懿书面确认，不得进入第 2 阶段。

### 第 2 阶段：统一范围权限层

- 加载当前用户有效授权；
- 提供组织、省份、赛事范围判断；
- 处理过期、停用和删除授权；
- 提供稳定的 `403`、`404` 语义，避免跨组织数据泄露。

### 第 3 阶段：EP-086～EP-089 组织主档

- 学校、省级主办方、国家级主办方和平台组织；
- 组织类型、省份、状态和版本；
- 平台管理员创建、查询和修改；
- 停用不物理删除历史数据；
- 创建和修改与审计同事务。

### 第 4 阶段：EP-090～EP-091 范围角色

- 按授权矩阵授予、修改和撤销角色；
- 校验目标用户、角色等级、`scope_id` 和调用人范围；
- 数据库提交后撤销目标用户旧会话；
- 幂等、乐观锁、审计和失败语义完整。

公共 RBAC 已明确：EP-007 只替换 `GLOBAL/0` 基础角色，不得删除或覆盖组织、省份和赛事范围授权。EP-090/EP-091 是本任务中范围授权的唯一写入口。第 4 阶段完成后必须先汇报文件、测试和接口证据，经段欣懿确认后才能进入第 5 阶段。

### 第 5 阶段：EP-085、EP-092、EP-093 学校查询和归属审核

- 登录用户只查询启用学校；
- 学校管理员只能查看和审核本校 `PENDING` 申请；
- 通过后为 `VERIFIED`，驳回后为 `REJECTED` 并保存原因、审核人、审核时间；
- 已确认学校不能由本人直接切换，驳回后可修改并重新提交；
- 学校归属审核与审计同事务。

### 第 6 阶段：测试和收口

- 补全权限拒绝、跨校、跨省、乐观锁、幂等和审计回滚测试；
- 运行后端完整测试和前端构建（如修改了前端）；
- 输出接口响应样例、测试截图、文件清单和未解决问题；
- PR 保持目标分支为 `feature/business-core`，不得直接推送 `develop`。

## 七、验收场景

1. 平台管理员创建 A 校、B 校、四川省省级主办方和湖南省省级主办方。
2. A 校管理员查询和修改时只能访问 A 校；直接请求 B 校数据返回 `403` 或不泄露存在性的 `404`。
3. 四川管理员只能访问四川省及其赛事；请求湖南省数据被拒绝。
4. 学生学校下拉只包含启用且有效的学校；停用学校不能新选。
5. 学生归属初始为 `PENDING`；A 校管理员确认后为 `VERIFIED`，记录审核人和时间。
6. A 校管理员不能审核 B 校申请；驳回必须填写原因。
7. EP-090、EP-091 不能授予全局 `ADMIN`，也不能授予同级或更高级管理员。
8. 过期、停用、删除授权以及伪造的 `scope_id` 不扩大权限。
9. 使用旧 `version` 修改组织或角色授权返回冲突，数据库版本不被覆盖。
10. 审计写入失败时组织或授权业务修改回滚。
11. 角色范围变更提交后撤销目标用户会话；撤销失败不返回完全成功。
12. 没有对应功能权限或数据范围时，后端返回拒绝，不能只依赖前端隐藏按钮。

## 八、完成证据

提交 PR 时必须提供：

1. 分支名、基于公共基线的提交和 PR 链接；
2. 修改文件清单，单独列出共享文件和新增公共能力；
3. 实际运行的构建、测试命令及通过数量；
4. EP-085～EP-093 的接口响应片段或页面截图；
5. 与 OpenAPI 和数据库字段的逐项映射结果；
6. 跨校、跨省、全局管理员授予拒绝、过期授权和乐观锁测试证据；
7. 仍需段欣懿合入的路由、菜单、权限种子或契约建议；
8. 未解决问题和需要协调的公共文件。

只有代码、没有实际运行结果，不算完成。

## 九、完成确认与后续

本任务全部验收通过后，由段欣懿确认并统一合入 `feature/business-core`。随后个人档案任务调用学校归属服务，竞赛任务使用组织、级别和范围权限继续返工。

在第 1 阶段门禁通过前，不生成个人档案与竞赛任务依赖本模块的联调任务。
