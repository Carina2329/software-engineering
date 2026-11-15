-- 认证与 RBAC 数据库增量草案
-- 目标：MySQL 8.0
-- 文档版本：0.2.4（2026-11-08）
-- 状态：已在独立联调库验证；执行前先核对真实库结构并备份数据。
-- 本脚本不新增数据库表。

USE contest_db;

-- 1. 用户增加邮箱登录标识
ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(128) NULL COMMENT '登录邮箱' AFTER password_hash;

CREATE UNIQUE INDEX uk_sys_user_email ON sys_user (email);

-- 2. 角色编码唯一
CREATE UNIQUE INDEX uk_sys_role_role_code ON sys_role (role_code);

-- 3. 关联表防止重复关系
CREATE UNIQUE INDEX uk_sys_user_role_user_role
    ON sys_user_role (user_id, role_id);

-- 4. 复用 sys_menu 作为目录、菜单、按钮/API 权限资源树
ALTER TABLE sys_menu
    MODIFY COLUMN parent_id BIGINT NULL COMMENT '上级权限资源；根节点为空';

CREATE UNIQUE INDEX uk_sys_menu_perms ON sys_menu (perms);
CREATE INDEX idx_sys_menu_parent_status ON sys_menu (parent_id, status);

-- 5. 角色权限关系唯一
CREATE UNIQUE INDEX uk_sys_role_menu_role_menu
    ON sys_role_menu (role_id, menu_id);

-- 6. 失败的未知账号登录也需要审计
ALTER TABLE sys_oper_log
    MODIFY COLUMN user_id BIGINT NULL COMMENT '操作人；未知账号失败登录时可为空';

ALTER TABLE sys_oper_log
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT '请求追踪号';

CREATE INDEX idx_sys_oper_log_user_created ON sys_oper_log (user_id, created_at);

-- 7. 基础角色
INSERT INTO sys_role
    (role_code, role_name, data_scope, created_at, created_by, deleted, version)
SELECT 'ADMIN', '系统管理员', 'ALL', NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');

INSERT INTO sys_role
    (role_code, role_name, data_scope, created_at, created_by, deleted, version)
SELECT 'STUDENT', '学生', 'SELF', NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'STUDENT');

INSERT INTO sys_role
    (role_code, role_name, data_scope, created_at, created_by, deleted, version)
SELECT 'TEACHER', '指导教师', 'GUIDED', NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TEACHER');

INSERT INTO sys_role
    (role_code, role_name, data_scope, created_at, created_by, deleted, version)
SELECT 'REVIEWER', '评审专家', 'ASSIGNED', NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'REVIEWER');

-- 8. 本地演示账号
-- 明文密码只写入开发说明，SQL 中只保存 BCrypt 哈希。
INSERT INTO sys_user
    (username, real_name, password_hash, email, status,
     created_at, created_by, deleted, version)
SELECT 'admin_demo', '演示管理员',
       '$2b$12$25dgA4gYN2jCJ8iYZcSMQ.PYSAgMZGOZVD7rz9Z6nRtRcJQw0FSzO',
       'admin_demo@example.edu', 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin_demo');

INSERT INTO sys_user
    (username, real_name, password_hash, email, status,
     created_at, created_by, deleted, version)
SELECT 'student_a', '演示学生 A',
       '$2b$12$2G8oriBS/utH63P1c9zm4.iIDotvNxfd0LEx2SQdBjCwXUmr6BKcO',
       'student_a@example.edu', 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'student_a');

INSERT INTO sys_user
    (username, real_name, password_hash, email, status,
     created_at, created_by, deleted, version)
SELECT 'student_b', '演示学生 B',
       '$2b$12$yU94JeairkpRPfQqPb.l3.UWWT33lAoTUICpsEwDTEle4.IrQK1g6',
       'student_b@example.edu', 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'student_b');

-- 9. 用户角色关联
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ADMIN'
WHERE u.username = 'admin_demo';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'STUDENT'
WHERE u.username IN ('student_a', 'student_b');

-- 10. 权限资源树
-- menu_type：1=目录，2=菜单，3=按钮/API 操作。
INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT NULL, '系统管理', 1, '/admin', NULL, 10, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '系统管理' AND deleted = 0
);

SET @admin_root_id = (
    SELECT id FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '系统管理' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @admin_root_id, '用户管理', 2, '/admin/users', 'system:user:list',
       10, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @admin_root_id AND path = '/admin/users' AND deleted = 0
);
SET @user_menu_id = (
    SELECT id FROM sys_menu
    WHERE parent_id = @admin_root_id AND path = '/admin/users' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @user_menu_id, '创建用户', 3, NULL, 'system:user:create',
       5, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:user:create' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @user_menu_id, '修改用户角色', 3, NULL, 'system:user:update',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:user:update' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @admin_root_id, '角色管理', 2, '/admin/roles', 'system:role:list',
       20, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @admin_root_id AND path = '/admin/roles' AND deleted = 0
);
SET @role_menu_id = (
    SELECT id FROM sys_menu
    WHERE parent_id = @admin_root_id AND path = '/admin/roles' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @role_menu_id, '创建角色', 3, NULL, 'system:role:create',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:role:create' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @role_menu_id, '修改角色', 3, NULL, 'system:role:update',
       20, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:role:update' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @role_menu_id, '角色授权', 3, NULL, 'system:role:authorize',
       30, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:role:authorize' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @admin_root_id, '权限资源', 2, '/admin/menus', 'system:menu:list',
       30, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @admin_root_id AND path = '/admin/menus' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT NULL, '文件中心', 1, '/file-center', NULL, 20, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '文件中心' AND deleted = 0
);

SET @file_root_id = (
    SELECT id FROM sys_menu
    WHERE parent_id IS NULL AND menu_name = '文件中心' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @file_root_id, '文件上传下载', 2, '/files', 'file:download',
       10, 1, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = @file_root_id AND path = '/files' AND deleted = 0
);
SET @file_menu_id = (
    SELECT id FROM sys_menu
    WHERE parent_id = @file_root_id AND path = '/files' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, path, perms, sort_no, visible, status,
     created_at, created_by, deleted, version)
SELECT @file_menu_id, '上传文件', 3, NULL, 'file:upload',
       10, 0, 1, NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'file:upload' AND deleted = 0
);

-- 11. 角色权限关联
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.id IN (
      @admin_root_id,
      @user_menu_id,
      @role_menu_id,
      @file_root_id,
      @file_menu_id
  )
  OR m.perms IN (
      'system:user:list',
      'system:user:create',
      'system:user:update',
      'system:role:list',
      'system:role:create',
      'system:role:update',
      'system:role:authorize',
      'system:menu:list',
      'file:upload',
      'file:download'
  )
WHERE r.role_code = 'ADMIN' AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.id IN (@file_root_id, @file_menu_id)
WHERE r.role_code = 'STUDENT' AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'file:upload' AND m.deleted = 0
WHERE r.role_code = 'STUDENT';
