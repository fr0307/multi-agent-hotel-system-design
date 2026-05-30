package com.hotel.system.state.model;

/**
 * One entry in the Architect's ADD Step 7 goal-check array.
 * Each required driver for the current iteration must appear with a verdict
 * in {met, partial, missing, deferred}.
 */
public record GoalCheck(
        String driverId,
        String mechanismInDesign,
        String quantitativeEvidence,
        String verdict,
        String carryoverNote
) {
    public GoalCheck {
        if (driverId == null) driverId = "";
        if (mechanismInDesign == null) mechanismInDesign = "";
        if (quantitativeEvidence == null) quantitativeEvidence = "n/a";
        if (verdict == null) verdict = "missing";
        if (carryoverNote == null) carryoverNote = "";
    }
}
