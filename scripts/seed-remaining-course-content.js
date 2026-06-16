const courses = [
    {
        title: "Advanced Software Architecture",
        bankName: "Architecture Review Practice Bank",
        bankDesc: "Practice questions about architecture drivers, quality attributes, patterns, documentation, and evaluation.",
        tags: ["architecture", "quality-attributes", "patterns"],
        chapters: [
            {
                name: "Module 1: Architecture Drivers and Quality Attributes",
                description: "Identify the forces that shape architecture before drawing components.",
                content: "This module follows the emphasis found in CMU SEI architecture training and university architecture syllabi: architecture starts with drivers. Students separate constraints, functional requirements, and quality attribute requirements, then translate ambiguous goals like fast, secure, reliable, or easy to change into measurable scenarios. The lab asks each team to write quality attribute scenarios for availability, modifiability, performance, security, and usability before proposing any structure.",
            },
            {
                name: "Module 2: Styles, Patterns, and Architectural Tactics",
                description: "Compare architectural styles and the tactics that support system qualities.",
                content: "Students study layered architecture, modular monoliths, hexagonal architecture, event-driven systems, service-oriented designs, and microservices. The goal is not to memorize pattern names, but to explain when a style improves a quality attribute and when it creates operational cost. Studio work asks students to map a legacy learning platform into candidate styles and justify decisions using coupling, latency, deployability, team ownership, and data consistency tradeoffs.",
            },
            {
                name: "Module 3: Documentation, ADRs, and Interface Contracts",
                description: "Document decisions so future teams can understand and evaluate the system.",
                content: "This module covers context diagrams, container/component views, sequence views for critical flows, data ownership diagrams, architecture decision records, and API/event contracts. Students practice writing concise ADRs that capture context, decision, alternatives, consequences, and rollback signals. The assignment is to document one system slice deeply enough that another team can implement a compatible service without asking for hidden tribal knowledge.",
            },
            {
                name: "Module 4: Architecture Evaluation and Evolution",
                description: "Evaluate architecture risks and plan controlled evolution.",
                content: "Students run a lightweight architecture review inspired by ATAM-style thinking: identify scenarios, trace them through the design, expose risks, and propose mitigations. Topics include technical debt, fitness functions, evolutionary architecture, observability as an architectural concern, and migration planning. The final review requires teams to defend one architecture change, name affected quality attributes, and describe how they would verify the change in production.",
            },
        ],
        posts: [
            ["How do we choose between modular monolith and microservices?", "Our team can separate the domain into services, but deployment and observability would become harder. What criteria should we use before splitting?"],
            ["Quality attribute scenario wording", "Is uptime target enough for availability, or should we describe stimulus, environment, response, and response measure?"],
            ["ADR scope question", "Should an ADR cover a whole architecture style decision, or can it document a smaller API contract decision?"],
        ],
        questions: {
            mcq: ["Which item is a quality attribute scenario?", "Choose the most architecture-relevant statement.", ["The system uses PostgreSQL.", "During peak registration, checkout p95 latency stays below 300 ms.", "The login button is blue.", "The team has five developers."], 1, "A quality attribute scenario includes a condition and measurable response."],
            tf: ["A pattern should be selected before quality attribute tradeoffs are known.", "Decide whether this statement is true or false.", false, "Patterns are useful only when evaluated against drivers and tradeoffs."],
            fill: ["Name one common artifact for recording architecture decisions.", "Provide a short name.", "Architecture Decision Record (ADR)", "ADRs capture context, decision, alternatives, and consequences."],
            essay: ["Explain one risk of moving too early to microservices.", "Mention both technical and team/operational impact.", "Early microservices can increase distributed transactions, deployment complexity, observability burden, and coordination cost before the domain boundaries are stable.", "A strong answer connects system quality tradeoffs with team operating cost."],
        },
    },
    {
        title: "Digital Product Strategy",
        bankName: "Product Strategy Practice Bank",
        bankDesc: "Practice questions about discovery, prioritization, roadmaps, analytics, and go-to-market strategy.",
        tags: ["product", "roadmap", "analytics"],
        chapters: [
            {
                name: "Module 1: Product Vision, Customer Problem, and Strategy",
                description: "Connect product vision to a specific customer problem and measurable outcome.",
                content: "Drawing on product strategy and roadmap courses from business schools and product organizations, this module starts with strategic clarity. Students define target users, jobs to be done, underserved needs, competitive alternatives, and business constraints. The workshop asks each team to write a product strategy brief that names the customer, the problem, the promise, the evidence, and the first outcome metric rather than a list of features.",
            },
            {
                name: "Module 2: Discovery, Prioritization, and Opportunity Sizing",
                description: "Use evidence to decide which problems deserve investment.",
                content: "Students combine qualitative discovery, funnel data, support tickets, sales feedback, and market signals. Prioritization frameworks are treated as decision aids, not automatic truth machines. The lab compares RICE, opportunity scoring, Kano thinking, and risk-first sequencing. Teams must show what evidence would change their decision and explain how they avoid prioritizing the loudest stakeholder over the highest-value customer problem.",
            },
            {
                name: "Module 3: Roadmaps, Experiments, and Product Analytics",
                description: "Translate strategy into a roadmap that communicates direction and learning.",
                content: "Following roadmap guidance from sources such as Atlassian and eCornell-style product courses, students build outcome-oriented roadmaps. The chapter covers themes, bets, discovery milestones, delivery milestones, guardrail metrics, experiment design, cohort analysis, retention, activation, and instrumentation plans. The deliverable is a roadmap that explains why now, what risk is being reduced, and how the team will know whether to continue, pivot, or stop.",
            },
            {
                name: "Module 4: Go-to-Market, Lifecycle, and Portfolio Tradeoffs",
                description: "Plan launch, adoption, growth, and product sunset decisions.",
                content: "The final module connects product strategy to go-to-market execution. Students plan positioning, pricing assumptions, launch channels, adoption loops, onboarding, customer success feedback, and portfolio balance. They also discuss mature-product decisions: managing technical debt, declining usage, bundling, compliance changes, and sunset communication. The final project asks teams to defend a product bet with discovery evidence, roadmap logic, analytics, and a launch narrative.",
            },
        ],
        posts: [
            ["Feature roadmap or outcome roadmap?", "Our roadmap currently lists features by quarter. How can we make it more strategic without losing delivery clarity?"],
            ["Choosing the first north-star metric", "For a learning product, should we optimize weekly active users, completion rate, or successful learning outcomes first?"],
            ["Experiment guardrails", "If an onboarding experiment improves activation but increases support tickets, how should we judge it?"],
        ],
        questions: {
            mcq: ["What should an outcome-oriented roadmap emphasize?", "Choose the best answer.", ["Only release dates", "Strategic themes, customer outcomes, and learning milestones", "The org chart", "A list of every stakeholder request"], 1, "A strategic roadmap communicates direction, priorities, and progress toward outcomes."],
            tf: ["Prioritization frameworks replace product judgment.", "Decide whether this statement is true or false.", false, "Frameworks structure tradeoffs, but judgment and evidence still matter."],
            fill: ["Name one evidence source for product discovery.", "Provide one source.", "Customer interviews, usage analytics, support tickets, sales feedback, or market research", "Discovery should combine qualitative and quantitative signals."],
            essay: ["Explain how analytics can change a product strategy decision.", "Use a concrete example.", "Analytics can reveal that a target segment is not activating, that retention differs by use case, or that a feature improves engagement while hurting support load, leading the team to narrow, pivot, or redesign the bet.", "A strong answer links metric evidence to a strategic decision."],
        },
    },
    {
        title: "Distributed Database Systems",
        bankName: "Distributed Database Practice Bank",
        bankDesc: "Practice questions about partitioning, replication, transactions, consistency, and failure recovery.",
        tags: ["distributed-database", "consistency", "replication"],
        chapters: [
            {
                name: "Module 1: Data Distribution, Sharding, and Query Routing",
                description: "Understand why data placement is a first-class database design problem.",
                content: "This module introduces the difference between distributed systems and distributed databases, then studies partitioning strategies, replication groups, placement metadata, query routing, and cross-partition query cost. Students compare hash sharding, range sharding, directory-based routing, and tenant-aware placement. The lab asks students to design a shard key for a learning platform and explain hot partitions, rebalancing, secondary indexes, and operational limits.",
            },
            {
                name: "Module 2: Transactions, Concurrency Control, and Isolation",
                description: "Study how distributed databases preserve correctness under concurrent access.",
                content: "Students review ACID properties, serializability, isolation levels, locking, timestamp ordering, optimistic concurrency control, and multi-version concurrency control. The distributed extension focuses on two-phase commit, coordinator failure, participant recovery, and why distributed transactions are expensive. Exercises ask students to reason about interleavings, anomalies, deadlocks, and when application-level sagas are safer than global transactions.",
            },
            {
                name: "Module 3: Replication, Consensus, and Consistency Models",
                description: "Compare strong consistency, eventual consistency, quorum reads/writes, and consensus.",
                content: "Building from common distributed database course topics, this chapter covers leader-follower replication, multi-leader replication, quorum protocols, read repair, hinted handoff, Raft/Paxos intuition, linearizability, causal consistency, and monotonic reads. Students analyze CAP tradeoffs during network partitions and map user-facing requirements to consistency choices. The lab simulates stale reads and asks teams to explain when they are acceptable.",
            },
            {
                name: "Module 4: Failure Recovery, Observability, and Operations",
                description: "Operate a distributed database during crashes, partitions, and growth.",
                content: "The final module covers logs, checkpoints, snapshots, failover, backup and restore, schema migration, capacity planning, latency budgets, and observability. Students learn that the hardest database bugs often appear during partial failure, not steady state. The final assignment is an incident review: given a split-brain or overloaded shard scenario, students identify symptoms, likely causes, immediate mitigation, long-term design fixes, and user communication.",
            },
        ],
        posts: [
            ["Shard key tradeoff", "If course_id is the shard key, enrollment queries are easy, but cross-course analytics are harder. Is that acceptable?"],
            ["When is eventual consistency okay?", "For course discussion counts, can we accept stale values if the post itself is strongly written?"],
            ["Two-phase commit failure case", "What should a participant do if it voted yes but cannot reach the coordinator afterward?"],
        ],
        questions: {
            mcq: ["Which property does linearizability provide?", "Choose the best answer.", ["Reads may return any old value", "Operations appear to occur atomically in real-time order", "All replicas are always available", "Indexes are automatically balanced"], 1, "Linearizability gives a strong single-copy illusion with real-time ordering."],
            tf: ["Two-phase commit can block if the coordinator fails at the wrong time.", "Decide whether this statement is true or false.", true, "Participants may be uncertain after voting yes until the coordinator decision is recovered."],
            fill: ["Name one common data partitioning strategy.", "Provide one strategy.", "Hash sharding, range sharding, or directory-based partitioning", "Partitioning controls placement and routing."],
            essay: ["Explain one tradeoff between strong and eventual consistency.", "Mention user impact and operational impact.", "Strong consistency simplifies user reasoning but may reduce availability or add latency during partitions; eventual consistency can improve availability and performance but requires conflict handling and user-visible tolerance for stale data.", "A strong answer connects the consistency model to product behavior."],
        },
    },
    {
        title: "Human-Computer Interaction",
        bankName: "HCI Studio Practice Bank",
        bankDesc: "Practice questions about user research, prototyping, usability testing, accessibility, and evaluation.",
        tags: ["hci", "usability", "prototyping"],
        chapters: [
            {
                name: "Module 1: User Research and Task Analysis",
                description: "Understand users, contexts, goals, and workflows before designing screens.",
                content: "This module follows common HCI syllabi that begin with user-centered design. Students learn interviews, contextual inquiry, observation, surveys, personas, scenarios, journey maps, and task analysis. The studio assignment asks students to study a real workflow, separate user goals from system features, and identify breakdowns caused by cognitive load, unclear feedback, access barriers, or mismatched mental models.",
            },
            {
                name: "Module 2: Interaction Design and Prototyping",
                description: "Move from sketches to testable interaction flows.",
                content: "Students practice information architecture, navigation models, form design, feedback loops, affordances, constraints, error prevention, and progressive disclosure. Prototypes move from paper sketches to low-fidelity wireframes and clickable flows. The chapter emphasizes that fidelity should match the learning goal: early prototypes test concepts and workflow; later prototypes test visual hierarchy, motion, edge cases, and real content.",
            },
            {
                name: "Module 3: Usability Evaluation and Iteration",
                description: "Use structured evaluation to improve interactive systems.",
                content: "Drawing from HCI evaluation literature, students compare heuristic evaluation, cognitive walkthroughs, think-aloud testing, task success metrics, time-on-task, error rate, satisfaction ratings, and qualitative coding. The lab asks students to run a five-participant usability test, synthesize findings by severity and confidence, and redesign one flow based on observed behavior rather than preference votes.",
            },
            {
                name: "Module 4: Accessibility, Ethics, and Socio-Technical Systems",
                description: "Design interfaces that work across abilities, contexts, and organizational constraints.",
                content: "The final module covers accessibility standards, inclusive design, localization, privacy, persuasive design ethics, dark patterns, and socio-technical effects. Students evaluate interfaces for keyboard access, color contrast, readable hierarchy, error recovery, and assistive technology compatibility. The final project requires a prototype, a usability report, and a reflection on who benefits, who is excluded, and how the design changes user behavior.",
            },
        ],
        posts: [
            ["How much fidelity for first usability test?", "Should our first test use a clickable Figma prototype or is a paper flow enough?"],
            ["Task success metric definition", "For a settings page, should success mean reaching the page or completing the preference change without help?"],
            ["Accessibility review scope", "Which checks should be mandatory before presenting the final prototype?"],
        ],
        questions: {
            mcq: ["What is the main purpose of a think-aloud usability test?", "Choose the best answer.", ["To benchmark server latency", "To hear user reasoning while they attempt tasks", "To generate production code", "To replace accessibility testing"], 1, "Think-aloud testing reveals user reasoning, confusion, and expectations."],
            tf: ["High-fidelity prototypes are always better for early discovery.", "Decide whether this statement is true or false.", false, "Low-fidelity prototypes can test concepts faster and reduce attachment to visual details."],
            fill: ["Name one usability metric.", "Provide one metric.", "Task success rate, time on task, error rate, or satisfaction rating", "Usability metrics should connect to a task."],
            essay: ["Explain why accessibility is part of HCI rather than a final polish step.", "Mention design process impact.", "Accessibility affects navigation, content structure, interaction patterns, feedback, and error recovery from the start; treating it as polish often leaves core workflows unusable for some users.", "A strong answer connects inclusion to interaction design decisions."],
        },
    },
    {
        title: "云原生微服务实践",
        bankName: "云原生微服务实践题库",
        bankDesc: "覆盖微服务边界、Kubernetes、可观测性、交付流水线和云原生安全的练习。",
        tags: ["cloud-native", "kubernetes", "microservices"],
        chapters: [
            {
                name: "第1章 微服务边界、容器化与十二要素应用",
                description: "理解云原生应用为什么先从边界、配置和运行方式开始。",
                content: "参考 CNCF 和 Kubernetes 生态的训练主题，本章从单体拆分、领域边界、服务契约和容器化开始。学生需要区分业务能力边界和技术分层边界，理解配置外置、无状态服务、健康检查、镜像构建和运行时隔离。实验会把一个简单课程服务容器化，写出 Dockerfile、环境变量配置、健康端点和本地依赖编排，并讨论过早拆分服务带来的事务、日志和部署复杂度。",
            },
            {
                name: "第2章 Kubernetes 工作负载、服务发现与配置管理",
                description: "掌握 Kubernetes 中应用部署、暴露和配置的核心对象。",
                content: "本章围绕 Kubernetes 官方基础概念展开：Pod、Deployment、ReplicaSet、Service、Ingress、ConfigMap、Secret、Namespace 和资源限制。学生需要理解声明式配置、滚动更新、探针、标签选择器和服务发现机制。实验要求学生部署一个多副本 API 服务，配置 readiness/liveness probe，模拟版本升级和回滚，并解释为什么 Kubernetes 解决的是编排问题而不是自动消除应用设计问题。",
            },
            {
                name: "第3章 可观测性、弹性与服务间通信",
                description: "用指标、日志和追踪理解分布式系统的运行状态。",
                content: "云原生系统的复杂度主要出现在运行期。本章介绍指标、结构化日志、分布式追踪、SLO、错误预算、超时、重试、熔断、限流、幂等和降级。学生会分析一次跨服务请求链路，定位延迟来源和错误传播路径。实验中需要为课程服务添加请求指标和 trace id，设计合理的超时与重试策略，并说明为什么无边界重试会放大故障。",
            },
            {
                name: "第4章 CI/CD、GitOps 与云原生安全",
                description: "把构建、部署、回滚和安全扫描纳入日常交付。",
                content: "最后一章覆盖镜像仓库、流水线、环境晋级、GitOps、灰度发布、供应链安全、镜像扫描、最小权限和密钥管理。学生需要设计一条从提交到部署的流水线，并说明如何在失败时回滚。课程项目要求团队交付一个可重复部署的微服务应用，包含 Kubernetes 清单、部署文档、可观测性面板、安全注意事项和一次故障演练记录。",
            },
        ],
        posts: [
            ["微服务拆分粒度怎么判断？", "如果一个课程系统里课程、章节、题库高度相关，是否应该拆成三个服务？"],
            ["Kubernetes 探针配置疑问", "readiness probe 和 liveness probe 的失败后果有什么区别？"],
            ["重试策略会不会造成雪崩？", "调用下游服务失败时，客户端重试次数应该怎么控制？"],
        ],
        questions: {
            mcq: ["Kubernetes Service 主要解决什么问题？", "选择最合适的一项。", ["镜像构建", "稳定访问一组 Pod", "数据库事务", "代码格式化"], 1, "Service 为动态变化的 Pod 集合提供稳定访问入口。"],
            tf: ["微服务越多，系统一定越容易维护。", "判断该说法是否正确。", false, "服务数量增加会带来部署、观测、数据一致性和团队协作成本。"],
            fill: ["写出一种常见的云原生可观测性信号。", "填写一个名称。", "指标、日志或分布式追踪", "三类信号经常一起用于定位生产问题。"],
            essay: ["解释为什么需要 readiness probe。", "结合滚动发布说明。", "readiness probe 表示实例是否准备好接流量；滚动发布时，未准备好的 Pod 不应进入负载均衡，避免把用户请求发送到尚未初始化完成的实例。", "答案应说明探针和流量接入之间的关系。"],
        },
    },
    {
        title: "教育技术与学习分析",
        bankName: "教育技术与学习分析题库",
        bankDesc: "覆盖学习分析定义、教育数据、预测模型、干预设计和伦理治理。",
        tags: ["learning-analytics", "education", "ethics"],
        chapters: [
            {
                name: "第1章 学习分析基础与教育技术场景",
                description: "理解学习分析如何支持教学改进，而不只是生成报表。",
                content: "参考 SoLAR 对学习分析的定义和密歇根大学学习分析课程的应用取向，本章介绍学习分析的目标：收集、分析、解释和沟通学习者及其情境数据，以改善学习和教学。学生会比较 LMS 行为数据、作业提交、测验成绩、讨论互动、视频观看和问卷数据的意义与局限。课堂案例要求把一个教学问题转化为可分析的问题、指标和行动建议。",
            },
            {
                name: "第2章 教育数据采集、清洗与隐私治理",
                description: "建立可信的数据基础和合规边界。",
                content: "本章讨论教育数据的来源、粒度、缺失值、时间戳、身份映射、课程结构和数据质量。学生需要识别日志数据中的偏差：未记录的线下学习、设备差异、教师策略变化和平台改版。隐私部分覆盖知情同意、最小化采集、去标识化、访问控制和数据保留。实验会让学生清洗一份模拟 LMS 数据，并写出数据字典和伦理风险说明。",
            },
            {
                name: "第3章 学习行为建模、预警与解释",
                description: "用模型发现风险，但避免把模型当作最终答案。",
                content: "学生学习描述性分析、聚类、序列分析、预测模型、早期预警和模型解释。重点是把模型输出和教学行动连接起来：预测低完成率之后，教师应该采取什么干预，学生看到什么反馈，系统如何避免标签化。实验要求训练一个简单风险模型，比较准确率、召回率、误报成本和解释特征，并讨论不同阈值对学生体验的影响。",
            },
            {
                name: "第4章 干预设计、公平性评估与持续改进",
                description: "从分析结果走向负责任的教学行动。",
                content: "最后一章关注干预设计和评估。学生需要把仪表盘、提醒、个性化资源推荐、教师看板和同伴支持机制设计成可测试的干预。课程讨论公平性、可解释性、教师工作负担、学生自主性和反馈疲劳。最终项目要求团队提出一个学习分析方案，说明目标人群、数据指标、模型或规则、干预流程、风险控制和效果评估方法。",
            },
        ],
        posts: [
            ["学习分析和普通成绩统计有什么区别？", "如果只是统计平均分和通过率，算不算学习分析？"],
            ["预警模型误报怎么办？", "如果系统把努力学习但暂时成绩低的学生标记为高风险，干预会不会造成压力？"],
            ["仪表盘应该给学生看哪些指标？", "学生端看板应该强调排名、进度还是下一步行动建议？"],
        ],
        questions: {
            mcq: ["学习分析最核心的目标是什么？", "选择最合适的一项。", ["替代教师评分", "理解并改进学习和教学", "隐藏学生数据", "只做期末排名"], 1, "学习分析应服务于可行动的教学改进。"],
            tf: ["学习平台日志可以完整代表学生的全部学习过程。", "判断该说法是否正确。", false, "日志只能记录平台内行为，不能完整覆盖线下学习和动机变化。"],
            fill: ["写出一种学习分析常用数据来源。", "填写一个来源。", "LMS 点击日志、作业提交、测验成绩、讨论互动或问卷", "学习分析通常整合多种教育数据。"],
            essay: ["解释早期预警模型为什么需要人工干预流程。", "说明模型和教学行动的关系。", "模型只能提示风险概率，教师或辅导人员需要结合背景信息判断干预方式，避免误报、标签化和不必要压力。", "答案应体现负责任使用模型。"],
        },
    },
    {
        title: "数据可视化设计",
        bankName: "数据可视化设计题库",
        bankDesc: "覆盖视觉编码、图表选择、交互设计、叙事表达和可视化评估。",
        tags: ["data-visualization", "design", "analytics"],
        chapters: [
            {
                name: "第1章 可视化目标、数据类型与视觉编码",
                description: "从分析目标和数据结构出发选择视觉表达。",
                content: "参考 Columbia 数据可视化课程和常见数据可视化教学大纲，本章介绍可视化为什么能帮助理解复杂数据。学生学习定量、分类、时间序列、层级、网络和地理数据的差异，以及位置、长度、角度、面积、颜色、形状和文本等视觉通道的表达能力。练习要求学生把同一份数据分别设计成探索型图表和沟通型图表，并解释编码选择。",
            },
            {
                name: "第2章 图表选择、设计原则与误导风险",
                description: "学习选择合适图表并避免常见可视化误导。",
                content: "本章覆盖柱状图、折线图、散点图、热力图、箱线图、直方图、堆叠图、地图和小 multiples。学生会讨论比例轴、截断轴、颜色尺度、排序、过度装饰、面积编码和样本偏差造成的误读。设计评审要求学生发现一张问题图表中的误导点，并重画成更清晰、更诚实的版本。",
            },
            {
                name: "第3章 交互式仪表盘与分析流程",
                description: "用交互支持探索，而不是堆满控件。",
                content: "学生学习过滤、联动、高亮、缩放、详情查看、注释、状态保持和渐进披露。章节强调仪表盘要围绕用户任务设计：监控、诊断、比较、追踪和决策支持。实验要求学生设计一个课程运营仪表盘，展示报名、完成率、讨论活跃度和风险学生趋势，并说明每个交互如何帮助用户回答具体问题。",
            },
            {
                name: "第4章 数据叙事、可访问性与评估",
                description: "把分析发现转化为可信、可读、可行动的表达。",
                content: "最后一章讨论叙事结构、标题和注释、视觉层级、颜色可访问性、移动端适配、导出报告和受众差异。学生需要区分探索型作品和解释型作品：前者帮助发现问题，后者帮助他人理解结论。最终项目包括一组可视化图表、设计说明、数据来源说明和可用性评估，要求展示从问题、数据、编码到结论的完整链路。",
            },
        ],
        posts: [
            ["什么时候应该用散点图？", "如果我想比较两个连续变量，还需要加趋势线吗？"],
            ["仪表盘筛选器太多怎么办？", "课程运营看板里有很多维度，是否应该全部放成筛选器？"],
            ["颜色方案如何兼顾美观和可读性？", "分类颜色和连续颜色尺度应该怎么区分？"],
        ],
        questions: {
            mcq: ["比较两个连续变量通常优先使用哪种图表？", "选择最合适的一项。", ["饼图", "散点图", "词云", "甘特图"], 1, "散点图适合观察两个连续变量的关系。"],
            tf: ["可视化设计只需要好看，不需要考虑任务和数据类型。", "判断该说法是否正确。", false, "图表必须服务于数据结构、分析目标和受众任务。"],
            fill: ["写出一种常见视觉编码通道。", "填写一个名称。", "位置、长度、颜色、面积、形状或文本", "视觉通道决定数据如何被感知。"],
            essay: ["解释为什么截断坐标轴可能误导读者。", "结合柱状图说明。", "截断坐标轴会夸大差异，让较小变化看起来像巨大变化，尤其在柱状图中会破坏长度与数值大小之间的直观对应。", "答案应说明视觉比例和误读风险。"],
        },
    },
    {
        title: "网络安全攻防基础",
        bankName: "网络安全攻防基础题库",
        bankDesc: "覆盖安全基础、Web 攻防、检测响应、防御加固和伦理边界。",
        tags: ["cybersecurity", "owasp", "nist"],
        chapters: [
            {
                name: "第1章 安全目标、风险管理与威胁建模",
                description: "建立攻防学习的合法边界和风险思维。",
                content: "参考 NIST Cybersecurity Framework 的风险管理思路，本章介绍机密性、完整性、可用性、身份认证、授权、审计和不可抵赖。学生学习资产、威胁、漏洞、影响、可能性和控制措施之间的关系，并使用 STRIDE 或类似方法做威胁建模。所有实验都限定在授权靶场和本地环境中，强调日志留存、最小权限和负责任披露。",
            },
            {
                name: "第2章 Web 应用常见漏洞与攻击路径",
                description: "理解 OWASP Top 10 风险背后的根因和利用条件。",
                content: "本章围绕注入、认证失效、访问控制缺陷、加密失败、配置错误、易受攻击组件、日志监控不足和服务端请求伪造等主题展开。学生不是追求攻击脚本数量，而是分析漏洞出现的输入边界、信任假设和权限模型。实验会在靶场中复现 SQL 注入和越权访问，并要求写出修复方案和验证测试。",
            },
            {
                name: "第3章 防御加固、检测与事件响应",
                description: "从攻击链反推防御控制和监控信号。",
                content: "学生学习安全编码、输入验证、输出编码、密码存储、会话管理、MFA、补丁管理、网络分段、WAF、日志设计、SIEM 基础和告警分级。事件响应部分覆盖准备、识别、遏制、根除、恢复和复盘。实验要求根据一段异常访问日志判断攻击路径，写出短期遏制动作和长期修复措施。",
            },
            {
                name: "第4章 红蓝对抗演练与安全工程化",
                description: "把攻防知识转化为可持续的安全工程实践。",
                content: "最后一章组织小型红蓝对抗：红队在授权范围内验证漏洞，蓝队部署检测、加固和响应流程。课程还讨论安全需求、威胁建模嵌入开发流程、依赖扫描、密钥管理、CI 安全检查和安全验收标准。最终报告要求学生同时说明攻击链、证据、影响范围、修复补丁、回归测试和伦理边界。",
            },
        ],
        posts: [
            ["靶场实验和真实系统边界", "如果在本地复现漏洞，报告里应该如何说明授权范围？"],
            ["越权漏洞怎么验证修复？", "只隐藏按钮够不够，还是必须在后端重新校验权限？"],
            ["安全日志应该记录什么？", "登录失败、敏感操作和异常请求是否都需要结构化记录？"],
        ],
        questions: {
            mcq: ["访问控制缺陷最应该在哪里修复？", "选择最合适的一项。", ["只在前端隐藏按钮", "在服务端强制校验权限", "改页面颜色", "删除日志"], 1, "权限必须在可信的服务端执行。"],
            tf: ["攻防实验可以在未授权的真实系统上进行。", "判断该说法是否正确。", false, "安全实验必须在授权范围内进行。"],
            fill: ["写出 NIST CSF 中一个常见安全活动类别。", "填写一个类别。", "识别、保护、检测、响应或恢复", "这些类别帮助组织管理网络安全风险。"],
            essay: ["解释为什么安全日志对事件响应重要。", "结合检测和复盘说明。", "安全日志能帮助识别攻击行为、还原时间线、确认影响范围、支持遏制和恢复，并为复盘改进控制措施提供证据。", "答案应体现日志和响应流程的关系。"],
        },
    },
];

function sqlText(value) {
    const text = String(value);
    let out = "U&'";
    for (const ch of text) {
        const cp = ch.codePointAt(0);
        if (ch === "'") {
            out += "''";
        } else if (ch === "\\") {
            out += "\\005C";
        } else if (cp < 128) {
            out += ch;
        } else if (cp <= 0xffff) {
            out += "\\" + cp.toString(16).toUpperCase().padStart(4, "0");
        } else {
            out += "\\+" + cp.toString(16).toUpperCase().padStart(6, "0");
        }
    }
    out += "'";
    return out;
}

function jsonb(value) {
    return `${sqlText(JSON.stringify(value))}::jsonb`;
}

function values(rows) {
    return rows.join(",\n");
}

const chapterRows = [];
const postRows = [];
const replyRows = [];
const bankRows = [];
const questionRows = [];
const optionRows = [];
const answerRows = [];

for (const course of courses) {
    course.chapters.forEach((chapter, index) => {
        chapterRows.push(`(${sqlText(course.title)}, ${index + 1}, ${sqlText(chapter.name)}, ${sqlText(chapter.description)}, ${sqlText(chapter.content)})`);
    });

    course.posts.forEach((post, index) => {
        postRows.push(`(${sqlText(course.title)}, ${index + 1}, ${sqlText(post[0])}, ${sqlText(post[1])}, ${jsonb(course.tags)}, ${index === 0 ? 1 : 0}, ${index === 1 ? 1 : 0})`);
        const reply = course.title.match(/[\u4e00-\u9fff]/)
            ? "这个问题可以结合本章的实验目标来看：先说明场景和约束，再给出可验证的判断标准。"
            : "A good way to approach this is to connect the question to the module objective, then state the tradeoff and how you would verify it.";
        replyRows.push(`(${sqlText(course.title)}, ${sqlText(post[0])}, 1, ${sqlText(reply)}, ${index === 0 ? 1 : 0})`);
    });

    bankRows.push(`(${sqlText(course.title)}, ${sqlText(course.bankName)}, ${sqlText(course.bankDesc)}, ${jsonb(course.tags)}, 2)`);

    const q = course.questions;
    questionRows.push(`(${sqlText(course.title)}, ${sqlText(course.bankName)}, 1, ${sqlText(q.mcq[0])}, ${sqlText(q.mcq[1])}, 0, 2, 5.00, 4, ${jsonb(course.tags)})`);
    q.mcq[2].forEach((option, optionIndex) => {
        const label = String.fromCharCode(65 + optionIndex);
        optionRows.push(`(${sqlText(course.title)}, ${sqlText(q.mcq[0])}, ${sqlText(label)}, ${sqlText(option)}, ${optionIndex === q.mcq[3] ? 1 : 0}, ${sqlText(optionIndex === q.mcq[3] ? q.mcq[4] : "This option does not best match the course concept.")})`);
    });

    questionRows.push(`(${sqlText(course.title)}, ${sqlText(course.bankName)}, 2, ${sqlText(q.tf[0])}, ${sqlText(q.tf[1])}, 2, 1, 4.00, 3, ${jsonb(course.tags)})`);
    optionRows.push(`(${sqlText(course.title)}, ${sqlText(q.tf[0])}, 'A', ${sqlText(course.title.match(/[\u4e00-\u9fff]/) ? "正确" : "True")}, ${q.tf[2] ? 1 : 0}, ${sqlText(q.tf[3])})`);
    optionRows.push(`(${sqlText(course.title)}, ${sqlText(q.tf[0])}, 'B', ${sqlText(course.title.match(/[\u4e00-\u9fff]/) ? "错误" : "False")}, ${q.tf[2] ? 0 : 1}, ${sqlText(q.tf[3])})`);

    questionRows.push(`(${sqlText(course.title)}, ${sqlText(course.bankName)}, 3, ${sqlText(q.fill[0])}, ${sqlText(q.fill[1])}, 3, 2, 6.00, 5, ${jsonb(course.tags)})`);
    answerRows.push(`(${sqlText(course.title)}, ${sqlText(q.fill[0])}, ${sqlText(q.fill[2])}, ${sqlText(q.fill[3])}, 6.00, 1)`);

    questionRows.push(`(${sqlText(course.title)}, ${sqlText(course.bankName)}, 4, ${sqlText(q.essay[0])}, ${sqlText(q.essay[1])}, 4, 2, 10.00, 8, ${jsonb(course.tags)})`);
    answerRows.push(`(${sqlText(course.title)}, ${sqlText(q.essay[0])}, ${sqlText(q.essay[2])}, ${sqlText(q.essay[3])}, 10.00, 1)`);
}

process.stdout.write(`
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION demo_uuidv7()
RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    ts_ms bigint := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
    b bytea := gen_random_bytes(16);
    hex text;
BEGIN
    b := set_byte(b, 0, ((ts_ms >> 40) & 255)::int);
    b := set_byte(b, 1, ((ts_ms >> 32) & 255)::int);
    b := set_byte(b, 2, ((ts_ms >> 24) & 255)::int);
    b := set_byte(b, 3, ((ts_ms >> 16) & 255)::int);
    b := set_byte(b, 4, ((ts_ms >> 8) & 255)::int);
    b := set_byte(b, 5, (ts_ms & 255)::int);
    b := set_byte(b, 6, (get_byte(b, 6) & 15) | 112);
    b := set_byte(b, 8, (get_byte(b, 8) & 63) | 128);
    hex := encode(b, 'hex');
    RETURN (
        substr(hex, 1, 8) || '-' ||
        substr(hex, 9, 4) || '-' ||
        substr(hex, 13, 4) || '-' ||
        substr(hex, 17, 4) || '-' ||
        substr(hex, 21, 12)
    )::uuid;
END;
$$;

WITH chapter_seed(course_title, sort_order, chapter_name, description, content) AS (
    VALUES
${values(chapterRows)}
)
INSERT INTO edu_chapter (
    id, course_id, teacher_id, chapter_name, description, content,
    attachment_urls, sort_order, status, view_count, like_count,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), c.id, c.teacher_id, s.chapter_name, s.description, s.content,
    '[]'::jsonb, s.sort_order, 1, 25 + s.sort_order * 9, 3 + s.sort_order,
    now(), now(), 0
FROM chapter_seed s
JOIN edu_course c ON c.title = s.course_title AND c.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_chapter existing
    WHERE existing.course_id = c.id
      AND existing.sort_order = s.sort_order
      AND existing.deleted = 0
);

WITH post_seed(course_title, post_no, title, content, tags, is_top, is_essence) AS (
    VALUES
${values(postRows)}
),
authors AS (
    SELECT c.id AS course_id, u.id AS user_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(u.id::text || c.id::text || 'remaining-posts')) AS rn
    FROM edu_course c
    JOIN auth_users u ON u.email LIKE 'student%@demo.edupivot.local'
    WHERE c.deleted = 0
),
chapter_pick AS (
    SELECT course_id, id AS chapter_id,
           row_number() OVER (PARTITION BY course_id ORDER BY sort_order) AS rn
    FROM edu_chapter
    WHERE deleted = 0
)
INSERT INTO edu_forum_post (
    id, course_id, sys_user_id, title, content, post_type, is_anonymous,
    attachment_urls, image_urls, tags, view_count, like_count, reply_count,
    share_count, is_top, is_essence, is_locked, status, chapter_id,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), c.id, a.user_id, p.title, p.content, 0, 0,
    '[]'::jsonb, '[]'::jsonb, p.tags, 40 + p.post_no * 11, 2 + p.post_no, 0,
    p.post_no % 2, p.is_top, p.is_essence, 0, 0, cp.chapter_id,
    now() - ((10 - p.post_no) || ' days')::interval,
    now(), 0
FROM post_seed p
JOIN edu_course c ON c.title = p.course_title AND c.deleted = 0
JOIN authors a ON a.course_id = c.id AND a.rn = p.post_no
LEFT JOIN chapter_pick cp ON cp.course_id = c.id AND cp.rn = p.post_no
WHERE NOT EXISTS (
    SELECT 1 FROM edu_forum_post existing
    WHERE existing.course_id = c.id
      AND existing.title = p.title
      AND existing.deleted = 0
);

WITH reply_seed(course_title, post_title, floor_number, content, accepted) AS (
    VALUES
${values(replyRows)}
),
reply_authors AS (
    SELECT c.id AS course_id, u.id AS user_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(u.id::text || c.id::text || 'remaining-replies')) AS rn
    FROM edu_course c
    JOIN auth_users u ON u.email LIKE 'student%@demo.edupivot.local'
    WHERE c.deleted = 0
)
INSERT INTO edu_forum_reply (
    id, post_id, course_id, sys_user_id, content, parent_reply_id,
    reply_to_user_id, is_anonymous, attachment_urls, image_urls,
    like_count, reply_count, is_accepted, floor_number, status,
    ip_address, user_agent, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), p.id, c.id, a.user_id, r.content, NULL,
    p.sys_user_id, 0, '[]'::jsonb, '[]'::jsonb,
    r.floor_number + 1, 0, r.accepted, r.floor_number, 0,
    '127.0.0.1', 'remaining-course-seed', now() - ((5 - r.floor_number) || ' days')::interval, now(), 0
FROM reply_seed r
JOIN edu_course c ON c.title = r.course_title AND c.deleted = 0
JOIN edu_forum_post p ON p.course_id = c.id AND p.title = r.post_title AND p.deleted = 0
JOIN reply_authors a ON a.course_id = c.id AND a.rn = r.floor_number + 6
WHERE NOT EXISTS (
    SELECT 1 FROM edu_forum_reply existing
    WHERE existing.post_id = p.id
      AND existing.content = r.content
      AND existing.deleted = 0
);

UPDATE edu_forum_post p
SET reply_count = counts.reply_count,
    last_reply_id = counts.last_reply_id,
    last_reply_time = counts.last_reply_time,
    last_reply_user_id = counts.last_reply_user_id,
    updated_at = now()
FROM (
    SELECT DISTINCT ON (r.post_id)
        r.post_id,
        count(*) OVER (PARTITION BY r.post_id) AS reply_count,
        r.id AS last_reply_id,
        r.created_at AS last_reply_time,
        r.sys_user_id AS last_reply_user_id
    FROM edu_forum_reply r
    WHERE r.deleted = 0
    ORDER BY r.post_id, r.created_at DESC
) counts
WHERE p.id = counts.post_id;

WITH bank_seed(course_title, bank_name, description, tags, difficulty) AS (
    VALUES
${values(bankRows)}
)
INSERT INTO edu_question_bank (
    id, course_id, sys_user_id, bank_name, description, bank_type,
    tags, difficulty, created_at, updated_at, deleted
)
SELECT demo_uuidv7(), c.id, c.teacher_id, b.bank_name, b.description, 0,
       b.tags, b.difficulty, now(), now(), 0
FROM bank_seed b
JOIN edu_course c ON c.title = b.course_title AND c.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_bank existing
    WHERE existing.course_id = c.id
      AND existing.bank_name = b.bank_name
      AND existing.deleted = 0
);

WITH question_seed(course_title, bank_name, q_no, title, content, q_type, difficulty, score, estimated_time, tags) AS (
    VALUES
${values(questionRows)}
)
INSERT INTO edu_question (
    id, question_bank_id, course_id, sys_user_id, question_title,
    question_content, question_type, difficulty, score, estimated_time,
    tags, image_urls, allow_partial_credit, view_count, status,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), qb.id, c.id, c.teacher_id, q.title,
    q.content, q.q_type, q.difficulty, q.score, q.estimated_time,
    q.tags, '[]'::jsonb, CASE WHEN q.q_type IN (3, 4) THEN 1 ELSE 0 END, 0, 1,
    now(), now(), 0
FROM question_seed q
JOIN edu_course c ON c.title = q.course_title AND c.deleted = 0
JOIN edu_question_bank qb ON qb.course_id = c.id AND qb.bank_name = q.bank_name AND qb.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question existing
    WHERE existing.question_bank_id = qb.id
      AND existing.question_title = q.title
      AND existing.deleted = 0
);

WITH option_seed(course_title, question_title, label, content, correct, explanation) AS (
    VALUES
${values(optionRows)}
)
INSERT INTO edu_question_option (
    id, question_id, course_id, option_content, option_label,
    is_correct, score, image_urls, explanation, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), q.id, q.course_id, o.content, o.label,
    o.correct, CASE WHEN o.correct = 1 THEN q.score ELSE 0 END,
    '[]'::jsonb, o.explanation, now(), now(), 0
FROM option_seed o
JOIN edu_course c ON c.title = o.course_title AND c.deleted = 0
JOIN edu_question q ON q.course_id = c.id AND q.question_title = o.question_title AND q.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_option existing
    WHERE existing.question_id = q.id
      AND existing.option_label = o.label
      AND existing.deleted = 0
);

WITH answer_seed(course_title, question_title, answer, explanation, score, sort_order) AS (
    VALUES
${values(answerRows)}
)
INSERT INTO edu_question_answer (
    id, question_id, course_id, answer_content, explanation,
    score, sort_order, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), q.id, q.course_id, a.answer, a.explanation,
    a.score, a.sort_order, now(), now(), 0
FROM answer_seed a
JOIN edu_course c ON c.title = a.course_title AND c.deleted = 0
JOIN edu_question q ON q.course_id = c.id AND q.question_title = a.question_title AND q.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_answer existing
    WHERE existing.question_id = q.id
      AND existing.sort_order = a.sort_order
      AND existing.deleted = 0
);
`);
