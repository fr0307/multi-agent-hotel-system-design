package com.hotel.system.state.model;

/**
 * One Critic issue, classified by severity.
 * BLOCK = missing required structural evidence; pass=false until fixed.
 * WARN  = present but vague / lacks mechanism / lacks quantitative evidence.
 * NIT   = stylistic.
 */
public record CriticIssue(
        Severity severity,
        String driverIdOrConcernId,
        String description,
        String suggestedFix
) {
    public CriticIssue {
        if (severity == null) severity = Severity.WARN;
        if (driverIdOrConcernId == null) driverIdOrConcernId = "";
        if (description == null) description = "";
        if (suggestedFix == null) suggestedFix = "";
    }

    public enum Severity {
        BLOCK,
        WARN,
        NIT;

        public static Severity from(String s) {
            if (s == null) return WARN;
            String t = s.trim().toUpperCase();
            return switch (t) {
                case "BLOCK" -> BLOCK;
                case "WARN" -> WARN;
                case "NIT" -> NIT;
                default -> WARN;
            };
        }
    }
}
