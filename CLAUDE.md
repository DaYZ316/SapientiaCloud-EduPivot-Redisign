# CLAUDE.md

## Part I: 编码行为准则

旨在减少常见 LLM 编程错误的行为准则。

**权衡：** 这些准则偏向于谨慎而非追求速度。对于琐碎简单的任务，请自行判断。

### 1. 编码前先思考

**不要主观假设。不要掩盖疑惑。将权衡取舍摆在明面上。**

在实现代码之前：

- 明确陈述你的假设。如果不确定，请提问。
- 如果存在多种解释，请将它们全部列出——不要默默地自行选择。
- 如果存在更简单的方案，请提出来。在必要时提出异议。
- 如果有任何不清楚的地方，停下来。指出让你困惑的地方，并提问。

### 2. 简单优先

**用最少的代码解决问题。不要有任何投机性/预测性的代码。**

- 不提供要求之外的任何功能。
- 不为一次性代码做抽象。
- 不提供未要求的"灵活性"或"可配置性"。
- 不为不可能发生的场景编写错误处理。
- 如果你写了 200 行代码，但其实 50 行就能搞定，请重写它。

问问你自己："高级工程师会认为这过于复杂吗？"如果是，请简化。

### 3. 外科手术式的精准修改

**只修改必须修改的地方。只清理你自己制造的烂摊子。**

修改现有代码时：

- 不要"优化"相邻的代码、注释或格式。
- 不要重构没有出问题的代码。
- 保持与现有风格一致，即使换作你会有不同的写法。
- 如果你注意到无关的死代码，提出来——但不要删除它。

当你的修改产生了孤立（不再被使用）的代码时：

- 移除**因为你的修改**而变得未使用的导入（imports）、变量或函数。
- 除非被要求，否则不要移除预先存在的死代码。

检验标准：每一行更改都应该能直接追溯到用户的请求。

### 4. 目标驱动执行

**明确成功标准。循环直至验证通过。**

将任务转化为可验证的目标：

- "添加验证" → "为无效输入编写测试，然后使测试通过"
- "修复 Bug" → "编写一个能复现该 Bug 的测试，然后使测试通过"
- "重构 X" → "确保重构前后测试都能通过"

对于多步骤任务，请陈述一个简要计划：

```
1. [步骤] → 验证: [检查项]
2. [步骤] → 验证: [检查项]
3. [步骤] → 验证: [检查项]
```

强有力的成功标准能让你独立进行循环验证。软弱的标准（比如"让它能跑起来"）则需要不断地澄清。

---

## Part II: 项目规范

### 5. 项目概览

**产品名称：** 智语·云枢（SapientiaCloud EduPivot）
**定位：** 云原生智慧教学平台，提供课程管理、通知推送、文件存储等核心能力。
**根包名：** `com.dayz`
**Maven 坐标：** `com.dayz:sc-edupivot:2.0.0-SNAPSHOT`

### 6. 技术栈

| 层级     | 技术选型                                                                        |
|--------|-----------------------------------------------------------------------------|
| 语言     | Java 21（虚拟线程已启用）                                                            |
| 框架     | Spring Boot 4.0.6 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 |
| ORM    | MyBatis-Plus 3.5.16                                                         |
| 数据库    | PostgreSQL 17 + PostGIS                                                     |
| 缓存     | Redis 8                                                                     |
| 消息队列   | Apache Kafka 3.9（KRaft 模式）                                                  |
| 对象存储   | MinIO                                                                       |
| 配置中心   | Nacos 3.1.0                                                                 |
| 数据库迁移  | Flyway                                                                      |
| API 文档 | SpringDoc OpenAPI 3.0.3 + Scalar 0.5.55                                     |
| 熔断器    | Resilience4j 2.3.0                                                          |
| 分布式追踪  | Micrometer Brave + Zipkin                                                   |
| 密码学    | BouncyCastle 1.84                                                           |
| 前端     | Vue 3.5 + TypeScript 6 + Vite 8 + Pinia 3 + Vue Router 5 + Vue I18n 11      |
| UI 组件  | Naive UI + Lucide Vue Next                                                  |
| 包管理    | pnpm 10                                                                     |
| 容器化    | Docker Compose + Nginx                                                      |

### 7. 微服务架构

```
sc-frontend (Vue 3 SPA, Nginx)
    │
sc-gateway (:39080) ── JWT 校验 + 路由 + 限流
    │
    ├── sc-auth (:28081)          认证、OAuth2、用户管理
    ├── sc-notification (:28082)  通知、SSE 推送
    ├── sc-course (:28084)        课程、章节、题库
    └── sc-storage (:28085)       文件上传/下载（MinIO）
```

所有服务共享同一个 PostgreSQL 实例（各服务拥有独立的 Flyway 历史表）和 Redis。

**端口分配：**

| 服务              | 端口               |
|-----------------|------------------|
| sc-gateway      | 39080            |
| sc-auth         | 28081            |
| sc-notification | 28082            |
| sc-course       | 28084            |
| sc-storage      | 28085            |
| sc-frontend     | 5173（开发）/ 80（容器） |

### 8. 模块结构（sc-common）

```
sc-common/
  sc-common-core       ApiResponse, PageResponse, ErrorCode, BusinessException, UuidV7Generator
  sc-common-web        GlobalExceptionHandler, WebAutoConfiguration
  sc-common-security   JWT RS256 签名/验证, SecurityFilterChain, @RateLimited, Token 黑名单, 刷新令牌
  sc-common-redis      RedisAutoConfiguration (CacheManager, RedisTemplate), KafkaIdempotencyGuard
  sc-common-events     Kafka 事件定义, KafkaAutoConfiguration, KafkaTopicConstants
  sc-common-feign      OpenFeign 配置, Resilience4j 熔断, Bearer Token 中继
```

### 9. 包命名规范

**业务服务包结构：**

```
com.dayz.sc.{service}
    .controller        REST 控制器
    .service           业务逻辑
    .repository        Repository 接口
    .repository.impl   MyBatis-Plus 实现（前缀 Mybatis）
    .mapper            MyBatis-Plus Mapper 接口
    .model
        .entity        数据库实体
        .dto           请求 DTO（输入）
        .vo            视图对象（输出）
        .enums         枚举
    .config            服务配置
    .event             Kafka 事件发布者
    .client            外部 API 客户端（如 OAuth）
    .kafka             Kafka 消费者
    .sse               SSE（仅 sc-notification）
    .persistence.type  MyBatis 类型处理器
```

**公共模块包结构：**

```
com.dayz.sc.common
    .response            ApiResponse, PageResponse
    .error               ErrorCode, ErrorCodes, BusinessException
    .util                UuidV7Generator, PageUtils
    .web.handler         GlobalExceptionHandler
    .security.*          安全相关（config, service, support, token, ratelimit, crypto, endpoint）
    .redis.config        Redis 自动配置
    .redis.kafka         Kafka 幂等守卫
    .events              事件定义
    .events.config       Kafka 自动配置
    .feign.*             Feign 客户端与配置
```

### 10. 后端编码规范

#### 10.1 Controller

- 注解：`@RestController` + `@RequestMapping("/api/{resource}")`
- 构造注入：`@RequiredArgsConstructor`
- 返回值：统一 `ApiResponse<T>`，分页 `ApiResponse<PageResponse<T>>`
- 参数校验：`@Valid @RequestBody`
- 获取当前用户：`@AuthenticationPrincipal Jwt jwt`，通过 `JwtPrincipalResolver.requireUserId(jwt)` 提取 userId
- 角色授权：在 Controller 内联判断（0=管理员, 1=学生, 2=教师）
- 敏感接口限流：`@RateLimited(maxRequests = N, windowSeconds = M)`
- 不使用 `@CrossOrigin`（由 Gateway 统一处理）

```java
@PostMapping
@RateLimited(maxRequests = 10, windowSeconds = 60)
public ApiResponse<UUID> createCourse(
        @Valid @RequestBody CreateCourseRequest request,
        @AuthenticationPrincipal Jwt jwt) {
```

#### 10.2 Service

- 注解：`@Service` + `@RequiredArgsConstructor`
- 事务：`@Transactional(rollbackFor = Exception.class)`
- 缓存：`@Cacheable`、`@CacheEvict`
- 异常：抛 `new BusinessException(ErrorCodes.XXX)`
- 数据访问：通过 Repository（不直接使用 Mapper）
- UUID 生成：统一使用 `UuidV7Generator.generate()`，禁止 `UUID.randomUUID()`

#### 10.3 Repository

- 接口：`.repository` 包（如 `CourseRepository`）
- 实现：`.repository.impl` 包，前缀 `Mybatis`（如 `MybatisCourseRepository`）
- 注解：`@Repository` + `@RequiredArgsConstructor`
- 查询：MyBatis-Plus `LambdaQueryWrapper`
- 分页：`wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size)`
- 单结果：返回 `Optional<T>`

#### 10.4 Mapper

- 注解：`@Mapper`
- 继承：`BaseMapper<Entity>`
- 空接口（CRUD 通过 BaseMapper）

#### 10.5 Entity

- 表映射：`@TableName("table_name")`
- 主键：`@TableId(type = IdType.INPUT)`，在 Service 层使用 `UuidV7Generator.generate()` 生成 ID（MyBatis-Plus 3.5.16 的
  `ASSIGN_UUID` 不支持 UUID 类型）
- 字段映射：`@TableField("column_name")`
- 自动填充：`@TableField(fill = FieldFill.INSERT)` / `FieldFill.INSERT_UPDATE`
- 软删除：`@TableLogic`
- Lombok：`@Getter @Setter @NoArgsConstructor @AllArgsConstructor` 或 `@Data`
- 时间类型：`Instant`（sc-auth）或 `LocalDateTime`（sc-course）

#### 10.6 DTO

- 定义为 Java record：`public record CreateCourseRequest(...)`
- 校验注解：`@NotBlank`、`@Email`、`@Size(max = N)`、`@Min(N)`
- 命名：`{Action}{Entity}Request`（如 `CreateCourseRequest`、`PasswordLoginRequest`）
- 分页：`{Entity}PageRequest`（含 `page`、`size` 及筛选字段）

#### 10.7 VO

- 定义为 Java record：`public record CourseVO(...)`
- 可选字段：`@Nullable`（来自 `org.jspecify.annotations`）
- 命名：`{Entity}VO`（如 `CourseVO`、`NotificationVO`）
- 包级空安全：`package-info.java` 中声明 `@NullMarked`

#### 10.8 枚举

- 无字段的简单枚举：`UserStatus`、`OauthProvider`
- 带整数码的枚举：提供 `fromCode(int)` 静态方法（如 `CourseStatus`、`NotificationType`）

### 11. 统一响应格式

```java
// 成功响应
public record ApiResponse<T>(int code, String message, @Nullable T data, Instant timestamp)
// code=0, message="success"

// 分页响应
public record PageResponse<T>(List<T> records, long total, long page, long size)
```

**错误码规范：**
| 范围 | 含义 |
|------|------|
| 0 | 成功 |
| 400xx | 请求错误 / 参数校验 |
| 401xx | 未授权 / 认证失败 |
| 403xx | 禁止访问 |
| 404xx | 资源不存在 |
| 500xx | 系统错误 |

### 12. 安全架构

- **JWT：** RS256 非对称加密，Auth 服务用私钥签名，其他服务通过 JWKS 端点验证
- **Access Token：** 30 分钟 TTL，包含 userId（subject）+ role（claim）+ jti
- **Refresh Token：** 7 天 TTL，存储在 Redis（`auth:refresh:{token}`），支持轮转
- **Token 黑名单：** 登出时 JTI 加入 Redis（`auth:blacklist:{jti}`），TTL 为 Token 剩余有效期
- **Gateway 注入：** `UserRoleHeaderFilter` 提取 userId/role 注入 `X-User-Id`、`X-User-Role` 请求头
- **限流：** `@RateLimited` 注解，Redis 滑动窗口（Lua 脚本）
- **密码编码：** BCrypt
- **公开端点：** 通过 `edupivot.security.public-endpoints` Nacos 配置

### 13. 缓存策略（Redis）

- **模式：** Cache-Aside（读：查缓存 → 未命中 → 查 DB → 写缓存；写：更新 DB → 删缓存）
- **Key 命名：** `{service}:{module}:{dimension}:{id}`（如 `auth:user:{userId}`）
- **空值缓存：** 短 TTL（2 分钟）防穿透
- **TTL 抖动：** 基础 TTL + 随机抖动（0-5 分钟）防雪崩
- **写策略：** 写时删缓存（不更新），下次读重建
- **序列化：** `GenericJacksonJsonRedisSerializer`（支持多态）
- **Spring Cache：** `RedisCacheManager`，命名缓存（`courseIdsByTeacher` 30min，`courseDetail` 10min）

### 14. 事件驱动（Kafka）

- **Topic：** `sc.course.events`、`sc.user.events`
- **死信 Topic：** 后缀 `.DLT`（自动创建）
- **错误处理：** `DefaultErrorHandler`，3 次重试（间隔 1 秒），失败后进 DLT
- **事件结构：** 每个事件包含 `eventId`（UUID v7），由 `UuidV7Generator.generate()` 生成
- **幂等消费：** `KafkaIdempotencyGuard`，Redis SETNX + 24h TTL，key 格式 `kafka:idempotent:{groupId}:{eventId}`
- **手动提交：** `AckMode.MANUAL_IMMEDIATE`，`ack.acknowledge()` 在 `finally` 块中调用
- **发布者：** `UserEventPublisher`（sc-auth）、`CourseEventPublisher`（sc-course）
- **消费者：** `UserEventConsumer`、`CourseEventConsumer`（sc-notification、sc-storage）

### 15. 服务间通信

- **同步：** OpenFeign（如 `StorageInternalClient`）
- **熔断：** Resilience4j（50% 失败阈值，30 秒开路）
- **Token 中继：** `FeignAutoConfiguration` 添加 `RequestInterceptor` 转发 Authorization 头
- **降级：** Feign 客户端提供 Fallback 类
- **内部端点：** `/api/{service}/internal/*`（服务间调用，无需鉴权）

### 16. 数据库规范

**表命名：**
| 前缀 | 服务 | 示例 |
|------|------|------|
| `auth_` | sc-auth | `auth_users`, `auth_user_identities` |
| `edu_` | sc-course | `edu_course`, `edu_enrollment` |
| `ntf_` | sc-notification | `ntf_notification`, `ntf_read_status` |
| `storage_` | sc-storage | `storage_object`, `storage_upload_session` |

**列规范：**

- 主键：`id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- 时间戳：`created_at`、`updated_at`、`deleted_at`（TIMESTAMPTZ）
- 软删除：`deleted SMALLINT NOT NULL DEFAULT 0`（0=正常, 1=已删除）
- 外键命名：`fk_{table}_{referenced_table}`
- 唯一约束：`uk_{table}_{columns}`
- 索引命名：`idx_{table}_{columns}`

**Flyway 规范：**

- 版本格式：`V{YYYYMMDD}{seq}`（如 `V2026061001`）
- 描述分隔：双下划线 `__` + snake_case 描述
- 各服务独立历史表（如 `flyway_schema_history_auth`）
- 迁移文件位置：`src/main/resources/db/migration/`
- 使用 `CREATE TABLE IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`
- 包含 `COMMENT ON TABLE/COLUMN` 注释

### 17. 前端规范

**目录结构：**

```
src/
  app/              入口（main.ts, App.vue, router, i18n）
  layouts/          布局组件
  features/         功能模块
    {feature}/
      api/          API 调用函数
      types/        TypeScript 接口
      views/        页面组件
      components/   功能组件
      stores/       Pinia Store
      i18n/         国际化（en-US.ts, zh-CN.ts）
  shared/
    api/            Axios 封装（request.ts）
    types/          共享类型（ApiResponse, PageResponse）
    components/     共享组件
    composables/    组合式函数
    styles/         全局样式
    i18n/           共享翻译
  assets/           静态资源
```

**API 调用：**

- 集中 Axios 实例（`shared/api/request.ts`），baseURL 来自环境变量
- 自动附加 Bearer Token，401 时自动刷新
- `request<T>(config)` 返回解包后的 `T`（从 `ApiResponse<T>` 中提取 `data`）

**状态管理：**

- Pinia Composition API 风格：`defineStore('name', () => { ... })`
- Token 持久化：localStorage

**路由：**

- HTML5 History 模式
- 路由守卫：`meta.requiresAuth`、`meta.guestOnly`
- 懒加载：`() => import('@/features/...')`
- 全屏 WebGL / 3D 教室页面必须定义为顶层受保护路由，使用 `meta.requiresAuth`，不要挂在 `MainLayout` 的 `children` 下。
- 全屏页面根容器和 canvas 使用 `position: fixed`、`inset: 0`、`width: 100vw`、`height: 100dvh`，避免被 layout、padding、`.content-container` 限制。
- 普通业务页面继续走 `MainLayout`；不要为了单个沉浸式页面污染通用布局结构。

**国际化：** Vue I18n，`en-US` + `zh-CN`，按功能模块拆分

**路径别名：** `@/*` → `src/*`

### 18. Docker 部署规范

**Java 服务 Dockerfile：**

- 多阶段构建：`mcr.microsoft.com/openjdk/jdk:21-ubuntu`
- Maven 构建使用 `--mount=type=cache,target=/root/.m2`
- 健康检查：`/actuator/health/liveness`
- 时区：`Asia/Shanghai`
- 密钥通过环境变量注入（如 `JWT_PRIVATE_KEY_B64` Base64 解码）

**前端 Dockerfile：**

- 多阶段：`node:22-alpine` 构建 → `nginx:1.27-alpine` 运行
- Vite 构建参数通过 Docker build args 注入

**docker-compose 规范：**

- 项目名：`sc-edupivot`
- 网络：`SC-Network`（bridge）
- 所有服务：`restart: unless-stopped`
- 基础设施服务必须配置 `healthcheck`
- 服务依赖：`condition: service_healthy`
- 环境变量：通过 `.env` 文件注入
- 持久化：命名卷

### 19. Nacos 配置规范

**配置层级：**

1. `sc-edupivot-{service}-common.yaml`（Group: `EDUPIVOT_NAVIGATOR`）— 业务配置，热刷新
2. `sc-edupivot-{service}-infra-{profile}.yaml`（Group: `EDUPIVOT_LOCAL` / `EDUPIVOT_DOCKER`）— 基础设施配置
3. `sc-edupivot-gateway-routes-{profile}.yaml` — 网关路由规则
4. 各服务 `application.yaml` — 仅保留 Nacos 引导配置

**环境 Profile：**

- `local` — 本地开发（localhost 连接）
- `docker` — Docker Compose（容器名连接）

**配置属性前缀：** `edupivot.*`（如 `edupivot.security.jwt.*`）

### 20. 空安全

- 包级 `@NullMarked`（通过 `package-info.java` + `org.jspecify.annotations`）
- Record 可选字段：`@Nullable`
- 泛型参数：`@NonNull`（如 `ApiResponse<@NonNull LoginResponse>`）
- Qodana / 静态检查必须保持为 0 个新增问题；修复时优先用最小类型声明或局部代码调整，不做无关重构。
- `@RateLimited` 使用默认值时写成 `@RateLimited`，不要写 `@RateLimited(maxRequests = 10)`。
- `@NullMarked` 包内泛型必须补齐类型用途注解，例如 `ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>>`。
- `ApiResponse`、`PageResponse`、Feign DTO、集合元素类型都要按实际语义标注 `@NonNull` / `@Nullable`。
- 已声明为非空的返回值不要再做无意义 null 判断；已有 null-safe 方法也不要在调用前重复写防御判断。
- `DefaultRedisScript` 等 Spring 泛型类型按脚本或 API 的真实返回值声明类型用途 nullability。
- Kafka 可选 Bean 仍保留 `getIfAvailable()` 后的 null 判定，但泛型声明必须完整。
- 仅构造注入、无额外可变状态的简单服务类优先使用 Java `record`；不得因此改变现有构造参数、依赖类型或业务行为。

### 21. 注释与文档

- Javadoc：所有公开类，`@author DaYZ` + `@since YYYY-MM-DD`
- 业务逻辑：中文注释
- 代码标识符：英文
- 设计文档：`docs/DESIGN.md`
- 缓存策略：`docs/REDIS-CACHE-STRATEGY.md`

### 22. 阿里巴巴 Java 开发规范（强制）

基于阿里巴巴 Java 开发手册，以下规则必须遵守：

#### 22.1 控制语句

- **if/else/for/while/do 必须使用大括号**，即使只有一行代码
- **switch 块必须包含 default 语句**，放在最后
- **禁止在条件判断中使用复杂表达式**，将结果赋值给有意义的布尔变量

```java
// ✗ 错误
if (a != null && (b.canRead() || c.canWrite()) && d.equals(e)) { ... }

// ✓ 正确
boolean hasAccess = a != null && (b.canRead() || c.canWrite());
boolean isOwner = d.equals(e);
if (hasAccess && isOwner) { ... }
```

#### 22.2 命名规范

- **POJO 布尔字段禁止使用 is 前缀**，否则部分框架解析会引起序列化错误
- **方法名、参数名、成员变量、局部变量统一使用 lowerCamelCase**

```java
// ✗ 错误
public record CourseAccessVO(boolean isPrimaryTeacher) {}
public void toForumReplyVOWithChildren() {}

// ✓ 正确
public record CourseAccessVO(@JsonProperty("isPrimaryTeacher") boolean primaryTeacher) {}
public void toForumReplyVoWithChildren() {}
```

#### 22.3 注释规范

- **所有接口方法必须使用 Javadoc 注释**，包含 `@param`、`@return` 标签
- **所有类必须添加 `@author` 和 `@since` 信息**
- **枚举字段必须添加注释**，说明每个数据项的用途
- **禁止使用行尾注释**，注释应另起一行
- **字段注释必须使用 Javadoc 格式**（`/** */`），禁止 `//` 注释

```java
// ✗ 错误
private static final int MAX_POLL_ROUNDS = 30; // 最多等 3s

// ✓ 正确
/** 最多等 3s */
private static final int MAX_POLL_ROUNDS = 30;
```

#### 22.4 魔法值

- **禁止任何魔法值直接出现在代码中**，必须定义为常量

```java
// ✗ 错误
if (authorization.startsWith("Bearer ")) { ... }
if (status == 403 || status == 404) { ... }

// ✓ 正确
private static final String BEARER_PREFIX = "Bearer ";
private static final int HTTP_FORBIDDEN = 403;
private static final int HTTP_NOT_FOUND = 404;

if (authorization.startsWith(BEARER_PREFIX)) { ... }
if (status == HTTP_FORBIDDEN || status == HTTP_NOT_FOUND) { ... }
```

#### 22.5 集合与线程池

- **集合初始化时必须指定初始值大小**
- **线程池禁止使用 Executors 创建**，必须通过 ThreadPoolExecutor 方式

```java
// ✗ 错误
Map<UUID, String> urls = new HashMap<>();
ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

// ✓ 正确
Map<UUID, String> urls = new HashMap<>(objects.size());
ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, r -> {
    Thread t = new Thread(r, "sse-heartbeat");
    t.setDaemon(true);
    return t;
});
```

#### 22.6 方法规范

- **单个方法总行数不超过 80 行**，超过需拆分为私有方法
- **禁止使用过时的类或方法**

```java
// ✗ 错误（BigDecimal.ROUND_HALF_UP 自 Java 9 起已弃用）
score.divide(divisor, 2, BigDecimal.ROUND_HALF_UP);

// ✓ 正确
import java.math.RoundingMode;
score.divide(divisor, 2, RoundingMode.HALF_UP);
```
