下面是一版可直接保存为 `GAME_TRANSFORMATION_PLAN.md` 的内容。

# AeroVerse Web 3D 游戏转型计划书

## 1. 目标
- 第一版做成“浏览器可直接进入”的 3D 游戏，而不是先做登录站。
- 采用 `Three.js + 现有 Vue/Vite 前端 + 现有 Spring Boot 后端`。
- 保留现有账号体系、JWT、用户资料和 OAuth 登录，但把它们降级为“账号入口”和“云同步能力”。
- 第一版玩法聚焦 `3D 探索 / 轻任务 / 收集 / 存档`，不碰大型开放世界和重度联机。

## 2. 现状判断
- 前端已经是 `Vue 3 + Vite + TypeScript + Pinia + Vue Router`，非常适合直接改成游戏壳。
- 当前页面结构还是登录页 / 成功页，说明现在的产品中心是“认证”，不是“玩法”。
- 后端已经具备 `Spring Boot + MyBatis-Plus + Redis + JWT + OAuth`，适合继续扩成游戏服务，而不是重写。
- 这套仓库本身更像“可扩展的 Web 平台”，所以最省成本的路线是先 Web 3D，再考虑 Unity。

## 3. 总体方案
- `Vue` 负责页面壳、HUD、面板、弹窗、账号中心、设置页。
- `Three.js` 负责场景、相机、渲染器、模型加载、动画循环、输入响应。
- `Pinia` 负责玩家状态、UI 状态、任务状态、存档状态。
- `IndexedDB` 负责本地存档和离线进度。
- `Spring Boot` 新增游戏域服务，负责角色档案、任务、背包、排行榜、云存档、在线状态。
- `Redis` 负责短期会话、在线状态、缓存、临时榜单。
- `Unity` 只作为第二阶段备选，不作为第一版主线。

## 4. 为什么先选 Three.js
- Three.js 官方文档明确推荐 `npm + build tool + Vite` 的方式来做本地开发和构建。
- Three.js 的最小运行模型就是 `scene + camera + renderer`，和你现在的 Vite 前端非常贴。
- Unity WebGL 官方文档强调的是桌面浏览器支持差异大，移动端不作为官方支持目标，压缩构建和服务器配置也更重。
- 你现在要的是“尽快做出能玩的版本”，不是先建一个更重的引擎工程。

## 5. 实施路线

| 阶段 | 目标 | 交付物 |
|---|---|---|
| Phase 0 | 改入口 | `/'` 进入游戏壳，不再默认跳登录页；登录变成右上角入口或覆盖层 |
| Phase 1 | 跑起来 | Three.js 场景、角色、镜头、基本移动、碰撞、触发器、HUD |
| Phase 2 | 可玩循环 | 交互、任务、收集、检查点、暂停、背包、存档 |
| Phase 3 | 账号同步 | 云存档、角色档案、登录后进度合并、本地与云端切换 |
| Phase 4 | 内容扩展 | 更多地图、任务链、道具、排行榜、事件系统 |
| Phase 5 | 备选迁移 | 只有在 Web 端遇到明显瓶颈时，再评估 Unity 客户端 |

## 6. 页面与路由
- `/'`：游戏主场景。
- `/login`：账号入口，不作为首屏主体。
- `/profile` 或右上角面板：用户资料、存档同步、退出登录。
- `/admin`：如需要，保留用户管理或调试入口。
- 进入游戏默认允许游客模式，登录后再开启云同步。

## 7. 后端与数据
- 新增一个独立的 `aeroverse-game` 后端模块，别把游戏逻辑继续塞进 `auth`。
- 游戏域接口建议独立在 `/api/game/**`。
- 数据建议按 `game_*` 前缀拆分，和 `auth_*` 分开。
- PostgreSQL 存持久数据，Redis 存在线态和缓存。
- 本地存档优先走 `IndexedDB`，不要把游戏存档压在 `localStorage` 上。

## 8. 建议接口
- `GET /api/game/profile`
- `POST /api/game/profile`
- `GET /api/game/save`
- `PUT /api/game/save`
- `GET /api/game/inventory`
- `PUT /api/game/inventory`
- `GET /api/game/quests`
- `PUT /api/game/quests`
- `GET /api/game/leaderboard`
- `POST /api/game/session/ping`

## 9. 验收标准
- 未登录时也能直接进入游戏并正常游玩。
- 登录后能自动同步角色档案和存档。
- canvas 自适应窗口，resize 不崩。
- 首屏不白屏，资源加载失败时有兜底。
- 本地存档可恢复，断网不影响基本游玩。
- Playwright 能跑通首屏、登录、关键交互。
- 后端测试覆盖新增游戏服务与接口。

## 10. 风险与约束
- 第一版不要做大型开放世界。
- 第一版不要做重联机 MMO。
- 第一版不要重写整套后端认证。
- 第一版不要直接切 Unity WebGL，除非 Web 版已经出现明确瓶颈。
- 游戏主 HUD 不要完全依赖通用后台 UI 组件，核心画面最好自绘。

## 11. 技术栈

| 层级 | 选型 | 作用 |
|---|---|---|
| 前端壳 | Vue 3 + Vite + TypeScript | 页面壳、路由、面板、账号入口 |
| 3D 引擎 | Three.js | 场景、相机、渲染、模型、动画 |
| 物理/碰撞 | Rapier | 角色移动、碰撞、触发器、刚体 |
| 状态管理 | Pinia | 玩家状态、UI 状态、任务状态 |
| 路由 | Vue Router | 游戏壳、账号入口、设置页 |
| API | Axios | 游戏与后端通信 |
| 本地存档 | IndexedDB | 离线存档、进度缓存 |
| 后端 | Spring Boot + MyBatis-Plus + Redis + JWT | 账号、档案、存档、排行榜、在线态 |
| 测试 | Vitest + Playwright + JUnit | 前端单测、E2E、后端测试 |
| 资源 | GLB / glTF / Web 音频 | 模型、贴图、音效 |
| 部署 | Vite 构建 + Nginx / 静态托管 + API 网关 | Web 首发部署 |

## 12. 备选栈
- 如果未来必须切客户端，再考虑 `Unity 6 + C# + URP + WebGL / Standalone`。
- 这条线只建议作为第二阶段，不建议作为第一版主线。

## 13. 官方参考
- [Three.js Installation](https://threejs.org/manual/en/installation.html)
- [Three.js Creating a Scene](https://threejs.org/manual/en/creating-a-scene.html)
- [Three.js Loading 3D Models](https://threejs.org/manual/en/loading-3d-models.html)
- [Rapier JS Getting Started](https://rapier.rs/docs/user_guides/javascript/getting_started_js)
- [Unity WebGL Browser Compatibility](https://docs.unity3d.com/kr/6000.0/Manual/webgl-browsercompatibility.html)
- [Unity WebGL Deploying Compressed Builds](https://docs.unity3d.com/es/current/Manual/webgl-deploying.html)