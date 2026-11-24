# 正式任务卡：队长代办制报名组织

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-TEAM-REGISTRATION-001` |
| 当前状态 | 待认领 |
| 当前负责人 | 待认领 |
| 目标分支 | `feature/business-team-registration` |
| PR 目标分支 | `feature/business-core` |
| 事实源 | V0.6 需求、`team-registration-contract.md`、`contest-selection-delta-v0.3.md`、最新 OpenAPI |

## 一、目标

完成队长代办制报名组织闭环：

```text
创建队伍
→ 队长填写全队成员
→ 提交报名和附件
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

### 阶段 2：报名轮次、附件和撤回

- EP-033 提交和重提。
- EP-080 撤回当前报名轮次。
- 创建 `team_registration`。
- 保存成员、资格和名额快照。
- 通过 `registration_id` 关联附件。
- 附件权限和类型校验。
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

- 队长填写和修改全队成员。
- 队长提交、撤回、查看驳回原因和重提。
- 教师确认。
- 管理员终审。
- 资格、名额、附件和并发异常展示。

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
V0.6 需求
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
com.campus.contest.module.work.service.RegistrationAttachmentPolicy
com.campus.contest.module.team.support.service.RegistrationEligibilityService
com.campus.contest.module.team.support.service.SchoolQuotaService
```

资格名额模块只提供：

```text
RegistrationEligibilityService.checkCanRegister(...)
SchoolQuotaService.occupy(...)
SchoolQuotaService.release(...)
```

文件模块只提供：

```text
RegistrationAttachmentPolicy.validateForCaptain(...)
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
- 附件按 registration_id 关联；不存在、他人私有、错误类型或超限文件均失败。
- 合法附件下载遵守 TEAM / GUIDED / ALL 数据范围。
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

- 完成阶段 1/2 后先交公共负责人审查，再继续阶段 3。
- 需要修改公共契约时停止并提交差异，不自行修改公共文件。
- PR 完成后保持 Ready，不自行合并。
