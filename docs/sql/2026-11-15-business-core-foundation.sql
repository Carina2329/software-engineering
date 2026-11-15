-- 业务核心公共基线增量（MySQL 8.0）
-- 版本：1.0.0（2026-11-15）
-- 基线：develop@63c2a14（PR #12 合并后）
-- 说明：本脚本只增量修改现有表，不创建新表。
-- 注意：DDL 在 MySQL 中会隐式提交，执行前必须完成数据库备份。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_business_core_preconditions//
CREATE PROCEDURE assert_business_core_preconditions()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'contest_edition'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'contest_edition is missing; initialize the base schema first';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'contest_edition'
      AND column_name = 'team_size_mode'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'business core foundation appears to be already applied';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM contest_edition
    GROUP BY contest_id, edition_name
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'duplicate edition names exist under the same contest; resolve them before migration';
  END IF;
END//
DELIMITER ;

CALL assert_business_core_preconditions();
DROP PROCEDURE assert_business_core_preconditions;

ALTER TABLE contest_edition
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '届次状态：0=DRAFT，1=REGISTERING，2=SUBMITTING，3=REVIEWING，4=PUBLISHING，5=ARCHIVED',
  ADD COLUMN team_size_mode VARCHAR(16) NOT NULL DEFAULT 'RANGE'
    COMMENT '队伍人数模式：FIXED 或 RANGE' AFTER max_team_size,
  ADD COLUMN max_teams_per_teacher TINYINT NOT NULL DEFAULT 3
    COMMENT '当前届次每位教师最多确认指导队伍数（1-10）' AFTER team_size_mode,
  ADD COLUMN topic_mode VARCHAR(24) DEFAULT NULL
    COMMENT '赛题模式；草稿可为空，发布前必填' AFTER max_teams_per_teacher,
  ADD COLUMN topic_review_required TINYINT NOT NULL DEFAULT 1
    COMMENT '选题或自拟项目是否需要管理员审核' AFTER topic_mode,
  ADD COLUMN topic_allocation_mode VARCHAR(16) NOT NULL DEFAULT 'REVIEW'
    COMMENT '限额赛题分配方式：REVIEW 或 FIRST_COME' AFTER topic_review_required,
  ADD COLUMN topic_change_limit TINYINT NOT NULL DEFAULT 1
    COMMENT '报名截止前允许更换赛题或项目次数（0-3）' AFTER topic_allocation_mode,
  ADD UNIQUE KEY uk_edition_contest_name (contest_id, edition_name);

ALTER TABLE contest_edition
  ADD CONSTRAINT chk_edition_status
    CHECK (status BETWEEN 0 AND 5),
  ADD CONSTRAINT chk_edition_team_size_mode
    CHECK (team_size_mode IN ('FIXED', 'RANGE')),
  ADD CONSTRAINT chk_edition_team_sizes
    CHECK (min_team_size BETWEEN 1 AND 5
      AND max_team_size BETWEEN 1 AND 5
      AND min_team_size <= max_team_size
      AND (team_size_mode = 'RANGE' OR min_team_size = max_team_size)),
  ADD CONSTRAINT chk_edition_teacher_limit
    CHECK (max_teams_per_teacher BETWEEN 1 AND 10),
  ADD CONSTRAINT chk_edition_topic_mode
    CHECK (topic_mode IS NULL OR topic_mode IN
      ('PRESET_UNLIMITED', 'PRESET_LIMITED', 'SELF_PROPOSED', 'NO_TOPIC')),
  ADD CONSTRAINT chk_edition_topic_allocation
    CHECK (topic_allocation_mode IN ('REVIEW', 'FIRST_COME')),
  ADD CONSTRAINT chk_edition_topic_change_limit
    CHECK (topic_change_limit BETWEEN 0 AND 3),
  ADD CONSTRAINT chk_edition_topic_review_required
    CHECK (topic_review_required IN (0, 1));

ALTER TABLE contest_topic
  MODIFY COLUMN max_teams INT DEFAULT NULL
    COMMENT '可选队伍容量；NULL 表示不限队伍数量';
