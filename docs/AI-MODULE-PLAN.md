# sc-ai 模块实施计划（第一期 MVP）

## 一、目标与范围

为「智语·云枢」新增 AI 教学助手服务 **sc-ai（Celestial Hub）**，第一期交付两项能力：

1. **智能问答（RAG）**：基于课程知识库的检索增强问答，LLM 流式输出（SSE）。
2. **会话管理**：对话历史、收藏、置顶（CRUD）。

> 暂不纳入：智能出题/题目溯源、TTS、Live2D（DESIGN.md §2.6 其余项，留作第二期）。

**成功标准**：
- `mvn -pl sc-ai -am -DskipTests clean package` 通过。
- `docker compose up -d --build` 后 SC-AI 容器 healthy。
- 经 Gateway（:39080）携带 JWT 调 `/api/ai/**`：能创建会话、上传文档入库、发起问答并收到基于知识库的流式答案、查询历史。

## 二、技术选型（已确认）

| 关注点 | 选型 | 说明 |
|--------|------|------|
| AI 框架 | **Spring AI 2.0.0 GA** | 专为 Spring Boot 4.0/4.1 + Spring Framework 7.0 设计，对齐项目 Boot 4.0.6 |
| LLM 接入 | **`spring-ai-starter-model-openai`** + 通义 OpenAI 兼容端点 | base-url 指向 `https://dashscope.aliyuncs.com/compatible-mode/v1`；规避 spring-ai-alibaba 专用 starter 仅支持 Boot 3.5 的不兼容问题 |
| Chat 模型 | DashScope `qwen-plus`（可配置） | 走 OpenAI 兼容协议 |
| Embedding | DashScope `text-embedding-v3`（1024 维） | 同一 API Key，走 OpenAI 兼容协议 |
| 向量库 | **Redis Stack**（`redis/redis-stack-server`） | 替换现有 `redis:8-alpine`；提供 RediSearch（向量检索硬要求）+ RedisJSON |
| 向量 Store | Spring AI `spring-ai-starter-vector-store-redis` | 原生对接 RediSearch |
| 会话/文档元数据 | PostgreSQL（MyBatis-Plus + Flyway） | 与现有服务一致 |
| 文档来源 | 经 sc-storage（Feign 内部端点）获取文件 | 复用现有存储能力 |

## 三、基础设施改动（关键前置）

### 3.1 Redis 镜像替换（docker-compose.yaml）
将 `redis` 服务镜像由 `redis:8-alpine` 改为 `redis/redis-stack-server:latest`（带 RediSearch/RedisJSON，协议兼容，现有缓存连接不受影响）。
- 命令保持密码鉴权：`redis-server --requirepass ... --appendonly yes`（redis-stack-server 支持透传 redis 参数，或改用 REDIS_ARGS 环境变量）。
- healthcheck 沿用 `redis-cli -a ... ping`。
- **风险**：现有 `redis-data` 卷格式兼容，但首次切换建议确认 RediSearch 模块已加载（`docker exec ... redis-cli MODULE LIST`）。

### 3.2 新增 sc-ai 容器（docker-compose.yaml）
照抄 sc-storage 服务定义，调整：
- `container_name: SC-AI`，`image: sc-ai:latest`，端口 `28086:28086`。
- `depends_on`: nacos / nacos-config-init / postgres / redis / kafka（healthy）。
- 环境变量追加：`DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}`。
- healthcheck 指向 `:28086/actuator/health/liveness`。

### 3.3 端口分配（更新 CLAUDE.md 端口表）
新增 **sc-ai = 28086**（现有占用 28081-28085，39080）。

### 3.4 .env / .env.example
新增 `DASHSCOPE_API_KEY=`（敏感，仅 .env.example 留空占位）。

## 四、后端模块结构（sc-ai/）

照抄 sc-course/sc-storage 的标准结构，根包 `com.dayz.sc.ai`：

```
sc-ai/
  pom.xml                      # 照抄 sc-storage/pom.xml，依赖见 §五
  Dockerfile                   # 照抄 sc-storage/Dockerfile，端口改 28086，-pl sc-ai
  src/main/java/com/dayz/sc/ai/
    ScAiApplication.java        # @SpringBootApplication @EnableDiscoveryClient @EnableFeignClients
    config/
      AiConfig.java             # ChatClient.Builder、向量 Store 相关 Bean
      package-info.java         # @NullMarked
    controller/
      ChatController.java       # POST /api/ai/chat（SSE 流式问答）
      ConversationController.java  # 会话 CRUD、收藏、置顶
      KnowledgeBaseController.java # 知识库文档上传/列表/删除
    service/
      RagChatService.java       # 检索 → 拼 prompt → 调 LLM 流式返回
      ConversationService.java  # 会话/消息持久化
      KnowledgeBaseService.java # 文档拉取(Feign sc-storage) → 解析 → 切分 → embedding → 入库
    repository/
      ConversationRepository.java  MessageRepository.java  KnowledgeDocRepository.java
    repository/impl/
      MybatisConversationRepository.java  ...（前缀 Mybatis）
    mapper/
      ConversationMapper.java  MessageMapper.java  KnowledgeDocMapper.java  # @Mapper extends BaseMapper
    model/
      entity/   Conversation  ChatMessage  KnowledgeDoc        # @TableName，主键 IdType.INPUT
      dto/      ChatRequest  CreateConversationRequest  UploadDocRequest ...  # record + 校验
      vo/       ConversationVO  ChatMessageVO  KnowledgeDocVO ...            # record + @Nullable
      enums/    MessageRole(USER/ASSISTANT/SYSTEM)  DocStatus(PENDING/INDEXED/FAILED)
    client/                     # 复用 sc-common-feign 的 StorageInternalClient（无需新建）
  src/main/resources/
    application.yaml            # 照抄 sc-storage，name=sc-ai，import sc-edupivot-ai-* 配置
    db/migration/
      V2026061601__create_ai_tables.sql   # 见 §六
```

## 五、Maven 依赖（pom.xml）

在根 `pom.xml` 的 `<modules>` 追加 `<module>sc-ai</module>`，并在 `<properties>` 增 `<spring-ai.version>2.0.0</spring-ai.version>`，`<dependencyManagement>` 引入 Spring AI BOM。

sc-ai/pom.xml 依赖（以 sc-storage 为基线）：
- 内部：sc-common-core / -web / -security / -feign / -redis / -events
- MyBatis-Plus（`mybatis-plus-spring-boot4-starter` + jsqlparser）
- Spring Boot：starter-webmvc、validation、actuator、flyway + flyway-database-postgresql、postgresql(runtime)
- Nacos：config + discovery
- SpringDoc Scalar、Micrometer Brave + Zipkin（链路追踪）
- **新增 AI 依赖**：
  - `org.springframework.ai:spring-ai-starter-model-openai`
  - `org.springframework.ai:spring-ai-starter-vector-store-redis`
  - 文档解析：`org.springframework.ai:spring-ai-tika-document-reader`（支持 PDF/Word/纯文本，Tika 内置）

## 六、数据库（Flyway: V2026061601__create_ai_tables.sql）

表前缀 **`ai_`**（沿用「服务前缀」规范，DESIGN/CLAUDE 未占用 ai_，新增）：

- `ai_conversation`：id、user_id、title、pinned(SMALLINT)、favorited(SMALLINT)、created_at、updated_at、deleted、deleted_at。索引 idx_ai_conversation_user。
- `ai_message`：id、conversation_id(FK)、role、content(TEXT)、created_at、deleted。索引 idx_ai_message_conversation。
- `ai_knowledge_doc`：id、user_id、storage_object_id、filename、status、chunk_count、created_at、deleted。索引 idx_ai_knowledge_doc_user。

> 向量本身存 Redis（RediSearch index），PG 只存文档元数据与会话。
> 列规范遵循 §16：UUID 主键（Service 层 UuidV7Generator 生成）、TIMESTAMPTZ、软删除、COMMENT。

## 七、Nacos 配置（新增文件）

照抄 storage 三件套：
- `nacos-config/sc-edupivot-ai-common.yaml`（Group EDUPIVOT_NAVIGATOR）：
  业务配置 `edupivot.ai.*`（chat-model、embedding-model、rag topK、chunk size），及 `spring.ai.openai.*`（base-url=通义兼容端点、api-key=${DASHSCOPE_API_KEY}、chat/embedding options）、`spring.ai.vectorstore.redis.*`（index、prefix、维度 1024）。
- `nacos-config/local/sc-edupivot-ai-infra-local.yaml`（Group EDUPIVOT_LOCAL）：PG + Redis 本地连接。
- `nacos-config/docker/sc-edupivot-ai-infra-docker.yaml`（Group EDUPIVOT_DOCKER）：PG + Redis 容器名连接。
- `scripts/nacos_config_init.py` 自动导入（按目录约定，确认是否需登记新 DataId）。

## 八、Gateway 路由（更新 routes 配置）

在 `sc-edupivot-gateway-routes-docker.yaml` 与 `-local.yaml` 追加：
- `ai-route`：`uri: lb://sc-ai`，`Path=/api/ai/**`。
- `ai-openapi-route`：`/openapi/ai` → `SetPath=/v3/api-docs`。
- 若有内部端点，加 `block-ai-internal`（仿 block-storage-internal，返回 403）。
- 确认 `public-endpoints` 无需放行（AI 接口全部要鉴权）。

## 九、安全与编码规范对齐

- Controller：`@RestController` + `/api/ai/...`，`@RequiredArgsConstructor`，返回 `ApiResponse<T>`；`@AuthenticationPrincipal Jwt jwt` + `JwtPrincipalResolver.requireUserId(jwt)` 取 userId；会话/文档按 userId 归属校验（防越权读他人会话）。
- 问答接口加 `@RateLimited`（LLM 调用昂贵，建议 maxRequests=20/60s）。
- 不使用 @CrossOrigin（Gateway 统一）。
- 空安全：package-info.java `@NullMarked`，VO 可选字段 `@Nullable`。
- 流式：ChatController 返回 `Flux<ServerSentEvent<String>>` 或 SseEmitter（参考 sc-notification 的 SSE 实现风格）。

## 十、文档更新

- `CLAUDE.md`：端口表 +sc-ai(28086)；微服务架构图 +sc-ai；表前缀表 +`ai_`。
- `docs/DESIGN.md`：标注 Celestial Hub 第一期落地范围（可选）。
- `nacos-config/README.md`：补 sc-ai DataId 清单。

## 十一、实施步骤与验证

```
1. 根 pom 加 module + Spring AI BOM        → 验证: mvn -ntp validate 通过
2. 建 sc-ai 骨架(pom/Application/config)    → 验证: mvn -pl sc-ai -am compile 通过
3. Flyway 迁移脚本 + entity/mapper/repo     → 验证: 编译通过
4. 会话管理(Controller/Service/CRUD)        → 验证: 编译通过，本地起服务命中 PG
5. 知识库(Feign取文件→解析→切分→embedding→入Redis) → 验证: 文档入库后 RediSearch 有索引
6. RAG问答(检索→prompt→LLM流式)             → 验证: 问答返回基于知识库的答案
7. 基础设施: Redis Stack 镜像 + sc-ai 容器 + 网关路由 + Nacos 配置 + .env
                                            → 验证: docker compose up -d --build 全 healthy
8. 端到端: 经 Gateway 携 JWT 跑通完整链路    → 验证: §一 成功标准全绿
9. 文档更新(CLAUDE.md 等)
```

## 十二、待你后续提供 / 风险

- **DASHSCOPE_API_KEY**：实施到第 6 步联调时需要你提供有效的通义 API Key（否则 LLM/embedding 调用 401）。
- **Redis Stack 切换**：现有缓存数据卷兼容性需在切换后实测确认 MODULE LIST 含 search。
- **Spring AI 2.0.0 + 通义兼容端点**：embedding 维度需与 Redis 索引声明一致（text-embedding-v3 = 1024），实施时以实际返回维度为准校正。
- 当前仓库无测试代码，本计划以「编译通过 + 容器 healthy + 端到端手测」为验证基线，不新增测试框架（遵循现状）。
