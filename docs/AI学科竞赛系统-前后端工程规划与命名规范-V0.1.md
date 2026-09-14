# AI 大学生学科竞赛全流程智能服务平台 —— 前后端工程规划与命名规范

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.1（草案） |
| 团队规模 | 6 人 |
| 关联文档 | 《功能需求说明书》V0.1|
| 默认技术栈 | 后端 Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis；前端 Vue 3 + TypeScript + Vite + Element Plus |


---

## 一、工程规划

### 1.1 仓库结构

```text
contest-agent-platform/
├─ backend/                     # Spring Boot 后端
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/campus/contest/
│     └─ resources/
│        ├─ application.yml
│        ├─ application-dev.yml
│        └─ mapper/
├─ frontend/                    # Vue 3 前端
│  ├─ package.json
│  ├─ vite.config.ts
│  └─ src/
├─ docs/                        # 需求、设计、测试等课程文档
├─ scripts/                     # 种子数据/历届数据/AI 测试集生成脚本
├─ docker/                      # Docker Compose、MySQL 初始化 SQL
└─ README.md
```

> 说明：本目录树为初始约定，后续可按需新增目录（如 `tests/`、`mock/`），新增目录须遵守本文档命名规则、组内知会并更新本节，文档版本号随之升级。

### 1.2 后端模块划分与负责人

后端基础包统一为 `com.campus.contest`，按领域分包：

```text
com.campus.contest
├─ ContestApplication.java
├─ common/                  # 统一返回、异常、常量、枚举、工具
├─ config/                  # Redis、MyBatis-Plus、CORS 等配置
├─ security/                # JWT、登录用户、权限注解
├─ storage/                 # 文件上传存储
├─ ai/                      # LLM 客户端、Prompt、RAG、相似度计算、异步任务编排
└─ module/
   ├─ system/               # 用户、角色、权限
   ├─ contest/              # 竞赛主档、届次、赛程节点、赛题
   ├─ team/                 # 队伍、队员、报名两级审核、选题
   ├─ work/                 # 作品、文件、版本、AI 预审、原创性初检
   ├─ review/               # 评分维度、评审任务、打分、成绩公示、异议处理
   ├─ knowledge/            # 规则知识库、问答
   ├─ rehearsal/            # 答辩预演
   ├─ notification/         # 站内通知、风险预警
   ├─ showcase/             # 获奖展厅、个人竞赛名片
   └─ statistics/           # 数据看板
```

Redis 用途约定：登录令牌/会话缓存、热点数据缓存（如竞赛列表）、AI 异步任务状态暂存。禁止将业务数据只存 Redis 不落库。

负责人约定（最终以实际能力微调）：

| 成员 | 负责模块 |
| --- | --- |
| A | `common`、`config`、`security`、`storage`、整体联调与部署 |
| B | `module/system`、`module/contest`、`module/team`、`module/notification` |
| C | `module/work`、`module/review`、`module/statistics`、`module/showcase` |
| F | `ai` 全部内容，以及 `knowledge` 的分段/检索、`rehearsal` 的出题与评分逻辑、`work` 的预审与相似度算法 |
| C + F | `module/knowledge`、`module/rehearsal` 的接口与持久化（C 写框架，F 提供 AI 能力） |
| D、E | 前端为主，见 1.3；联调期协助各自对接模块的接口自测 |

> 协作边界约定：`work` 模块的 AI 预审/原创性初检、`knowledge` 的问答、`rehearsal` 的出题评分，均由 C/B 提供 REST 接口与持久化，F 通过内部 Service 接口提供 AI 能力；接口签名在第 3 周设计定稿时冻结，禁止联调期临时改签名。

每个模块内部统一分层：

```text
module/contest/
├─ controller/   ContestController.java / EditionController.java
├─ service/      ContestService.java + impl/ContestServiceImpl.java
├─ mapper/       ContestMapper.java / EditionMapper.java
├─ entity/       ContestDO.java / EditionDO.java
├─ dto/          ContestCreateDTO.java / EditionQuery.java
└─ vo/           EditionDetailVO.java
```

跨模块只允许调用 Service 接口，禁止跨模块直接使用 Mapper。

### 1.3 前端目录与负责人

```text
frontend/src/
├─ api/                # 按业务模块拆分：auth、contest、team、work、review、knowledge、rehearsal、notification、showcase、statistics
├─ assets/
├─ components/         # 通用组件
├─ composables/        # useXxx 组合式函数
├─ directives/
├─ layouts/            # 学生/教师/管理员等不同布局
├─ router/
├─ stores/             # Pinia
├─ styles/
├─ types/
├─ utils/
└─ views/
   ├─ common/          # 登录、首页、竞赛列表等公共页面
   ├─ student/         # D 负责：组队、选题、作品、预审报告、答辩预演、问答
   ├─ showcase/        # D 负责：获奖展厅、个人竞赛名片
   ├─ teacher/         # E 负责：报名审核、队伍进度与预警、作品查看、结果查看
   ├─ reviewer/        # E 负责：评审任务、打分
   └─ admin/           # E 负责：竞赛/届次/赛题/评委/公示/知识库/看板配置
```

约定：

- D 主写学生端与展厅/名片，E 主写教师/评委/管理端；公共组件由双方共建，不独占；
- 页面必须按角色目录放，禁止四种角色页面混在一个目录；
- 前后端接口联调前，前端统一使用 `src/api/` 下的 mock 数据或本地假接口，不许等后端。

### 1.4 开发阶段计划（核心开发 8 周，映射到 16 周课程安排的第 4–14 周）

| 周次 | 阶段 | 工作 | 完成标志 |
| --- | --- | --- | --- |
| 第 1 周 | 契约先行 | A 定数据库/接口/命名，B/C 写表结构（含竞赛/届次两级模型），D/E 出页面原型，F 整理知识库与 AI 测试集 | 接口清单冻结、数据库脚本可初始化 |
| 第 2 周 | 骨架与权限 | A 搭前后端骨架并实现登录鉴权，B 完成用户/竞赛模块 | 四类角色可登录，菜单按角色区分 |
| 第 3 周 | 报名链路 | B 完成届次/赛题/队伍/两级审核，D 完成学生报名组队页面 | 可创建队伍、选导师、过审核、选题 |
| 第 4 周 | 作品链路 | C 完成作品上传与版本管理，D 完成学生提交页，F 提供第一版 AI 预审能力 | 文档可上传并生成预审报告 |
| 第 5 周 | 评审链路 | C 完成评审分配、打分汇总与公示，E 完成评委/管理端 | 评委可盲评打分并出排名 |
| 第 6 周 | AI 深化 | F 完成 RAG 问答/答辩预演/原创性初检，C 接通接口，D/E 展示结果 | 问答可引用来源，预演可出报告 |
| 第 7 周 | 完善与数据 | 全员补预警、展厅、名片、看板、种子数据（含历届数据）、AI 评测 | 演示数据完整，加分项按取舍落地 |
| 第 8 周 | 交付 | A 部署 Docker，全员测试（含 AI 降级验证），D/E 录视频，补齐课程文档 | 一键启动，验收必达项全过 |

### 1.5 Git 协作规范

分支模型：

| 分支 | 用途 |
| --- | --- |
| `main` | 可演示的稳定版本 |
| `develop` | 日常集成分支 |
| `feature/contest-create` | 新功能，从 `develop` 拉出 |
| `fix/login-expired` | 缺陷修复 |
| `refactor/review-service` | 重构 |

提交信息使用 Conventional Commits：

```text
feat(contest): 竞赛届次创建与发布
feat(work): 对接 AI 预审接口
fix(team): 修复成员退出后队长状态错误
docs(db): 补充届次表索引
```

流程要求：

- 成员从 `develop` 拉功能分支，完成后发起合并，由 A 或另一名组员 Code Review；
- 禁止直接提交 `main`；
- 密钥、真实数据、`.env` 禁止进仓库；
- 每周至少合一次 `develop`，避免大冲突。

### 1.6 Definition of Done

一个功能"完成"必须同时满足：

- 后端接口通过自测，返回结构与接口文档一致；
- 前端页面可操作，错误状态有提示；
- 代码通过 ESLint/格式化检查，命名符合本规范；
- 涉及数据库的改动已同步到初始化 SQL；
- 涉及 AI 的功能有至少一条可演示的输入输出记录，且 AI 服务断开时页面有降级提示。

---

## 二、统一业务术语

全项目禁止同义混用：

| 中文 | 统一用词 | 说明 |
| --- | --- | --- |
| 竞赛 | `contest` | 赛事主档，可举办多届 |
| 届次 | `edition` | 竞赛的一次举办实例 |
| 赛程节点 | `milestone` | 届次下的时间节点 |
| 赛题 | `topic` | 届次下设题目 |
| 队伍 | `team` | 参赛队伍 |
| 队员 | `teamMember` | 队伍成员关系 |
| 指导教师 | `teacher` | 通过角色区分 |
| 评审专家 | `reviewer` | 通过角色区分 |
| 作品提交记录 | `workSubmission` | 一次作品版本 |
| 附件 | `file` | 作品文件 |
| AI 预审 | `aiPrecheck` | 对照评审细则的提交前检查 |
| 原创性初检 | `originalityCheck` | 与本届及往届作品的相似度比对 |
| 适配度分析 | `fitAnalysis` | 智能选题的适配度与能力缺口分析 |
| 评分维度 | `rubric` | 维度与权重 |
| 评审任务 | `reviewAssignment` | 专家被分配的任务 |
| 成绩公示 | `resultPublication` | 成绩公示与获奖名单 |
| 异议申诉 | `appeal` | 公示期内的成绩异议 |
| 答辩预演 | `rehearsalSession` | 一次预演会话 |
| 规则文档 | `knowledgeDoc` | 知识库文档 |
| 问答消息 | `qaMessage` | 带引用来源的消息 |
| 通知 | `notification` | 站内消息 |
| 风险预警 | `riskWarning` | 标红预警记录 |
| 获奖展厅 | `showcase` | 获奖作品展示 |
| 竞赛名片 | `portfolio` | 个人竞赛履历 |

状态值统一成枚举，禁止散落 0/1：

| 枚举 | 取值示例 |
| --- | --- |
| `EditionStatusEnum` | `DRAFT`、`REGISTERING`、`SUBMITTING`、`REVIEWING`、`PUBLISHING`、`ARCHIVED` |
| `TeamStatusEnum` | `PENDING_TEACHER`、`PENDING_ADMIN`、`APPROVED`、`REJECTED` |
| `SubmissionStatusEnum` | `DRAFT`、`SUBMITTED`、`LOCKED` |
| `AiPrecheckStatusEnum` | `PENDING`、`RUNNING`、`SUCCESS`、`FAILED` |
| `ReviewStatusEnum` | `PENDING`、`IN_PROGRESS`、`SUBMITTED` |
| `AppealStatusEnum` | `PENDING`、`UPHELD`、`ADJUSTED` |
| `WarningTypeEnum` | `DEADLINE_APPROACHING`、`INACTIVE`、`PRECHECK_FAILED` |

---

## 三、命名规范

### 3.1 数据库命名

- 表名全小写 snake_case、单数；系统表加 `sys_` 前缀；
- 主键 `id`，时间字段 `created_at`/`updated_at`，逻辑删除 `deleted`；
- 普通索引 `idx_表名_字段`，唯一索引 `uk_表名_字段`；
- 业务表统一带届次外键 `edition_id`（队伍、作品、评审、公示等均挂在届次下）；
- 表名示例：`sys_user`、`contest`、`contest_edition`、`contest_topic`、`team`、`team_member`、`work_submission`、`review_assignment`、`appeal`、`risk_warning`、`showcase_item`。

### 3.2 Java 命名

| 对象 | 规范 | 正确示例 | 错误示例 |
| --- | --- | --- | --- |
| 包名 | 全小写 | `com.campus.contest.module.team` | `com.campus.Team` |
| 类名 | UpperCamelCase | `EditionService` | `editionService` |
| 方法 | lowerCamelCase，动词开头 | `createEdition()` | `edition_create()` |
| 变量 | lowerCamelCase | `teamList` | `team_list`、`list1` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE` | `defaultPageSize` |
| 枚举 | 业务 + `Enum` | `EditionStatusEnum` | `Status` |
| 枚举值 | UPPER_SNAKE_CASE | `PENDING_TEACHER` | `PendingTeacher` |
| Controller | `XxxController` | `EditionController` | `Ctl` |
| Service | `XxxService` | `TeamService` | `TeamSvc` |
| Service 实现 | `XxxServiceImpl` | `TeamServiceImpl` | `TeamServiceImp` |
| Mapper | `XxxMapper` | `WorkSubmissionMapper` | `WorkSubmissionDao` |
| 实体 | `XxxDO` | `EditionDO` | `EditionEntity` |
| 新增/修改入参 | `XxxCreateDTO` / `XxxUpdateDTO` | `EditionCreateDTO` | `EditionDTO` |
| 查询参数 | `XxxQuery` | `EditionQuery` | `EditionSearchDTO` |
| 返回对象 | `XxxVO` | `EditionDetailVO` | `EditionDTO` |

### 3.3 前端命名

| 对象 | 规范 | 正确示例 | 错误示例 |
| --- | --- | --- | --- |
| Vue 文件 | PascalCase，多单词 | `EditionCreateDialog.vue` | `edition_create.vue`、`Dialog.vue` |
| 页面目录 | 按角色小写 | `views/admin/` | `views/ADMIN/` |
| TS 变量/函数 | camelCase | `fetchTeamList()` | `fetch_team_list()` |
| 常量 | UPPER_SNAKE_CASE | `ROLE_ADMIN` | `roleAdmin` |
| 类型/接口 | PascalCase，不加 I | `interface EditionDetail` | `IEditionDetail` |
| API 函数 | `fetch/create/update/delete` 开头 | `createTeam()`、`fetchEditionList()` | `save()` |
| 路由 path | 小写连字符 | `/admin/editions` | `/Admin/editions` |
| 路由 name | PascalCase | `name: 'AdminEditionList'` | `name: 'admin-edition-list'` |
| Pinia | `use` + PascalCase | `useAuthStore()` | `authStore()` |
| 组件事件 | kebab-case | `@submit-success` | `@submitSuccess` |
| CSS 类 | kebab-case/BEM | `.contest-card__header` | `.contestCardHeader` |

组件规范：

- 组件必须是多单词：`ContestCard.vue` 可以，`Card.vue` 不行；
- 使用 `<script setup lang="ts">`，业务代码禁止 `any`；
- Props 用 `defineProps<T>()`，事件用 `defineEmits<...>()`；
- 页面负责请求数据和跳转，通用组件只负责展示和通知，不直接调接口；
- 请求统一走 `src/api/request.ts`，禁止页面内新建 Axios 实例；
- 组件样式默认 `scoped`。

### 3.4 API 命名

- 统一前缀 `/api/v1`，资源用复数、多词连字符：`/api/v1/contests`、`/api/v1/review-assignments`；
- 只有非 CRUD 动作才用动词：`/works/{id}/ai-precheck`、`/teams/{id}/choose-topic`；
- 前端函数名与后端动作一一对应：`fetchEditionList` ↔ `GET /editions`，`submitReviewScore` ↔ `POST /review-assignments/{id}/submit-score`。

典型接口命名：

```text
POST   /api/v1/auth/login
GET    /api/v1/contests
POST   /api/v1/contests/{id}/editions
POST   /api/v1/editions/{id}/publish
POST   /api/v1/editions/{editionId}/teams
POST   /api/v1/teams/{id}/choose-topic
GET    /api/v1/topics/{id}/fit-analysis
POST   /api/v1/works/{id}/ai-precheck
GET    /api/v1/works/{id}/precheck-report
POST   /api/v1/works/{id}/originality-check
POST   /api/v1/review-assignments/{id}/submit-score
POST   /api/v1/editions/{id}/appeals
POST   /api/v1/rehearsals
GET    /api/v1/warnings
GET    /api/v1/showcase/works
GET    /api/v1/statistics/overview
```

---

## 四、交付检查清单

- [ ] 后端 `mvn compile`、前端 `pnpm build` 全部通过；
- [ ] 代码格式化、命名符合本规范；
- [ ] 四个角色登录后菜单和数据权限正确；
- [ ] "发布竞赛届次 → 组队选题 → 两级审核 → 作品 → AI 预审 → 盲评 → 成绩公示"主链路可演示；
- [ ] 规则问答回答带来源引用，超库问题明确拒答；
- [ ] AI 服务断开时主链路可走通、页面有降级提示；
- [ ] 数据库可从零初始化，Docker 一键启动；
- [ ] 种子数据（含 2–3 个历史届次）和 AI 测试集在 `scripts/` 下可复现；
- [ ] 课程文档齐全，README 含启动步骤和演示账号。

> 重要提醒：本规范在设计定稿周必须冻结接口与命名，后续只允许小范围修订；频繁改契约会让 6 个人的工作同时返工。确需修订时，组内确认后升级文档版本号并全员同步。
