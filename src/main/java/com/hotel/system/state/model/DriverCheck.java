package com.hotel.system.state.model;

/**
 * Critic's per-driver verdict for the current iteration.
 * One entry per required driver of this iteration.
 */
public record DriverCheck(
        String driverId,
        String evidenceInDesign,
        String evidenceInDiagram,
        String verdict
) {
    public DriverCheck {
        if (driverId == null) driverId = "";
        if (evidenceInDesign == null) evidenceInDesign = "";
        if (evidenceInDiagram == null) evidenceInDiagram = "";
        if (verdict == null) verdict = "missing";
    }
}
