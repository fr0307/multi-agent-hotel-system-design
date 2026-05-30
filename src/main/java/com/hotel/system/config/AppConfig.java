package com.hotel.system.config;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class AppConfig {
    public final int iterations;
    public final int maxRevisions;
    public final Path outputDir;

    public final String openAiBaseUrl;
    public final String openAiApiKey;
    public final String openAiModel;

    /** "Multi-Agent (Distributed reasoning + collaborative verification)". Used by report Section 二. */
    public final String paradigm;

    /** Whether HumanCheckpoint nodes should auto-approve without blocking on stdin. */
    public final boolean autoApprove;

    /** Whether to fall back to MockChatModel when no API key is configured. Off by default. */
    public final boolean useMock;

    /** Upper bound (UTF-8 bytes) for prior_carryover propagated to the next iteration. */
    private final int carryoverCapBytes;

    private AppConfig(int iterations, int maxRevisions, Path outputDir,
                      String openAiBaseUrl, String openAiApiKey, String openAiModel,
                      String paradigm, boolean autoApprove, boolean useMock, int carryoverCapBytes) {
        this.iterations = iterations;
        this.maxRevisions = maxRevisions;
        this.outputDir = outputDir;
        this.openAiBaseUrl = openAiBaseUrl;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.paradigm = paradigm;
        this.autoApprove = autoApprove;
        this.useMock = useMock;
        this.carryoverCapBytes = carryoverCapBytes;
    }

    public int carryoverCapBytes() {
        return carryoverCapBytes;
    }

    public static AppConfig fromEnv() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }

        int iterations = parseIntOrDefault(getPropOrEnv(props, "ma.iterations", "MA_ITERATIONS"), 4);
        int maxRevisions = parseIntOrDefault(getPropOrEnv(props, "ma.max-revisions", "MA_MAX_REVISIONS"), 2);

        String out = getPropOrEnv(props, "ma.output-dir", "MA_OUTPUT_DIR");
        Path outputDir = out == null || out.isBlank() ? Path.of("").toAbsolutePath() : Path.of(out).toAbsolutePath();

        String baseUrl = getPropOrEnv(props, "spring.ai.openai.base-url", "APP_OPENAI_BASE_URL");
        String apiKey = getPropOrEnv(props, "spring.ai.openai.api-key", "APP_OPENAI_API_KEY");
        String model = getPropOrEnv(props, "spring.ai.openai.chat.options.model", "APP_OPENAI_MODEL");
        if (model == null || model.isBlank()) model = "qwen3-32b";

        String paradigm = getPropOrEnv(props, "ma.paradigm", "MA_PARADIGM");
        if (paradigm == null || paradigm.isBlank()) {
            paradigm = "Multi-Agent (Distributed reasoning + collaborative verification)";
        }

        boolean autoApprove = parseBoolOrDefault(getPropOrEnv(props, "ma.auto-approve", "MA_AUTO_APPROVE"), false);
        boolean useMock = parseBoolOrDefault(getPropOrEnv(props, "ma.use-mock", "MA_USE_MOCK"), false);
        int carryover = parseIntOrDefault(getPropOrEnv(props, "ma.carryover-cap-bytes", "MA_CARRYOVER_CAP_BYTES"), 2048);

        return new AppConfig(iterations, maxRevisions, outputDir, baseUrl, apiKey, model,
                paradigm, autoApprove, useMock, carryover);
    }

    private static String getPropOrEnv(Properties props, String propKey, String envKey) {
        // Priority 1: application.properties
        String propVal = props.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        // Priority 2: Environment variables
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        // Priority 3: JVM system properties (-Dxxx)
        String sysVal = System.getProperty(propKey);
        if (sysVal != null && !sysVal.isBlank()) {
            return sysVal.trim();
        }
        return null;
    }

    private static int parseIntOrDefault(String v, int d) {
        if (v == null || v.isBlank()) return d;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception ignored) {
            return d;
        }
    }

    private static boolean parseBoolOrDefault(String v, boolean d) {
        if (v == null || v.isBlank()) return d;
        String t = v.trim().toLowerCase();
        return switch (t) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> d;
        };
    }
}
