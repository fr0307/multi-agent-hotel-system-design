package com.hotel.system.state.model;

/**
 * One ADD Step 4 design-concept alternative considered for an iteration.
 * Exactly one alternative per iteration must have {@code chosen = true}.
 */
public record Alternative(
        String concept,
        String pros,
        String cons,
        boolean chosen,
        String rationale
) {
    public Alternative {
        if (concept == null) concept = "";
        if (pros == null) pros = "";
        if (cons == null) cons = "";
        if (rationale == null) rationale = "";
    }
}
