-- 业务核心公共基线回滚（MySQL 8.0）
-- 版本：1.0.0（2026-11-15）
-- 注意：仅在已备份的开发环境执行；生产回滚必须先确认没有业务数据依赖新字段。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_business_core_rollback_safe//
CREATE PROCEDURE assert_business_core_rollback_safe()
BEGIN
  IF EXISTS (SELECT 1 FROM contest_topic WHERE max_teams IS NULL) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'contest_topic.max_teams contains NULL; resolve data before restoring NOT NULL';
  END IF;

  DELETE rm
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
  WHERE m.perms IN (
    'contest:create',
    'contest:update',
    'edition:create',
    'edition:update',
    'edition:publish',
    'edition:milestone:create',
    'edition:milestone:update'
  );

  DELETE FROM sys_menu
  WHERE perms IN (
    'contest:create',
    'contest:update',
    'edition:create',
    'edition:update',
    'edition:publish',
    'edition:milestone:create',
    'edition:milestone:update'
  );

  DELETE rm
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
  WHERE m.path IN ('/contest-center', '/contests') AND m.deleted = 0;

  DELETE FROM sys_menu
  WHERE path IN ('/contest-center', '/contests') AND deleted = 0;
END//
DELIMITER ;

CALL assert_business_core_rollback_safe();
DROP PROCEDURE assert_business_core_rollback_safe;

ALTER TABLE contest_edition
  DROP CHECK chk_edition_topic_review_required,
  DROP CHECK chk_edition_topic_change_limit,
  DROP CHECK chk_edition_topic_allocation,
  DROP CHECK chk_edition_topic_mode,
  DROP CHECK chk_edition_teacher_limit,
  DROP CHECK chk_edition_team_sizes,
  DROP CHECK chk_edition_team_size_mode,
  DROP CHECK chk_edition_status,
  DROP INDEX uk_edition_contest_name,
  DROP COLUMN topic_change_limit,
  DROP COLUMN topic_allocation_mode,
  DROP COLUMN topic_review_required,
  DROP COLUMN topic_mode,
  DROP COLUMN max_teams_per_teacher,
  DROP COLUMN team_size_mode,
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '届次状态';

ALTER TABLE contest_topic
  MODIFY COLUMN max_teams INT NOT NULL DEFAULT 1 COMMENT '可报名队伍数';
