# Nacos 配置说明

作者：DaYZ
日期：2026-05-08

## 基本约定

当前项目只使用 Nacos Config，不使用 Nacos Discovery。

- 格式：`YAML`
- 公共 Group：`AEROVERSE_NAVIGATOR`
- 本地环境 Group：`AEROVERSE_LOCAL`
- Docker 环境 Group：`AEROVERSE_DOCKER`
- 本地启动默认 profile：`local`
- Docker Compose 启动默认 profile：`docker`

## 应用导入配置

`aeroverse-web/src/main/resources/application.yaml` 和
`aeroverse-gateway/src/main/resources/application.yaml` 只保留 Nacos 引导配置。
业务配置统一放到下列 DataId 中。

### aeroverse-web

公共配置：

```text
aeroverse-navigator-common.yaml
Group: AEROVERSE_NAVIGATOR
refreshEnabled: true
```

包含：

- Web 服务端口
- 虚拟线程
- Actuator
- SpringDoc/Scalar
- JWT issuer/ttl

按环境区分的基础设施配置：

```text
local/aeroverse-navigator-infra-local.yaml
Group: AEROVERSE_LOCAL
refreshEnabled: false

docker/aeroverse-navigator-infra-docker.yaml
Group: AEROVERSE_DOCKER
refreshEnabled: false
```

包含：

- PostgreSQL 连接
- Redis 连接

按环境区分的认证配置：

```text
local/aeroverse-navigator-auth-local.yaml
Group: AEROVERSE_LOCAL
refreshEnabled: false

docker/aeroverse-navigator-auth-docker.yaml
Group: AEROVERSE_DOCKER
refreshEnabled: false
```

包含：

- JWT secret
- Google OAuth2 clientId/clientSecret/redirectUri
- GitHub OAuth clientId/clientSecret/redirectUri

### aeroverse-gateway

公共配置：

```text
aeroverse-gateway-common.yaml
Group: AEROVERSE_NAVIGATOR
refreshEnabled: true
```

包含：

- 网关服务端口
- 虚拟线程
- Actuator
- Scalar 聚合文档入口

按环境区分的路由配置：

```text
local/aeroverse-gateway-routes-local.yaml
Group: AEROVERSE_LOCAL
refreshEnabled: true

docker/aeroverse-gateway-routes-docker.yaml
Group: AEROVERSE_DOCKER
refreshEnabled: true
```

当前启用路由：

```text
auth-route: /api/auth/** -> aeroverse-web
auth-openapi-route: /openapi/auth -> aeroverse-web /v3/api-docs
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

- `aeroverse-navigator-feature-local.yaml`
- `aeroverse-navigator-feature-docker.yaml`
- `aeroverse.feature.*`
- `aeroverse.storage.minio.*`

后续接入校园、导航、资产或 AI 模块时，再按实际 `@ConfigurationProperties`
或 `@Value` 绑定项新增对应配置。

## 注意事项

- 生产环境不建议在 Nacos 中明文保存真实密钥。
- 真实密钥优先通过环境变量、密钥管理平台或部署平台注入。
- 数据库、Redis 和认证密钥配置建议关闭热刷新。
