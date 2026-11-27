# 正式任务卡：报名组织前端前置契约与读取能力

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-TEAM-FRONTEND-CONTRACT-001` |
| 当前状态 | 已完成，PR #31 已合并 |
| 负责人 | 段欣懿 |
| 基线 | `feature/business-core@692f630` |
| 目标分支 | `codex/team-registration-frontend-contract` |
| 依赖 | V0.6、报名组织契约、EP-028、EP-033、EP-034、EP-096/097 |
| 后续依赖 | `BIZ-TEAM-REGISTRATION-FRONTEND-001` |

## 一、目标

补齐阶段 4 前端无法自行解决的公共读取能力。本任务只完成后端读取模型、教师选择接口和公共 OpenAPI 契约，不实现前端页面。

## 二、必须补齐的能力

### 1. 指导教师选择接口

新增最小化教师选择接口：

```text
GET /api/v1/editions/{edition_id}/available-teachers
```

查询参数：

```text
keyword  可选，按教师姓名或用户名查询
page     默认 1
size     默认 20，最大 50
```

权限与范围：

- 权限沿用 `team:registration:submit`。
- 只允许具有学生身份、且在当前届次可创建或提交报名的队长调用。
- 只返回启用、学校归属 VERIFIED 且具有 `TEACHER` 角色的账号。
- 只返回 `id`、`real_name`、`school_id`、`school_name`、`college`、`title`。
- 不返回用户名、邮箱、手机号、密码哈希或其他账号敏感信息。
- 关键词为空时允许分页浏览；不得调用全局用户管理接口。

响应字段需冻结到 OpenAPI 和接口汇总，前端不得用全局 `/users` 代替。

### 2. EP-028 当前报名读取模型

扩展队伍详情响应，保证队长刷新页面后仍能展示当前报名轮次信息：

```text
current_registration_status
current_reject_stage
current_reject_reason
current_teacher_id
current_teacher_name
current_teacher_application_status
```

规则：

- 仅从 `team.current_registration_id` 关联的当前轮次读取。
- 仅对已通过 `requireReadable` 的队长、教师、管理员返回。
- `current_teacher_id/name` 从当前轮次教师申请读取；未选择或已撤回时为空。
- 驳回原因和阶段来自当前轮次的 `reject_stage/reject_reason`。
- 报名阶段不返回附件，作品文件留到后续作品提交流程。

### 3. 公共契约同步

- 更新 `docs/api/openapi-v1.json`。
- 更新 `docs/api/接口汇总.md`。
- 如需新增错误码或权限，停止并提交差异，不自行扩展。
- 不新增表；不修改 V0.6 主体流程。

## 三、验收

1. 队长可按姓名选择教师，学生无法调用全局用户列表。
2. 停用、非教师、学校未审核账号不会出现在教师选择结果。
3. EP-028 刷新后仍返回当前报名状态、教师申请状态、驳回阶段和原因。
4. 队长、教师、管理员范围和 404/403 语义保持现有规则。
5. Full backend tests、真实 MySQL 测试和前端现有构建均不受影响。

## 四、停止点

- 完成后交前端任务负责人消费。
- 未冻结教师选择接口前，学生报名页面不得用教师 ID 手工输入或全局用户列表顶替。
- 未补齐 EP-028 读取模型前，不得声称“驳回后查看原因并重提”闭环已完成。

## 五、交付结果

- 新增 `GET /api/v1/editions/{edition_id}/available-teachers`，对应 EP-098。
- EP-028 增加当前报名状态、驳回阶段/原因和教师选择/申请状态；不返回报名附件。
- OpenAPI 升级到 1.6.1，接口汇总同步。
- 全量测试：457 通过，0 失败，38 跳过。
- 真实 MySQL：`TeamFrontendContractMysqlTest` 和阶段 3 全部 MySQL 门控测试通过。

后续公共接入由 PR #34 完成，EP-099 教师筛选和名称选择由 PR #35 补齐。
