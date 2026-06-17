# Nacos 配置说明

作者：DaYZ
日期：2026-05-08

## 基本约定

当前项目同时使用 Nacos Config 和 Nacos Discovery。

- 格式：`YAML`
- 公共 Group：`EDUPIVOT_NAVIGATOR`
- 本地环境 Group：`EDUPIVOT_LOCAL`
- Docker 环境 Group：`EDUPIVOT_DOCKER`
- 本地启动默认 profile：`local`
- Docker Compose 启动默认 profile：`docker`

## 应用导入配置

各服务的 `application.yaml` 只保留 Nacos 引导配置。
业务配置统一放到下列 DataId 中。

按环境区分的基础设施配置：

```text
local/sc-edupivot-infra-local.yaml
Group: EDUPIVOT_LOCAL
refreshEnabled: false

docker/sc-edupivot-infra-docker.yaml
Group: EDUPIVOT_DOCKER
refreshEnabled: false
```

包含：

- PostgreSQL 连接
- Redis 连接

按环境区分的存储基础设施配置：

```text
local/sc-edupivot-storage-infra-local.yaml
Group: EDUPIVOT_LOCAL
refreshEnabled: false

docker/sc-edupivot-storage-infra-docker.yaml
Group: EDUPIVOT_DOCKER
refreshEnabled: false
```

包含：

- MinIO 连接信息

### sc-ai

公共配置：

```text
sc-edupivot-ai-common.yaml
Group: EDUPIVOT_NAVIGATOR
refreshEnabled: true
```

包含：

- 服务端口、虚拟线程、Actuator、Scalar
- Spring AI：DashScope OpenAI 兼容端点、chat 模型（qwen-plus）、embedding 模型（text-embedding-v3）
- Redis 向量库（RediSearch 索引）配置
- RAG 参数（top-k、相似度阈值）、知识库切分参数

按环境区分的基础设施配置：

```text
local/sc-edupivot-ai-infra-local.yaml
Group: EDUPIVOT_LOCAL
refreshEnabled: false

docker/sc-edupivot-ai-infra-docker.yaml
Group: EDUPIVOT_DOCKER
refreshEnabled: false
```

包含：

- PostgreSQL 连接
- Redis 连接（向量库与缓存共用）

> 注意：sc-ai 需要 `DASHSCOPE_API_KEY` 环境变量（通义百炼 API Key），通过 `.env` 注入。

### sc-gateway

公共配置：

```text
sc-edupivot-gateway-common.yaml
Group: EDUPIVOT_NAVIGATOR
refreshEnabled: true
```

包含：

- 网关服务端口
- 虚拟线程
- Actuator
- Scalar 聚合文档入口

按环境区分的路由配置：

```text
local/sc-edupivot-gateway-routes-local.yaml
Group: EDUPIVOT_LOCAL
refreshEnabled: true

docker/sc-edupivot-gateway-routes-docker.yaml
Group: EDUPIVOT_DOCKER
refreshEnabled: true
```

当前启用路由：

```text
# 业务路由
auth-route: /api/auth/** -> sc-auth
notification-route: /api/notifications/** -> sc-notification
course-route: /api/courses/**, /api/chapters/**, /api/enrollments/**, /api/forums/**, /api/question-banks/**, /api/invitations/** -> sc-course
storage-route: /api/storage/** -> sc-storage
ai-route: /api/ai/** -> sc-ai

# OpenAPI 文档路由
auth-openapi-route: /openapi/auth -> sc-auth /v3/api-docs
notification-openapi-route: /openapi/notification -> sc-notification /v3/api-docs
course-openapi-route: /openapi/course -> sc-course /v3/api-docs
storage-openapi-route: /openapi/storage -> sc-storage /v3/api-docs
ai-openapi-route: /openapi/ai -> sc-ai /v3/api-docs

# 内部端点保护（返回 403）
storage-internal-block: /api/storage/internal/** -> 403
auth-internal-block: /api/auth/**/internal/** -> 403
```

## 启动方式

本地启动：

```bash
SPRING_PROFILES_ACTIVE=local
```

Docker Compose 启动：

```bash
docker compose up -d --build
```

Docker Compose 中已设置：

```text
SPRING_PROFILES_ACTIVE=docker
NACOS_SERVER_ADDR=nacos:8848
```

## 清理说明

已移除当前代码没有读取的配置：

- `sc-edupivot-feature-local.yaml`
- `sc-edupivot-feature-docker.yaml`
- `edupivot.feature.*`
- `edupivot.storage.minio.*`

后续接入校园、导航、资产或 AI 模块时，再按实际 `@ConfigurationProperties`
或 `@Value` 绑定项新增对应配置。

## 注意事项

- 生产环境不建议在 Nacos 中明文保存真实密钥。
- 真实密钥优先通过环境变量、密钥管理平台或部署平台注入。
- 数据库、Redis 和认证密钥配置建议关闭热刷新。
