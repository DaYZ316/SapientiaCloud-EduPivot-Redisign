# AgentSearch 索引效果测试报告

测试时间：2026-06-21 02:05-02:20（Asia/Shanghai）  
后端网关：`http://117.72.194.197:39080`  
测试账号：`admin@edupivot.xyz`（管理员，role=0）  
测试方式：通过网关登录后调用公开 API 和 `/api/ai/chat` SSE，解析 `agent_search` 事件；未记录 access token 和密码。

## 1. 结论摘要

AgentSearch 的教学数据检索主链路可用，能够通过 AI Chat 的工具调用检索课程、章节、题库/题目等授权内容，并能正确发出 `started -> results/empty -> completed` 事件。管理员账号可访问课程列表，课程范围收窄也有效。

发现两个问题：

1. **聊天记忆向量索引存在删除后残留**：临时会话删除成功后，后续 `searchChatMemory` 仍能检索到被删会话内容，且普通 RAG context 也继续命中已删除 conversationId。
2. **`queryPlatformApi` 对已登记 OpenAPI 路由误判未登记**：`/openapi/auth` 中存在 `/api/auth/users/me`，但 AgentSearch `queryPlatformApi(auth, /api/auth/users/me)` 返回“该只读接口未在平台 OpenAPI 中登记”。

个人知识库当前为空，空索引返回行为正常。

## 2. 基准数据

登录账号验证：

- 用户：DaYZ
- 邮箱：`admin@edupivot.xyz`
- 角色：管理员（role=0）
- 登录接口：`POST /api/auth/password/login`

远端数据基线：

- `GET /api/courses?page=1&size=5`：成功，课程总数 20
- `GET /api/question-banks?page=1&size=5`：成功，题库总数 60
- `GET /api/question-banks/questions?page=1&size=5`：成功，题目总数 1200
- `GET /api/ai/knowledge-docs?page=1&size=20`：成功，当前用户个人知识库文档 0 条
- `GET /api/ai/conversations?page=1&size=10`：初始为空；测试过程中创建的临时会话均尝试删除，删除接口返回 200

公开网关边界：

- `/openapi/ai`、`/openapi/course`、`/openapi/auth` 可访问
- course 内部接口 `/internal/ai/search` 未经网关暴露，外部视角只能通过 `/api/ai/chat` 触发 AgentSearch 工具
- 直连 `28084`、`28086` 不可达，符合服务不直接对外暴露的预期

## 3. 覆盖用例与结果

| 用例 | 触发工具/域 | 关键结果 | 结论 |
| --- | --- | --- | --- |
| 当前用户识别 | `getCurrentUserProfile` / `profile` | `results total=1`，回答识别为 DaYZ、管理员 | 通过 |
| 可访问课程列表 | `listMyCourses` / `courses` | `results total=5`，返回 5 门课程，第一条为“中文游戏原型与关卡叙事实训” | 通过 |
| 教学数据检索：`Ableton Live and Web Audio` | `searchTeachingData` / `teaching` | `results total=5`，均为 `Music Production for Interactive Media` 的章节 | 通过 |
| 教学数据检索：`heightfield sculpting` | `searchTeachingData` / `teaching` | `results total=5`，均为 `Terrain Editing for Games and Simulations` 的章节 | 通过 |
| 教学数据无命中 | `searchTeachingData` / `teaching` | `empty total=0`，关键词 `quantum potato spaceship scheduling protocol` | 通过 |
| 个人知识库空索引 | `searchPersonalKnowledge` / `knowledge` | `empty total=0`，当前用户知识库文档 0 条 | 通过 |
| 课程范围正例 | 带 `courseId=Music Production...` 的 `searchTeachingData` | `Ableton Live and Web Audio` 命中 5 条 | 通过 |
| 课程范围反例 | 带 `courseId=Terrain Editing...` 的 `searchTeachingData` | 同关键词返回 `empty total=0` | 通过 |
| 平台 API 查询 | `queryPlatformApi` / `platform` | `/api/auth/users/me` 返回 `PLATFORM_API_ERROR`，称 OpenAPI 未登记 | 未通过 |
| 聊天记忆删除回归 | `searchChatMemory` / `memory` | 删除临时会话后仍返回 5 条聊天记忆，其中包含已删除会话的唯一 token | 未通过 |

## 4. 典型事件证据

课程列表事件：

```json
{
  "phase": "results",
  "domain": "courses",
  "query": "可访问课程",
  "total": 5,
  "items": [
    {
      "sourceType": "COURSE",
      "title": "中文游戏原型与关卡叙事实训",
      "relationLabel": "管理员可见"
    }
  ]
}
```

教学数据命中事件：

```json
{
  "phase": "results",
  "domain": "teaching",
  "query": "Ableton Live and Web Audio",
  "total": 5,
  "items": [
    {
      "sourceType": "CHAPTER",
      "title": "1. Orientation, references, and production brief: Ableton Live and Web Audio",
      "contextLabel": "Music Production for Interactive Media"
    }
  ]
}
```

课程范围反例事件：

```json
{
  "phase": "empty",
  "domain": "teaching",
  "query": "Ableton Live and Web Audio",
  "total": 0,
  "items": []
}
```

删除后聊天记忆残留事件：

```json
{
  "phase": "results",
  "domain": "memory",
  "query": "AGENTSEARCH_DELETE_PROBE_019EE63D_ALPHA",
  "total": 5,
  "items": [
    {
      "sourceType": "CHAT_MEMORY",
      "snippet": "User: Please remember this temporary memory token exactly: AGENTSEARCH_DELETE_PROBE_019EE63D_ALPHA. Reply with the token once. AI: AGENTSEARCH_DELETE_PROBE_019EE63D_ALPHA"
    }
  ]
}
```

## 5. 问题详情

### P1：聊天记忆向量索引删除后残留

复现步骤：

1. 创建临时聊天，消息包含唯一 token：`AGENTSEARCH_DELETE_PROBE_019EE63D_ALPHA`
2. 收到 AI 回复后，调用 `DELETE /api/ai/conversations/{id}`，返回 200
3. 等待约 6 秒
4. 发起新聊天，要求 `searchChatMemory` 精确检索该 token

实际结果：

- `searchChatMemory` 返回 `results total=5`
- 结果包含已删除会话中的唯一 token
- 新聊天的普通 `context` 也出现已删除 conversationId：`019ee63d-6c4b-7442-8c26-d6248e25c72f`

影响：

- 用户删除会话后，AI 仍可能检索并使用已删除会话内容
- 会造成隐私/合规风险，也会污染后续问答上下文
- 测试产生的早期乱码问答也持续被 RAG context 命中，说明残留不是个别 token 问题

建议排查：

- 检查 `ChatVectorMemoryService.deleteConversationMemory` 的 Redis VectorStore 删除表达式是否被当前 Spring AI Redis 实现正确支持
- 检查 `indexChatTurn` 是异步写入，是否存在“删除先执行、异步索引后完成”的竞态
- 建议在删除会话时阻塞等待未完成索引任务，或给向量文档加 deleted/version 标记并在检索 filter 中排除
- 增加集成测试：创建带唯一 token 的会话 -> 等待索引 -> 删除 -> 检索 token 应为 0

### P2：queryPlatformApi 对 OpenAPI 路由误判未登记

复现步骤：

1. `GET /openapi/auth` 可看到路径 `/api/auth/users/me`
2. 通过 AI Chat 要求调用 `queryPlatformApi`：service=`auth`，path=`/api/auth/users/me`

实际结果：

- AgentSearch 事件返回 `PLATFORM_API_ERROR`
- snippet：`该只读接口未在平台 OpenAPI 中登记，无法查询。`

影响：

- 平台只读 API 查询能力不可可靠使用
- 模型在后续回答中可能改用其他工具或记忆补偿，导致表现不稳定

建议排查：

- 检查 AI 服务内 `edupivot.ai.agent-search.gateway-base-url` 是否能访问 `http://sc-gateway:39080/openapi/auth`
- 检查 `PlatformApiSearchClient.loadOperations` 是否因请求异常、超时、鉴权或网关路由问题加载到空 OpenAPI
- 增加运行时日志或 actuator 指标，暴露 operationCache 每个 service 的加载数量
- 单元测试已有路径匹配覆盖，建议补一个部署环境 smoke test：加载 `/openapi/auth` 后必须包含 `/api/auth/users/me`

## 6. 质量观察

正向表现：

- AgentSearch SSE 事件结构稳定，前端可展示检索进度和来源卡片
- 教学数据命中能够返回 sourceType/sourceLabel/title/contextLabel/snippet/relationLabel
- 管理员权限下 relationLabel 正确显示为“管理员可见”
- courseId 范围约束有效，能避免跨课程误命中
- 无命中时能返回 `empty` 事件并给出清晰回答

限制与注意事项：

- 本次从外部网关无法直接调用 course 内部 `/internal/ai/search`，因此教学数据底层检索通过 AI 工具链间接验证
- 当前账号个人知识库为空，未覆盖真实知识库文档向量命中质量，只验证了空索引行为
- 第一次中文提示通过 PowerShell 管道传入 Node 时出现编码问题，模型收到类似 `????????` 的输入；后续改用 ASCII 英文提示复测，避免将终端编码误判为线上功能缺陷

## 7. 建议验收标准

修复后建议至少通过以下回归：

- `searchTeachingData("Ableton Live and Web Audio")` 仍命中 Music Production 相关章节
- 带 Music courseId 命中，带 Terrain courseId 不命中
- `searchPersonalKnowledge` 在空知识库时返回 `empty total=0`
- 删除会话后，使用唯一 token 调 `searchChatMemory` 必须返回 `empty total=0`
- `queryPlatformApi(auth, /api/auth/users/me)` 必须返回 `PLATFORM_API` 而不是 `PLATFORM_API_ERROR`

