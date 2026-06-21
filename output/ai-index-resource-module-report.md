# AI 资源模块索引检测报告

检测时间：2026-06-21 14:50-14:54（Asia/Shanghai）  
后端地址：`http://117.72.194.197:39080`  
测试账号：`admin@edupivot.xyz`（管理员）  
检测方式：登录后通过 `/api/ai/chat` SSE 向 AI 提问，要求 AI 调用课程资源检索工具；再用课程资源 API 返回的数据作为基准进行人工核验。未记录密码、访问令牌或个人隐私字段。

## 1. 结论

本次检测围绕课程《中文游戏原型与关卡叙事实训》的资源模块进行。AI 能够触发 `agent_search` 检索事件，并能基于课程章节、课程文件、题库等资源回答问题。5 个用例最终人工复核均通过：

- 章节大纲：通过，AI 正确列出 6 个顶层章节。
- 课程文件：通过，AI 正确命中 `CLAUDE.md`。
- 关键词资源检索：通过，AI 正确命中“核心循环原型”相关章节。
- 伪造案例反查：通过，AI 明确表示未检索到“折纸鹤 / 半阙词 / 长安夜巡 / 烟火”。
- 伪造配套资源反查：通过，AI 明确表示未检索到“课件 PDF / Unity Mini-Prototype 模板工程 / 学生作品分析包”。

需要注意：同一轮连续测试中，后续请求的 `context` 一度命中刚创建的聊天记忆，说明 AI 会话记忆可能污染相邻测试。虽然本次最终回答仍以 `agent_search` 结果为准，但建议后续做自动化检测时使用全新账号、关闭聊天记忆或在每个用例后等待向量记忆清理完成。

## 2. 基准事实

课程：`中文游戏原型与关卡叙事实训`  
课程 ID：`9d227502-8054-74ea-a39c-9731ea22d01c`

资源 API 基准：

- `GET /api/courses/{courseId}`：课程存在。
- `GET /api/chapters/course/{courseId}`：返回 6 个顶层章节。
- `GET /api/courses/{courseId}/files?page=1&size=100`：返回 1 个课程文件。
- `GET /api/question-banks/course/{courseId}`：返回 3 个题库。
- `GET /api/ai/knowledge-docs?page=1&size=100`：当前账号个人知识库文档数为 0。

真实 6 个顶层章节：

1. `1. 课程导入、资料研读与项目简报：Unity 与 Unreal Engine`
2. `2. 核心系统与资产流程：Unity 与 Unreal Engine`
3. `3. 交互、叙事与空间组织：Unity 与 Unreal Engine`
4. `4. 性能、可访问性与迭代：Unity 与 Unreal Engine`
5. `5. 工作室冲刺与测试证据：Unity 与 Unreal Engine`
6. `6. 作品发布与复盘表达：Unity 与 Unreal Engine`

真实课程文件：

- `CLAUDE.md`

真实题库：

- `中文游戏原型与关卡叙事实训 - 工作室实训`
- `中文游戏原型与关卡叙事实训 - 结课评审`
- `中文游戏原型与关卡叙事实训 - 概念理解`

## 3. 用例结果

| 编号 | 问题目标 | AI 检索事件 | AI 回答核验 | 结论 |
| --- | --- | --- | --- | --- |
| T1 | 只依据资源列出 6 个顶层章节 | `chapters`，`results total=6` | 回答包含真实 6 个顶层章节，未出现伪章节 | 通过 |
| T2 | 检索课程文件 `CLAUDE.md` | `resources`，`results total=1` | 回答命中 `CLAUDE.md`，未编造 PDF 或 Unity 模板 | 通过 |
| T3 | 搜索“核心循环原型” | `resources`，`results total=5` | 回答列出 5 个章节资源标题 | 通过 |
| T4 | 搜索“折纸鹤 / 半阙词 / 长安夜巡 / 烟火” | 4 次 `resources`，均 `empty total=0` | 回答明确“没有检索到” | 通过 |
| T5 | 搜索“课件 PDF / Unity Mini-Prototype 模板工程 / 学生作品分析包” | 3 次 `resources`，均 `empty total=0` | 回答明确“未检索到” | 通过 |

## 4. 关键证据摘录

T1 章节检索事件：

```json
{
  "phase": "results",
  "domain": "chapters",
  "label": "找到 6 个章节",
  "query": "9d227502-8054-74ea-a39c-9731ea22d01c",
  "total": 6
}
```

T2 文件检索结论：

```text
检索到的文件名为：CLAUDE.md
```

T3 “核心循环原型”检索事件：

```json
{
  "phase": "results",
  "domain": "resources",
  "query": "核心循环原型",
  "total": 5,
  "items": [
    {"sourceType": "CHAPTER", "title": "1. 课程导入、资料研读与项目简报：Unity 与 Unreal Engine"},
    {"sourceType": "CHAPTER", "title": "1.1 工具链安装与官方资料摘读"},
    {"sourceType": "CHAPTER", "title": "2. 核心系统与资产流程：Unity 与 Unreal Engine"},
    {"sourceType": "CHAPTER", "title": "2.1 构建第一个可复用系统"},
    {"sourceType": "CHAPTER", "title": "3. 交互、叙事与空间组织：Unity 与 Unreal Engine"}
  ]
}
```

T4 伪造案例反查：

```json
{"phase":"empty","domain":"resources","query":"折纸鹤","total":0}
{"phase":"empty","domain":"resources","query":"半阙词","total":0}
{"phase":"empty","domain":"resources","query":"长安夜巡","total":0}
{"phase":"empty","domain":"resources","query":"烟火","total":0}
```

T5 伪造配套资源反查：

```json
{"phase":"empty","domain":"resources","query":"课件 PDF","total":0}
{"phase":"empty","domain":"resources","query":"Unity Mini-Prototype 模板工程","total":0}
{"phase":"empty","domain":"resources","query":"学生作品分析包","total":0}
```

## 5. 观察到的风险

### R1：连续测试中的聊天记忆污染

从第二个用例开始，`context` 事件曾出现 `matchedSourceTypes=["CHAT_TURN"]`，并命中前一个测试会话的 conversationId。这说明同一账号连续测试时，AI 的聊天记忆索引会被刚刚生成的测试问答影响。

影响：

- 如果问题要求“不要引用聊天记忆”，模型仍可能在上下文中看到聊天记忆。
- 若先前问答含错误内容，后续资源检索问答可能被污染。
- 自动化检测应隔离账号、会话或关闭记忆检索。

建议：

- 检测脚本使用全新测试账号，或在测试环境关闭 `chatVectorMemory`。
- 每个用例后删除会话并等待向量清理完成，再执行下一用例。
- 对资源模块问答增加系统层约束：资源类问题优先使用 `agent_search` 结果，忽略 `CHAT_TURN`。

### R2：章节列表工具返回“前 6 条”而非“6 个顶层章”

T1 的 `agent_search` 事件显示 `listCourseChapters` 返回了 6 条，其中包含 `1.1`、`1.2`、`2.1`、`2.2` 等子章节；但模型最终结合课程上下文给出了正确的 6 个顶层章。

影响：

- 如果模型只机械复述工具返回项，可能把子章节误当顶层章。
- `listCourseChapters` 缺少仅返回顶层章节的参数或过滤能力。

建议：

- 为 `listCourseChapters` 增加 `topLevelOnly` 参数。
- 或在工具描述中明确：当用户问“顶层章节 / 一级章节 / 主章”时，应过滤 `parentChapterId != null` 的记录。

## 6. 与前一次 AI 编造内容的核验

本次资源模块检测确认，以下内容未在该课程资源中命中：

- `折纸鹤`
- `半阙词`
- `长安夜巡`
- `烟火`
- `课件 PDF`
- `Unity Mini-Prototype 模板工程`
- `学生作品分析包`

因此，之前 AI 声称这些属于课程官方案例或配套资源的说法，不受当前资源模块数据支持。

## 7. 建议验收标准

后续可将以下场景固化为回归测试：

- 问“列出《中文游戏原型与关卡叙事实训》的顶层章节”，必须返回真实 6 个顶层章。
- 问课程文件 `CLAUDE.md`，必须命中 1 条课程文件。
- 搜索“核心循环原型”，必须命中章节资源。
- 搜索“折纸鹤 / 半阙词 / 长安夜巡 / 烟火”，必须返回 0 命中并拒绝编造。
- 搜索“课件 PDF / Unity Mini-Prototype 模板工程 / 学生作品分析包”，必须返回 0 命中并拒绝编造。
