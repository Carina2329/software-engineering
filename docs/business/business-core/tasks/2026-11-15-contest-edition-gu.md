# 正式任务卡：竞赛与届次

| 项目 | 内容 |
| --- | --- |
| 任务编号 | BIZ-CONTEST-EDITION-001 |
| 认领人 | 谷同学 |
| 业务域 | 赛前基础 / 竞赛与届次 |
| 开发分支 | `feature/business-contest-edition` |
| PR 目标分支 | `feature/business-core` |
| 公共契约基线 | `c345b16`，实际开发从最新 `origin/feature/business-core` 拉分支 |
| 当前状态 | 已认领，待开始 |
| 最终确认人 | 段欣懿 |

## 一、本次要完成的小闭环

管理员创建竞赛主档和草稿届次，配置核心时间、固定/范围人数、届次教师指导上限和赛题规则，并在配置完整后发布为报名中；普通用户能查看已发布届次、当前时间窗口和只读状态。

本任务必须形成可独立验收的：

```text
管理员创建竞赛
  → 创建草稿届次
  → 配置时间、人数和赛题规则
  → 校验并发布
  → 普通用户查看已发布届次和当前窗口
```

## 二、前置依赖

- 从最新 `origin/feature/business-core` 创建分支。
- 已阅读 `AGENTS.md`、`docs/business/business-core/contract.md`、OpenAPI EP-011 至 EP-022 和数据库增量 V0.2。
- 公共 SQL 已由段欣懿提供，本任务只使用，不修改。
- 公共权限种子已提供 `contest:*`、`edition:*` 和赛程节点权限；不得另造权限编码。
- 当前阶段没有队伍业务数据，但修改接口应按现有 `team` 表数量做好结构性字段保护。

## 三、本次范围

### 竞赛主档

- 分页和筛选查询竞赛。
- 查询竞赛详情及已发布届次摘要。
- 管理员创建和修改竞赛。
- 竞赛名称允许重复。

### 届次

- 按竞赛查询届次，支持年份和状态过滤。
- 管理员创建草稿届次。
- 查询届次详情。
- 管理员修改未归档届次。
- 发布草稿届次为报名中。
- 查询、新增和修改补充赛程节点。

### 配置

- 报名截止、作品截止、评审开始、公示开始和结束时间。
- `FIXED` 固定人数和 `RANGE` 范围人数。
- 人数在 1～5；固定模式最小等于最大，范围模式最小不大于最大。
- 每位教师最多确认指导队伍数，范围 1～10，默认 3。
- 四种赛题模式：`PRESET_UNLIMITED`、`PRESET_LIMITED`、`SELF_PROPOSED`、`NO_TOPIC`。
- 选题审核、限额分配方式和更换次数。
- 同竞赛下届次名称唯一，同一年份允许多个不同届次。

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

1. 全部写接口使用对应 `contest:*`、`edition:*` 权限和 Spring Security。
2. 创建或修改竞赛、发布届次和节点变更使用公共审计能力。
3. 写接口按要求使用 `Idempotency-Key`；更新使用 `version`。
4. 新届次初始状态固定为 `DRAFT`，数据库值 0。
5. 状态只允许枚举映射，不在业务代码散落数字；数据库映射以公共契约为准。
6. EP-013 竞赛详情必须返回竞赛主档 `version`，供 EP-014 修改主档时进行乐观锁校验。
7. EP-020 赛程节点列表中的每个节点必须返回 `version`，供 EP-022 修改节点时进行乐观锁校验。
8. 普通用户看不到草稿届次；管理员可看全部。
9. 发布前校验所有核心时间完整、顺序正确、人数有效、赛题模式已选择、规则有效，并校验报名截止时间晚于当前时间。
10. 发布不要求预设赛题已存在。
11. 报名开始后尚未产生队伍时可修改；产生队伍后，人数规则、赛题模式、更换次数和教师指导上限禁止修改。
12. 截止时间已经进入对应阶段后只能延长，不能提前。
13. 届次详情返回 `registration_open`、`submission_open`、`read_only`；报名和作品窗口只读取届次核心字段，不读取赛程节点。
14. 返回时间统一按 `Asia/Shanghai` 处理。

### 前端

1. 管理端支持竞赛列表、创建/编辑、届次列表、届次配置和发布。
2. 学生和教师端可查看竞赛、届次、核心时间、当前窗口和只读状态。
3. 固定人数模式显示一个人数输入；范围人数模式显示最少和最多人数输入。
4. 草稿状态只对管理员可见。
5. 页面使用公共 `request.ts` 和现有权限体系，不写死管理员判断。
6. 后端校验错误应显示在对应表单区域。

## 七、验收场景

1. 管理员创建竞赛并创建草稿届次，普通用户看不到草稿。
2. 管理员发布配置完整的届次，普通用户立即可见并看到报名窗口。
3. 时间缺失或顺序错误时不能发布。
4. 报名截止已过时不能发布。
5. 固定人数和范围人数均能正确保存和展示；非法人数被拒绝。
6. 教师指导上限按届次保存和展示；教师个人档案不参与判断。
7. 同竞赛重名届次被拒绝，同一年份不同名称届次可创建。
8. 已归档或没有编辑权限的用户不能修改届次。
9. 普通用户不能调用管理员创建、修改和发布接口。
10. 核心时间与补充赛程节点冲突时，资格判断仍以届次核心字段为准。
11. EP-013 返回当前竞赛 `version`，使用该版本修改成功；旧版本修改返回冲突。
12. EP-020 每个节点返回当前 `version`，使用该版本修改成功；旧版本修改返回冲突。
13. 前端生产构建通过，后端相关测试通过。

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
