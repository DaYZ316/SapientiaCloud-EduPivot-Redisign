# 灵境·航途 AeroVerse Navigator 项目架构规划

## 1. 项目定位

**灵境·航途 AeroVerse Navigator** 是一个面向校园导航场景的数字孪生系统，核心目标是将学校外层建筑、道路、广场等空间环境进行
3D 还原，并提供可交互的校园导航体验。

核心能力：

- 校园外层建筑 3D 可视化
- 建筑点击、高亮与信息展示
- 起点与终点选择
- 校园路径规划
- 3D 路线绘制
- 镜头飞行定位
- 后期扩展 AI 问路能力

## 2. 推荐技术栈

| 模块     | 技术选型                          |
|--------|-------------------------------|
| 后端     | Spring Boot 3.5.x             |
| JDK    | JDK 21                        |
| ORM    | MyBatis-Plus                  |
| 数据库    | PostgreSQL + PostGIS          |
| 缓存     | Redis 7.x                     |
| 前端     | Vue 3 + TypeScript + Vite     |
| 3D 渲染  | Three.js                      |
| UI 组件库 | Naive UI / Element Plus       |
| 模型格式   | GLB / glTF                    |
| 建模工具   | Blender                       |
| 资产存储   | MinIO / 本地静态资源                |
| AI 扩展  | Spring AI + PgVector / Milvus |

## 3. 总体架构

```text
aeroverse-navigator
├── aeroverse-frontend          # Vue3 + Three.js 前端
├── aeroverse-backend           # Spring Boot 后端
├── aeroverse-model-assets      # 校园模型、贴图、GLB 文件
├── aeroverse-docs              # 项目文档、接口文档、数据库设计
└── aeroverse-deploy            # Docker、Nginx、部署脚本
```

## 4. 后端架构

第一版建议采用 **模块化单体架构**，不建议一开始直接上完整 Spring Cloud 微服务。

```text
aeroverse-backend
├── aeroverse-common            # 公共模块
├── aeroverse-campus            # 校园建筑与兴趣点模块
├── aeroverse-navigation        # 路径规划模块
├── aeroverse-asset             # 3D 模型与资源管理模块
├── aeroverse-user              # 用户、收藏、历史记录，可选
├── aeroverse-ai                # AI 问路模块，后期扩展
└── aeroverse-web               # Spring Boot 启动模块
```

基础包名建议：

```text
com.dayz.aeroverse
```

业务包结构示例：

```text
com.dayz.aeroverse.campus
├── controller
├── service
│   └── impl
├── mapper
├── model
│   ├── entity
│   ├── dto
│   └── vo
├── cache
├── config
└── enums
```

## 5. 核心业务模块

### 5.1 校园建筑模块

模块名：

```text
aeroverse-campus
```

核心职责：

- 建筑信息管理
- 建筑类型分类
- 建筑坐标维护
- 建筑模型节点绑定
- 兴趣点 POI 管理
- 建筑详情查询

核心接口：

```text
GET    /api/campus/buildings
GET    /api/campus/buildings/{id}
GET    /api/campus/buildings/by-node/{modelNodeId}
POST   /api/campus/buildings
PUT    /api/campus/buildings/{id}
DELETE /api/campus/buildings/{id}
```

### 5.2 导航路径模块

模块名：

```text
aeroverse-navigation
```

核心职责：

- 校园路网节点管理
- 道路边管理
- 最短路径计算
- 路径距离计算
- 预计步行时间计算
- 返回 Three.js 可绘制的路径点

核心接口：

```text
GET  /api/navigation/nodes
GET  /api/navigation/edges
POST /api/navigation/routes
```

请求示例：

```json
{
  "startNodeId": 1,
  "endNodeId": 12,
  "strategy": "SHORTEST"
}
```

返回示例：

```json
{
  "distance": 680.5,
  "estimatedTime": 520,
  "points": [
    { "x": 12.4, "y": 0.2, "z": -30.1 },
    { "x": 18.9, "y": 0.2, "z": -42.8 }
  ]
}
```

### 5.3 模型资产模块

模块名：

```text
aeroverse-asset
```

核心职责：

- GLB 模型管理
- 贴图资源管理
- 模型版本管理
- 建筑与模型资源绑定
- 模型压缩信息维护

核心接口：

```text
POST /api/assets/models/upload
GET  /api/assets/models/{id}
GET  /api/assets/models/latest
```

### 5.4 AI 问路模块

模块名：

```text
aeroverse-ai
```

第一版可以不做，后期扩展。

核心职责：

- 自然语言识别目的地
- 校园知识库问答
- 自动调用导航接口
- 自动定位目标建筑

示例：

```text
用户：我要去教务处
系统：识别目的地为行政楼，调用导航接口，生成路线。
```

## 6. 数据库设计

推荐使用：

```text
PostgreSQL + PostGIS
```

### 6.1 建筑表

```text
campus_building
- id
- name
- code
- type
- description
- longitude
- latitude
- height
- model_node_id
- model_asset_id
- cover_url
- status
- created_at
- updated_at
```

### 6.2 兴趣点表

```text
campus_poi
- id
- name
- type
- building_id
- floor
- room_no
- description
- x
- y
- z
```

### 6.3 导航节点表

```text
nav_node
- id
- name
- type
- longitude
- latitude
- x
- y
- z
```

### 6.4 导航边表

```text
nav_edge
- id
- from_node_id
- to_node_id
- distance
- walk_time
- bidirectional
- accessible
```

### 6.5 模型资源表

```text
asset_model
- id
- name
- file_url
- file_size
- format
- version
- draco_compressed
- description
- created_at
```

## 7. Redis 缓存设计

缓存 Key 规划：

```text
aeroverse:building:{id}
aeroverse:building:node:{modelNodeId}
aeroverse:campus:scene:metadata
aeroverse:nav:path:{startNodeId}:{endNodeId}
aeroverse:asset:model:latest
```

适合缓存的数据：

- 建筑详情
- 建筑模型节点映射
- 校园初始化元数据
- 热门路径结果
- 最新模型资源信息

## 8. 前端架构

```text
aeroverse-frontend
├── src
│   ├── api
│   ├── assets
│   ├── components
│   ├── engine
│   │   ├── scene.ts
│   │   ├── camera.ts
│   │   ├── renderer.ts
│   │   ├── controls.ts
│   │   ├── loaders
│   │   │   └── gltfLoader.ts
│   │   ├── interaction
│   │   │   ├── raycaster.ts
│   │   │   └── outline.ts
│   │   └── route
│   │       └── routeRenderer.ts
│   ├── stores
│   ├── views
│   │   ├── DigitalTwinView.vue
│   │   ├── AdminView.vue
│   │   └── LoginView.vue
│   └── main.ts
```

前端核心功能：

- 加载校园 GLB 模型
- 控制 3D 相机浏览校园
- 点击建筑并高亮
- 展示建筑信息面板
- 选择起点和终点
- 请求后端路径规划
- 在 3D 场景绘制路线
- 镜头平滑飞行到目标建筑

## 9. Three.js 场景设计

核心对象：

```text
Scene
PerspectiveCamera
WebGLRenderer
OrbitControls
GLTFLoader
Raycaster
OutlinePass
CSS2DRenderer
Line2 / TubeGeometry
```

场景能力：

- 校园整体模型加载
- 建筑节点识别
- 鼠标点击交互
- 建筑描边高亮
- 建筑标签显示
- 路径线绘制
- 相机飞行动画
- 后期扩展昼夜切换

## 10. 模型资产规范

```text
模型格式：GLB
贴图格式：WebP / JPG
单个主模型大小：建议控制在 30MB 以内
建筑节点命名：需要与后端 model_node_id 对应
压缩方式：Draco / Meshopt
LOD 策略：核心建筑精细，远处建筑低模
```

命名示例：

```text
building_library
building_teaching_01
building_dormitory_01
building_canteen_01
gate_south
road_main_01
```

## 11. 开发阶段规划

### 第一阶段：3D 场景原型

目标：

- 搭建 Vue 3 + Three.js 项目
- 导入校园白模
- 实现基础相机控制
- 实现建筑点击识别

### 第二阶段：建筑数据接入

目标：

- 搭建 Spring Boot 后端
- 设计建筑数据表
- 完成建筑 CRUD
- 前端点击建筑后请求后端详情

### 第三阶段：校园导航能力

目标：

- 设计 nav_node 和 nav_edge
- 录入校园道路节点
- 实现 Dijkstra / A* 算法
- 返回路径点数组
- 前端绘制 3D 路线

### 第四阶段：后台管理

目标：

- 建筑管理
- 道路节点管理
- 模型资源管理
- 兴趣点管理

### 第五阶段：性能优化

目标：

- GLB 模型压缩
- 贴图压缩
- 模型分块加载
- Redis 缓存
- 接口响应优化

### 第六阶段：AI 问路扩展

目标：

- 整理校园知识库
- 接入 Spring AI
- 识别用户自然语言意图
- 自动匹配目的地
- 调用导航接口生成路线

## 12. 最终推荐方案

第一版核心架构：

```text
Vue 3 + TypeScript + Three.js
+
Spring Boot 3.5.x + JDK 21
+
PostgreSQL/PostGIS + Redis
+
GLB 校园模型资产
+
模块化单体架构
```

第一版优先完成核心闭环：

```text
校园模型加载
建筑点击查询
建筑信息展示
起终点选择
路径规划
3D 路线绘制
```

后期再扩展：

```text
Spring Cloud 微服务
AI 问路
WebSocket 实时状态
室内导航
能耗数据孪生
安防数据孪生
```
