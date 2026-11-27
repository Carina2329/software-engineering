# 正式任务卡：报名组织阶段 4 前端闭环

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-TEAM-REGISTRATION-FRONTEND-001` |
| 当前状态 | 已完成，PR #32 已合并；公共接入 PR #34、#35 已完成 |
| 负责人 | 李同学，单一负责人 |
| 基线 | 前置任务 `BIZ-TEAM-FRONTEND-CONTRACT-001` 合并后的最新 `feature/business-core` |
| 目标分支 | `feature/business-team-registration-frontend` |
| PR 目标分支 | `feature/business-core` |
| 依赖 | 阶段 1～3 后端、教师选择接口、EP-028 扩展读取模型 |

## 一、目标

由单一负责人完成学生、教师、管理员三类角色的报名组织前端闭环，并完成真实页面联调。内部按页面拆分，不拆给多人。

不得使用假数据、内存 Store、独立 Axios 实例或绕过现有 `request.ts`。

## 二、工作包

### 工作包 A：队伍 API 和类型

允许新增或修改：

```text
frontend/src/api/team.ts
frontend/src/types/team.ts
```

覆盖接口：

- EP-026 队伍列表
- EP-027 创建队伍
- EP-028 详情和当前报名读取模型
- EP-029 修改队伍/成员
- EP-031 成员列表
- EP-032 移出成员
- EP-033 提交或重提报名
- EP-034 教师/管理员审核
- EP-080 撤回报名
- EP-096/097 资格名单维护和查询
- 前置任务提供的教师选择接口

通用要求：

- 所有写接口生成 `Idempotency-Key`，同一次重试复用同一个 Key。
- 修改接口使用服务端返回的最新 `version`。
- `can_register` 只使用后端结论，不在前端重复拼接资格规则。
- 统一处理 400、401、403、404、409、429、503。
- 409 区分版本冲突、状态冲突、名额满、重复资格和幂等冲突。

### 工作包 B：学生队伍中心

允许新增：

```text
frontend/src/views/team/TeamCenterPage.vue
frontend/src/views/team/TeamDetailPage.vue
frontend/src/components/team/MemberEditor.vue
frontend/src/components/team/RegistrationSubmitPanel.vue
frontend/src/components/team/RegistrationTimeline.vue
```

页面能力：

- 按届次查看本人队伍，创建 DRAFT 队伍。
- 最多填写 5 人；普通队员不需要账号。
- 修改姓名、学校、学号，移出普通队员，不允许移出队长。
- 使用前置任务提供的教师选择器提交报名。
- 展示 `can_register/register_state/register_reason`。
- PENDING_TEACHER/PENDING_ADMIN 可撤回。
- REJECTED 展示驳回阶段和原因，修改后直接重提。
- WITHDRAWN 修改后可重提。
- APPROVED 只读。
- 刷新后仍能展示当前轮次、指导教师和驳回信息。

### 工作包 C：教师确认页

允许新增：

```text
frontend/src/views/team/TeacherApprovalPage.vue
frontend/src/components/team/ApprovalDecisionDialog.vue
```

页面能力：

- 按届次查看当前教师的 PENDING_TEACHER 队伍。
- 查看队伍详情和成员快照。
- 通过后进入 PENDING_ADMIN。
- 拒绝必须填写原因。
- 容量满、轮次已变化或非本申请教师时展示服务端 409/403。

### 工作包 D：管理员终审

允许新增：

```text
frontend/src/views/admin/team/TeamApprovalPage.vue
frontend/src/views/admin/team/EntryGrantManagementPage.vue
frontend/src/components/team/EntryGrantBatchEditor.vue
frontend/src/components/team/SchoolQuotaEditor.vue
```

管理员队伍页：

- 按状态、队伍名称和指导教师筛选。
- 只审核 PENDING_ADMIN 队伍。
- 查看成员、资格和名额信息。
- 通过或填写原因驳回。
- 展示管理员数据范围外的 403 和不可见数据 404。

资格名单页：

- 按 ACTIVE/REVOKED 查询历史。
- 批量 UPSERT 和 REVOKE。
- 显示版本、目标队伍和撤销原因。
- 归档届次只读。

学校名额组件：

- 实现完整替换编辑器和 `max_teams=null` 不限量输入。
- 展示 `used_teams`，禁止把名额降到占用数以下。
- 空数组表示清除未占用配置。
- `SchoolQuotaEditor.vue` 由公共负责人在现有 `EditionEditorPage.vue` 中接入；任务负责人不得直接修改该公共页面。

## 三、公共文件边界

以下文件由段欣懿统一接入，任务负责人不得修改：

```text
frontend/src/api/request.ts
frontend/src/router/index.ts
frontend/src/stores/auth.ts
frontend/src/main.ts
frontend/src/App.vue
frontend/src/layouts/AdminLayout.vue
frontend/src/views/admin/EditionEditorPage.vue
docs/api/openapi-v1.json
docs/api/接口汇总.md
docs/sql/
```

建议公共路由：

```text
/teams?edition_id=...
/teams/:teamId
/team-approvals?edition_id=...
/admin/team-approvals?edition_id=...
/admin/editions/:editionId/entry-grants
```

路由、菜单和权限由段欣懿最后接入。提交 PR 时需提供页面截图和建议路由说明。

## 四、验收场景

1. 队长创建队伍、填写 5 人并提交报名信息。
2. 第 6 人不能录入，同校同学号不能重复。
3. 不满足资格时展示后端 `can_register=false` 和原因。
4. 教师通过后学生看到 PENDING_ADMIN；教师拒绝后学生看到原因并可重提。
5. 管理员通过后队伍只读；管理员驳回后队长可修改并重提。
6. 撤回后状态、教师和名额信息正确刷新。
7. 版本冲突返回 409 并刷新详情，不静默覆盖。
8. 跨校或越权操作返回 403，范围外资源返回 404。
9. 管理员资格批量维护和归档只读正常。
10. 学校名额不能低于已占用数，空数组清除非占用配置。
11. 所有页面刷新可恢复，不依赖本地临时状态。
12. `npm run build` 通过；后端全量测试和真实 MySQL 测试通过。

## 五、停止点

- 前置任务未合并前，可以开发 API/类型和学生草稿页面，但不得用临时教师接口或手填教师 ID 完成报名提交。
- 完成前端页面后停止，不自行改公共路由、菜单、权限种子或 OpenAPI。
- 公共接入完成后先交段欣懿做页面联调和最终验收。

## 六、最终结果

- PR #32 合并提交：`8e360de`。
- 公共接入 PR #34 合并提交：`399b08e`。
- 名称选择与审核历史 PR #35 合并提交：`e8dc7b1`。
- 学生、教师、管理员页面已完成真实联调。
- 最终报名闭环队伍状态为 `APPROVED`，审核历史可继续查看。
