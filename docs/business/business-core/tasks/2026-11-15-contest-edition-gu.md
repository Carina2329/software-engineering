# 正式任务卡：竞赛与届次

| 项目 | 内容 |
| --- | --- |
| 任务编号 | BIZ-CONTEST-EDITION-001 |
| 认领人 | 谷同学 |
| 业务域 | 赛前基础 / 竞赛与届次 |
| 开发分支 | `feature/business-contest-edition` |
| PR 目标分支 | `feature/business-core` |
| 公共契约基线 | 最新 `origin/feature/business-core`，必须包含 v1.4.0 多组织基线 |
| 当前状态 | PR #13 已合并；公共路由、首页入口和管理员菜单已接入，待集成验收 |
| 最终确认人 | 段欣懿 |

## 一、本次要完成的小闭环

平台管理员维护竞赛系列；组织管理员在授权范围内创建具体比赛实例，配置主办组织、级别、学校/省份范围、可见范围、核心时间、人数和赛题规则，并发布给对应范围内的用户查看。

本任务必须形成可独立验收的：

```text
平台管理员维护竞赛系列
  → 组织管理员创建草稿比赛实例
  → 配置主办组织、级别、范围和规则
  → 配置时间、人数和赛题规则
  → 校验并发布
  → 普通用户查看已发布届次和当前窗口
```

## 二、前置依赖

- 从最新 `origin/feature/business-core` 创建分支。
- 已阅读 `AGENTS.md`、`docs/business/business-core/contract.md`、OpenAPI EP-011 至 EP-022 和多组织数据库增量。
- 公共 SQL 已由段欣懿提供，本任务只使用，不修改。
- 公共权限种子已提供 `contest:*`、`edition:*`、组织和范围权限；不得另造权限编码。
- 当前用户必须从认证上下文和角色范围解析可管理的组织、省份或具体比赛，不接受客户端任意指定管理范围。
- 当前阶段没有队伍业务数据，但修改接口应按现有 `team` 表数量做好结构性字段保护。

## 三、本次范围

### 竞赛系列

- 分页和筛选查询竞赛。
- 查询竞赛详情及已发布届次摘要。
- 平台管理员创建和修改竞赛系列。
- 竞赛系列使用稳定 `series_code`；普通组织管理员不得创建官方系列。

### 具体比赛实例

- 按竞赛查询届次，支持年份和状态过滤。
- 授权组织管理员创建草稿比赛实例。
- 查询届次详情。
- 管理员修改未归档届次。
- 发布草稿届次为报名中。
- 查询、新增和修改补充赛程节点。
- 比赛实例包含主办组织 `owner_org_id`、级别 `level`、所属学校 `school_id`、省份 `province_code`、可见范围 `visibility`。
- `SCHOOL` 必须指定学校；`PROVINCE` 必须指定省份；`NATIONAL` 不绑定具体省份。
- 参赛资格模式支持 `OPEN`、`INVITED`、`QUALIFIED`。
- 增加是否允许跨校组队 `cross_school_team_allowed`，默认 false。

### 配置

- 报名截止、作品截止、评审开始、公示开始和结束时间。
- `FIXED` 固定人数和 `RANGE` 范围人数。
- 人数在 1～5；固定模式最小等于最大，范围模式最小不大于最大。
- 每位教师最多确认指导队伍数，范围 1～10，默认 3。
- 四种赛题模式：`PRESET_UNLIMITED`、`PRESET_LIMITED`、`SELF_PROPOSED`、`NO_TOPIC`。
- 选题审核、限额分配方式和更换次数。
- 同一主办组织、同一年度下比赛名称唯一；不同学校可以使用相同届次名称。

## 四、允许修改的文件

后端：

- `backend/src/main/java/com/campus/contest/module/contest/controller/`
- `backend/src/main/java/com/campus/contest/module/contest/dto/`
- `backend/src/main/java/com/campus/contest/module/contest/entity/`
- `backend/src/main/java/com/campus/contest/module/contest/enums/`
- `backend/src/main/java/com/campus/contest/module/contest/mapper/`
- `backend/src/main/java/com/campus/contest/module/contest/service/`
- `backend/src/main/java/com/campus/contest/module/contest/vo/`
- `backend/src/test/java/com/campus/contest/module/contest/`

前端：

- `frontend/src/api/contest.ts`（新增）
- `frontend/src/types/contest.ts`（新增）
- `frontend/src/views/admin/ContestManagementPage.vue`（新增）
- `frontend/src/views/admin/EditionEditorPage.vue`（新增）
- `frontend/src/views/common/ContestListPage.vue`（新增）
- `frontend/src/views/common/EditionDetailPage.vue`（新增）
- 本任务专属组件可放在相应 `components/` 目录，文件名必须使用 `Contest` 或 `Edition` 语义。

## 五、禁止修改的文件

- `docs/api/openapi-v1.json`
- `docs/api/接口汇总.md`
- `docs/sql/`
- `docs/db/`
- `docs/business/business-core/contract.md`
- `AGENTS.md`
- `backend/pom.xml`
- `backend/src/main/resources/application*.yml`
- 认证、RBAC、文件、队伍、选题应用、作品、评审和通知模块
- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/layouts/`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- 其他人的 `feature/business-profile` 分支文件

需要增加路由或公共菜单时，在 PR 描述中给出建议路由、权限编码和页面路径，由段欣懿统一合入。不得复制一套路由守卫或 Axios 实例。

## 六、实现要求

### 后端

1. 全部写接口使用对应 `contest:*`、`edition:*` 权限和 Spring Security，并同时校验角色数据范围。
2. 创建或修改竞赛、发布届次和节点变更的成功审计必须与业务修改处于同一事务，审计失败时回滚。
3. EP-012、EP-016、EP-019、EP-021 要求 `Idempotency-Key`；EP-014、EP-018、EP-022 的幂等头可选，无 key 时由 version 兜底。
4. 新届次初始状态固定为 `DRAFT`，数据库值 0。
5. 状态只允许枚举映射，不在业务代码散落数字；数据库映射以公共契约为准。
6. EP-013 竞赛详情必须返回竞赛主档 `version`，供 EP-014 修改主档时进行乐观锁校验。
7. EP-020 赛程节点列表中的每个节点必须返回 `version`，供 EP-022 修改节点时进行乐观锁校验。
8. 普通用户只能看到 `SCHOOL`、`PROVINCE`、`PUBLIC`、`UNLISTED` 中符合其学校、省份或邀请范围的比赛。
9. 管理员只能看到自己授权组织、省份或具体比赛范围内的草稿。
10. 发布前校验所有核心时间完整、顺序正确、人数有效、赛题模式已选择、规则有效，并校验报名截止时间晚于当前时间。
11. 发布不要求预设赛题已存在。
12. 报名开始后尚未产生队伍时可修改；产生队伍后，人数规则、赛题模式、更换次数和教师指导上限禁止修改。
13. DRAFT 始终可编辑；非 DRAFT 已发布比赛的核心截止时间只能延长，不能提前。
14. 届次详情返回 `registration_open`、`submission_open`、`read_only`；报名和作品窗口只读取届次核心字段，不读取赛程节点。
15. 返回时间统一按 `Asia/Shanghai` 处理。

### 前端

1. 管理端支持竞赛列表、创建/编辑、届次列表、届次配置和发布。
2. 学生和教师端可查看竞赛、届次、核心时间、当前窗口和只读状态。
3. 固定人数模式显示一个人数输入；范围人数模式显示最少和最多人数输入。
4. 草稿状态只对管理范围内的管理员可见。
5. 页面使用公共 `request.ts` 和现有权限体系，不写死管理员判断。
6. 后端校验错误应显示在对应表单区域。

## 七、验收场景

1. 管理员创建竞赛并创建草稿届次，普通用户看不到草稿。
2. 管理员发布配置完整的届次，普通用户立即可见并看到报名窗口。
3. 时间缺失或顺序错误时不能发布。
4. 报名截止已过时不能发布。
5. 固定人数和范围人数均能正确保存和展示；非法人数被拒绝。
6. 教师指导上限按届次保存和展示；教师个人档案不参与判断。
7. 同一主办组织、同一年度下重名届次被拒绝；不同主办组织可以使用相同名称。
8. 已归档或没有编辑权限的用户不能修改届次。
9. 普通用户不能调用管理员创建、修改和发布接口。
10. 核心时间与补充赛程节点冲突时，资格判断仍以届次核心字段为准。
11. EP-013 返回当前竞赛 `version`，使用该版本修改成功；旧版本修改返回冲突。
12. EP-020 每个节点返回当前 `version`，使用该版本修改成功；旧版本修改返回冲突。
13. A 学校管理员不能查看或修改 B 学校草稿；四川管理员不能管理湖南比赛。
14. 校级比赛必须绑定学校，省级比赛必须绑定省份，国家级比赛不能冒用省级主办范围。
15. 前端生产构建通过，后端相关测试通过。

## 八、提交证据

PR 描述中必须包含：

1. 分支名和提交哈希。
2. 修改文件清单。
3. 后端测试命令、通过数和失败数。
4. 前端构建命令和结果。
5. 创建竞赛、草稿、发布、普通用户可见性、权限拒绝、时间校验、人数校验、重名和并发版本冲突的 HTTP 响应摘要。
6. EP-013 竞赛详情和 EP-020 赛程节点列表的实际响应片段，证明返回真实 `version`。
7. 管理端和学生端页面截图或可复现验证步骤。
8. 使用的 EP 编号和权限编码。
9. 需要段欣懿合入的公共路由或菜单片段。
10. 未解决事项及是否影响下一阶段。

## 九、完成后的下一步

PR 完成后保持未合并，交段欣懿审查。段欣懿确认并合入 `feature/business-core` 后，结合个人档案验收结果决定是否开放报名组织阶段；本任务负责人不得自行合并或提前开发队伍业务。
