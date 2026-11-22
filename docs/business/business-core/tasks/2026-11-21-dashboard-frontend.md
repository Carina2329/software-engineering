# 正式任务卡：数字大屏一期前端与可视化

| 项目 | 内容 |
| --- | --- |
| 任务编号 | `BIZ-DASHBOARD-FRONTEND-001` |
| 认领人 | 李同学 |
| 业务域 | 平台运行总览 / 大屏可视化 |
| 开发分支 | `feature/business-dashboard-frontend` |
| PR 目标分支 | `feature/business-core` |
| 公共契约 | 最新 `feature/business-core` 和 `dashboard-contract.md` |
| 当前状态 | 已完成并合并（PR #19，合并提交 `5492c181ad38e202f3c0b82f0d71578b5a13e358`） |
| 最终确认人 | 段欣懿 |

## 一、任务目标

完成平台管理员使用的数字大屏页面，通过 REST 获取首次快照，通过 WebSocket 接收实时更新。

页面必须真实、清晰、适合全屏展示，不展示尚未产生的队伍、作品、评审或获奖数据。

接口字段、消息结构和前端行为以
[`dashboard-contract.md`](../dashboard-contract.md) 为唯一事实源。

## 二、前置依赖

- 从最新 `feature/business-core` 创建分支。
- 已阅读 `AGENTS.md`、公共业务契约、本任务卡和 `dashboard-contract.md`。
- 使用现有 Vue 3、TypeScript、Vite、Element Plus 和统一 Axios 客户端。
- ECharts 依赖由段欣懿统一确认后加入。
- 公共路由、菜单和权限种子由段欣懿统一接入。

## 三、页面内容

顶部：

- 标题“学科竞赛平台运行总览”；
- 统计范围“全平台”；
- WebSocket 实时连接状态。

核心指标：

- 有效学校数；
- 有效用户数；
- 当前开放报名的届次数；
- 待审核学校归属申请数。

中部区域：

- 省份学校分布，横向柱状图；
- 用户角色构成，环形图。

赛事运行区域：

- 竞赛系列数；
- 已发布届次数；
- 校级、省级、国家级分布；
- 最近 7 天报名截止列表，最多 5 条。

底部只显示：

- 数据更新时间；
- 数据来源说明。

底部不得放届次状态图、广告、操作按钮或其他业务模块。

## 四、允许修改的文件

```text
frontend/src/api/dashboard.ts
frontend/src/types/dashboard.ts
frontend/src/views/admin/DashboardPage.vue
frontend/src/components/dashboard/
frontend/src/views/admin/dashboard/
```

如新增 ECharts 引用，只允许在数字大屏专属组件中使用。

## 五、禁止修改

- `frontend/src/router/index.ts`
- `frontend/src/layouts/`
- `frontend/src/api/request.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- 后端代码
- OpenAPI、公共 SQL、权限种子和 `AGENTS.md`
- 其他业务页面和公共组件

需要增加路由、菜单或权限时，在 PR 描述中提供建议，由段欣懿统一合入。

## 六、前端实现要求

1. 使用 `frontend/src/api/request.ts`，不得创建第二套 Axios。
2. 首次进入调用 REST 快照；快照成功后请求 ticket 并连接 WebSocket。
3. 不使用轮询替代 WebSocket。
4. 断线时保留最后一次数据，显示受控的“实时连接中断”状态。
5. 重连采用指数退避，最大间隔 30 秒。
6. 重连成功后先重新请求 REST 快照，再恢复 WebSocket。
7. 页面卸载时关闭连接、清理定时器并释放 ECharts 实例。
8. WebSocket 消息必须按 `type` 和 `version` 校验，未知消息忽略。
9. 加载、无数据、接口失败、鉴权失败和 WebSocket 断开都有明确状态。
10. 禁止生产代码中保留假数据。
11. 所有图表在容器尺寸变化时自动 resize。
12. 主要适配 `1920×1080` 和 `2560×1440`，普通笔记本不得横向溢出。
13. 底部只展示更新时间和来源。
14. 不新增独立后端或 Node 服务。

## 七、建议视觉方向

- 延续现有深绿、米白和金色品牌色；
- 使用 CSS Grid 组织全屏布局；
- 使用 Apache ECharts 完成柱状图、环形图和必要的数据提示；
- 不使用三维地图、WebGL 或高成本粒子动画；
- 图表和数字优先，装饰元素不得干扰阅读；
- 连接中断、无数据和服务异常必须清楚可辨。

## 八、开发方式

- 后端契约未完成期间，可以使用契约样例或本地 fixture 开发页面；
- fixture 不得提交为生产数据路径；
- 联调阶段必须切换到真实 REST 和 WebSocket；
- 不得自行修改后端字段或另造统计口径。

## 九、测试与验收

必须完成：

1. `pnpm run build`；
2. `1920×1080` 截图；
3. `2560×1440` 截图；
4. 普通笔记本尺寸截图；
5. WebSocket 正常连接和实时更新验证；
6. 断开后端后显示连接中断；
7. 恢复后端后自动重连并刷新数据；
8. 空数据和服务异常状态；
9. 页面卸载后连接关闭；
10. 非平台管理员无法访问页面接口。

PR 描述必须包含：

- 分支、HEAD 和公共基线；
- 修改文件清单；
- 页面截图；
- REST 和 WebSocket 联调步骤；
- 构建结果；
- 路由和菜单建议；
- 未解决问题。

## 十、停止点

- PR 完成后保持 Ready，不自行合并。
- 不改公共路由、菜单和权限种子。
- 不开始数字大屏二期或其他业务模块。
- 完成后把 HEAD、截图、构建结果和联调步骤交给段欣懿。
