# 正式任务卡：个人档案

| 项目 | 内容 |
| --- | --- |
| 任务编号 | BIZ-PROFILE-001 |
| 认领人 | 刘同学 |
| 业务域 | 赛前基础 / 个人档案 |
| 开发分支 | `feature/business-profile` |
| PR 目标分支 | `feature/business-core` |
| 公共契约基线 | 最新 `origin/feature/business-core`，必须包含 v1.4.0 多组织基线 |
| 当前状态 | 已认领；PR #14 保持 Draft，按 v1.4.0 多组织基线返工 |
| 最终确认人 | 段欣懿 |

## 一、本次要完成的小闭环

登录用户进入自己的个人档案页面后，可以查看并修改角色对应的档案字段；保存后刷新仍能看到新值。学生只能维护学生字段，教师只能维护教师字段，任何用户都不能通过该接口修改角色、权限或账号状态。

本任务必须形成可独立验收的：

```text
登录
  → 查询当前用户档案
  → 编辑本人角色对应字段
  → 版本校验保存
  → 审计记录
  → 刷新确认结果
```

## 二、冻结字段

学生：

- 姓名 `real_name`
- 学校 `school_id`
- 学号 `student_no`
- 学院 `college`
- 专业 `major`
- 年级 `grade`

教师：

- 姓名 `real_name`
- 学校 `school_id`
- 学院 `college`
- 职称 `title`

其他：

- `version` 用于乐观锁，不属于业务资料字段，但查询和修改必须处理。
- `skill_tags` 只保留旧数据只读兼容，本任务不提供编辑入口。
- `research_area`、`max_teams` 不得出现在个人档案编辑页面和修改接口中。

## 三、前置依赖

- 从最新 `origin/feature/business-core` 创建分支，不以 `develop` 或旧高并发分支为起点。
- 已阅读 `AGENTS.md`、`docs/business/business-core/contract.md`、对应 OpenAPI EP-003/EP-004。
- 使用现有 Spring Security 当前用户能力和审计日志服务。
- 使用多组织公共增量中的 `organization`、`sys_user.school_id` 和 `(school_id, student_no)` 唯一约束。
- 使用公共 `GET /api/v1/schools` 查询启用学校，不在个人档案模块重复创建学校列表接口。
- 学校确认使用公共 `GET /api/v1/school-affiliations` 和 `PATCH /api/v1/school-affiliations/{user_id}`，不另造审核模型。

## 四、允许修改的文件

后端：

- `backend/src/main/java/com/campus/contest/module/system/controller/UserController.java`
  - 只允许增加 `GET /api/v1/users/me` 的档案字段适配和 `PATCH /api/v1/users/me`。
  - 不得改动 EP-005 用户列表和 EP-007 管理员修改用户逻辑。
- `backend/src/main/java/com/campus/contest/module/system/dto/`
  - 可新增个人档案修改 DTO。
- `backend/src/main/java/com/campus/contest/module/system/service/`
  - 可新增个人档案 Service 接口及实现。
- `backend/src/main/java/com/campus/contest/module/system/vo/CurrentUserVO.java`
  - 增加学校、学号、职称、版本和角色对应的只读兼容字段。
- `backend/src/main/java/com/campus/contest/module/system/mapper/SysUserMapper.java`
  - 仅在本任务确实需要条件更新或查询时做最小修改。
- `backend/src/test/java/com/campus/contest/module/system/`
  - 新增本任务单元测试、HTTP 测试或数据范围测试。

前端：

- `frontend/src/api/profile.ts`（新增）
- `frontend/src/types/profile.ts`（新增）
- `frontend/src/views/common/ProfilePage.vue`（新增）
- 本任务专属组件可放在 `frontend/src/views/common/components/`，文件名必须带 `Profile` 语义。

## 五、禁止修改的文件

- `docs/api/openapi-v1.json`
- `docs/api/接口汇总.md`
- `docs/sql/`
- `docs/db/`
- `docs/business/business-core/contract.md`
- `AGENTS.md`
- `backend/pom.xml`
- `backend/src/main/resources/application*.yml`
- 认证、验证码、会话、RBAC 的现有 Controller、Service、Mapper 和测试
- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/router/index.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- 其他人的 `feature/business-contest-edition` 分支文件

需要修改路由时，在 PR 描述中提供建议路由和页面组件路径，由段欣懿统一合入。验收页面暂时无法通过公共路由打开时，可以用组件构建、直接调用 HTTP 和页面测试证明功能，不另建第二套路由。

## 六、实现要求

### 后端

1. `GET /api/v1/users/me` 返回当前用户的角色相关档案、所属学校、学校确认状态和最新 `version`。
2. `PATCH /api/v1/users/me` 只修改当前登录用户。
3. 学生请求携带 `title` 时返回参数错误；教师请求携带 `student_no`、`major` 或 `grade` 时返回参数错误。
4. 姓名、学号允许本人修改；学号必须在同一学校内唯一，不同学校允许相同学号。
5. 学生和教师首次选择学校后进入 `PENDING`；学校管理员在现有用户管理页确认或驳回。
6. 更新必须使用 `version` 乐观锁；旧版本返回 HTTP 409 和现有冲突业务码。
7. 成功变更与审计写入必须处于同一事务；审计失败时档案修改回滚。
8. 不允许通过本接口修改 `username`、`password_hash`、`email`、`status`、角色和权限。
9. 权限必须由 Spring Security 校验，不能只依赖前端隐藏输入框。

### 前端

1. 档案页面根据当前用户角色展示对应字段。
2. 保存请求使用公共 `request.ts`，不创建第二套 Axios。
3. 保存成功显示结果并刷新当前档案及版本。
4. 版本冲突时明确提示“资料已被修改，请刷新后重试”，不得静默覆盖。
5. 教师页面不能显示学生字段，学生页面不能显示职称字段。
6. 表单校验与服务端错误提示保持一致。

## 七、验收场景

1. 学生查看和修改姓名、学校、学号、学院、专业、年级，刷新后数据仍存在。
2. 教师查看和修改姓名、学校、学院、职称，刷新后数据仍存在。
3. 学生提交教师字段被拒绝；教师提交学生字段被拒绝。
4. 用户不能修改其他用户的档案。
5. 两个请求使用相同旧版本时，只有第一个成功，第二个返回冲突。
6. 同校重复学号被拒绝，不同学校相同学号可以成功。
7. 姓名或学号修改后，审计日志能查到操作，但不包含敏感字段。
8. 学生和教师能选择学校并显示 `PENDING`、`VERIFIED`、`REJECTED` 和驳回原因；已确认学校不能由本人直接切换。
9. 学校管理员通过现有用户管理页处理待审核列表，确认或驳回后写审计。
10. 前端生产构建通过，后端相关测试通过。

## 八、提交证据

PR 描述中必须包含：

1. 分支名和提交哈希。
2. 修改文件清单。
3. 实际执行的后端测试命令、通过数和失败数。
4. 前端构建命令和结果。
5. 学生和教师查询、成功修改、角色越界、重复学号、版本冲突的 HTTP 响应摘要。
6. 审计记录证据，敏感字段已脱敏。
7. 页面截图或可复现的页面验证步骤。
8. 未解决问题和需要段欣懿合入的公共文件片段。

## 九、完成后的下一步

PR 完成后保持未合并，交段欣懿审查。段欣懿确认并合入 `feature/business-core` 后，根据验收结果开放报名组织阶段；本任务负责人不得自行合并或提前开发报名业务。
