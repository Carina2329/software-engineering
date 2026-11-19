# 多组织、学校、范围权限与比赛归属数据库增量设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.1 |
| 日期 | 2026-11-18 |
| 状态 | 公共基线，配套增量、回滚和种子 SQL |
| 关联需求 | 《AI学科竞赛系统-功能需求说明书》V0.5 |
| 设计范围 | 组织、学校、省份、用户学校归属、范围角色、比赛归属和可见范围 |

## 一、核心模型

```text
organization
  ├─ SCHOOL
  ├─ PROVINCE_ORGANIZER
  ├─ NATIONAL_ORGANIZER
  └─ PLATFORM

sys_user.school_id → organization(type=SCHOOL)

contest
  └─ contest_edition
       ├─ owner_org_id → organization
       ├─ level
       ├─ school_id / province_code
       ├─ visibility
       └─ entry_mode
```

省份只在组织主档、比赛实例和权限范围中保存，不在个人档案中重复保存。

## 二、新增 `organization`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `org_code` | VARCHAR(32) | 稳定组织编码，全平台唯一 |
| `org_name` | VARCHAR(128) | 组织名称 |
| `org_type` | VARCHAR(24) | SCHOOL、PROVINCE_ORGANIZER、NATIONAL_ORGANIZER、PLATFORM |
| `province_code` | VARCHAR(6) NULL | 学校或省级主办方所在省份 |
| `status` | TINYINT | 1 启用，0 停用 |
| 公共字段 | - | created/updated/deleted/version |

规则：

- `org_code` 唯一。
- 学校必须填写 `province_code`。
- 省级主办方必须填写 `province_code`。
- 国家级和平台组织的 `province_code` 为空。
- 组织停用后禁止新比赛和新用户归属，但历史数据保留。

## 三、`sys_user` 增量

新增：

- `school_id BIGINT NULL`：学生和教师所属学校。
- `school_verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'`：PENDING、VERIFIED、REJECTED。
- `school_reviewed_by BIGINT NULL`：审核管理员。
- `school_reviewed_at DATETIME NULL`：审核时间。
- `school_review_reason VARCHAR(500) NULL`：驳回原因或审核说明。

学号约束调整：

- 删除 `uk_sys_user_student_no(student_no)`。
- 新增 `uk_sys_user_school_student(school_id, student_no)`。
- `username`、`email` 仍全平台唯一。

规则：

- 学生和教师必须最终绑定一所 `SCHOOL` 类型组织。
- 平台超级管理员可以不绑定学校。
- 学校首次选择后为待确认；管理员确认后才能访问学校范围业务。
- 已确认学校变更需要学校管理员或平台管理员处理。
- 学校管理员通过现有用户管理中的“学校归属审核”队列确认或驳回；驳回必须填写原因。
- 历史比赛报名保存学校与学号快照。

## 四、`sys_user_role` 增量

新增：

- `scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL'`。
- `scope_id BIGINT NOT NULL DEFAULT 0`。
- `status TINYINT NOT NULL DEFAULT 1`。
- `valid_from / valid_to DATETIME NULL`。
- `updated_at / updated_by / deleted / deleted_at / version`。

范围：

- `GLOBAL`：全局，`scope_id=0`。
- `ORGANIZATION`：`scope_id=organization.id`。
- `PROVINCE`：暂时使用省份数字编码，例如四川 `510000`。
- `EVENT`：`scope_id=contest_edition.id`。

唯一约束改为：

```text
UNIQUE(user_id, role_id, scope_type, scope_id)
```

后端授权必须同时检查权限码和数据范围。只拥有角色但没有合法范围时不能访问管理数据。

角色分配更新使用 `version` 乐观锁；撤销可先置为停用并保留历史，角色或范围变化后必须撤销目标用户会话。

## 五、`contest` 增量

新增：

- `series_code VARCHAR(32)`：稳定竞赛系列编码，全平台唯一。

`level` 保留用于兼容旧数据，但不再作为具体比赛范围依据；所有新业务以 `contest_edition.level` 为准。

## 六、`contest_edition` 增量

新增：

- `owner_org_id BIGINT NOT NULL`：比赛主办组织。
- `level VARCHAR(16) NOT NULL`：SCHOOL、PROVINCE、NATIONAL。
- `school_id BIGINT NULL`：校级比赛所属学校。
- `province_code VARCHAR(6) NULL`：省级比赛所属省份。
- `visibility VARCHAR(16) NOT NULL DEFAULT 'DRAFT'`：DRAFT、UNLISTED、SCHOOL、PROVINCE、PUBLIC。
- `entry_mode VARCHAR(16) NOT NULL DEFAULT 'OPEN'`：OPEN、INVITED、QUALIFIED。
- `cross_school_team_allowed TINYINT NOT NULL DEFAULT 0`。

规则：

- `SCHOOL`：必须填写 `school_id`，`province_code` 为空。
- `PROVINCE`：必须填写 `province_code`，`school_id` 为空。
- `NATIONAL`：`school_id` 与 `province_code` 均为空。
- 普通用户只能查看已发布且符合其学校、省份、公开或邀请范围的比赛。
- 组织管理员只能管理 `owner_org_id` 在授权范围内的比赛。
- 原 `uk_edition_contest_name(contest_id, edition_name)` 调整为由主办组织、年度和名称保证业务唯一。
- 新索引覆盖 `(owner_org_id, level, visibility, status)`、`(province_code, status)`、`(school_id, status)`。

## 七、省份字典

使用现有 `sys_dict_type` / `sys_dict_item`，字典编码：

```text
CN_PROVINCE
```

公共种子脚本为 `2026-11-19-cn-province-dictionary-seed.sql`。

第一阶段使用标准省级编码，例如：

- 四川 `510000`
- 湖南 `430000`
- 广东 `440000`

业务代码只使用编码，不把中文名称作为判断条件。

## 八、强制事务审计

以下成功写操作与 `sys_oper_log` 写入必须在同一事务：

- 个人档案修改
- 组织创建和修改
- 用户学校归属确认与变更
- 角色范围授权和撤销
- 竞赛系列和比赛实例创建、修改、发布
- 赛程节点创建和修改

审计失败时业务回滚，不返回成功。

## 九、第一阶段演示数据

- 四川省
- A、B、C 三所四川测试学校
- 一所湖南 D 学校，仅用于跨省隔离测试
- 2～3 个竞赛系列
- A、B 学校校赛
- 四川省赛
- 全国赛
- 晋级采用人工名单，不自动计算

## 十、迁移要求

1. 先建立组织表并回填平台组织。
2. 为现有学生、教师和评审专家分配默认测试学校。
3. 回填现有 `sys_user_role.scope_type=GLOBAL, scope_id=0`。
4. 回填现有比赛实例的主办组织、级别和可见范围。
5. 重建学号唯一索引前检查同校重复数据。
6. 所有 DDL 在备份后的独立开发库演练。
