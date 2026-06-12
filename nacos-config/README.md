# Nacos 配置说明

作者：DaYZ
日期：2026-05-08

## 基本约定

当前项目只使用 Nacos Config，不使用 Nacos Discovery。

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

按环境区分的认证配置：

```text
local/sc-edupivot-auth-local.yaml
Group: EDUPIVOT_LOCAL
refreshEnabled: false

docker/sc-edupivot-auth-docker.yaml
Group: EDUPIVOT_DOCKER
refreshEnabled: false
```

包含：

- JWT secret
- Google OAuth2 clientId/clientSecret/redirectUri
- GitHub OAuth clientId/clientSecret/redirectUri

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
auth-route: /api/auth/** -> sc-auth
auth-openapi-route: /openapi/auth -> sc-auth /v3/api-docs
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
