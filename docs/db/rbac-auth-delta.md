# 认证与 RBAC 数据库增量设计

- 冻结日期：2026-11-03
- 适用分支：`feature/rbac-auth`
- 数据库：MySQL 8.0
- 基线：现有《6组 数据库设计》的 26 张表
- 新增表：0 张

## 一、设计结论

本次不新增权限资源表或角色权限表，直接复用现有六张系统表：

| 表 | 用途 |
|---|---|
| `sys_user` | 用户主体、邮箱、密码哈希和账号状态 |
| `sys_role` | 角色、数据范围和版本 |
| `sys_user_role` | 用户与角色多对多关系 |
| `sys_menu` | 目录、菜单、按钮/API 统一权限资源 |
| `sys_role_menu` | 角色与权限资源多对多关系 |
| `sys_oper_log` | 登录、授权、权限变更和失败处理审计 |

取消原草案中的 `sys_permission_resource` 和 `sys_role_permission`，避免与 `sys_menu`、`sys_role_menu` 形成两套重复权限模型。

## 二、复用并增量修改

### `sys_user`

现有字段继续保留。增加：

- `email VARCHAR(128) NULL`
- `uk_sys_user_username (username)`
- `uk_sys_user_email (email)`

MySQL 唯一索引允许多个 `NULL`，因此历史账号可以不绑定邮箱；新注册账号必须有经过验证的邮箱。

### `sys_role`

现有字段继续保留。增加：

- `uk_sys_role_role_code (role_code)`

`data_scope` 继续使用以下值：

```text
ALL
SELF
TEAM
GUIDED
ASSIGNED
```

### `sys_user_role`

保留现有两列关系结构，增加：

- `uk_sys_user_role_user_role (user_id, role_id)`

### `sys_menu`

保留现有字段，作为统一权限资源树：

- `menu_type = 1`：目录；
- `menu_type = 2`：菜单；
- `menu_type = 3`：按钮/API 操作；
- `perms`：权限编码；
- 根节点的 `parent_id` 改为 `NULL`。

增加：

- `uk_sys_menu_perms (perms)`
- `idx_sys_menu_parent_status (parent_id, status)`

`perms` 为空的目录节点不受唯一索引限制。

### `sys_role_menu`

保留现有关系结构，增加：

- `uk_sys_role_menu_role_menu (role_id, menu_id)`

### `sys_oper_log`

- 将 `user_id` 改为可空，支持账号不存在的失败登录审计；
- 增加 `idx_sys_oper_log_user_created (user_id, created_at)`。

`params` 只能保存脱敏后的请求摘要，不能包含密码、OTP、JWT 或 Refresh Token。

## 三、新增表

无。

以下数据只保存在 Redis：

- `auth:captcha:{captchaId}`：图片验证码；
- `auth:challenge:{challengeId}`：邮箱和 OTP 场景绑定；
- `auth:session:{sid}`：会话和 Refresh Token 哈希；
- `auth:user-sessions:{userId}`：用户会话索引；
- `auth:idempotency:*`：幂等键；
- `auth:rate:*`：登录、验证码和 OTP 限流计数。

Redis 会话丢失时用户重新登录，不将瞬态会话数据落入 MySQL。

## 四、种子数据

增量 SQL 包含：

- `ADMIN`、`STUDENT`、`TEACHER`、`REVIEWER` 四个角色；
- 本周演示权限资源树；
- `admin_demo`、`student_a`、`student_b` 三个账号；
- 三个账号的 BCrypt 密码哈希和角色关联。

演示账号只用于本地开发，不能用于生产。

## 五、事务与 Redis

数据库事务只覆盖 MySQL 表操作，不包含 Redis。

用户角色或角色权限变更：

1. 在 MySQL 事务中更新关系表和版本号；
2. 提交 MySQL 事务；
3. 调用公共会话撤销能力；
4. Redis 撤销成功后才将操作视为完整成功；
5. Redis 撤销失败时返回 HTTP 503，并在 `sys_oper_log` 标注 `SESSION_REVOCATION_FAILED` 供恢复后重试。

不引入消息队列或 Outbox。

## 六、迁移前检查

`2026-11-03-rbac-auth-foundation.sql` 目前只是冻结草案，尚未执行。执行前必须：

1. 确认实际 MySQL 已具有设计中的基础表；
2. 检查 `sys_user.username` 是否已有重复值；
3. 检查 `sys_role.role_code` 是否已有重复值；
4. 检查 `sys_user_role` 和 `sys_role_menu` 是否已有重复关系；
5. 检查 `sys_user` 是否已经存在 `email` 字段；
6. 备份现有数据，不能直接删除数据卷重建。

## 七、原有数据库文档待修正项

现有 Word/PDF 数据库设计存在两个问题：

- 命名规则要求唯一索引，但导出的 DDL 没有实际创建；
- 多个模块的 DDL 段落中重复出现 `CREATE TABLE sys_user`、`CREATE TABLE team` 等引用占位语句，按文档顺序执行会发生重复建表。

后续同步 Word/PDF 时必须一并修正，不能把当前分组 DDL 原样当作可执行脚本。
