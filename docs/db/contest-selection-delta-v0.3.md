# 队长代办制报名组织数据库增量设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.3 |
| 日期 | 2026-11-24 |
| 状态 | 公共契约冻结，待生成正式迁移脚本 |
| 前置 | `contest-selection-delta-v0.2.md`、多组织平台公共基线 |
| 关联契约 | `docs/business/business-core/team-registration-contract.md` |

## 一、与 V0.2 的关系

本版本保留赛题、项目申报和教师指导容量设计，但替代 V0.2 中以下内容：

- 删除 `team_invitation`。
- 删除邀请状态和邀请并发设计。
- `team_member` 改为参赛成员快照。
- 新增 `team_registration` 报名轮次。
- 附件通过 `registration_id` 关联。
- 新增资格名单和学校名额。
- 明确队长代办制和教师两级审核事务。

## 二、修改 `team`

新增：

```sql
ALTER TABLE team
  ADD COLUMN current_registration_id BIGINT NULL
    COMMENT '当前或最近报名轮次 team_registration.id',
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '0=DRAFT,1=PENDING_TEACHER,2=PENDING_ADMIN,3=APPROVED,4=REJECTED,5=WITHDRAWN';
```

约束：

```sql
CHECK (status BETWEEN 0 AND 5)
KEY idx_team_current_registration (current_registration_id)
```

`leader_id` 创建后不可通过业务接口修改。

## 三、修改 `team_member`

```sql
ALTER TABLE team_member
  ADD COLUMN edition_id BIGINT NULL,
  ADD COLUMN member_name VARCHAR(32) NULL,
  ADD COLUMN school_id BIGINT NULL,
  ADD COLUMN student_no VARCHAR(20) NULL,
  MODIFY COLUMN user_id BIGINT NULL;
```

迁移顺序：

1. 从 `team.edition_id` 回填 `team_member.edition_id`。
2. 对已有 `user_id` 回填姓名、学校和学号快照。
3. 校验所有有效成员都有学校和非空学号。
4. 将 `edition_id/member_name/school_id/student_no` 改为 NOT NULL。

活跃唯一性：

```sql
active_edition_id BIGINT
  GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND status = 1 THEN edition_id ELSE NULL END
  ) STORED,

active_school_id BIGINT
  GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND status = 1 THEN school_id ELSE NULL END
  ) STORED,

active_student_no VARCHAR(20)
  GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND status = 1 THEN student_no ELSE NULL END
  ) STORED,

UNIQUE KEY uk_team_member_active_student
  (active_edition_id, active_school_id, active_student_no)
```

保留 `uk_team_member(team_id, user_id)`，但不得将其作为跨队伍唯一性依据。

## 四、新增 `team_registration`

```sql
CREATE TABLE team_registration (
  id BIGINT NOT NULL AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  edition_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  remark VARCHAR(500) NULL,
  members_snapshot JSON NOT NULL,
  eligibility_snapshot JSON NULL,
  quota_snapshot JSON NULL,
  quota_held TINYINT NOT NULL DEFAULT 0,
  quota_school_id BIGINT NULL,
  reject_stage VARCHAR(16) NULL,
  reject_reason VARCHAR(500) NULL,
  submitted_at DATETIME NOT NULL,
  withdrawn_at DATETIME NULL,
  decided_at DATETIME NULL,
  admin_reviewed_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_registration_team_round (team_id, round_no),
  KEY idx_registration_edition_status (edition_id, status),
  CONSTRAINT chk_registration_status CHECK (
    status IN ('PENDING_TEACHER','PENDING_ADMIN','APPROVED','REJECTED','WITHDRAWN')
  ),
  CONSTRAINT chk_registration_reject_stage CHECK (
    reject_stage IS NULL OR reject_stage IN ('TEACHER','ADMIN')
  ),
  CONSTRAINT chk_registration_quota_held CHECK (quota_held IN (0,1)),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`members_snapshot` 最多 5 个对象，应用层校验。

## 五、新增 `team_registration_attachment`

```sql
CREATE TABLE team_registration_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  registration_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  file_type VARCHAR(24) NOT NULL,
  sort_no TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  UNIQUE KEY uk_registration_file (registration_id, file_id),
  KEY idx_registration_attachment (registration_id),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

文件本体继续使用 `file` 表，不使用 `work_file`。

## 六、新增 `team_teacher_application`

```sql
CREATE TABLE team_teacher_application (
  id BIGINT NOT NULL AUTO_INCREMENT,
  registration_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(500) NULL,
  applied_at DATETIME NOT NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME NULL,
  version INT NOT NULL DEFAULT 0,
  KEY idx_teacher_application_registration (registration_id, status),
  KEY idx_teacher_application_teacher (teacher_id, status),
  CONSTRAINT chk_teacher_application_status CHECK (
    status IN ('PENDING','ACCEPTED','REJECTED','WITHDRAWN')
  ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

同一报名轮次只能一条 PENDING 记录，使用活跃生成列唯一索引或事务内条件插入保证。

## 七、新增 `edition_entry_grant`

```sql
CREATE TABLE edition_entry_grant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  target_edition_id BIGINT NOT NULL,
  grant_type VARCHAR(16) NOT NULL,
  leader_user_id BIGINT NULL,
  leader_name VARCHAR(32) NOT NULL,
  school_id BIGINT NOT NULL,
  student_no VARCHAR(20) NOT NULL,
  source_edition_id BIGINT NULL,
  source_team_code VARCHAR(16) NULL,
  target_team_id BIGINT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  reason VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  revoked_at DATETIME NULL,
  revoked_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

ACTIVE 唯一索引：

```sql
active_target_edition_id BIGINT GENERATED ALWAYS AS (
  CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN target_edition_id ELSE NULL END
) STORED,
active_school_id BIGINT GENERATED ALWAYS AS (
  CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN school_id ELSE NULL END
) STORED,
active_student_no VARCHAR(20) GENERATED ALWAYS AS (
  CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN student_no ELSE NULL END
) STORED,
UNIQUE KEY uk_entry_grant_active_leader (
  active_target_edition_id, active_school_id, active_student_no
)
```

REVOKED 记录生成列为 NULL，不影响重新导入。

## 八、新增 `edition_school_quota`

```sql
CREATE TABLE edition_school_quota (
  id BIGINT NOT NULL AUTO_INCREMENT,
  edition_id BIGINT NOT NULL,
  school_id BIGINT NOT NULL,
  max_teams INT NULL,
  used_teams INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_quota_edition_school (edition_id, school_id),
  CONSTRAINT chk_quota_max CHECK (max_teams IS NULL OR max_teams > 0),
  CONSTRAINT chk_quota_used CHECK (used_teams >= 0),
  CONSTRAINT chk_quota_capacity CHECK (
    max_teams IS NULL OR used_teams <= max_teams
  ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

未配置行或 `max_teams IS NULL` 时不限流，也不占用。

## 九、事务和约束

- 接受教师：锁定届次和当前申请，统计有效 PENDING_ADMIN/APPROVED 轮次。
- 提交报名：锁定 team，条件占用名额，创建轮次、快照、附件和教师申请。
- 名额释放：仅当 `quota_held=1` 时减一次并置 0。
- 提交/撤回：使用 team 和 registration 的 version 条件更新。
- 所有关键写操作与 `sys_oper_log` 同事务。

## 十、回滚

回滚前置：

- 无业务代码依赖新增字段和表；
- 已备份数据库；
- 已确认没有待处理报名轮次。

回滚顺序：

1. 删除权限种子新增权限。
2. 删除 `edition_school_quota`。
3. 删除 `edition_entry_grant`。
4. 删除 `team_registration_attachment`。
5. 删除 `team_teacher_application`。
6. 删除 `team_registration`。
7. 删除 `team.current_registration_id`。
8. 恢复 `team.status` 默认值和注释。
9. 删除 `team_member` 快照字段和活跃唯一索引。
10. 若旧数据无法恢复 `user_id NOT NULL`，停止回滚并交人工处理。
