package com.hotel.system.state.model;

/**
 * A single provided interface of an ADD Step 5 component.
 * The {@code signatureOrTopic} captures the concrete contract: a REST path,
 * a method signature, a Kafka topic name, etc.
 */
public record InterfaceSpec(
        String name,
        String signatureOrTopic,
        String protocol,
        String payloadSchema
) {
    public InterfaceSpec {
        if (name == null) name = "";
        if (signatureOrTopic == null) signatureOrTopic = "";
        if (protocol == null) protocol = "";
        if (payloadSchema == null) payloadSchema = "";
    }
}
