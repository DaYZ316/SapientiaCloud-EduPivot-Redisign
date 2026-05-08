# AeroVerse Navigator 前端实施计划

## 目标

前端采用 Vue 3、TypeScript、Vite 建立浏览器端应用基础，第一阶段先完成认证入口和登录成功后的占位页面，为后续三维校园导航、建筑交互、能耗监控与 AI 问路扩展留出清晰结构。

## 当前阶段

- 新增 `aeroverse-frontend` 前端工程。
- 建立路由、Pinia 状态、Axios 请求封装、基础主题样式。
- 登录页对接后端已有 OAuth 授权码接口：
  - `POST /api/auth/google/login`
  - `POST /api/auth/github/login`
- 提供演示登录能力，方便在 OAuth 回调尚未完整配置前验证前端路由与登录态。
- 登录成功后进入一个轻量仪表盘页面，作为后续校园三维主场景的占位入口。

## 后续计划

- 接入正式 OAuth 授权跳转与回调页。
- 增加 Three.js 场景模块，完成 GLB 加载、建筑点击、高亮和镜头飞行。
- 增加导航面板，对接建筑、POI、路径规划接口。
- 增加能耗监控面板，对接实时快照、趋势图和 WebSocket 告警。
- 增加基础单元测试和登录流程 E2E 测试。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router + Pinia
- Axios
- Naive UI
- SCSS
- Vitest
