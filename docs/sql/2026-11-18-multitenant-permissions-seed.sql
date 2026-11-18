-- 多组织基础权限种子（MySQL 8.0）
-- 依赖：RBAC 基础种子、业务核心权限种子和 multitenant-foundation.sql

SET NAMES utf8mb4;

INSERT INTO sys_role
  (role_code, role_name, data_scope, created_by, deleted, version)
SELECT 'SCHOOL_ADMIN', '学校管理员', 'ORGANIZATION', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SCHOOL_ADMIN' AND deleted = 0);

INSERT INTO sys_role
  (role_code, role_name, data_scope, created_by, deleted, version)
SELECT 'PROVINCE_ADMIN', '省级赛事管理员', 'PROVINCE', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'PROVINCE_ADMIN' AND deleted = 0);

INSERT INTO sys_role
  (role_code, role_name, data_scope, created_by, deleted, version)
SELECT 'NATIONAL_ADMIN', '国家级赛事管理员', 'ORGANIZATION', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'NATIONAL_ADMIN' AND deleted = 0);

INSERT INTO sys_role
  (role_code, role_name, data_scope, created_by, deleted, version)
SELECT 'EVENT_MANAGER', '赛事负责人', 'EVENT', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'EVENT_MANAGER' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT NULL, '组织与赛事', 1, '/organization-center', NULL, 12, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu
  WHERE parent_id IS NULL AND path = '/organization-center' AND deleted = 0
);

SET @org_root_id = (
  SELECT id FROM sys_menu
  WHERE parent_id IS NULL AND path = '/organization-center' AND deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @org_root_id, '组织管理', 2, '/admin/organizations', 'organization:list',
       10, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'organization:list' AND deleted = 0);

SET @organization_menu_id = (
  SELECT id FROM sys_menu WHERE perms = 'organization:list' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @organization_menu_id, '创建组织', 3, NULL, 'organization:create',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'organization:create' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @organization_menu_id, '修改组织', 3, NULL, 'organization:update',
       20, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'organization:update' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @organization_menu_id, '分配范围角色', 3, NULL, 'organization:role:assign',
       30, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'organization:role:assign' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @org_root_id, '学校归属审核', 2, '/admin/school-affiliations', 'school:affiliation:list',
       30, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'school:affiliation:list' AND deleted = 0);

SET @school_affiliation_menu_id = (
  SELECT id FROM sys_menu WHERE perms = 'school:affiliation:list' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @school_affiliation_menu_id, '确认或驳回学校归属', 3, NULL, 'school:affiliation:review',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'school:affiliation:review' AND deleted = 0);

INSERT INTO sys_menu
  (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
   created_at, created_by, deleted, version)
SELECT @org_root_id, '学校列表', 2, '/schools', 'school:list',
       20, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'school:list' AND deleted = 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN ('organization:list', 'organization:create', 'organization:update', 'organization:role:assign')
WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'school:list'
WHERE r.role_code IN ('ADMIN', 'SCHOOL_ADMIN', 'PROVINCE_ADMIN', 'NATIONAL_ADMIN', 'STUDENT', 'TEACHER', 'REVIEWER')
  AND r.deleted = 0 AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN ('organization:list', 'organization:role:assign')
WHERE r.role_code IN ('ADMIN', 'SCHOOL_ADMIN', 'PROVINCE_ADMIN', 'NATIONAL_ADMIN')
  AND r.deleted = 0 AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN ('school:affiliation:list', 'school:affiliation:review')
WHERE r.role_code IN ('ADMIN', 'SCHOOL_ADMIN')
  AND r.deleted = 0 AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perms IN (
    'edition:create',
    'edition:update',
    'edition:publish',
    'edition:milestone:create',
    'edition:milestone:update'
  )
WHERE r.role_code IN ('SCHOOL_ADMIN', 'PROVINCE_ADMIN', 'NATIONAL_ADMIN', 'EVENT_MANAGER')
  AND r.deleted = 0 AND m.deleted = 0;
