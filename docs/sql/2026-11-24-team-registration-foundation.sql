-- 队长代办制报名组织公共基线（MySQL 8.0）
-- 版本：1.0.0（2026-11-24）
-- 前置：已执行 2026-11-18-multitenant-foundation.sql
-- 注意：执行前必须备份数据库；本脚本不创建测试账号或测试队伍。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_team_registration_preconditions//
CREATE PROCEDURE assert_team_registration_preconditions()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'team'
      AND column_name = 'member_count'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'team table must exist before team registration migration';
  END IF;

  IF EXISTS (SELECT 1 FROM team LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'team table is not empty; map legacy team statuses before migration';
  END IF;
END//
DELIMITER ;

CALL assert_team_registration_preconditions();
DROP PROCEDURE assert_team_registration_preconditions;

ALTER TABLE team
  ADD COLUMN current_registration_id BIGINT NULL
    COMMENT '当前或最近报名轮次 team_registration.id' AFTER teacher_id,
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '0=DRAFT,1=PENDING_TEACHER,2=PENDING_ADMIN,3=APPROVED,4=REJECTED,5=WITHDRAWN',
  ADD CONSTRAINT chk_team_status CHECK (status BETWEEN 0 AND 5),
  ADD KEY idx_team_current_registration (current_registration_id);

ALTER TABLE team_member
  ADD COLUMN edition_id BIGINT NULL COMMENT '所属届次快照' AFTER team_id,
  ADD COLUMN member_name VARCHAR(32) NULL COMMENT '成员姓名快照' AFTER user_id,
  ADD COLUMN school_id BIGINT NULL COMMENT '成员学校 organization.id' AFTER member_name,
  ADD COLUMN student_no VARCHAR(20) NULL COMMENT '成员学号，同届同校唯一' AFTER school_id,
  MODIFY COLUMN user_id BIGINT NULL COMMENT '平台账号；仅队长必须非空';

ALTER TABLE team_member
  ADD COLUMN active_edition_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status = 1 THEN edition_id ELSE NULL END
    ) STORED,
  ADD COLUMN active_school_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status = 1 THEN school_id ELSE NULL END
    ) STORED,
  ADD COLUMN active_student_no VARCHAR(20)
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status = 1 THEN student_no ELSE NULL END
    ) STORED,
  ADD COLUMN active_leader_team_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status = 1 AND is_leader = 1 THEN team_id ELSE NULL END
    ) STORED,
  ADD UNIQUE KEY uk_team_member_active_student
    (active_edition_id, active_school_id, active_student_no),
  ADD UNIQUE KEY uk_team_member_active_leader (active_leader_team_id),
  ADD KEY idx_team_member_edition (edition_id);

CREATE TABLE team_registration (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  team_id BIGINT NOT NULL COMMENT '队伍',
  edition_id BIGINT NOT NULL COMMENT '届次',
  round_no INT NOT NULL COMMENT '队伍内报名轮次',
  status VARCHAR(16) NOT NULL COMMENT '报名轮次状态',
  remark VARCHAR(500) DEFAULT NULL COMMENT '本轮报名说明',
  members_snapshot JSON NOT NULL COMMENT '提交时成员快照，最多 5 人',
  eligibility_snapshot JSON DEFAULT NULL COMMENT '提交时资格快照',
  quota_snapshot JSON DEFAULT NULL COMMENT '提交时学校名额快照',
  quota_held TINYINT NOT NULL DEFAULT 0 COMMENT '本轮是否实际占用名额',
  quota_school_id BIGINT DEFAULT NULL COMMENT '本轮占用名额的学校',
  reject_stage VARCHAR(16) DEFAULT NULL COMMENT 'TEACHER/ADMIN',
  reject_reason VARCHAR(500) DEFAULT NULL COMMENT '本轮驳回原因',
  submitted_at DATETIME NOT NULL COMMENT '提交时间',
  withdrawn_at DATETIME DEFAULT NULL COMMENT '撤回时间',
  decided_at DATETIME DEFAULT NULL COMMENT '最终审核时间',
  admin_reviewed_by BIGINT DEFAULT NULL COMMENT '管理员终审人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_registration_team_round (team_id, round_no),
  KEY idx_registration_edition_status (edition_id, status),
  KEY idx_registration_quota_school (edition_id, quota_school_id),
  CONSTRAINT chk_registration_status CHECK (
    status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED', 'REJECTED', 'WITHDRAWN')
  ),
  CONSTRAINT chk_registration_reject_stage CHECK (
    reject_stage IS NULL OR reject_stage IN ('TEACHER', 'ADMIN')
  ),
  CONSTRAINT chk_registration_quota_held CHECK (quota_held IN (0, 1)),
  CONSTRAINT chk_registration_members_json CHECK (
    JSON_TYPE(members_snapshot) = 'ARRAY'
    AND JSON_LENGTH(members_snapshot) BETWEEN 1 AND 5
  ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='队长代办制报名轮次、审核和快照';

ALTER TABLE team
  ADD CONSTRAINT fk_team_current_registration
    FOREIGN KEY (current_registration_id) REFERENCES team_registration (id);

CREATE TABLE team_registration_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  registration_id BIGINT NOT NULL COMMENT '报名轮次',
  file_id BIGINT NOT NULL COMMENT 'file.id',
  file_type VARCHAR(24) NOT NULL COMMENT '报名材料类型',
  sort_no TINYINT NOT NULL DEFAULT 0 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  UNIQUE KEY uk_registration_attachment_file (registration_id, file_id),
  KEY idx_registration_attachment (registration_id),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='报名轮次与现有文件元数据的关联';

CREATE TABLE team_teacher_application (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  registration_id BIGINT NOT NULL COMMENT '报名轮次',
  teacher_id BIGINT NOT NULL COMMENT '指导教师',
  status VARCHAR(16) NOT NULL COMMENT 'PENDING/ACCEPTED/REJECTED/WITHDRAWN',
  reason VARCHAR(500) DEFAULT NULL COMMENT '拒绝或撤回原因',
  applied_at DATETIME NOT NULL COMMENT '申请时间',
  handled_at DATETIME DEFAULT NULL COMMENT '处理时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  active_pending_registration_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status = 'PENDING' THEN registration_id ELSE NULL END
    ) STORED,
  UNIQUE KEY uk_teacher_application_pending (active_pending_registration_id),
  KEY idx_teacher_application_registration (registration_id, status),
  KEY idx_teacher_application_teacher (teacher_id, status),
  CONSTRAINT chk_teacher_application_status CHECK (
    status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
  ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='每个报名轮次的教师选择与审核历史';

CREATE TABLE edition_entry_grant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  target_edition_id BIGINT NOT NULL COMMENT '目标届次',
  grant_type VARCHAR(16) NOT NULL COMMENT 'INVITED/QUALIFIED',
  leader_user_id BIGINT DEFAULT NULL COMMENT '已知队长账号',
  leader_name VARCHAR(32) NOT NULL COMMENT '预登记队长姓名',
  school_id BIGINT NOT NULL COMMENT '队长学校 organization.id',
  student_no VARCHAR(20) NOT NULL COMMENT '队长学号',
  source_edition_id BIGINT DEFAULT NULL COMMENT '晋级来源届次',
  source_team_code VARCHAR(16) DEFAULT NULL COMMENT '晋级来源队伍编号',
  target_team_id BIGINT DEFAULT NULL COMMENT '匹配后的目标队伍',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  reason VARCHAR(500) DEFAULT NULL COMMENT '邀请、晋级或撤销说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT DEFAULT NULL,
  revoked_at DATETIME DEFAULT NULL,
  revoked_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  active_target_edition_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN target_edition_id ELSE NULL END
    ) STORED,
  active_school_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN school_id ELSE NULL END
    ) STORED,
  active_student_no VARCHAR(20)
    GENERATED ALWAYS AS (
      CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN student_no ELSE NULL END
    ) STORED,
  UNIQUE KEY uk_entry_grant_active_leader (
    active_target_edition_id, active_school_id, active_student_no
  ),
  KEY idx_entry_grant_target_team (target_edition_id, target_team_id),
  KEY idx_entry_grant_pre_register (
    target_edition_id, leader_user_id, school_id, student_no
  ),
  CONSTRAINT chk_entry_grant_type CHECK (grant_type IN ('INVITED', 'QUALIFIED')),
  CONSTRAINT chk_entry_grant_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='比赛级邀请和晋级名单，可先于目标队伍导入';

CREATE TABLE edition_school_quota (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  edition_id BIGINT NOT NULL COMMENT '届次',
  school_id BIGINT NOT NULL COMMENT '学校 organization.id',
  max_teams INT DEFAULT NULL COMMENT 'NULL 表示不限量',
  used_teams INT NOT NULL DEFAULT 0 COMMENT '当前占用数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_quota_edition_school (edition_id, school_id),
  CONSTRAINT chk_quota_max CHECK (max_teams IS NULL OR max_teams > 0),
  CONSTRAINT chk_quota_used CHECK (used_teams >= 0),
  CONSTRAINT chk_quota_capacity CHECK (
    max_teams IS NULL OR used_teams <= max_teams
  ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='届次下各学校的报名队伍名额';

ALTER TABLE team_member
  MODIFY COLUMN edition_id BIGINT NOT NULL COMMENT '所属届次快照',
  MODIFY COLUMN member_name VARCHAR(32) NOT NULL COMMENT '成员姓名快照',
  MODIFY COLUMN school_id BIGINT NOT NULL COMMENT '成员学校 organization.id',
  MODIFY COLUMN student_no VARCHAR(20) NOT NULL COMMENT '成员学号，同届同校唯一';
