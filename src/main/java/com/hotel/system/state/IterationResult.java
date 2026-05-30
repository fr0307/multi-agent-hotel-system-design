package com.hotel.system.state;

import com.hotel.system.state.model.Alternative;
import com.hotel.system.state.model.ComponentSpec;
import com.hotel.system.state.model.CriticIssue;
import com.hotel.system.state.model.DriverCheck;
import com.hotel.system.state.model.GoalCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate of one ADD iteration. Built incrementally by Architect &rarr; Critic &rarr; Scribe
 * via {@link Builder}. Backwards-compatible getters (design / mermaid / issues / decisionLog)
 * are preserved so existing log writers still compile.
 */
public final class IterationResult {

    // -- Core identity --
    private final int iteration;
    private final String goal;
    private final String ts;

    // -- Legacy / digest fields (kept for backward compat) --
    private final String design;
    private final String mermaid;
    private final List<String> issues;
    private final List<String> decisionLog;
    private final int revisionsUsed;

    // -- Interaction cost --
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final int humanTurns;
    private final int agentTurns;
    private final long durationMs;

    // -- Architect ADD Step 3-7 structured outputs --
    private final String iterationFocus;
    private final String step3Element;
    private final List<Alternative> step4Alternatives;
    private final List<ComponentSpec> step5Components;
    private final String step6DiagramFullMermaid;
    private final String step6DiagramDeltaMermaid;
    private final List<GoalCheck> step7GoalCheck;

    // -- Critic structured outputs --
    private final List<DriverCheck> criticDriverChecks;
    private final List<CriticIssue> criticIssuesTyped;
    private final boolean criticPass;

    // -- Carryover propagated to next iteration --
    private final String carryoverForNextIteration;

    // -- Raw JSON copies (for appendix) --
    private final String architectRawJson;
    private final String criticRawJson;

    // -- Required drivers for this iteration (allocated by Orchestrator/Engine) --
    private final List<String> requiredDrivers;

    private IterationResult(Builder b) {
        this.iteration = b.iteration;
        this.goal = nz(b.goal);
        this.ts = nz(b.ts);

        this.design = nz(b.design);
        this.mermaid = nz(b.mermaid);
        this.issues = b.issues == null ? List.of() : List.copyOf(b.issues);
        this.decisionLog = b.decisionLog == null ? List.of() : List.copyOf(b.decisionLog);
        this.revisionsUsed = b.revisionsUsed;

        this.promptTokens = b.promptTokens;
        this.completionTokens = b.completionTokens;
        this.totalTokens = b.totalTokens;
        this.humanTurns = b.humanTurns;
        this.agentTurns = b.agentTurns;
        this.durationMs = b.durationMs;

        this.iterationFocus = nz(b.iterationFocus);
        this.step3Element = nz(b.step3Element);
        this.step4Alternatives = b.step4Alternatives == null ? List.of() : List.copyOf(b.step4Alternatives);
        this.step5Components = b.step5Components == null ? List.of() : List.copyOf(b.step5Components);
        this.step6DiagramFullMermaid = nz(b.step6DiagramFullMermaid);
        this.step6DiagramDeltaMermaid = nz(b.step6DiagramDeltaMermaid);
        this.step7GoalCheck = b.step7GoalCheck == null ? List.of() : List.copyOf(b.step7GoalCheck);

        this.criticDriverChecks = b.criticDriverChecks == null ? List.of() : List.copyOf(b.criticDriverChecks);
        this.criticIssuesTyped = b.criticIssuesTyped == null ? List.of() : List.copyOf(b.criticIssuesTyped);
        this.criticPass = b.criticPass;

        this.carryoverForNextIteration = nz(b.carryoverForNextIteration);
        this.architectRawJson = nz(b.architectRawJson);
        this.criticRawJson = nz(b.criticRawJson);
        this.requiredDrivers = b.requiredDrivers == null ? List.of() : List.copyOf(b.requiredDrivers);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static Builder builder() {
        return new Builder();
    }

    // -- Getters --
    public int getIteration() { return iteration; }
    public String getGoal() { return goal; }
    public String getTs() { return ts; }
    public String getDesign() { return design; }
    public String getMermaid() { return mermaid; }
    public List<String> getIssues() { return issues; }
    public List<String> getDecisionLog() { return decisionLog; }
    public int getRevisionsUsed() { return revisionsUsed; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public int getHumanTurns() { return humanTurns; }
    public int getAgentTurns() { return agentTurns; }
    public long getDurationMs() { return durationMs; }

    public String getIterationFocus() { return iterationFocus; }
    public String getStep3Element() { return step3Element; }
    public List<Alternative> getStep4Alternatives() { return step4Alternatives; }
    public List<ComponentSpec> getStep5Components() { return step5Components; }
    public String getStep6DiagramFullMermaid() { return step6DiagramFullMermaid; }
    public String getStep6DiagramDeltaMermaid() { return step6DiagramDeltaMermaid; }
    public List<GoalCheck> getStep7GoalCheck() { return step7GoalCheck; }

    public List<DriverCheck> getCriticDriverChecks() { return criticDriverChecks; }
    public List<CriticIssue> getCriticIssuesTyped() { return criticIssuesTyped; }
    public boolean isCriticPass() { return criticPass; }

    public String getCarryoverForNextIteration() { return carryoverForNextIteration; }
    public String getArchitectRawJson() { return architectRawJson; }
    public String getCriticRawJson() { return criticRawJson; }
    public List<String> getRequiredDrivers() { return requiredDrivers; }

    // -- Builder --
    public static final class Builder {
        private int iteration;
        private String goal;
        private String ts;
        private String design;
        private String mermaid;
        private List<String> issues = new ArrayList<>();
        private List<String> decisionLog = new ArrayList<>();
        private int revisionsUsed;

        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int humanTurns;
        private int agentTurns;
        private long durationMs;

        private String iterationFocus;
        private String step3Element;
        private List<Alternative> step4Alternatives = new ArrayList<>();
        private List<ComponentSpec> step5Components = new ArrayList<>();
        private String step6DiagramFullMermaid;
        private String step6DiagramDeltaMermaid;
        private List<GoalCheck> step7GoalCheck = new ArrayList<>();

        private List<DriverCheck> criticDriverChecks = new ArrayList<>();
        private List<CriticIssue> criticIssuesTyped = new ArrayList<>();
        private boolean criticPass;

        private String carryoverForNextIteration;
        private String architectRawJson;
        private String criticRawJson;
        private List<String> requiredDrivers = new ArrayList<>();

        public Builder iteration(int v) { this.iteration = v; return this; }
        public Builder goal(String v) { this.goal = v; return this; }
        public Builder ts(String v) { this.ts = v; return this; }
        public Builder design(String v) { this.design = v; return this; }
        public Builder mermaid(String v) { this.mermaid = v; return this; }
        public Builder issues(List<String> v) { this.issues = v; return this; }
        public Builder decisionLog(List<String> v) { this.decisionLog = v; return this; }
        public Builder revisionsUsed(int v) { this.revisionsUsed = v; return this; }

        public Builder promptTokens(int v) { this.promptTokens = v; return this; }
        public Builder completionTokens(int v) { this.completionTokens = v; return this; }
        public Builder totalTokens(int v) { this.totalTokens = v; return this; }
        public Builder humanTurns(int v) { this.humanTurns = v; return this; }
        public Builder agentTurns(int v) { this.agentTurns = v; return this; }
        public Builder durationMs(long v) { this.durationMs = v; return this; }

        public Builder iterationFocus(String v) { this.iterationFocus = v; return this; }
        public Builder step3Element(String v) { this.step3Element = v; return this; }
        public Builder step4Alternatives(List<Alternative> v) { this.step4Alternatives = v; return this; }
        public Builder step5Components(List<ComponentSpec> v) { this.step5Components = v; return this; }
        public Builder step6DiagramFullMermaid(String v) { this.step6DiagramFullMermaid = v; return this; }
        public Builder step6DiagramDeltaMermaid(String v) { this.step6DiagramDeltaMermaid = v; return this; }
        public Builder step7GoalCheck(List<GoalCheck> v) { this.step7GoalCheck = v; return this; }

        public Builder criticDriverChecks(List<DriverCheck> v) { this.criticDriverChecks = v; return this; }
        public Builder criticIssuesTyped(List<CriticIssue> v) { this.criticIssuesTyped = v; return this; }
        public Builder criticPass(boolean v) { this.criticPass = v; return this; }

        public Builder carryoverForNextIteration(String v) { this.carryoverForNextIteration = v; return this; }
        public Builder architectRawJson(String v) { this.architectRawJson = v; return this; }
        public Builder criticRawJson(String v) { this.criticRawJson = v; return this; }
        public Builder requiredDrivers(List<String> v) { this.requiredDrivers = v; return this; }

        public IterationResult build() {
            return new IterationResult(this);
        }
    }
}
