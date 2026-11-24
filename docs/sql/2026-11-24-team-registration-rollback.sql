-- 队长代办制报名组织公共基线回滚（MySQL 8.0）
-- 仅在已备份且确认业务代码不依赖新增结构时执行。

SET NAMES utf8mb4;

ALTER TABLE team
  DROP FOREIGN KEY fk_team_current_registration,
  DROP INDEX idx_team_current_registration,
  DROP CHECK chk_team_status,
  DROP COLUMN current_registration_id,
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '报名状态';

DROP TABLE IF EXISTS edition_school_quota;
DROP TABLE IF EXISTS edition_entry_grant;
DROP TABLE IF EXISTS team_teacher_application;
DROP TABLE IF EXISTS team_registration_attachment;
DROP TABLE IF EXISTS team_registration;

ALTER TABLE team_member
  DROP INDEX uk_team_member_active_student,
  DROP INDEX uk_team_member_active_leader,
  DROP COLUMN active_edition_id,
  DROP COLUMN active_school_id,
  DROP COLUMN active_student_no,
  DROP COLUMN active_leader_team_id,
  DROP COLUMN edition_id,
  DROP COLUMN member_name,
  DROP COLUMN school_id,
  DROP COLUMN student_no,
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '学生';

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.perms IN (
  'team:list',
  'team:create',
  'team:view',
  'team:update',
  'team:member:list',
  'team:member:remove',
  'team:registration:submit',
  'team:approve',
  'team:eligibility:manage'
);

DELETE FROM sys_menu
WHERE perms IN (
  'team:list',
  'team:create',
  'team:view',
  'team:update',
  'team:member:list',
  'team:member:remove',
  'team:registration:submit',
  'team:approve',
  'team:eligibility:manage'
);
