-- 多组织公共基线回滚（MySQL 8.0）
-- 仅用于已备份的开发环境；执行前确认没有业务数据依赖组织和范围字段。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_multitenant_rollback_safe//
CREATE PROCEDURE assert_multitenant_rollback_safe()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM sys_user
    WHERE deleted = 0 AND student_no IS NOT NULL
    GROUP BY student_no
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'duplicate student_no values exist across schools; global unique rollback is unsafe';
  END IF;
END//
DELIMITER ;

CALL assert_multitenant_rollback_safe();
DROP PROCEDURE assert_multitenant_rollback_safe;

DELETE ur
FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code IN ('SCHOOL_ADMIN', 'PROVINCE_ADMIN', 'NATIONAL_ADMIN', 'EVENT_MANAGER');

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.perms IN (
    'organization:list',
    'organization:create',
    'organization:update',
    'organization:role:assign',
    'school:list',
    'school:affiliation:list',
    'school:affiliation:review'
  )
  OR m.path IN ('/organization-center', '/admin/organizations', '/schools', '/admin/school-affiliations');

DELETE FROM sys_menu
WHERE perms IN (
    'organization:list',
    'organization:create',
    'organization:update',
    'organization:role:assign',
    'school:list',
    'school:affiliation:list',
    'school:affiliation:review'
  )
  OR path IN ('/organization-center', '/admin/organizations', '/schools', '/admin/school-affiliations');

DELETE FROM sys_role
WHERE role_code IN ('SCHOOL_ADMIN', 'PROVINCE_ADMIN', 'NATIONAL_ADMIN', 'EVENT_MANAGER');

ALTER TABLE contest_edition
  DROP CHECK chk_edition_scope,
  DROP CHECK chk_edition_cross_school,
  DROP CHECK chk_edition_entry_mode,
  DROP CHECK chk_edition_visibility,
  DROP CHECK chk_edition_event_level,
  DROP INDEX idx_edition_school_status,
  DROP INDEX idx_edition_province_status,
  DROP INDEX idx_edition_owner_scope,
  DROP INDEX uk_edition_owner_year_name,
  DROP COLUMN cross_school_team_allowed,
  DROP COLUMN entry_mode,
  DROP COLUMN visibility,
  DROP COLUMN province_code,
  DROP COLUMN school_id,
  DROP COLUMN level,
  DROP COLUMN owner_org_id;

ALTER TABLE contest_edition
  ADD UNIQUE KEY uk_edition_contest_name (contest_id, edition_name);

ALTER TABLE contest
  DROP INDEX uk_contest_series_code,
  DROP COLUMN series_code;

ALTER TABLE sys_user_role
  DROP CHECK chk_user_role_validity,
  DROP CHECK chk_user_role_status,
  DROP CHECK chk_user_role_scope_type,
  DROP INDEX idx_user_role_status,
  DROP INDEX idx_user_role_scope,
  DROP INDEX uk_user_role_scope,
  DROP COLUMN version,
  DROP COLUMN deleted_at,
  DROP COLUMN deleted,
  DROP COLUMN updated_by,
  DROP COLUMN updated_at,
  DROP COLUMN valid_to,
  DROP COLUMN valid_from,
  DROP COLUMN status,
  DROP COLUMN scope_id,
  DROP COLUMN scope_type;

ALTER TABLE sys_user_role
  ADD UNIQUE KEY uk_user_role (user_id, role_id);

ALTER TABLE sys_user
  DROP CHECK chk_sys_user_school_verification,
  DROP INDEX idx_user_school_verification,
  DROP INDEX idx_user_school,
  DROP INDEX uk_sys_user_school_student,
  DROP COLUMN school_review_reason,
  DROP COLUMN school_reviewed_at,
  DROP COLUMN school_reviewed_by,
  DROP COLUMN school_verification_status,
  DROP COLUMN school_id;

ALTER TABLE sys_user
  ADD UNIQUE KEY uk_sys_user_student_no (student_no);

DROP TABLE IF EXISTS organization;
