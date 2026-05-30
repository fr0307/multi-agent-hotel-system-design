# 项目说明：Multi-Agent（ADD 3.0）酒店定价系统架构设计

本项目实现了一个基于多 Agent 协作的 ADD 3.0（Attribute-Driven Design）架构设计流程。程序在命令行中按迭代执行多个节点（Orchestrator / HumanCheckpointBefore / Architect / Critic / Scribe / ContextCompactor / HumanCheckpointAfter / NextIteration），并将每轮的结构化设计产物写入 Markdown 报告与对话日志。

---

## 1. 项目结构与文件职责

### 1.1 顶层目录

- [pom.xml](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/pom.xml)：Maven 配置（JDK 17）。依赖包含 Spring AI、Spring AI Alibaba Graph（用于 StateGraph 工作流）、Jackson、SLF4J 等。
- [application.properties.example](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/application.properties.example)：配置样例（运行时实际读取 `application.properties` 或环境变量 / JVM 参数）。
- [run.sh](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/run.sh)：运行脚本（可按需改为本机可用的启动方式）。
- [2026SoftArch-assignment2_extracted.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/2026SoftArch-assignment2_extracted.md)：作业材料/题目提取文档（用于理解输入约束，不参与运行）。

### 1.2 Java 代码结构（src/main/java）

入口与装配

- [App.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/App.java)：程序入口。
  - 从 [AppConfig](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/config/AppConfig.java) 加载运行参数（迭代次数、输出目录、模型配置、是否 auto-approve 等）。
  - 选择 ChatModel：OpenAI 兼容（Spring AI OpenAIChatModel）/ DashScope（Qwen 系列优先，且关闭 thinking 以提高严格 JSON 成功率）/ Mock（必须显式开启）。
  - 创建 [MultiAgentEngine](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java) 并启动 `run()`。
- [AppConfig.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/config/AppConfig.java)：配置解析（优先级：`application.properties` > 环境变量 > JVM `-D`）。
  - `ma.iterations` / `MA_ITERATIONS`：迭代轮数，默认 4。
  - `ma.max-revisions` / `MA_MAX_REVISIONS`：每轮 Critic 驱动的最大返工次数，默认 2。
  - `ma.output-dir` / `MA_OUTPUT_DIR`：输出目录；为空则使用当前目录绝对路径。
  - `ma.auto-approve` / `MA_AUTO_APPROVE`：HumanCheckpoint 自动选择 approve（用于无人值守跑通）。
  - `ma.use-mock` / `MA_USE_MOCK`：允许在无 API Key 时使用 MockChatModel（默认 false，防止误把 mock 输出当真实设计）。
  - `ma.carryover-cap-bytes` / `MA_CARRYOVER_CAP_BYTES`：跨迭代 `prior_carryover` 的 UTF-8 字节上限。
  - `spring.ai.openai.*` / `APP_OPENAI_*`：OpenAI 兼容 BaseUrl / ApiKey / Model（默认模型名 `qwen3-32b`）。

多 Agent 工作流引擎

- [MultiAgentEngine.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java)：核心编排。
  - 使用 Alibaba Cloud AI Graph 的 `StateGraph` 定义节点与条件边（见 [buildGraph()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L87-L527)）。
  - 负责加载 prompts、向 LLM 发起“严格 JSON”调用（见 [callJson()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L730-L756)）、维护状态键、写入报告与对话日志。
  - Architect/Critic 输出采用 ADD 3.0（Step 3–7）结构化 schema，并在 Scribe 阶段聚合为 IterationResult。

日志与交互

- [ConsoleIO.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/io/ConsoleIO.java)：人类检查点输入（`approve/retry`），支持 `ma.auto-approve` 无阻塞运行。
- [MarkdownLogWriter.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/log/MarkdownLogWriter.java)：输出写入器。
  - `conversation_log.md`：逐 Turn 记录每个节点的输入 prompt、LLM 输出 JSON、token 使用。
  - `architecture_report.md`：按作业 Appendix 风格输出：ADD Step 1 + 每轮 Iteration（Step 2–7 的结构化小节）+ 交互成本分析 + 个人反思占位。

模型/数据结构

- [Turn.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/state/Turn.java)：一次节点执行的记录（ts/node/input/outputJson + token 统计）。
- [IterationResult.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/state/IterationResult.java)：一轮迭代的产物汇总（既保留 legacy digest 字段，也包含 ADD Step 3–7 的结构化字段、Critic 结构化检查、raw JSON appendix 等）。
- [state/model/*](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/state/model/)：结构化 schema（Alternative / ComponentSpec / InterfaceSpec / GoalCheck / DriverCheck / CriticIssue…）。
- [engine/model/*](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/model/)：历史遗留/兼容用途的数据结构（当前主流程以 state/model + IterationResult 为主）。

LLM 适配与 Mock

- [MockChatModel.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/ai/MockChatModel.java)：离线 mock，用于“流程能跑通”。注意：其输出 schema 仍偏旧（主要是 `design/diagram_code/decision_log`），引擎会用 fallback 勉强兼容，因此 mock 输出不可作为最终报告质量依据。
- [llm/*](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/llm)：早期自写 HTTP 客户端（当前主流程已改用 Spring AI `ChatClient`，该目录主要用于参考/回溯）。

### 1.3 Prompts（src/main/resources/prompts）

项目将每个 Agent 的 system/user prompt 外置为资源文件，运行时由 [PromptTemplates.load()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L1115-L1139) 加载，并使用简单的 `{{var}}` 模板替换渲染。

基础上下文

- [prior_knowledge.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/prior_knowledge.md)：唯一允许使用的领域知识/ADD 方法与 HPS（Hotel Pricing System）案例输入（用例、质量属性、约束等）。会被写入 `conversation_log.md` 的开头，同时被 Orchestrator/Architect/Critic 注入上下文。

Orchestrator（固定每轮目标）

- [orchestrator_system.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/orchestrator_system.md)：固定 4 轮 iteration goals（必须逐字匹配），输出 JSON：`iteration_goal/routing/decision_log`。
- [orchestrator_user.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/orchestrator_user.md)：注入 `prior_knowledge/compacted_history/iteration`，要求复制 system prompt 中的固定 goal 文本。

Architect（ADD 3.0 Step 3–7 结构化设计）

- [architect_system.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/architect_system.md)：要求 Architect 产出 ADD 3.0（Step 3–7）结构化 JSON（含 alternatives / components+interfaces / full+delta Mermaid / goal-check 等），并保留 `diagram_code` 作为兼容字段。
- [architect_user.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/architect_user.md)：注入 `prior_knowledge/compacted_history/iteration/goal/required_drivers/prior_carryover`，并可拼接 `critic_block`（用于返工）。
- [architect_critic_block.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/architect_critic_block.md)：当 Critic 不通过时，把 issues 作为必须修复项注入 Architect 下一次 user prompt。

Critic（按 required_drivers 做严格检查）

- [critic_system.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/critic_system.md)：输出结构化 JSON：`pass/driver_check/issues/decision_log`，issues 必须有 severity（BLOCK/WARN/NIT）并给出可粘贴的 concrete suggested_fix。
- [critic_user.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/critic_user.md)：注入 `prior_knowledge/compacted_history/required_drivers/prior_carryover` 以及 Architect 的 `design/diagram_code`，按 iteration 固定的 driver 分配表做检查。

ContextCompactor（压缩历史上下文）

- [context_compactor_system.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/context_compactor_system.md)：只允许“压缩”，禁止新增决策；要求保留 driver_id token、未解决 BLOCK/WARN、carryover 线索。
- [context_compactor_user.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/context_compactor_user.md)：把过去的 iteration results 列表传入，要求压缩到 <= 1500 chars；引擎侧还会再做硬截断以避免上下文失控。

Human Checkpoints（人工闸门）

- [human_checkpoint_before.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/human_checkpoint_before.md)：每轮开始前，展示 goal，要求输入 `approve/retry`（retry 回到 Orchestrator 重定本轮 goal）。
- [human_checkpoint_after.md](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/resources/prompts/human_checkpoint_after.md)：每轮结束后，展示 goal，要求输入 `approve/retry`（retry 会回到 Architect 重跑本轮设计与检查）。

---

## 2. 整体协作流程（多 Agent + 每轮执行细节）

### 2.1 Agent 角色（按节点）

所有 Agent 都通过 [callJson()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L730-L756) 调用 LLM，并约定输出为 JSON（解析失败时会用 `raw_text` 包装原始输出，避免流程中断）。

- Orchestrator：根据 iteration（1~4）生成本轮 `iteration_goal`（固定文本），并记录对话。
- HumanCheckpointBefore：在进入本轮设计前，人工决定 approve 或 retry；retry 会回到 Orchestrator 重新生成 goal。
- Architect：根据 `prior_knowledge + compacted_history + iteration_goal + required_drivers + prior_carryover` 输出 ADD 3.0 Step 3–7 结构化设计。
- Critic：对 Architect 产物做“本轮 required_drivers + prior_carryover”的严格检查；若存在 BLOCK 或 fail，则触发 Architect 返工（最多 `maxRevisions` 次）。
- Scribe：聚合 Architect/Critic 的结构化输出为 IterationResult，并写入 `architecture_report.md` 与 `conversation_log.md`。
- ContextCompactor：将所有历史迭代结果压缩为 `compacted_history`，用于下一轮 prompt，抑制上下文增长。
- HumanCheckpointAfter：本轮结束后人工确认；retry 会回到 Architect（同一 iteration goal 下重跑 Architect/Critic）。
- NextIteration：清理本轮易变状态，iteration++，进入下一轮或结束。

### 2.2 一轮 iteration 的状态机流程

主流程在 [buildGraph()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L87-L527) 中定义，整体可以概括为：

1. Orchestrator：生成 `iteration_goal`（固定 4 轮目标之一；缺失时回退到默认建议值）
2. HumanCheckpointBefore：
   - approve：进入 Architect
   - retry：回到 Orchestrator（重定本轮 goal）
3. Architect：输出 ADD 3.0 Step 3–7 的结构化字段（并保留 `design/diagram_code/decision_log` 兼容字段）
4. Critic：输出 `pass/driver_check/issues/decision_log`，并路由：
   - pass：进入 Scribe
   - revise：若 `revision_count < maxRevisions`，回到 Architect，且把 issues 注入 `architect_critic_block.md`
   - maxed：达到返工上限也会进入 Scribe（带着未解决 issues 定稿）
5. Scribe：写入 IterationResult，并构造 `prior_carryover`（未解决 issues + Step7 deferred 的 carryover_note）注入下一轮
6. ContextCompactor：压缩全部历史 IterationResult 为 `compacted_history`
7. HumanCheckpointAfter：
   - approve：进入 NextIteration
   - retry：回到 Architect（重跑本轮 Architect/Critic，不重定 goal）
8. NextIteration：
   - iteration <= cfg.iterations：回到 Orchestrator 开启下一轮
   - 否则：结束（END）

### 2.3 输出物（便于组内复盘）

运行后输出目录（`ma.output-dir`，默认当前目录）会包含：

- `conversation_log.md`：逐 Turn 记录“每个节点的输入 prompt + LLM 输出 JSON + token 使用”（最适合排查 prompt/解析/路由问题）。
- `architecture_report.md`：按 ADD Step 1 + 每轮 Step 2–7 结构化小节输出，同时包含 driver-check 表、issues 分桶、raw JSON appendix、交互成本分析等（最适合组内评审与写作）。

---

## 3. 代码阅读建议（从哪里开始）

- 从入口看整体：先读 [App.java](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/App.java) 的模型选择与 Engine 启动。
- 再看流程图：重点读 [buildGraph()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L87-L527) 的节点与条件边。
- 最后对照 prompts：把每个节点拼的 system/user prompt 与输出 JSON schema 对应到 `prompts/*.md`。

---

## 4. 已知实现细节与注意点（便于理解行为）

- iteration goal 的来源：引擎内置默认目标（用于建议/兜底，见 [defaultIterationGoal()](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L792-L800)），但正常情况下以 Orchestrator 的 JSON 输出 `iteration_goal` 为准（缺失时才回退）。
- Critic prompt 的上下文注入：当前实现会把 `prior_knowledge/compacted_history/required_drivers/prior_carryover` 注入 Critic 的 user prompt（见 [critic 节点](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L250-L305)）。
- HumanCheckpointAfter 的 retry：会回到 Architect 重跑本轮（同一 goal），并将 `revision_count` 归零（见 [human_checkpoint_after → architect](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L426-L526)）。
- prior_carryover：Scribe 会把未解决的（非 NIT）critic issues + Step7 中 verdict=deferred 的 carryover_note 汇总并注入下一轮，避免“上一轮遗留问题丢失”。
- ContextCompactor 长度控制：prompt 侧目标是 <= 1500 chars，但引擎还会额外硬截断，保证跨迭代 prompt envelope 不会爆炸（见 [compactor 节点](file:///d:/code/java/Software_Architecture2026/Multi-Agent-Hotel-System-Design/src/main/java/com/hotel/system/engine/MultiAgentEngine.java#L399-L424)）。

