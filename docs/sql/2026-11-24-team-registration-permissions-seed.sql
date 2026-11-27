-- 队长代办制报名组织权限种子（MySQL 8.0）
-- 可重复执行；前置：已执行基础业务权限种子。

SET NAMES utf8mb4;

SET @contest_root_id = (
  SELECT id FROM sys_menu
  WHERE parent_id IS NULL AND menu_name = '竞赛中心' AND deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @contest_root_id, '报名组织', 2, '/admin/team-registration', NULL,
       30, 1, 1, NOW(), 0, 0, 0
WHERE @contest_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @contest_root_id
      AND (menu_name = '报名组织' OR path = '/teams')
      AND deleted = 0
  );

SET @team_menu_id = (
  SELECT id FROM sys_menu
  WHERE parent_id = @contest_root_id
    AND menu_name = '报名组织'
    AND deleted = 0
  ORDER BY id
  LIMIT 1
);

UPDATE sys_menu
SET menu_name = '报名组织',
    menu_type = 2,
    path = '/admin/team-registration',
    sort_no = 30,
    visible = 1,
    status = 1,
    deleted = 0
WHERE id = @team_menu_id;

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '查询队伍', 3, NULL, 'team:list',
       10, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:list' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '创建队伍', 3, NULL, 'team:create',
       20, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:create' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '查看队伍', 3, NULL, 'team:view',
       30, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:view' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '修改队伍', 3, NULL, 'team:update',
       40, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:update' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '查询成员', 3, NULL, 'team:member:list',
       50, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:member:list' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '移出成员', 3, NULL, 'team:member:remove',
       60, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:member:remove' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '提交报名', 3, NULL, 'team:registration:submit',
       70, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:registration:submit' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '审核报名', 3, NULL, 'team:approve',
       80, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'team:approve' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @team_menu_id, '维护资格名单', 3, NULL, 'team:eligibility:manage',
       90, 0, 1, NOW(), 0, 0, 0
WHERE @team_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'team:eligibility:manage' AND deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, @team_menu_id
FROM sys_role r
WHERE r.role_code IN ('ADMIN', 'STUDENT', 'TEACHER')
  AND r.deleted = 0
  AND @team_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN (
    'team:list',
    'team:create',
    'team:view',
    'team:update',
    'team:member:list',
    'team:member:remove',
    'team:registration:submit'
  )
WHERE r.role_code = 'STUDENT'
  AND r.deleted = 0
  AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN ('team:list', 'team:view', 'team:member:list', 'team:approve')
WHERE r.role_code = 'TEACHER'
  AND r.deleted = 0
  AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN (
    'team:list',
    'team:view',
    'team:member:list',
    'team:approve',
    'team:eligibility:manage'
  )
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;
