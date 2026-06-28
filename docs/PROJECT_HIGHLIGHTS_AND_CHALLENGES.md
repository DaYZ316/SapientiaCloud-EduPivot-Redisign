# SapientiaCloud EduPivot 项目重难点与亮点

## 写作格式说明

本材料按简历和面试常用写法整理：

- 简历条目采用 XYZ 结构：完成了什么、产生了什么价值、通过什么技术手段实现。
- 面试展开采用 STAR/CAR 结构：背景/挑战、行动、结果。
- 技术点避免只堆栈名，尽量说明问题、方案和业务价值。

联网参考：

- MIT Career Advising: STAR Method
  <https://capd.mit.edu/resources/the-star-method-for-behavioral-interviews/>
- National Careers Service: STAR Method
  <https://nationalcareers.service.gov.uk/careers-advice/interview-advice/the-star-method>
- Indeed: Challenge Action Result Resume
  <https://ca.indeed.com/career-advice/resumes-cover-letters/challenge-action-result-resume>
- Teal: XYZ Resume Formula
  <https://www.tealhq.com/post/xyz-resume>

## 项目概述

智语·云枢 SapientiaCloud EduPivot 是一个面向智慧教学场景的云原生平台，覆盖认证、课程、章节、题库、文件存储、通知、AI 教学助手、3D 虚拟教室和直播课堂等模块。

后端采用 Spring Boot 4、Spring Cloud、Spring Cloud Alibaba、Nacos、Gateway、OpenFeign、Kafka、Redis、PostgreSQL、MinIO、Flyway、Docker Compose；前端采用 Vue 3、TypeScript、Pinia、Naive UI、Three.js、LiveKit。

系统按微服务拆分为 sc-gateway、sc-auth、sc-course、sc-storage、sc-notification、sc-ai、sc-frontend，并通过公共模块 sc-common 复用响应封装、安全、Feign、事件、Redis 等基础能力。

## 项目重难点

### 1. AI 教学助手的 RAG 检索增强问答

难点：

普通大模型问答容易脱离课程资料，回答不可控。教学场景要求 AI 能围绕教师上传的课件、文档和课程上下文进行回答，同时还要支持历史会话记忆和流式输出。

方案：

- sc-ai 通过 sc-storage 获取文件信息和下载地址。
- 使用 TikaDocumentReader 解析 PDF、Word 等文档。
- 使用 TokenTextSplitter 切分文档内容。
- 将 chunk 写入 Redis Vector Store，并记录 userId、docId、sourceType 等元数据。
- 问答时结合向量检索、课程上下文、历史会话和兜底策略。
- 使用 SSE 向前端流式返回回答、上下文命中信息和错误事件。

结果：

AI 回答不再只是泛聊，而是可以基于课程资料和历史对话生成更贴合教学场景的回答，形成“上传资料 -> 向量化入库 -> 检索增强问答”的完整链路。

相关代码：

- `sc-ai/src/main/java/com/dayz/sc/ai/service/KnowledgeBaseService.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/RagChatService.java`

### 2. AI 出题/组卷结果的稳定性与可用性

难点：

大模型生成题目时容易出现 JSON 格式错误、题型不匹配、选项缺失、答案不完整、分值不一致、LaTeX 不可渲染等问题。如果直接把模型输出展示给教师，结果不可控，也难以入库复用。

方案：

- 定义严格的题目 JSON 输出规则。
- 对单选、多选、判断、填空、简答等题型分别做结构校验。
- 对题干、选项、答案、解析、分值、预计用时等字段做规范化处理。
- 对分值进行按权重分配，保证总分和小题分数一致。
- 对 LaTeX、数学公式和选项标签进行检测和修复。
- 支持多轮生成、校验、自动修复和问题追踪。
- 生成结果以结构化 payload 保存，供前端预览、导出和后续入题库。

结果：

把不可控的自然语言生成转换为可预览、可追踪、可保存、可导出的结构化题目/试卷，提升 AI 功能在真实教学流程中的可用性。

相关代码：

- `sc-ai/src/main/java/com/dayz/sc/ai/service/QuestionGenerationService.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/QuestionGenerationQualityPolicy.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/QuestionPaperExportFormatter.java`

### 3. 长耗时 AI 任务的异步化与进度反馈

难点：

AI 出题和组卷属于长耗时任务，如果直接同步调用，接口容易超时，用户长时间无反馈；同时生成过程涉及多个阶段，前端需要展示“接收请求、检索资料、生成草稿、校验修复、组装结果”等状态。

方案：

- 使用 Kafka 拆分请求、进度和完成事件。
- `QuestionGenerationKafkaBridge` 将 requestId 与 Reactor Sink 关联。
- Worker 消费生成请求，执行 AI 出题/组卷逻辑。
- 每个阶段通过 progress topic 发布事件。
- 前端通过 SSE 获取 generation_stage、agent_search、generation_result 等事件。
- 当 Kafka 不可用或派发超时时，保留本地 fallback 生成路径。

结果：

长耗时 AI 任务从阻塞式接口变成可观测、可取消、可恢复反馈的异步流程，提升用户体验和系统可用性。

相关代码：

- `sc-ai/src/main/java/com/dayz/sc/ai/service/QuestionGenerationKafkaBridge.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/event/QuestionGenerationWorker.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/GenerationMessageStateService.java`

### 4. 直播课堂的实时转写与增量总结

难点：

直播课堂内容是连续音频流，不能每次都全量总结，否则成本高、延迟大、上下文容易超长。系统还需要区分临时转写和最终转写，并将总结实时同步给课堂参与者。

方案：

- 通过 DashScope ASR 建立实时语音识别流。
- 临时转写直接推送给前端，最终转写按 sequenceNo 持久化。
- 基于“上一版压缩状态 + 新增转写片段”做增量总结。
- 总结结果包含 overview、keyPoints、timeline、questions、mindMap。
- 通过 LiveSummaryEventHub 向课堂推送转写和总结快照。
- 使用线程池、ReentrantLock 和最小字符数/时间间隔控制总结频率。

结果：

实现了面向课堂直播的实时字幕、增量总结、知识点提炼、复习问题和思维导图生成，避免重复总结和上下文爆炸。

相关代码：

- `sc-ai/src/main/java/com/dayz/sc/ai/service/LiveSummaryService.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/DashScopeAsrClient.java`
- `sc-ai/src/main/java/com/dayz/sc/ai/service/LiveSummaryEventHub.java`

### 5. 3D 虚拟教室的性能与实时座位同步

难点：

3D 教室不是普通表单页面，需要处理 GLB 模型加载、HDR 环境贴图、相机边界、座位交互、多人同步、资源释放和 WebSocket 重连。桌椅数量较多时，如果逐个渲染模型，性能压力明显。

方案：

- 使用 Three.js 构建全屏 3D 教室。
- 通过 GLTFLoader 加载不同规格教室和桌椅模型。
- 使用 InstancedMesh 批量渲染桌椅，减少 draw call。
- 使用 Raycaster 处理座位点击、右键离座和出口交互。
- 使用 Sprite 展示座位上的用户标记。
- 使用 WebSocket 同步座位快照、入座、离座和直播状态。
- 组件卸载时释放 geometry、material、texture、controls、renderer 等资源。

结果：

实现了可交互、可同步、可扩展的沉浸式 3D 教室，支撑学生入座、换座、离座、直播状态联动等课堂互动场景。

相关代码：

- `sc-frontend/src/features/classroom/components/Classroom3D.vue`
- `sc-frontend/src/features/classroom/composables/ModelInstanceManager.ts`
- `sc-frontend/src/features/classroom/composables/SeatSpriteManager.ts`
- `sc-frontend/src/features/classroom/composables/seatSyncSocket.ts`

### 6. LiveKit 直播课堂链路

难点：

在线课堂需要同时支持教师摄像头、麦克风、屏幕共享、学生订阅、画中画、网络质量展示和离开页面后的直播状态保持。实时音视频状态复杂，前端需要正确处理 track publish、subscribe、unpublish、reconnect、disconnect 等事件。

方案：

- 后端生成 LiveKit 兼容 JWT，按房间和角色控制权限。
- 前端使用 livekit-client 连接房间。
- 教师端支持摄像头、麦克风、屏幕共享。
- 学生端自动订阅远端音视频。
- 支持屏幕共享优先、摄像头画中画。
- 使用 LiveKit data packet 同步摄像头浮窗位置和连接质量。
- 定时采样 WebRTC stats，用于展示网络状态。

结果：

形成了可用于课堂直播的实时音视频链路，并与 3D 教室、课堂状态、AI 实时总结联动。

相关代码：

- `sc-course/src/main/java/com/dayz/sc/course/service/LiveKitTokenService.java`
- `sc-frontend/src/features/classroom/composables/useClassroomLive.ts`
- `sc-frontend/src/features/classroom/components/ClassroomLivePanel.vue`

### 7. 微服务安全与治理一致性

难点：

系统拆分为多个服务后，认证、授权、用户身份传递、Token 生命周期、接口限流、服务调用和配置治理都需要统一，否则容易出现安全边界混乱。

方案：

- Gateway 作为统一入口，校验 JWT。
- 使用 RS256/JWKS 做非对称验签。
- Token 黑名单用于登出后失效控制。
- Refresh Token 存 Redis，且使用 SHA-256 token hash，支持轮换和批量撤销。
- Gateway 将 userId、role 注入 `X-User-Id`、`X-User-Role` 传给下游服务。
- 使用 Redis + Lua 滑动窗口实现接口限流。
- 使用 Nacos 管理业务配置、基础设施配置和网关路由。
- 使用 Flyway 管理各服务数据库迁移。

结果：

微服务间形成统一安全边界和治理方式，降低重复实现和认证不一致风险。

相关代码：

- `sc-gateway/src/main/java/com/dayz/sc/gateway/config/GatewaySecurityConfiguration.java`
- `sc-gateway/src/main/java/com/dayz/sc/gateway/filter/UserRoleHeaderFilter.java`
- `sc-common/sc-common-security/src/main/java/com/dayz/sc/common/security/token/RefreshTokenService.java`
- `sc-common/sc-common-security/src/main/java/com/dayz/sc/common/security/ratelimit/RateLimiterService.java`

## 项目亮点

### 1. 云原生微服务架构完整

项目不是单体 Demo，而是具备网关、配置中心、服务发现、对象存储、消息队列、数据库迁移、统一鉴权、统一响应和容器化部署的完整微服务系统。

可强调：

- Spring Cloud Gateway 统一入口。
- Nacos 统一配置和服务发现。
- OpenFeign 负责服务间调用。
- Kafka 支撑事件驱动和长耗时任务。
- Redis 支撑缓存、限流、Token、向量检索。
- PostgreSQL + Flyway 管理业务数据和迁移。
- MinIO 管理文件对象。
- Docker Compose 一键拉起基础设施和应用服务。

### 2. AI 能力和教学业务深度结合

AI 不是独立聊天框，而是贯穿课程资料、知识库、题库、试卷、直播课堂和历史会话。

可强调：

- 课程资料 RAG 问答。
- AI 智能出题。
- AI 智能组卷。
- 出题过程 trace 和自动修复。
- 直播课堂实时转写和总结。
- 教师可基于 AI 结果继续编辑、预览和导出。

### 3. 实时互动体验突出

项目同时包含 3D 教室、WebSocket 座位同步、LiveKit 直播、屏幕共享、画中画、网络质量监控、实时转写和 AI 总结。

可强调：

- 3D 教室提供沉浸式课堂入口。
- WebSocket 保持多人座位状态一致。
- LiveKit 提供实时音视频能力。
- AI 转写总结提升课堂复盘效率。

### 4. AI 输出可追踪、可修复、可落库

很多项目只展示大模型文本，本项目将模型输出转为结构化教学资产。

可强调：

- 生成阶段事件可视化。
- 校验问题可追踪。
- 自动修复提升结果质量。
- 结构化 payload 支撑预览、导出和后续入库。

### 5. 安全设计较完整

项目覆盖登录态签发、刷新、撤销、登出、网关鉴权、下游身份传递、接口限流等关键环节。

可强调：

- RS256 JWT + JWKS。
- Refresh Token 轮换。
- Token 黑名单。
- Redis 滑动窗口限流。
- Gateway 统一注入用户身份。

### 6. 前端工程复杂度较高

前端不仅有普通业务页面，还处理 3D、实时音视频、SSE、WebSocket、文件预览、Markdown/KaTeX 渲染、国际化和状态持久化。

可强调：

- Vue 3 Composition API。
- Pinia 状态管理。
- Three.js 3D 场景。
- LiveKit 实时音视频。
- SSE AI 流式输出。
- WebSocket 座位同步。
- Vue I18n 国际化。

## 简历版描述

智语·云枢 SapientiaCloud EduPivot：基于 Spring Cloud + Vue 3 的云原生智慧教学平台，包含认证、课程、题库、文件存储、通知、AI 教学助手、3D 虚拟教室和直播课堂等模块。后端采用 Spring Boot 4、Nacos、Gateway、OpenFeign、Kafka、Redis、PostgreSQL、MinIO、Flyway；前端采用 Vue 3、TypeScript、Pinia、Naive UI、Three.js、LiveKit。

简历条目：

- 设计并实现 AI 教学助手模块，基于 Spring AI、DashScope、Redis Vector Store 构建 RAG 问答链路，支持课程资料解析、向量化入库、历史会话记忆和 SSE 流式回复。
- 实现 AI 出题/组卷能力，通过 JSON 结构约束、题型校验、分值分配、LaTeX 规范化、多轮修复和生成进度追踪，提高大模型输出的稳定性和业务可用性。
- 基于 Kafka + Reactor Sink 拆分长耗时 AI 生成任务，实现请求派发、进度推送、结果回传和失败兜底，避免同步接口长时间阻塞。
- 构建 LiveKit 直播课堂链路，支持教师摄像头、麦克风、屏幕共享、学生观看、画中画布局、连接质量同步和网络状态采样。
- 基于 Three.js 实现 3D 虚拟教室，使用 GLB 模型加载、InstancedMesh 桌椅实例化、座位精灵、Raycaster 交互和 WebSocket 座位同步，提升课堂沉浸式互动体验。
- 搭建微服务基础能力，包括 Gateway 统一鉴权、RS256 JWT/JWKS、Refresh Token 轮换、Token 黑名单、Redis 滑动窗口限流、Feign 服务调用和 Flyway 多服务迁移管理。

## 面试展开版

### 1. AI 出题/组卷

背景：

教师需要快速生成结构化题目和试卷，但大模型输出不可控，经常出现格式不合法、答案缺失、题型不匹配、分值错误等问题。

行动：

我设计了完整的生成链路：先规范请求参数，再组织课程上下文和检索证据；模型输出后做 JSON 解析、题型校验、选项答案校验、分值分配、LaTeX 检测和自动修复；生成过程通过阶段事件持续推送给前端。

结果：

把原本不可直接使用的大模型文本转换为可预览、可追踪、可保存到题库的结构化题目/试卷，明显提升 AI 在教学业务中的落地性。

### 2. RAG 知识库问答

背景：

普通 AI 问答无法保证答案来自课程资料，教学场景需要让 AI 围绕教师上传的资料和课程内容回答。

行动：

我通过 sc-storage 获取资料，使用 Tika 解析文件，切分文本后写入 Redis 向量库；问答时组合向量检索结果、课程上下文、历史会话和兜底策略，并通过 SSE 流式返回。

结果：

实现了“资料入库 -> 向量检索 -> 课程问答”的闭环，让 AI 回答更贴合课程内容。

### 3. 3D 教室与实时直播

背景：

在线教学不仅需要课程管理，还需要实时互动和沉浸式课堂体验。

行动：

我使用 Three.js 构建 3D 教室，使用 InstancedMesh 优化桌椅渲染，通过 Raycaster 实现座位交互，并用 WebSocket 同步座位状态；直播部分使用 LiveKit 实现音视频、屏幕共享、画中画和网络质量采样。

结果：

形成了“课程管理 + 3D 课堂 + 实时直播 + AI 总结”的完整教学闭环，交互体验明显区别于普通后台管理系统。

## 答辩时可选的重点话术

如果只能讲 1 个重点，优先讲 AI 出题/组卷，因为它最能体现业务复杂度和工程处理能力。

如果讲 2 个重点，选择 AI 出题/组卷 + RAG 知识库问答，能突出 AI 能力不是简单调接口。

如果讲 3 个重点，选择 AI 出题/组卷 + RAG 知识库问答 + 3D 教室/LiveKit 实时课堂，能覆盖后端、AI、前端和实时通信。

## 一句话总结

这个项目的核心亮点是：用微服务架构承载完整智慧教学业务，用 AI 能力增强课程资料、题库和直播课堂，用 3D 教室和实时音视频提升课堂互动体验。
