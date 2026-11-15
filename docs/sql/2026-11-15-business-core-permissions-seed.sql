-- 业务核心权限资源种子（MySQL 8.0）
-- 版本：1.0.0（2026-11-15）
-- 可在已执行 RBAC 基础种子后重复运行。

SET NAMES utf8mb4;

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT NULL, '竞赛中心', 1, '/contest-center', NULL, 15, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '竞赛中心' AND deleted = 0
);

SET @contest_root_id = (
    SELECT id FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '竞赛中心' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_root_id, '竞赛与届次', 2, '/contests', NULL,
       10, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @contest_root_id AND path = '/contests' AND deleted = 0
);

SET @contest_center_id = (
    SELECT id FROM sys_menu
    WHERE parent_id = @contest_root_id AND path = '/contests' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '创建竞赛', 3, NULL, 'contest:create',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'contest:create' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '修改竞赛', 3, NULL, 'contest:update',
       20, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'contest:update' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '创建届次', 3, NULL, 'edition:create',
       30, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'edition:create' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '修改届次', 3, NULL, 'edition:update',
       40, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'edition:update' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '发布届次', 3, NULL, 'edition:publish',
       50, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'edition:publish' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '新增赛程节点', 3, NULL, 'edition:milestone:create',
       60, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'edition:milestone:create' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @contest_center_id, '修改赛程节点', 3, NULL, 'edition:milestone:update',
       70, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'edition:milestone:update' AND deleted = 0
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id = @contest_root_id OR m.id = @contest_center_id
WHERE r.role_code IN ('ADMIN', 'STUDENT', 'TEACHER', 'REVIEWER')
  AND r.deleted = 0
  AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN (
    'contest:create',
    'contest:update',
    'edition:create',
    'edition:update',
    'edition:publish',
    'edition:milestone:create',
    'edition:milestone:update'
  )
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;
