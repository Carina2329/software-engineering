-- 多组织、学校、范围权限与比赛归属公共基线（MySQL 8.0）
-- 版本：1.0.0（2026-11-18）
-- 前置：已执行 2026-11-15-business-core-foundation.sql
-- 注意：DDL 会隐式提交，执行前必须备份数据库。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_multitenant_preconditions//
CREATE PROCEDURE assert_multitenant_preconditions()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'organization'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'organization already exists; multitenant baseline may already be applied';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'contest_edition'
      AND column_name = 'team_size_mode'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '2026-11-15 business foundation must be applied first';
  END IF;
END//

DROP PROCEDURE IF EXISTS drop_index_if_exists//
CREATE PROCEDURE drop_index_if_exists(IN target_table VARCHAR(64), IN target_index VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = target_table
      AND index_name = target_index
  ) THEN
    SET @drop_index_sql = CONCAT(
      'ALTER TABLE `', target_table, '` DROP INDEX `', target_index, '`'
    );
    PREPARE drop_index_stmt FROM @drop_index_sql;
    EXECUTE drop_index_stmt;
    DEALLOCATE PREPARE drop_index_stmt;
  END IF;
END//
DELIMITER ;

CALL assert_multitenant_preconditions();
DROP PROCEDURE assert_multitenant_preconditions;

CREATE TABLE organization (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  org_code VARCHAR(32) NOT NULL COMMENT '稳定组织编码',
  org_name VARCHAR(128) NOT NULL COMMENT '组织名称',
  org_type VARCHAR(24) NOT NULL COMMENT 'SCHOOL/PROVINCE_ORGANIZER/NATIONAL_ORGANIZER/PLATFORM',
  province_code VARCHAR(6) DEFAULT NULL COMMENT '省级行政区编码',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_organization_code (org_code),
  KEY idx_organization_type_province (org_type, province_code, status),
  CONSTRAINT chk_organization_type
    CHECK (org_type IN ('SCHOOL', 'PROVINCE_ORGANIZER', 'NATIONAL_ORGANIZER', 'PLATFORM')),
  CONSTRAINT chk_organization_province
    CHECK (
      (org_type IN ('SCHOOL', 'PROVINCE_ORGANIZER') AND province_code IS NOT NULL)
      OR (org_type IN ('NATIONAL_ORGANIZER', 'PLATFORM') AND province_code IS NULL)
    ),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校、省级/国家级主办方和平台组织主档';

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
VALUES
  ('PLATFORM', '赛事平台', 'PLATFORM', NULL, 1, 0, 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
VALUES
  ('DEMO-SC-001', '四川演示学校', 'SCHOOL', '510000', 1, 0, 0);

SET @platform_org_id = (
  SELECT id FROM organization WHERE org_code = 'PLATFORM' AND deleted = 0
);
SET @demo_school_id = (
  SELECT id FROM organization WHERE org_code = 'DEMO-SC-001' AND deleted = 0
);

ALTER TABLE sys_user
  ADD COLUMN school_id BIGINT DEFAULT NULL COMMENT '所属学校 organization.id' AFTER student_no,
  ADD COLUMN school_verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    COMMENT '学校归属状态：PENDING/VERIFIED/REJECTED' AFTER school_id,
  ADD COLUMN school_reviewed_by BIGINT DEFAULT NULL
    COMMENT '学校归属审核人' AFTER school_verification_status,
  ADD COLUMN school_reviewed_at DATETIME DEFAULT NULL
    COMMENT '学校归属审核时间' AFTER school_reviewed_by,
  ADD COLUMN school_review_reason VARCHAR(500) DEFAULT NULL
    COMMENT '学校归属驳回或审核说明' AFTER school_reviewed_at;

UPDATE sys_user
SET school_id = @demo_school_id,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '历史数据迁移默认学校'
WHERE deleted = 0
  AND username <> 'admin_demo';

UPDATE sys_user
SET school_id = NULL,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '平台超级管理员'
WHERE username = 'admin_demo';

CALL drop_index_if_exists('sys_user', 'uk_sys_user_student_no');

ALTER TABLE sys_user
  ADD UNIQUE KEY uk_sys_user_school_student (school_id, student_no),
  ADD KEY idx_user_school (school_id, status, deleted),
  ADD KEY idx_user_school_verification (school_id, school_verification_status, deleted),
  ADD CONSTRAINT chk_sys_user_school_verification
    CHECK (school_verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'));

ALTER TABLE sys_user_role
  ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL'
    COMMENT 'GLOBAL/ORGANIZATION/PROVINCE/EVENT' AFTER role_id,
  ADD COLUMN scope_id BIGINT NOT NULL DEFAULT 0
    COMMENT 'GLOBAL=0；其他类型保存对应范围 ID 或省份编码' AFTER scope_type,
  ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态' AFTER scope_id,
  ADD COLUMN valid_from DATETIME DEFAULT NULL COMMENT '生效时间' AFTER status,
  ADD COLUMN valid_to DATETIME DEFAULT NULL COMMENT '失效时间' AFTER valid_from,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    AFTER valid_to,
  ADD COLUMN updated_by BIGINT DEFAULT NULL AFTER updated_at,
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER updated_by,
  ADD COLUMN deleted_at DATETIME DEFAULT NULL AFTER deleted,
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER deleted_at;

CALL drop_index_if_exists('sys_user_role', 'uk_user_role');
CALL drop_index_if_exists('sys_user_role', 'uk_sys_user_role_user_role');

ALTER TABLE sys_user_role
  ADD UNIQUE KEY uk_user_role_scope (user_id, role_id, scope_type, scope_id),
  ADD KEY idx_user_role_scope (scope_type, scope_id),
  ADD KEY idx_user_role_status (user_id, status, deleted),
  ADD CONSTRAINT chk_user_role_scope_type
    CHECK (scope_type IN ('GLOBAL', 'ORGANIZATION', 'PROVINCE', 'EVENT')),
  ADD CONSTRAINT chk_user_role_status CHECK (status IN (0, 1)),
  ADD CONSTRAINT chk_user_role_validity
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_from < valid_to);

ALTER TABLE contest
  ADD COLUMN series_code VARCHAR(32) DEFAULT NULL COMMENT '稳定竞赛系列编码' AFTER contest_name;

UPDATE contest
SET series_code = CONCAT('LEGACY-', id)
WHERE series_code IS NULL;

ALTER TABLE contest
  MODIFY COLUMN series_code VARCHAR(32) NOT NULL COMMENT '稳定竞赛系列编码',
  ADD UNIQUE KEY uk_contest_series_code (series_code);

ALTER TABLE contest_edition
  ADD COLUMN owner_org_id BIGINT DEFAULT NULL COMMENT '主办组织 organization.id' AFTER contest_id,
  ADD COLUMN level VARCHAR(16) DEFAULT NULL COMMENT 'SCHOOL/PROVINCE/NATIONAL' AFTER year,
  ADD COLUMN school_id BIGINT DEFAULT NULL COMMENT '校级比赛所属学校' AFTER level,
  ADD COLUMN province_code VARCHAR(6) DEFAULT NULL COMMENT '省级比赛所属省份' AFTER school_id,
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
    COMMENT 'DRAFT/UNLISTED/SCHOOL/PROVINCE/PUBLIC' AFTER topic_change_limit,
  ADD COLUMN entry_mode VARCHAR(16) NOT NULL DEFAULT 'OPEN'
    COMMENT 'OPEN/INVITED/QUALIFIED' AFTER visibility,
  ADD COLUMN cross_school_team_allowed TINYINT NOT NULL DEFAULT 0
    COMMENT '是否允许跨校组队' AFTER entry_mode;

UPDATE contest_edition ce
JOIN contest c ON c.id = ce.contest_id
SET ce.level = CASE
      WHEN c.level = 'PROVINCE' THEN 'PROVINCE'
      WHEN c.level = 'NATIONAL' THEN 'NATIONAL'
      ELSE 'SCHOOL'
    END,
    ce.owner_org_id = CASE
      WHEN c.level = 'NATIONAL' THEN @platform_org_id
      ELSE @demo_school_id
    END,
    ce.school_id = CASE
      WHEN c.level = 'SCHOOL' THEN @demo_school_id
      ELSE NULL
    END,
    ce.province_code = CASE
      WHEN c.level = 'PROVINCE' THEN '510000'
      ELSE NULL
    END,
    ce.visibility = CASE
      WHEN ce.status = 0 THEN 'DRAFT'
      WHEN c.level = 'SCHOOL' THEN 'SCHOOL'
      WHEN c.level = 'PROVINCE' THEN 'PROVINCE'
      ELSE 'PUBLIC'
    END;

ALTER TABLE contest_edition
  MODIFY COLUMN owner_org_id BIGINT NOT NULL COMMENT '主办组织 organization.id',
  MODIFY COLUMN level VARCHAR(16) NOT NULL COMMENT 'SCHOOL/PROVINCE/NATIONAL';

CALL drop_index_if_exists('contest_edition', 'uk_edition_contest_name');

ALTER TABLE contest_edition
  ADD UNIQUE KEY uk_edition_owner_year_name (owner_org_id, year, edition_name),
  ADD KEY idx_edition_owner_scope (owner_org_id, level, visibility, status),
  ADD KEY idx_edition_province_status (province_code, status),
  ADD KEY idx_edition_school_status (school_id, status),
  ADD CONSTRAINT chk_edition_event_level
    CHECK (level IN ('SCHOOL', 'PROVINCE', 'NATIONAL')),
  ADD CONSTRAINT chk_edition_visibility
    CHECK (visibility IN ('DRAFT', 'UNLISTED', 'SCHOOL', 'PROVINCE', 'PUBLIC')),
  ADD CONSTRAINT chk_edition_entry_mode
    CHECK (entry_mode IN ('OPEN', 'INVITED', 'QUALIFIED')),
  ADD CONSTRAINT chk_edition_cross_school
    CHECK (cross_school_team_allowed IN (0, 1)),
  ADD CONSTRAINT chk_edition_scope
    CHECK (
      (level = 'SCHOOL' AND school_id IS NOT NULL AND province_code IS NULL)
      OR (level = 'PROVINCE' AND school_id IS NULL AND province_code IS NOT NULL)
      OR (level = 'NATIONAL' AND school_id IS NULL AND province_code IS NULL)
    );

DROP PROCEDURE IF EXISTS drop_index_if_exists;
