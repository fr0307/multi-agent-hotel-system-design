package com.hotel.system.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.hotel.system.config.AppConfig;
import com.hotel.system.io.ConsoleIO;
import com.hotel.system.log.MarkdownLogWriter;
import com.hotel.system.state.IterationResult;
import com.hotel.system.state.Turn;
import com.hotel.system.state.model.Alternative;
import com.hotel.system.state.model.ComponentSpec;
import com.hotel.system.state.model.CriticIssue;
import com.hotel.system.state.model.DriverCheck;
import com.hotel.system.state.model.GoalCheck;
import com.hotel.system.state.model.InterfaceSpec;
import com.hotel.system.util.TimeUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public final class MultiAgentEngine {
    private final AppConfig cfg;
    private final ObjectMapper mapper;
    private final ChatClient chatClient;
    private final ConsoleIO io;
    private final MarkdownLogWriter writer;
    private final IterationStats stats = new IterationStats();
    private final List<IterationResult> results = new ArrayList<>();

    public MultiAgentEngine(AppConfig cfg, ObjectMapper mapper, ChatClient chatClient, ConsoleIO io, MarkdownLogWriter writer) {
        this.cfg = cfg;
        this.mapper = mapper;
        this.chatClient = chatClient;
        this.io = io;
        this.writer = writer;
    }

    /** Read-only view of completed iterations. Useful for App.java to finalize report. */
    public List<IterationResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public void run() throws Exception {
        Files.createDirectories(cfg.outputDir);
        writer.initFiles();

        PromptTemplates prompts = PromptTemplates.load();
        String priorKnowledge = prompts.priorKnowledge;
        writer.appendConversationHeader(priorKnowledge);
        writer.appendArchitectureHeader(cfg);
        writer.appendReportSectionOne(priorKnowledge, cfg);

        CompiledGraph graph = buildGraph(prompts).compile();
        graph.setMaxIterations(recommendedMaxIterations());
        RunnableConfig config = RunnableConfig.builder().threadId("multi-agent-hotel-design").build();
        graph.invoke(Map.of(), config);

        writer.appendInteractionCostTable(cfg, results);
        writer.appendReflectionPlaceholder();
    }

    private StateGraph buildGraph(PromptTemplates prompts) throws GraphStateException {
        String priorKnowledge = prompts.priorKnowledge;
        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();
            state.registerKeyAndStrategy("prior_knowledge", new ReplaceStrategy());
            state.registerKeyAndStrategy("compacted_history", new ReplaceStrategy());
            state.registerKeyAndStrategy("iteration", new ReplaceStrategy());
            state.registerKeyAndStrategy("iteration_goal", new ReplaceStrategy());
            state.registerKeyAndStrategy("revision_count", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_design", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_mermaid", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_decision_log", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_iteration_focus", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step3", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step4", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step5", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step6_full", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step6_delta", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_step7", new ReplaceStrategy());
            state.registerKeyAndStrategy("architect_raw_json", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_pass", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_issues", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_issues_struct", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_driver_check", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_decision_log", new ReplaceStrategy());
            state.registerKeyAndStrategy("critic_raw_json", new ReplaceStrategy());
            state.registerKeyAndStrategy("prior_carryover", new ReplaceStrategy());
            state.registerKeyAndStrategy("human_before", new ReplaceStrategy());
            state.registerKeyAndStrategy("human_after", new ReplaceStrategy());
            state.registerKeyAndStrategy("conversation", new AppendStrategy());
            state.registerKeyAndStrategy("results", new AppendStrategy());
            state.registerKeyAndStrategy("compactor_output", new ReplaceStrategy());

            return state.input(Map.of(
                    "prior_knowledge", priorKnowledge,
                    "compacted_history", "",
                    "iteration", 1,
                    "iteration_goal", "",
                    "revision_count", 0,
                    "critic_pass", false,
                    "critic_issues", List.of(),
                    "critic_decision_log", List.of(),
                    "architect_decision_log", List.of(),
                    "prior_carryover", ""
            ));
        };

        var orchestrator = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            String compactedHistory = stringValue(state, "compacted_history", "");

            String system = prompts.orchestratorSystem;

            String suggested = defaultIterationGoal(iteration);
            String user = prompts.render(prompts.orchestratorUser, Map.of(
                    "prior_knowledge", priorKnowledge,
                    "compacted_history", safe(compactedHistory),
                    "iteration", iteration,
                    "prefer", suggested
            ));

            CallResult call = callJson(system, user);
            JsonNode out = call.json();
            String goal = textOrFallback(out, "iteration_goal", suggested);

            Turn turn = toTurn("Orchestrator", user, out, call.usage());
            stats.addAgentTurn(iteration, turn);
            writer.appendConversationTurn(turn);
            return Map.of(
                    "iteration_goal", goal,
                    "human_before", "",
                    "revision_count", 0,
                    "conversation", turn
            );
        });

        var humanBefore = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            String goal = stringValue(state, "iteration_goal", "");
            String prompt = prompts.render(prompts.humanCheckpointBefore, Map.of(
                    "iteration", iteration,
                    "goal", goal
            ));
            String v = io.readKeyword(prompt, "approve", "retry");
            ObjectNode out = mapper.createObjectNode().put("human_input", v);
            Turn turn = toTurn("HumanCheckpointBefore", prompt, out, emptyUsage());
            stats.addHumanTurn(iteration, turn);
            writer.appendConversationTurn(turn);
            return Map.of(
                    "human_before", v,
                    "conversation", turn,
                    "revision_count", 0
            );
        });

        var architect = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            int revision = intValue(state, "revision_count", 0);
            String goal = stringValue(state, "iteration_goal", "");
            String compactedHistory = stringValue(state, "compacted_history", "");
            String priorCarryover = stringValue(state, "prior_carryover", "");
            String requiredDrivers = requiredDriversFor(iteration);

            List<String> criticIssues = listOfStrings(state.value("critic_issues").orElse(List.of()));
            Map<String, Object> blockVars = new LinkedHashMap<>();
            blockVars.put("revision", revision);
            blockVars.put("max_revisions", cfg.maxRevisions);
            blockVars.put("issues", String.join("\n", criticIssues));
            String criticBlock = criticIssues.isEmpty() ? "" : prompts.render(prompts.architectCriticBlock, blockVars);

            String system = prompts.architectSystem;

            Map<String, Object> userVars = new LinkedHashMap<>();
            userVars.put("prior_knowledge", priorKnowledge);
            userVars.put("compacted_history", safe(compactedHistory));
            userVars.put("iteration", iteration);
            userVars.put("goal", goal);
            userVars.put("required_drivers", requiredDrivers);
            userVars.put("prior_carryover", safe(priorCarryover));
            userVars.put("critic_block", criticBlock);
            String user = prompts.render(prompts.architectUser, userVars);

            CallResult call = callJson(system, user);
            JsonNode out = call.json();

            String iterationFocus = textOrFallback(out, "iteration_focus", "");
            String step3Element = textOrFallback(out, "step3_element", "");
            List<Alternative> step4 = parseAlternatives(out.get("step4_alternatives"));
            List<ComponentSpec> step5 = parseComponents(out.get("step5_components"));
            String step6Full = textOrFallback(out, "step6_diagram_full_mermaid", "");
            String step6Delta = textOrFallback(out, "step6_diagram_delta_mermaid", "");
            List<GoalCheck> step7 = parseGoalChecks(out.get("step7_goal_check"));

            String design = textOrFallback(out, "design", iterationFocus.isBlank()
                    ? "TODO: design placeholder (LLM did not return design)."
                    : iterationFocus);
            // Prefer step6_diagram_full_mermaid as the canonical diagram; fall back to legacy diagram_code.
            String mermaid = !step6Full.isBlank()
                    ? step6Full
                    : textOrFallback(out, "diagram_code", defaultMermaid(iteration));
            List<String> decisionLog = arrayOfStrings(out.get("decision_log"));
            String rawJson = safeJson(out);

            Turn turn = toTurn("Architect", user, out, call.usage());
            stats.addAgentTurn(iteration, turn);
            writer.appendConversationTurn(turn);

            Map<String, Object> commit = new LinkedHashMap<>();
            commit.put("architect_design", design);
            commit.put("architect_mermaid", mermaid);
            commit.put("architect_decision_log", decisionLog);
            commit.put("architect_iteration_focus", iterationFocus);
            commit.put("architect_step3", step3Element);
            commit.put("architect_step4", step4);
            commit.put("architect_step5", step5);
            commit.put("architect_step6_full", step6Full.isBlank() ? mermaid : step6Full);
            commit.put("architect_step6_delta", step6Delta);
            commit.put("architect_step7", step7);
            commit.put("architect_raw_json", rawJson);
            commit.put("conversation", turn);
            return commit;
        });

        var critic = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            int revision = intValue(state, "revision_count", 0);
            String goal = stringValue(state, "iteration_goal", "");
            String design = stringValue(state, "architect_design", "");
            String mermaid = stringValue(state, "architect_mermaid", "");
            String compactedHistory = stringValue(state, "compacted_history", "");
            String priorCarryover = stringValue(state, "prior_carryover", "");
            String requiredDrivers = requiredDriversFor(iteration);

            String system = prompts.criticSystem;

            Map<String, Object> userVars = new LinkedHashMap<>();
            userVars.put("prior_knowledge", priorKnowledge);
            userVars.put("compacted_history", safe(compactedHistory));
            userVars.put("iteration", iteration);
            userVars.put("goal", goal);
            userVars.put("required_drivers", requiredDrivers);
            userVars.put("prior_carryover", safe(priorCarryover));
            userVars.put("design", design);
            userVars.put("diagram_code", mermaid);
            String user = prompts.render(prompts.criticUser, userVars);

            CallResult call = callJson(system, user);
            JsonNode out = call.json();

            // Strict parse: if "pass" is missing or not boolean, default to false (fail-closed).
            boolean pass = boolOrFallback(out, "pass", false);
            List<String> issuesPlain = parseIssueDescriptions(out.get("issues"));
            List<CriticIssue> issuesTyped = parseCriticIssues(out.get("issues"));
            List<DriverCheck> driverChecks = parseDriverChecks(out.get("driver_check"));
            List<String> decisionLog = arrayOfStrings(out.get("decision_log"));

            // Hard rule: any BLOCK ⇒ fail.
            boolean hasBlock = issuesTyped.stream().anyMatch(i -> i.severity() == CriticIssue.Severity.BLOCK);
            if (hasBlock) pass = false;

            int nextRevision = revision;
            if (!pass && revision < cfg.maxRevisions) nextRevision = revision + 1;

            String rawJson = safeJson(out);

            Turn turn = toTurn("Critic", user, out, call.usage());
            stats.addAgentTurn(iteration, turn);
            writer.appendConversationTurn(turn);

            Map<String, Object> commit = new LinkedHashMap<>();
            commit.put("critic_pass", pass);
            commit.put("critic_issues", issuesPlain);
            commit.put("critic_issues_struct", issuesTyped);
            commit.put("critic_driver_check", driverChecks);
            commit.put("critic_decision_log", decisionLog);
            commit.put("critic_raw_json", rawJson);
            commit.put("revision_count", nextRevision);
            commit.put("conversation", turn);
            return commit;
        });

        var scribe = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            int revisionsUsed = intValue(state, "revision_count", 0);
            String goal = stringValue(state, "iteration_goal", "");

            String design = stringValue(state, "architect_design", "");
            String mermaid = stringValue(state, "architect_mermaid", "");
            List<String> criticIssues = listOfStrings(state.value("critic_issues").orElse(List.of()));

            List<String> mergedDecisions = new ArrayList<>();
            mergedDecisions.addAll(listOfStrings(state.value("architect_decision_log").orElse(List.of())));
            mergedDecisions.addAll(listOfStrings(state.value("critic_decision_log").orElse(List.of())));

            String iterationFocus = stringValue(state, "architect_iteration_focus", "");
            String step3 = stringValue(state, "architect_step3", "");
            @SuppressWarnings("unchecked")
            List<Alternative> step4 = (List<Alternative>) state.value("architect_step4").orElse(List.of());
            @SuppressWarnings("unchecked")
            List<ComponentSpec> step5 = (List<ComponentSpec>) state.value("architect_step5").orElse(List.of());
            String step6Full = stringValue(state, "architect_step6_full", mermaid);
            String step6Delta = stringValue(state, "architect_step6_delta", "");
            @SuppressWarnings("unchecked")
            List<GoalCheck> step7 = (List<GoalCheck>) state.value("architect_step7").orElse(List.of());

            @SuppressWarnings("unchecked")
            List<DriverCheck> driverChecks = (List<DriverCheck>) state.value("critic_driver_check").orElse(List.of());
            @SuppressWarnings("unchecked")
            List<CriticIssue> issuesTyped = (List<CriticIssue>) state.value("critic_issues_struct").orElse(List.of());
            boolean criticPass = boolValue(state, "critic_pass", false);

            String architectRawJson = stringValue(state, "architect_raw_json", "");
            String criticRawJson = stringValue(state, "critic_raw_json", "");

            String carryover = buildCarryover(issuesTyped, step7);
            carryover = clip(carryover, cfg.carryoverCapBytes());

            String ts = TimeUtil.nowIso();
            IterationResult result = IterationResult.builder()
                    .iteration(iteration)
                    .goal(goal)
                    .ts(ts)
                    .design(design)
                    .mermaid(normalizeMermaid(mermaid))
                    .issues(criticIssues)
                    .decisionLog(mergedDecisions)
                    .revisionsUsed(revisionsUsed)
                    .promptTokens(stats.getPromptTokensForIteration(iteration))
                    .completionTokens(stats.getCompletionTokensForIteration(iteration))
                    .totalTokens(stats.getTotalTokensForIteration(iteration))
                    .humanTurns(stats.getHumanTurnsForIteration(iteration))
                    .agentTurns(stats.getAgentTurnsForIteration(iteration))
                    .durationMs(stats.getDurationMsForIteration(iteration))
                    .iterationFocus(iterationFocus)
                    .step3Element(step3)
                    .step4Alternatives(step4)
                    .step5Components(step5)
                    .step6DiagramFullMermaid(normalizeMermaid(step6Full))
                    .step6DiagramDeltaMermaid(normalizeMermaid(step6Delta))
                    .step7GoalCheck(step7)
                    .criticDriverChecks(driverChecks)
                    .criticIssuesTyped(issuesTyped)
                    .criticPass(criticPass)
                    .carryoverForNextIteration(carryover)
                    .architectRawJson(architectRawJson)
                    .criticRawJson(criticRawJson)
                    .requiredDrivers(requiredDriverList(iteration))
                    .build();

            writer.appendArchitectureIteration(result);
            results.add(result);

            ObjectNode out = mapper.createObjectNode();
            out.put("iteration", iteration);
            out.put("ts", ts);
            out.put("revisions_used", revisionsUsed);
            out.put("finalized", true);
            out.put("critic_pass", criticPass);
            out.put("issues_count", criticIssues.size());
            out.put("carryover_bytes", carryover.getBytes(StandardCharsets.UTF_8).length);

            Turn turn = toTurn("Scribe", "Finalize iteration result and write logs.", out, emptyUsage());
            stats.addAgentTurn(iteration, turn);
            writer.appendConversationTurn(turn);

            Map<String, Object> commit = new LinkedHashMap<>();
            commit.put("results", result);
            commit.put("prior_carryover", carryover);
            commit.put("conversation", turn);
            return commit;
        });

        var compactor = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            String system = prompts.contextCompactorSystem;

            @SuppressWarnings("unchecked")
            List<IterationResult> resultsSoFar = (List<IterationResult>) state.value("results").orElse(List.of());

            String user = prompts.render(prompts.contextCompactorUser, Map.of(
                    "results", renderResultsForCompaction(resultsSoFar)
            ));

            CallResult call = callJson(system, user);
            JsonNode out = call.json();
            String compacted = textOrFallback(out, "compacted_history", simpleCompact(resultsSoFar));
            // Hard cap so the prompt envelope never explodes across iterations.
            compacted = clip(compacted, 4096);

            Turn turn = toTurn("ContextCompactor", user, out, call.usage());
            stats.addAgentTurn(iteration, turn);
            writer.appendConversationTurn(turn);
            return Map.of(
                    "compacted_history", compacted,
                    "compactor_output", compacted,
                    "conversation", turn
            );
        });

        var humanAfter = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            String goal = stringValue(state, "iteration_goal", "");
            String prompt = prompts.render(prompts.humanCheckpointAfter, Map.of(
                    "iteration", iteration,
                    "goal", goal
            ));
            String v = io.readKeyword(prompt, "approve", "retry");
            ObjectNode out = mapper.createObjectNode().put("human_input", v);
            Turn turn = toTurn("HumanCheckpointAfter", prompt, out, emptyUsage());
            stats.addHumanTurn(iteration, turn);
            writer.appendConversationTurn(turn);
            if ("retry".equalsIgnoreCase(v)) {
                writer.appendConversationNote("Checkpoint retry: rerun Architect and Critic for the same iteration goal.");
                return Map.of(
                        "human_after", v,
                        "revision_count", 0,
                        "conversation", turn
                );
            }
            return Map.of(
                    "human_after", v,
                    "conversation", turn
            );
        });

        var nextIteration = node_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            int next = iteration + 1;
            // Note: prior_carryover is intentionally NOT reset here; it flows from scribe
            // of iter N into architect of iter N+1.
            // Map.of() caps at 10 pairs; use Map.ofEntries for 11+.
            return Map.ofEntries(
                    Map.entry("iteration", next),
                    Map.entry("iteration_goal", ""),
                    Map.entry("revision_count", 0),
                    Map.entry("critic_pass", false),
                    Map.entry("critic_issues", List.of()),
                    Map.entry("critic_issues_struct", List.of()),
                    Map.entry("critic_driver_check", List.of()),
                    Map.entry("critic_decision_log", List.of()),
                    Map.entry("architect_decision_log", List.of()),
                    Map.entry("human_before", ""),
                    Map.entry("human_after", "")
            );
        });

        var checkpointBeforeDecision = edge_async(state -> {
            String v = stringValue(state, "human_before", "retry");
            return "approve".equalsIgnoreCase(v) ? "approve" : "retry";
        });

        var criticDecision = edge_async(state -> {
            boolean pass = boolValue(state, "critic_pass", false);
            int revision = intValue(state, "revision_count", 0);
            if (pass) return "pass";
            if (revision >= cfg.maxRevisions) return "maxed";
            return "revise";
        });

        var checkpointAfterDecision = edge_async(state -> {
            String v = stringValue(state, "human_after", "retry");
            return "approve".equalsIgnoreCase(v) ? "approve" : "retry";
        });

        var continueDecision = edge_async(state -> {
            int iteration = intValue(state, "iteration", 1);
            return iteration <= cfg.iterations ? "continue" : "end";
        });

        return new StateGraph("Multi-Agent Hotel System Design", stateFactory)
                .addNode("orchestrator", orchestrator)
                .addNode("human_checkpoint_before", humanBefore)
                .addNode("architect", architect)
                .addNode("critic", critic)
                .addNode("scribe", scribe)
                .addNode("context_compactor", compactor)
                .addNode("human_checkpoint_after", humanAfter)
                .addNode("next_iteration", nextIteration)
                .addEdge(START, "orchestrator")
                .addEdge("orchestrator", "human_checkpoint_before")
                .addConditionalEdges("human_checkpoint_before", checkpointBeforeDecision, Map.of(
                        "approve", "architect",
                        "retry", "orchestrator"
                ))
                .addEdge("architect", "critic")
                .addConditionalEdges("critic", criticDecision, Map.of(
                        "pass", "scribe",
                        "maxed", "scribe",
                        "revise", "architect"
                ))
                .addEdge("scribe", "context_compactor")
                .addEdge("context_compactor", "human_checkpoint_after")
                .addConditionalEdges("human_checkpoint_after", checkpointAfterDecision, Map.of(
                        "retry", "architect",
                        "approve", "next_iteration"
                ))
                .addConditionalEdges("next_iteration", continueDecision, Map.of(
                        "continue", "orchestrator",
                        "end", END
                ));
    }

    // ----------------------------------------------------------------------
    //  Architect schema parsing
    // ----------------------------------------------------------------------
    private List<Alternative> parseAlternatives(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<Alternative> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x == null || !x.isObject()) continue;
            out.add(new Alternative(
                    textOrEmpty(x, "concept"),
                    textOrEmpty(x, "pros"),
                    textOrEmpty(x, "cons"),
                    x.has("chosen") && x.get("chosen").asBoolean(false),
                    textOrEmpty(x, "rationale")
            ));
        }
        return out;
    }

    private List<ComponentSpec> parseComponents(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<ComponentSpec> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x == null || !x.isObject()) continue;
            List<String> responsibilities = arrayOfStrings(x.get("responsibilities"));
            List<String> required = arrayOfStrings(x.get("required_interfaces"));
            List<InterfaceSpec> provided = new ArrayList<>();
            JsonNode pi = x.get("provided_interfaces");
            if (pi != null && pi.isArray()) {
                for (JsonNode iface : pi) {
                    if (iface == null || !iface.isObject()) continue;
                    provided.add(new InterfaceSpec(
                            textOrEmpty(iface, "name"),
                            textOrEmpty(iface, "signature_or_topic"),
                            textOrEmpty(iface, "protocol"),
                            textOrEmpty(iface, "payload_schema")
                    ));
                }
            }
            out.add(new ComponentSpec(
                    textOrEmpty(x, "name"),
                    responsibilities,
                    provided,
                    required
            ));
        }
        return out;
    }

    private List<GoalCheck> parseGoalChecks(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<GoalCheck> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x == null || !x.isObject()) continue;
            out.add(new GoalCheck(
                    textOrEmpty(x, "driver_id"),
                    textOrEmpty(x, "mechanism_in_design"),
                    textOrEmpty(x, "quantitative_evidence"),
                    textOrEmpty(x, "verdict"),
                    textOrEmpty(x, "carryover_note")
            ));
        }
        return out;
    }

    // ----------------------------------------------------------------------
    //  Critic schema parsing
    // ----------------------------------------------------------------------
    private List<DriverCheck> parseDriverChecks(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<DriverCheck> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x == null || !x.isObject()) continue;
            out.add(new DriverCheck(
                    textOrEmpty(x, "driver_id"),
                    textOrEmpty(x, "evidence_in_design"),
                    textOrEmpty(x, "evidence_in_diagram"),
                    textOrEmpty(x, "verdict")
            ));
        }
        return out;
    }

    /** Plain string form for legacy consumers; preserves the leading severity tag. */
    private List<String> parseIssueDescriptions(JsonNode n) {
        if (n == null) return List.of();
        if (n.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode x : n) {
                if (x == null || x.isNull()) continue;
                if (x.isObject()) {
                    String sev = textOrEmpty(x, "severity").toUpperCase();
                    String desc = textOrEmpty(x, "description");
                    String fix = textOrEmpty(x, "suggested_fix");
                    String driver = textOrEmpty(x, "driver_id_or_concern_id");
                    StringBuilder sb = new StringBuilder();
                    if (!sev.isEmpty() && !desc.startsWith("[")) sb.append("[").append(sev).append("] ");
                    if (!driver.isEmpty()) sb.append(driver).append(": ");
                    sb.append(desc);
                    if (!fix.isBlank()) sb.append(" Suggested fix: ").append(fix);
                    String line = sb.toString().trim();
                    if (!line.isBlank()) out.add(line);
                } else {
                    String s = x.asText();
                    if (s != null && !s.isBlank()) out.add(s.trim());
                }
            }
            return out;
        }
        return List.of();
    }

    private List<CriticIssue> parseCriticIssues(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<CriticIssue> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x == null) continue;
            if (x.isObject()) {
                String sev = textOrEmpty(x, "severity");
                String driver = textOrEmpty(x, "driver_id_or_concern_id");
                String desc = textOrEmpty(x, "description");
                String fix = textOrEmpty(x, "suggested_fix");
                out.add(new CriticIssue(CriticIssue.Severity.from(sev), driver, desc, fix));
            } else if (x.isTextual()) {
                // Fallback: best-effort extract "[BLOCK] ..." prefix
                String s = x.asText("").trim();
                CriticIssue.Severity sev = CriticIssue.Severity.WARN;
                String desc = s;
                if (s.startsWith("[BLOCK]")) { sev = CriticIssue.Severity.BLOCK; desc = s.substring(7).trim(); }
                else if (s.startsWith("[WARN]")) { sev = CriticIssue.Severity.WARN; desc = s.substring(6).trim(); }
                else if (s.startsWith("[NIT]")) { sev = CriticIssue.Severity.NIT; desc = s.substring(5).trim(); }
                out.add(new CriticIssue(sev, "", desc, ""));
            }
        }
        return out;
    }

    // ----------------------------------------------------------------------
    //  Required drivers per iteration (single source of truth, matches prompt)
    // ----------------------------------------------------------------------
    private static String requiredDriversFor(int iteration) {
        return switch (iteration) {
            case 1 -> "CRN-1, CON-1, CON-2, CON-6";
            case 2 -> "HPS-1, HPS-2, HPS-3, HPS-4, HPS-5, HPS-6, CON-5";
            case 3 -> "QA-2, QA-3, QA-8";
            case 4 -> "QA-5, QA-6, QA-7, QA-9, CON-3, CON-4";
            default -> "";
        };
    }

    private static List<String> requiredDriverList(int iteration) {
        String csv = requiredDriversFor(iteration);
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ----------------------------------------------------------------------
    //  Carryover construction & UTF-8 safe clipping
    // ----------------------------------------------------------------------
    private static String buildCarryover(List<CriticIssue> issues, List<GoalCheck> goalChecks) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (CriticIssue ci : issues) {
            if (ci.severity() == CriticIssue.Severity.NIT) continue;
            sb.append(idx++).append(". [").append(ci.severity().name()).append("] ");
            if (ci.driverIdOrConcernId() != null && !ci.driverIdOrConcernId().isBlank()) {
                sb.append(ci.driverIdOrConcernId()).append(": ");
            }
            sb.append(ci.description());
            if (ci.suggestedFix() != null && !ci.suggestedFix().isBlank()) {
                sb.append(" — suggested: ").append(ci.suggestedFix());
            }
            sb.append('\n');
        }
        for (GoalCheck gc : goalChecks) {
            if (!"deferred".equalsIgnoreCase(gc.verdict())) continue;
            if (gc.carryoverNote() == null || gc.carryoverNote().isBlank()) continue;
            sb.append(idx++).append(". [DEFERRED] ").append(gc.driverId()).append(": ")
              .append(gc.carryoverNote()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String clip(String s, int maxBytes) {
        if (s == null) return "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return s;
        int cut = Math.max(0, maxBytes - 16);
        // Walk back to a UTF-8 boundary
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) cut--;
        String head = new String(bytes, 0, cut, StandardCharsets.UTF_8);
        return head + "…[clipped]";
    }

    // ----------------------------------------------------------------------
    //  LLM call boilerplate
    // ----------------------------------------------------------------------
    private CallResult callJson(String system, String user) {
        ChatResponse response = chatClient.prompt().system(system).user(user).call().chatResponse();
        Usage usage = emptyUsage();
        String content = "";
        if (response != null) {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                usage = response.getMetadata().getUsage();
            }
            if (response.getResult() != null && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                content = response.getResult().getOutput().getText();
            }
        }
        if (content == null) content = "";
        String stripped = stripCodeFences(content.trim());
        try {
            JsonNode n = mapper.readTree(stripped);
            if (n != null && n.isObject()) return new CallResult(n, usage);
            ObjectNode wrapped = mapper.createObjectNode();
            wrapped.set("value", n);
            return new CallResult(wrapped, usage);
        } catch (Exception e) {
            ObjectNode wrapped = mapper.createObjectNode();
            wrapped.put("raw_text", content);
            return new CallResult(wrapped, usage);
        }
    }

    private Turn toTurn(String node, String input, JsonNode output, Usage usage) throws IOException {
        String ts = TimeUtil.nowIso();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        int promptTokens = tokenOrZero(usage == null ? null : usage.getPromptTokens());
        int completionTokens = tokenOrZero(usage == null ? null : usage.getCompletionTokens());
        int totalTokens = tokenOrZero(usage == null ? null : usage.getTotalTokens());
        return new Turn(ts, node, input, json, promptTokens, completionTokens, totalTokens);
    }

    private String safeJson(JsonNode out) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            return out == null ? "" : out.toString();
        }
    }

    private int tokenOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Usage emptyUsage() {
        return new EmptyUsage();
    }

    private String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("(?s)^```\\w*\\s*", "");
            t = t.replaceFirst("(?s)```\\s*$", "");
        }
        return t.trim();
    }

    private String defaultIterationGoal(int iteration) {
        return switch (iteration) {
            case 1 -> "Establishing an Overall System Structure";
            case 2 -> "Identifying Structures to Support Primary Functionality";
            case 3 -> "Addressing Reliability and Availability Quality Attributes";
            case 4 -> "Addressing Development and Operations";
            default -> "Continue refining the architecture using ADD.";
        };
    }

    private String defaultMermaid(int iteration) {
        String title = "Iteration " + iteration + " - High-level components";
        return """
                flowchart TB
                  subgraph %s
                    UI[Web App UI]
                    API[API Gateway]
                    RES[Reservation Service]
                    INV[Inventory Service]
                    PAY[Payment Service]
                    CUS[Customer Service]
                    DB[(Database)]
                    MQ[(Message Bus)]
                    UI --> API
                    API --> RES
                    API --> INV
                    API --> CUS
                    RES --> DB
                    INV --> DB
                    RES --> MQ
                    PAY --> MQ
                  end
                """.formatted(title).trim();
    }

    private String normalizeMermaid(String diagramCode) {
        if (diagramCode == null) return "";
        String s = diagramCode.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("```")) {
            s = s.replaceFirst("(?s)^```\\w*\\s*", "");
            s = s.replaceFirst("(?s)```\\s*$", "");
        }
        // Convert Mermaid round-node syntax `ID(Label)` to square-node syntax `ID[Label]`
        // so labels remain valid even when models mix styles.
        s = s.replaceAll("([A-Za-z][A-Za-z0-9_]*)\\(([^\\)\\n]+)\\)", "$1[$2]");
        // Fix malformed dotted edge label suffix like `|REST API|. B` -> `|REST API| B`.
        s = s.replaceAll("\\|\\s*\\.\\s*([A-Za-z][A-Za-z0-9_]*)", "| $1");
        // Split accidentally concatenated statements like `... ]C -.-> ...`.
        s = s.replaceAll("\\]([A-Za-z][A-Za-z0-9_]*)\\s*(-\\.|--|==)", "]\n$1 $2");
        String[] lines = s.split("\\R");
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            String t = line;
            // Drop style/class directives because LLMs often emit invalid Mermaid style syntax.
            String lower = t.trim().toLowerCase();
            if (lower.startsWith("classdef ") || lower.startsWith("class ") || lower.startsWith("style ")) {
                continue;
            }
            t = t.replaceAll("\\s{2,}", " ").trim();
            if (!t.isBlank()) normalized.add(t);
        }
        String body = String.join("\n", normalized).trim();
        if (body.isEmpty()) return "";
        // Ensure a valid header
        String firstLineLower = body.split("\\R", 2)[0].trim().toLowerCase();
        if (!(firstLineLower.startsWith("flowchart") || firstLineLower.startsWith("graph")
                || firstLineLower.startsWith("sequencediagram") || firstLineLower.startsWith("classdiagram"))) {
            body = "flowchart LR\n" + body;
        }
        return body;
    }

    private String renderResultsForCompaction(List<IterationResult> results) {
        StringBuilder sb = new StringBuilder();
        for (IterationResult r : results) {
            sb.append("Iteration ").append(r.getIteration()).append(": ").append(r.getGoal()).append("\n");
            sb.append("Design: ").append(trimTo(r.getDesign(), 500)).append("\n");
            if (!r.getIssues().isEmpty()) sb.append("Issues: ").append(String.join("; ", r.getIssues())).append("\n");
            if (r.getCarryoverForNextIteration() != null && !r.getCarryoverForNextIteration().isBlank()) {
                sb.append("Carryover: ").append(trimTo(r.getCarryoverForNextIteration(), 600)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String simpleCompact(List<IterationResult> results) {
        StringBuilder sb = new StringBuilder();
        for (IterationResult r : results) {
            sb.append("I").append(r.getIteration()).append(": ").append(trimTo(r.getGoal(), 120)).append(" | ");
        }
        String s = sb.toString().trim();
        return trimTo(s, 900);
    }

    private String textOrFallback(JsonNode n, String field, String fallback) {
        if (n == null) return fallback;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return fallback;
        String s = v.asText();
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private String textOrEmpty(JsonNode n, String field) {
        return textOrFallback(n, field, "");
    }

    private boolean boolOrFallback(JsonNode n, String field, boolean fallback) {
        if (n == null) return fallback;
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isBoolean()) return fallback;
        return v.asBoolean(fallback);
    }

    private List<String> arrayOfStrings(JsonNode n) {
        if (n == null || n.isNull()) return List.of();
        if (!n.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode x : n) {
            if (x != null && !x.isNull()) {
                String s = x.asText();
                if (s != null && !s.isBlank()) out.add(s.trim());
            }
        }
        return out;
    }

    private List<String> listOfStrings(Object maybeList) {
        if (maybeList == null) return List.of();
        if (maybeList instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                String s;
                if (o instanceof String str) s = str;
                else if (o instanceof JsonNode jn && !jn.isNull()) s = jn.asText();
                else s = o.toString();
                if (s != null && !s.isBlank()) out.add(s.trim());
            }
            return out;
        }
        return List.of(maybeList.toString());
    }

    private boolean boolValue(OverAllState state, String key, boolean d) {
        return state.value(key).map(v -> (v instanceof Boolean) ? (Boolean) v : d).orElse(d);
    }

    private int intValue(OverAllState state, String key, int d) {
        return state.value(key)
                .map(v -> (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString()))
                .orElse(d);
    }

    private String stringValue(OverAllState state, String key, String d) {
        return state.value(key).map(Object::toString).orElse(d);
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private String trimTo(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }

    private int recommendedMaxIterations() {
        int perIterationBase = 8;
        int perIterationRevisionWorst = 4 * Math.max(0, cfg.maxRevisions);
        int perIterationBudget = perIterationBase + perIterationRevisionWorst;
        int planned = Math.max(1, cfg.iterations) * perIterationBudget;
        return Math.max(100, planned + 20);
    }

    private static final class CallResult {
        private final JsonNode json;
        private final Usage usage;

        private CallResult(JsonNode json, Usage usage) {
            this.json = json;
            this.usage = usage;
        }

        private JsonNode json() {
            return json;
        }

        private Usage usage() {
            return usage;
        }
    }

    private static final class IterationStats {
        private final List<PerIteration> rows = new ArrayList<>();

        private void addAgentTurn(int iteration, Turn turn) {
            PerIteration row = row(iteration);
            row.agentTurns++;
            addTokensAndTiming(row, turn);
        }

        private void addHumanTurn(int iteration, Turn turn) {
            PerIteration row = row(iteration);
            row.humanTurns++;
            addTokensAndTiming(row, turn);
        }

        private int getPromptTokensForIteration(int iteration) {
            return row(iteration).promptTokens;
        }

        private int getCompletionTokensForIteration(int iteration) {
            return row(iteration).completionTokens;
        }

        private int getTotalTokensForIteration(int iteration) {
            return row(iteration).totalTokens;
        }

        private int getHumanTurnsForIteration(int iteration) {
            return row(iteration).humanTurns;
        }

        private int getAgentTurnsForIteration(int iteration) {
            return row(iteration).agentTurns;
        }

        private long getDurationMsForIteration(int iteration) {
            PerIteration row = row(iteration);
            if (row.firstMs <= 0 || row.lastMs < row.firstMs) return 0L;
            return row.lastMs - row.firstMs;
        }

        private void addTokensAndTiming(PerIteration row, Turn turn) {
            long now = System.currentTimeMillis();
            if (row.firstMs == 0L) row.firstMs = now;
            row.lastMs = now;
            row.promptTokens += Math.max(0, turn.getPromptTokens());
            row.completionTokens += Math.max(0, turn.getCompletionTokens());
            row.totalTokens += Math.max(0, turn.getTotalTokens());
        }

        private PerIteration row(int iteration) {
            int idx = Math.max(1, iteration) - 1;
            while (rows.size() <= idx) {
                rows.add(new PerIteration());
            }
            return rows.get(idx);
        }

        private static final class PerIteration {
            private int promptTokens;
            private int completionTokens;
            private int totalTokens;
            private int humanTurns;
            private int agentTurns;
            private long firstMs;
            private long lastMs;
        }
    }

    private static final class PromptTemplates {
        private final String priorKnowledge;

        private final String orchestratorSystem;
        private final String orchestratorUser;

        private final String humanCheckpointBefore;
        private final String humanCheckpointAfter;

        private final String architectSystem;
        private final String architectUser;
        private final String architectCriticBlock;

        private final String criticSystem;
        private final String criticUser;

        private final String contextCompactorSystem;
        private final String contextCompactorUser;

        private PromptTemplates(
                String priorKnowledge,
                String orchestratorSystem,
                String orchestratorUser,
                String humanCheckpointBefore,
                String humanCheckpointAfter,
                String architectSystem,
                String architectUser,
                String architectCriticBlock,
                String criticSystem,
                String criticUser,
                String contextCompactorSystem,
                String contextCompactorUser
        ) {
            this.priorKnowledge = priorKnowledge;
            this.orchestratorSystem = orchestratorSystem;
            this.orchestratorUser = orchestratorUser;
            this.humanCheckpointBefore = humanCheckpointBefore;
            this.humanCheckpointAfter = humanCheckpointAfter;
            this.architectSystem = architectSystem;
            this.architectUser = architectUser;
            this.architectCriticBlock = architectCriticBlock;
            this.criticSystem = criticSystem;
            this.criticUser = criticUser;
            this.contextCompactorSystem = contextCompactorSystem;
            this.contextCompactorUser = contextCompactorUser;
        }

        private String render(String template, Map<String, ?> vars) {
            String out = template;
            for (Map.Entry<String, ?> e : vars.entrySet()) {
                String k = "{{" + e.getKey() + "}}";
                Object v = e.getValue();
                out = out.replace(k, v == null ? "" : v.toString());
            }
            return out.trim();
        }

        private static PromptTemplates load() throws IOException {
            String base = "prompts/";
            return new PromptTemplates(
                    readResource(base + "prior_knowledge.md"),
                    readResource(base + "orchestrator_system.md"),
                    readResource(base + "orchestrator_user.md"),
                    readResource(base + "human_checkpoint_before.md"),
                    readResource(base + "human_checkpoint_after.md"),
                    readResource(base + "architect_system.md"),
                    readResource(base + "architect_user.md"),
                    readResource(base + "architect_critic_block.md"),
                    readResource(base + "critic_system.md"),
                    readResource(base + "critic_user.md"),
                    readResource(base + "context_compactor_system.md"),
                    readResource(base + "context_compactor_user.md")
            );
        }

        private static String readResource(String classpathLocation) throws IOException {
            try (InputStream in = MultiAgentEngine.class.getClassLoader().getResourceAsStream(classpathLocation)) {
                if (in == null) throw new IOException("Missing classpath resource: " + classpathLocation);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        }
    }
}
