# 正式任务卡：队长代办制报名组织

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-TEAM-REGISTRATION-001` |
| 当前状态 | 已完成，公共基线 `e8dc7b1` |
| 当前负责人 | 李同学完成业务实现；段欣懿完成公共契约、接入和验收 |
| 目标分支 | 业务、前端和公共接入分支均已合并至 `feature/business-core` |
| PR 目标分支 | `feature/business-core` |
| 事实源 | V0.6.1 需求、`team-registration-contract.md`、`contest-selection-delta-v0.3.md`、最新 OpenAPI |

## 一、目标

完成队长代办制报名组织闭环：

```text
创建队伍
→ 队长填写全队成员
→ 提交报名信息
→ 教师确认
→ 管理员终审
→ 驳回修改重提或撤回
```

普通队员无平台账号，队员邀请、申请入队、接受邀请、队员自退、队长转让和队伍解散均不在范围内。

## 二、阶段

### 阶段 1：队伍与成员

- EP-027 创建 DRAFT 队伍和队长成员记录。
- EP-029 修改队名和成员快照。
- EP-031 查询成员快照。
- EP-032 按 `member_id` 移出成员。
- 1～5 人校验。
- 同届次同校同学号唯一。
- 队长固定。

### 阶段 2：报名轮次和撤回

- EP-033 提交和重提。
- EP-080 撤回当前报名轮次。
- 创建 `team_registration`。
- 保存成员、资格和名额快照。
- 报名阶段不接收、不保存报名附件。
- 幂等、version 和审计。

### 阶段 3：教师确认、资格名额和终审

- EP-034 教师确认。
- EP-034 管理员终审。
- 资格名单匹配和 ACTIVE 唯一规则。
- 学校名额占用/释放。
- EP-018 配置学校名额。
- EP-096/EP-097 预登记、撤销和查询资格名单。
- 管理员数据范围校验。
- 终审不重新检查报名截止时间。

### 阶段 4：前端和完整测试

正式任务拆分为：

1. 前置公共能力：`BIZ-TEAM-FRONTEND-CONTRACT-001`
   - `2026-11-25-team-registration-frontend-prerequisites.md`
   - 负责教师选择接口、EP-028 当前报名读取模型和公共契约。
2. 单负责人前端闭环：`BIZ-TEAM-REGISTRATION-FRONTEND-001`
   - `2026-11-25-team-registration-frontend.md`
   - 内部包含学生队伍、教师确认、管理员终审、资格名单和学校名额组件。

公共路由、菜单、权限种子和现有管理员页面由段欣懿统一接入。
阶段 4 已完成并由 PR #32、#34、#35 分三次合并：前端页面、公共接入、名称选择与审核历史查看。

## 三、文件和所有权

业务负责人可修改：

```text
backend/src/main/java/com/campus/contest/module/team/
backend/src/test/java/com/campus/contest/module/team/
frontend/src/api/team.ts
frontend/src/types/team.ts
frontend/src/views/team/
frontend/src/views/admin/team/
frontend/src/components/team/
```

公共负责人维护：

```text
V0.6.1 需求
team-registration-contract.md
contest-selection-delta-v0.3.md
docs/sql/
docs/api/openapi-v1.json
docs/api/接口汇总.md
权限种子
frontend/src/router/index.ts
frontend/src/api/request.ts
frontend/src/stores/auth.ts
frontend/src/main.ts
```

## 四、跨模块接口

报名事务唯一负责人是队伍后端报名服务。

以下支持接口已由公共基础负责人提供，李同学只消费：

```text
com.campus.contest.module.team.support.service.RegistrationEligibilityService
com.campus.contest.module.team.support.service.SchoolQuotaService
```

资格名额模块只提供：

```text
RegistrationEligibilityService.checkCanRegister(...)
SchoolQuotaService.occupy(...)
SchoolQuotaService.release(...)
```

这些能力必须在 EP-033 调用方事务内工作，不单独实现报名提交接口，不修改报名轮次或队伍状态。

## 五、禁止项

- 不恢复邀请制或队员账号。
- 不实现学生申请入队。
- 不实现队长转让、队伍解散、队员自退。
- 不实现教师暂停接收申请。
- 不使用 Redis 抢名额。
- 不使用 RabbitMQ。
- 不新增独立文件中心。
- 不新增 `PENDING_REVIEW` 或 `DISBANDED`。
- 不复用删除的 EP 编号。

## 六、验收

- 队长学校未审核不能创建或提交。
- 队员无需账号。
- 同届次同学校同学号跨队并发唯一。
- 最多 5 人。
- 学生申请入队接口和邀请接口当前不存在，历史归档仍可查。
- 提交后成员冻结，驳回后可编辑重提。
- 历史报名轮次 JSON 不变化。
- 报名提交无需上传附件，作品文件留到后续作品提交流程。
- 教师拒绝、管理员驳回、撤回正确清理 teacher_id 并释放名额。
- 重复释放不重复扣减。
- 截止后管理员仍可审核截止前提交的报名。
- 终审资格失效按 ADMIN 驳回。
- ACTIVE 资格唯一，REVOKED 后可重新导入。
- 资格可预登记并匹配后续创建队伍。
- 同一资格已绑定其他队伍时返回 409，不自动改绑。
- 管理员不能审核范围外队伍。
- 提交、审核、撤回并发有确定结果。
- 审计、幂等和 version 完整。
- 未配置学校名额或 max_teams 为空时不限量。
- 有限学校名额并发提交不超限。
- EP-030、EP-081～EP-084 当前不存在，其他 EP 编号未重排。

## 七、停止点

- 报名组织阶段已完成，不再向现有页面增加非验收必需的扩展功能。
- 后续只修复正式联调发现的明确 Bug，不作为并行开发主任务。
- 赛题、选题、作品提交和评审属于下一阶段，本任务不再继续扩展。

## 八、最终交付记录

### 合并 PR

| PR | 内容 | 合并提交 |
| --- | --- | --- |
| #25 | 队长代办制契约 | `ba3f636` |
| #26 | 资格、名额和报名支持能力 | `9e98d51` |
| #27 | 队伍、成员与报名轮次 | `ab39d36` |
| #28 | EP-018 学校名额 | `7b5d15f` |
| #29 | 教师确认与管理员终审 | `0c0fcf7` |
| #30 | 阶段 4 任务卡 | `692f630` |
| #31 | 教师选择与 EP-028 读取模型 | `77e9332` |
| #32 | 学生、教师、管理员前端闭环 | `8e360de` |
| #33 | 报名阶段移除附件要求 | `e3d62ca` |
| #34 | 公共路由、菜单、名额配置与 UI 统一 | `399b08e` |
| #35 | 名称选择器、教师筛选和审核历史 | `e8dc7b1` |

### 最终验收

- 全量后端测试：454 通过，0 失败，43 个环境门控跳过。
- 真实 MySQL 报名组织门控测试：5 通过，0 失败，0 跳过。
- 前端 `vue-tsc` 和 Vite 生产构建通过。
- 使用真实学生、教师、管理员账号完成：

```text
队长创建 2 人队伍
→ 提交报名
→ 教师确认
→ 管理员终审
→ APPROVED
```

- 审计记录完整写入 `TEAM_CREATE`、`TEAM_UPDATE`、`TEAM_REGISTRATION_SUBMIT`、
  `TEAM_TEACHER_APPROVE` 和 `TEAM_ADMIN_APPROVE`。
- 管理员可切换状态查看已通过和已驳回队伍；非待审核状态只显示“查看”。
- 学校、教师、届次和资格对象均提供名称选择，主要业务表单不再要求手工填写 ID。

### 已知边界

- `team_registration_attachment` 表仅保留历史兼容，新报名不写入。
- 作品附件留到后续作品提交流程，不属于本任务。
- 资格名单仍保留少量高级可选原始 ID 字段，用于跨届次预登记和历史匹配。
- 本轮未重新创建全新数据库，复用独立测试库 `contest_db_dashboard_e2e`；
  全新库初始化已在公共接入联调中验证。
