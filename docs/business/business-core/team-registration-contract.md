# 队长代办制报名组织共享契约

| 项目 | 内容 |
| --- | --- |
| 契约版本 | 1.0.0 |
| 冻结日期 | 2026-11-24 |
| 需求基线 | 《AI学科竞赛系统-功能需求说明书》V0.6 |
| 接口基线 | `docs/api/openapi-v1.json` |
| 数据库增量 | `docs/db/contest-selection-delta-v0.3.md` |
| 目标业务 | 队长创建队伍、维护成员快照、提交和撤回报名、教师确认、管理员终审 |

## 一、不可变业务边界

- 只有队长需要平台账号。
- 队长必须已有 `VERIFIED` 学校归属。
- 普通队员不需要平台账号，不产生登录、邀请或确认数据。
- 队长统一填写、修改、提交和撤回报名。
- 队长固定为创建队伍的平台账号，不支持转让。
- 不支持队伍解散、队员自行退出、邀请入队或申请入队。
- 不提供教师暂停接收申请功能。
- 当前版本删除 EP-030、EP-081、EP-082、EP-083、EP-084。

## 二、流程

```text
队长创建队伍
→ DRAFT
→ 填写本人和队员资料
→ 上传报名材料
→ 选择教师并提交
→ PENDING_TEACHER
→ 教师确认
→ PENDING_ADMIN
→ 管理员终审
→ APPROVED / REJECTED
```

驳回或撤回后：

```text
REJECTED / WITHDRAWN
→ 队长修改成员、附件或说明
→ 重新提交，生成新的报名轮次
→ PENDING_TEACHER
```

## 三、队伍状态

`team.status` 数字映射固定为：

| 状态 | 数字值 | 允许操作 |
| --- | ---: | --- |
| `DRAFT` | 0 | 修改队名和成员、提交报名 |
| `PENDING_TEACHER` | 1 | 只读，可撤回 |
| `PENDING_ADMIN` | 2 | 只读，可撤回 |
| `APPROVED` | 3 | 只读，成员冻结 |
| `REJECTED` | 4 | 修改成员、附件或说明后直接重提 |
| `WITHDRAWN` | 5 | 修改成员、附件或说明后重提 |

不允许：

- `DISBANDED`
- `PENDING_REVIEW`
- 邀请或队员账号状态
- 队长转让状态

## 四、数据归属

### `team` 聚合信息

- `edition_id`
- `team_name`
- `team_code`
- `leader_id`
- `teacher_id`
- `status`
- `current_registration_id`
- `member_count`
- `version`

`team` 不保存报名说明、驳回阶段、驳回原因、成员快照、资格快照或名额快照。

### `team_member` 当前成员

- `team_id`
- `edition_id`
- `user_id`，队长非空，其他成员可为空
- `member_name`
- `school_id`
- `student_no`
- `is_leader`
- `status`
- `version`

最少收集姓名、学校、学号。不得采集身份证号等无关信息。

同一学生届次唯一性使用：

```text
UNIQUE(
  ACTIVE edition_id,
  ACTIVE school_id,
  ACTIVE normalized student_no
)
```

### `team_registration` 报名轮次

- `team_id`
- `edition_id`
- `round_no`
- `status`
- `remark`
- `members_snapshot`
- `eligibility_snapshot`
- `quota_snapshot`
- `quota_held`
- `reject_stage`
- `reject_reason`
- `submitted_at`
- `withdrawn_at`
- `decided_at`
- `admin_reviewed_by`
- `version`

规则：

- 每轮保存成员 JSON 快照，最多 5 人。
- `reject_stage` 只能是 `TEACHER` 或 `ADMIN`。
- `team_registration` 不保存 `teacher_application_id`。
- 当前驳回信息从 `team.current_registration_id` 对应轮次读取。

### `team_teacher_application`

只通过 `registration_id` 关联报名轮次，不建立反向引用。

状态为：

```text
PENDING / ACCEPTED / REJECTED / WITHDRAWN
```

### `team_registration_attachment`

必须使用明确报名轮次 ID：

```text
registration_id + file_id + file_type + sort_no
```

不得使用 `team_version` 区分附件轮次。

## 五、审核与截止时间

- 报名截止只在 EP-033 提交时检查。
- 截止前成功提交的轮次，截止后仍可正常审核。
- EP-034 终审不重新检查报名截止时间。
- 终审重新检查资格、成员快照、学校、学号、省份、管理员数据范围和 `PENDING_ADMIN` 状态。
- 终审发现资格失效时按 `ADMIN` 驳回。
- 已通过报名后的资格撤销不自动删除报名，转管理员人工处理。

## 六、教师申请事务

| 操作 | 教师申请 | `team.teacher_id` | 名额 |
| --- | --- | --- | --- |
| 教师拒绝 | `REJECTED` | 清空 | 释放 |
| 管理员驳回 | 保留本轮 `ACCEPTED` 历史 | 清空 | 释放 |
| 队长撤回 | `WITHDRAWN` | 清空 | 释放 |
| 新轮次教师接受 | 新申请 `ACCEPTED` | 写入新教师 | 保持占用 |

管理员驳回后的教师接受记录不计入当前指导容量，因为对应轮次已 `REJECTED`。

## 七、资格与名额

### 资格判断

- `visibility` 决定能否看到比赛。
- `registration_open` 只决定时间窗口。
- `can_register` 由后端结合 `OPEN / INVITED / QUALIFIED`、资格名单、成员、学校和名额计算。

资格不满足时 EP-033 返回明确的 `register_state` 和 HTTP 409，不创建报名轮次，不进入 `REJECTED`。

### 资格名单

`edition_entry_grant` 只限制 `ACTIVE` 记录唯一：

```text
UNIQUE(
  ACTIVE target_edition_id,
  ACTIVE school_id,
  ACTIVE normalized student_no
)
```

`REVOKED` 记录不参与唯一性。撤销后允许重新导入 ACTIVE 资格。

预登记以目标届次、队长学校、队长学号匹配；有账号时保存 `leader_user_id`。队伍创建后优先按账号，否则按学校+学号匹配，条件写入 `target_team_id`。已绑定其他队伍时返回 409，不自动改绑。

### 学校名额

- 无名额记录或 `max_teams IS NULL`：不限量。
- 有限量轮次在 EP-033 成功时原子占用一次。
- 教师拒绝、管理员驳回、队长撤回时原子释放一次。
- `quota_held` 保证每个轮次最多占用和释放一次。
- 跨校队伍占用队长已审核学校名额。

## 八、附件权限

EP-033 创建附件映射前必须校验：

1. 文件存在、未删除、上传完成。
2. 当前队长有权使用该文件。
3. 文件不属于其他用户、队伍或无关业务。
4. MIME、扩展名和大小符合现有上传策略。
5. 文件用途与报名材料类型匹配。
6. 禁止通过猜测或伪造 `file_id` 关联私有文件。
7. 下载继续使用现有文件权限和 `TEAM / GUIDED / ALL` 数据范围。

## 九、事务边界

提交报名事务唯一负责人：队伍后端报名服务。

```text
锁定 team
→ 校验 team.version、状态和成员
→ 校验可见性、时间窗口和 can_register
→ 占用有限学校名额并设置 quota_held
→ 创建 team_registration 和成员/资格/名额快照
→ 创建附件映射
→ 创建教师申请
→ 更新 team.status/current_registration_id/version
→ 写审计
```

资格名额和文件模块只提供同一事务内可调用的校验、占用和释放方法，不拥有 EP-033 状态更新。

## 十、接口

保留并调整：

- EP-026 队伍列表
- EP-027 创建队伍
- EP-028 队伍详情和资格状态
- EP-029 修改队名和成员快照
- EP-031 成员列表
- EP-032 按 `member_id` 移出成员
- EP-033 提交或重提报名
- EP-034 教师/管理员两级审核
- EP-080 撤回当前报名轮次
- EP-018 新增届次学校名额配置
- EP-096 批量预登记、更新或撤销比赛级邀请/晋级资格
- EP-097 查询届次资格名单

当前删除：

- EP-030
- EP-081
- EP-082
- EP-083
- EP-084

删除编号不得复用，其他 EP 不重编号。
