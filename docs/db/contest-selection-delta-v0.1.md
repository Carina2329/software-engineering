# 竞赛赛题、项目申报与教师申请数据库增量设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.1 |
| 日期 | 2026-11-12 |
| 状态 | 设计草案，待组内评审；尚未生成或执行 SQL |
| 关联需求 | 《AI学科竞赛系统-功能需求说明书》V0.3 |
| 设计范围 | 届次赛题模式、自拟项目、教师指导申请、队伍邀请和选题变更 |

## 一、版本记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V0.1 | 2026-11-12 | 首次形成赛题、项目申报和教师申请数据库增量设计，替代原“每个赛题默认限量”的统一假设 |

本文件是设计增量说明，不是可执行 SQL。正式变更数据库前，需要组内评审并另行生成增量 SQL、回滚方案和迁移检查。

## 二、确定的新业务规则

1. 每个届次只能选择一种赛题模式：
   - `PRESET_UNLIMITED`：统一赛题、不限队伍数量；
   - `PRESET_LIMITED`：统一赛题、限制队伍数量；
   - `SELF_PROPOSED`：团队自拟项目；
   - `NO_TOPIC`：无赛题环节。
2. 不支持统一赛题与自拟项目混合模式。
3. 限额赛题默认采用 `REVIEW` 申请审核；`FIRST_COME` 只能由主办方明确启用。
4. 自拟项目必须填写项目名称、项目方向、项目简介；技术关键字和创新点简述可选。
5. 自拟项目默认需要管理员审核，主办方可按届次关闭审核。
6. 每支队伍同时只能有一个有效教师申请；被拒绝或撤回后可申请其他教师。
7. 不限制教师收到的待处理申请数量，只限制最终确认指导的队伍数。
8. 同一队伍报名截止前默认最多更换一次赛题或项目。

## 三、复用现有表

| 表 | 处理方式 |
| --- | --- |
| `contest` | 保持不变，作为竞赛主档 |
| `contest_edition` | 增量增加赛题环节配置 |
| `contest_topic` | 保留，用于管理员发布的统一赛题 |
| `team` | 保留，最终报名状态和已确认指导教师继续使用 |
| `team_member` | 保留，继续表示已加入队伍的成员关系 |
| `sys_user` | 保留教师最大可指导队伍数配置 |
| `work_file` | 保持不变，作品附件关联继续按作品版本管理 |

## 四、`contest_edition` 建议新增字段

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `topic_mode` | `VARCHAR(24)` | 是 | 无默认，迁移时显式回填 | 四选一赛题模式 |
| `topic_review_required` | `TINYINT` | 是 | `1` | 选题或自拟项目是否需要管理员审核 |
| `topic_allocation_mode` | `VARCHAR(16)` | 是 | `REVIEW` | 限额赛题分配方式：`REVIEW` / `FIRST_COME` |
| `topic_change_limit` | `TINYINT` | 是 | `1` | 报名截止前允许更换次数，范围 0-3 |

约束建议：

- `PRESET_UNLIMITED` 和 `SELF_PROPOSED` 不读取限额容量；
- `NO_TOPIC` 不得要求选题或项目申报；
- `FIRST_COME` 只允许和 `PRESET_LIMITED` 组合使用；
- 已产生选题数据后修改 `topic_mode` 必须经过兼容性校验。

## 五、`contest_topic` 修改

### 必改

- `max_teams` 从 `NOT NULL DEFAULT 1` 调整为允许 `NULL`；
- `NULL` 表示不限队伍数量；
- 只有 `PRESET_LIMITED` 模式要求 `max_teams` 为正整数；
- `chosen_count` 只统计已通过审核或已确认的预设赛题队伍，不统计待审核申请。

### 不建议修改

- 不增加 `purpose` 字段；
- 不把自拟项目写入 `contest_topic`，避免管理员赛题和团队申报数据混在一起。

## 六、新增 `team_topic_application`

用于统一保存预设赛题选择、自拟项目申报、审核状态和更换记录。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `team_id` | BIGINT | 队伍 |
| `edition_id` | BIGINT | 届次 |
| `selection_type` | VARCHAR(16) | `PRESET` / `SELF_PROPOSED` |
| `topic_id` | BIGINT NULL | 预设赛题；自拟项目为空 |
| `project_name` | VARCHAR(128) NULL | 自拟项目名称 |
| `direction` | VARCHAR(64) NULL | 自拟项目方向 |
| `summary` | VARCHAR(1000) NULL | 自拟项目简介 |
| `tech_keywords` | VARCHAR(512) NULL | 技术关键字，可选 |
| `innovation` | VARCHAR(1000) NULL | 创新点简述，可选 |
| `status` | VARCHAR(16) | `DRAFT`、`PENDING`、`APPROVED`、`REJECTED`、`WITHDRAWN` |
| `change_no` | TINYINT | 当前更换序号，首次为 0 |
| `reviewed_by` | BIGINT NULL | 审核人 |
| `reviewed_at` | DATETIME NULL | 审核时间 |
| `review_reason` | VARCHAR(500) NULL | 驳回或审核说明 |
| `created_at/updated_at` | DATETIME | 时间字段 |
| `deleted/deleted_at/version` | 现有规范字段 | 逻辑删除与乐观锁 |

约束和索引建议：

- `(team_id, edition_id, status)` 索引，用于查询当前申请；
- 应用层事务保证同一队伍只有一条 `PENDING` 或 `APPROVED` 当前申请；
- 同一队伍同一届次只能有一个最终有效的赛题或自拟项目；
- `PRESET` 要求 `topic_id` 非空且项目字段为空；
- `SELF_PROPOSED` 要求项目名称、方向、简介非空且 `topic_id` 为空；
- `change_no` 不得大于届次 `topic_change_limit`。

`team.topic_id` 的处理建议：

- 可以继续保留为“当前已通过的预设赛题”冗余指针，便于现有查询；
- 自拟项目或 `NO_TOPIC` 时 `team.topic_id` 为空；
- 新业务逻辑以 `team_topic_application` 的状态记录为准，禁止只依赖 `team.topic_id` 判断申报状态。

## 七、新增 `team_teacher_application`

用于支持教师双向选择和容量占用。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `team_id` | BIGINT | 队伍 |
| `teacher_id` | BIGINT | 被申请教师 |
| `status` | VARCHAR(16) | `PENDING`、`ACCEPTED`、`REJECTED`、`WITHDRAWN` |
| `reason` | VARCHAR(500) NULL | 拒绝或撤回说明 |
| `applied_at` | DATETIME | 申请时间 |
| `handled_at` | DATETIME NULL | 处理时间 |
| `created_at/updated_at` | DATETIME | 时间字段 |
| `deleted/deleted_at/version` | 现有规范字段 | 逻辑删除与乐观锁 |

约束和并发规则：

- 同一队伍同时只能存在一条 `PENDING` 申请；
- 同一教师可以收到多条 `PENDING` 申请，不设置待处理申请数量上限；
- 教师接受时在数据库事务中统计该教师当前 `ACCEPTED` 或已确认指导队伍数；
- 并发接受不得使教师最终指导队伍数超过 `sys_user.max_teams`；
- 教师可以通过独立状态暂停接收新申请；
- 只有被接受后才写入 `team.teacher_id`，拒绝和撤回不占名额。

如果后续确认“最大指导数应按届次配置”，应将容量从 `sys_user` 的全局值扩展为教师-届次配置；本轮不提前增加该表。

## 八、新增 `team_invitation`

现有 `team_member` 只记录已加入关系，无法完整表示邀请待确认和拒绝状态。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `team_id` | BIGINT | 队伍 |
| `invitee_user_id` | BIGINT | 被邀请学生 |
| `inviter_user_id` | BIGINT | 邀请人 |
| `status` | VARCHAR(16) | `PENDING`、`ACCEPTED`、`REJECTED`、`CANCELLED`、`EXPIRED` |
| `expire_at` | DATETIME NULL | 邀请过期时间 |
| `handled_at` | DATETIME NULL | 接受或拒绝时间 |
| `created_at/updated_at` | DATETIME | 时间字段 |
| `deleted/deleted_at/version` | 现有规范字段 | 逻辑删除与乐观锁 |

规则：

- 同一队伍对同一学生只能有一个待处理邀请；
- 接受邀请时重新校验队伍人数上限、学生届次唯一性和邀请有效期；
- 邀请接受并写入 `team_member` 后才算正式加入；
- 该流程是沟通后的确认机制，不使用 Redis 抢占。

## 九、事务与并发边界

| 操作 | 事务要求 |
| --- | --- |
| 教师接受指导申请 | 锁定/条件更新教师容量；写申请状态和 `team.teacher_id` |
| 接受队伍邀请 | 校验学生同届次唯一、队伍人数上限；写成员关系 |
| 限额赛题审核通过 | 校验容量；写申请状态并更新已确认数量 |
| 更换限额赛题 | 事务内完成新容量占用、申请切换和旧容量释放 |
| 无赛题作品提交 | 不校验 topic/application 状态 |

跨 MySQL 与 Redis 的操作不声称原子。如果后续为 `FIRST_COME` 使用 Redis 预占，必须提供数据库失败补偿和 Redis/MySQL 对账方案。

## 十、权限和接口影响

| 权限 | 用途 | 状态 |
| --- | --- | --- |
| `team:topic:select` | 队伍选择赛题或申报自拟项目 | 已有契约 |
| `team:topic:review` | 管理员审核赛题或自拟项目 | 建议新增 |
| `team:registration:submit` | 提交或撤回教师申请与报名 | 已有契约 |
| `team:approve` | 教师确认、管理员终审 | 已有契约 |
| `team:member:invite` | 队长邀请成员 | 建议新增 |
| `team:member:list` | 查询队伍成员和邀请 | 已有契约 |
| `team:invitation:respond` | 被邀请人接受或拒绝邀请 | 建议新增 |

OpenAPI V1.2.0 已增加 EP-079 至 EP-084。RBAC 初始化 SQL 尚未新增上述权限节点，必须在实现前单独评审和补充，禁止前端写死权限。

## 十一、迁移要求

正式生成 SQL 前必须确认：

1. 现有 `contest_edition` 的赛题模式如何按历史数据回填；
2. 现有 `contest_topic.max_teams` 是否代表真实业务容量；
3. 已有 `team.topic_id` 如何迁移为 `team_topic_application` 的已通过记录；
4. 已有 `team.teacher_id` 如何补齐教师接受记录；
5. `team_member` 历史数据如何初始化成员关系；
6. 所有迁移必须先备份并在独立库演练，禁止直接修改现有数据卷。
