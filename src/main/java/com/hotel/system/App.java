package com.hotel.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.system.ai.MockChatModel;
import com.hotel.system.config.AppConfig;
import com.hotel.system.engine.MultiAgentEngine;
import com.hotel.system.io.ConsoleIO;
import com.hotel.system.log.MarkdownLogWriter;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        var cfg = AppConfig.fromEnv();
        var mapper = new ObjectMapper();

        ChatModel chatModel = createChatModel(mapper, cfg);
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var io = new ConsoleIO(reader, cfg);
        var writer = new MarkdownLogWriter(cfg.outputDir, mapper);

        var engine = new MultiAgentEngine(cfg, mapper, chatClient, io, writer);
        engine.run();

        log.info("Done. Outputs: {}", cfg.outputDir.toAbsolutePath());
        log.info(" - {}", cfg.outputDir.resolve("conversation_log.md").toAbsolutePath());
        log.info(" - {}", cfg.outputDir.resolve("architecture_report.md").toAbsolutePath());
    }

    private static ChatModel createChatModel(ObjectMapper mapper, AppConfig cfg) {
        // Special handling: if the configured model is a qwen-family model AND we have a
        // bailian / dashscope API key, prefer the DashScope-specific ChatModel so we can
        // turn off the "thinking" feature that messes with strict-JSON outputs.
        if (cfg.openAiApiKey != null && !cfg.openAiApiKey.isBlank()
                && cfg.openAiModel != null && cfg.openAiModel.toLowerCase().startsWith("qwen")) {
            DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(cfg.openAiApiKey).build();
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel(cfg.openAiModel)
                    .withEnableThinking(false)
                    .build();
            return DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .defaultOptions(options)
                    .build();
        }

        if (cfg.openAiApiKey != null && !cfg.openAiApiKey.isBlank()) {
            String baseUrl = cfg.openAiBaseUrl != null && !cfg.openAiBaseUrl.isBlank()
                    ? cfg.openAiBaseUrl
                    : "https://api.openai.com";
            var openAiApi = org.springframework.ai.openai.api.OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(cfg.openAiApiKey)
                    .build();
            var options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                    .model(cfg.openAiModel)
                    .build();
            return org.springframework.ai.openai.OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();
        }

        // Fallback: a raw env var named AI_DASHSCOPE_API_KEY (legacy convenience).
        String key = System.getenv("AI_DASHSCOPE_API_KEY");
        if (key != null && !key.isBlank()) {
            DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(key).build();
            return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
        }

        // No real API key available. Only fall back to MockChatModel if the user
        // explicitly opted in (ma.use-mock=true) — otherwise fail fast so we never
        // ship a "mock-generated" report by accident.
        if (cfg.useMock) {
            log.warn("No API key configured but ma.use-mock=true → using MockChatModel. "
                    + "The generated report will be deterministic stubs, NOT a real LLM design.");
            return new MockChatModel(mapper);
        }
        throw new IllegalStateException(
                "No LLM API key configured. Set spring.ai.openai.api-key in "
                        + "src/main/resources/application.properties (see application.properties.example),"
                        + " or export APP_OPENAI_API_KEY=... ."
                        + " To explicitly opt into the offline mock, set ma.use-mock=true.");
    }
}
