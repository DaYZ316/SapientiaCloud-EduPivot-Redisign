# SapientiaCloud EduPivot

当前正式版本：`2.0.0`

SapientiaCloud EduPivot 是一个面向教学场景的全栈平台，包含课程与课堂管理、用户认证、通知、对象存储、AI 助手、实时课堂能力，以及统一网关和前端应用。仓库采用后端 Maven 多模块、前端 Vue 3 单应用、Docker Compose 编排的结构。

## 技术栈

- 后端：Java 21、Spring Boot 4、Spring Cloud 2025、Spring Cloud Alibaba、MyBatis-Plus、Flyway、Spring AI
- 前端：Vue 3、Vite、TypeScript、Pinia、Vue Router、Vue I18n、Three.js、LiveKit Client
- 基础设施：PostgreSQL/PostGIS、Redis Stack、Kafka、MinIO、Nacos、LiveKit
- 可观测性：Spring Boot Actuator、Prometheus、Grafana、Loki、Alloy、SkyWalking
- 部署：Docker Compose、Nginx、Windows/POSIX artifact deployment scripts

## 目录结构

```text
.
├── sc-auth/            # 认证、用户、OAuth、JWT
├── sc-notification/    # 通知与消息推送
├── sc-course/          # 课程、章节、课堂、题库、论坛等教学业务
├── sc-storage/         # MinIO 对象存储与文件处理
├── sc-ai/              # AI 助手、RAG、生成、导出、实时总结
├── sc-gateway/         # API 网关、路由、聚合 OpenAPI
├── sc-common/          # 后端公共模块
├── sc-frontend/        # Vue 前端应用
├── nacos-config/       # Nacos 配置模板
├── postgres-init/      # PostgreSQL 初始化脚本
├── deploy/             # Nginx、观测、artifact 运行时配置
└── scripts/            # 辅助脚本
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 22+ 和 pnpm 10+
- Docker 和 Docker Compose

首次运行前复制环境变量模板：

```bash
cp .env.example .env
cp sc-frontend/.env.example sc-frontend/.env
```

`.env` 中至少需要设置数据库、Redis、Nacos、MinIO、JWT、LiveKit 等密钥。AI 能力需要 `DASHSCOPE_API_KEY`；Web 搜索能力需要按需设置 `AGENT_SEARCH_WEB_SEARCH_API_KEY`。

## 本地开发

启动基础设施和后端服务：

```bash
docker compose up -d --build
```

前端本地开发：

```bash
cd sc-frontend
pnpm install
pnpm dev
```

Vite 默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:39080`。相关代理目标可在 `sc-frontend/.env` 中调整。

常用本地端口：

| 服务 | 默认端口 |
| --- | --- |
| 前端容器 | 18080 |
| 网关 | 39080 |
| Auth | 28081 |
| Notification | 28082 |
| Course | 28084 |
| Storage | 28085 |
| AI | 28086 |
| Nacos | 8848 / 8080 |
| MinIO | 9000 / 9001 |
| SkyWalking UI | 18090 |
| Grafana | 13000 |

## 构建与检查

后端全量构建：

```bash
mvn clean package
```

构建单个后端服务及其依赖：

```bash
mvn -pl sc-ai -am package
```

前端类型检查和构建：

```bash
cd sc-frontend
pnpm build
```

前端代码检查：

```bash
cd sc-frontend
pnpm lint
pnpm stylelint
pnpm format:check
```

## 配置说明

后端服务的 `application.yaml` 只保留 Nacos 引导配置。业务配置由 `nacos-config/` 下的 YAML 文件导入，Docker Compose 启动时会通过 `scripts/nacos_config_init.py` 写入 Nacos。

常见分组：

- `EDUPIVOT_NAVIGATOR`：公共配置
- `EDUPIVOT_LOCAL`：本地环境配置
- `EDUPIVOT_DOCKER`：Docker 环境配置

## 部署

源码构建部署使用根目录的 `docker-compose.yaml`：

```bash
docker compose up -d --build
```

产物部署使用 `docker-compose.artifact.yaml` 和 `deploy_artifact.bat`，会上传已构建的 JAR、前端 `dist`、Nacos 配置和部署配置：

```bat
deploy_artifact.bat infra
deploy_artifact.bat all-backend
deploy_artifact.bat frontend
deploy_artifact.bat all
```

私有服务器信息放在被 Git 忽略的 `deploy_artifact.local.bat` 中。

## 常用入口

- 前端应用：`http://localhost:5173` 或容器模式 `http://localhost:18080`
- API 网关：`http://localhost:39080`
- 聚合 API 文档：通过网关 Scalar/OpenAPI 配置访问
- Nacos 控制台：`http://localhost:8080`
- MinIO 控制台：`http://localhost:9001`
- Grafana：`http://localhost:13000`
- SkyWalking：`http://localhost:18090`
