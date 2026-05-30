package com.hotel.system.state.model;

import java.util.List;

/**
 * An ADD Step 5 instantiated architectural element: name, responsibilities,
 * provided interfaces (concrete contracts), and required interfaces (names only).
 */
public record ComponentSpec(
        String name,
        List<String> responsibilities,
        List<InterfaceSpec> providedInterfaces,
        List<String> requiredInterfaces
) {
    public ComponentSpec {
        if (name == null) name = "";
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        providedInterfaces = providedInterfaces == null ? List.of() : List.copyOf(providedInterfaces);
        requiredInterfaces = requiredInterfaces == null ? List.of() : List.copyOf(requiredInterfaces);
    }
}
