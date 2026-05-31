package com.codereview.assistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codereview.assistant.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ModelConfigurationServiceTests {

    private static final AppProperties.Provider DEEPSEEK = new AppProperties.Provider(
            "deepseek",
            "DeepSeek",
            "https://api.deepseek.com",
            "deepseek-chat",
            "DEEPSEEK_API_KEY"
    );
    private static final AppProperties.Provider CUSTOM = new AppProperties.Provider(
            "custom",
            "Custom compatible model",
            "https://api.deepseek.com",
            "deepseek-chat",
            "AI_MODEL_API_KEY"
    );

    @Test
    void exposesDefaultProviderWithoutLeakingApiKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "test-api-key");
        ModelConfigurationService service = service(environment);

        AiModelProviderOptions options = service.options();

        assertThat(options.defaultProviderId()).isEqualTo("deepseek");
        assertThat(options.providers()).first().satisfies(provider -> {
            assertThat(provider.id()).isEqualTo("deepseek");
            assertThat(provider.baseUrl()).isEqualTo("https://api.deepseek.com");
            assertThat(provider.modelId()).isEqualTo("deepseek-chat");
            assertThat(provider.apiKeyEnv()).isEqualTo("DEEPSEEK_API_KEY");
            assertThat(provider.apiKeyAvailable()).isTrue();
        });
        assertThat(options.providers()).anySatisfy(provider -> {
            assertThat(provider.id()).isEqualTo("custom");
            assertThat(provider.apiKeyEnv()).isEqualTo("AI_MODEL_API_KEY");
            assertThat(provider.apiKeyAvailable()).isFalse();
        });
    }

    @Test
    void resolvesDefaultProviderFromEnvironmentApiKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "test-api-key");
        ModelConfigurationService service = service(environment);

        ResolvedAiModelConfig config = service.resolve(new AiModelConfigInput(null, null, null, null));

        assertThat(config.providerId()).isEqualTo("deepseek");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.modelId()).isEqualTo("deepseek-chat");
        assertThat(config.apiKey()).isEqualTo("test-api-key");
        assertThat(config.ready()).isTrue();
    }

    @Test
    void requestApiKeyOverridesEnvironmentAndIsMarkedReady() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "env-api-key");
        ModelConfigurationService service = service(environment);

        ResolvedAiModelConfig config = service.resolve(new AiModelConfigInput(
                "deepseek",
                "https://api.deepseek.com/v1",
                "deepseek-reasoner",
                "request-api-key"
        ));

        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(config.modelId()).isEqualTo("deepseek-reasoner");
        assertThat(config.apiKey()).isEqualTo("request-api-key");
        assertThat(config.ready()).isTrue();
    }

    @Test
    void resolvesCustomProviderFromRequestFields() {
        ModelConfigurationService service = service(new MockEnvironment());

        ResolvedAiModelConfig config = service.resolve(new AiModelConfigInput(
                "custom",
                "https://openrouter.ai/api",
                "custom-review-model",
                "request-api-key"
        ));

        assertThat(config.providerId()).isEqualTo("custom");
        assertThat(config.baseUrl()).isEqualTo("https://openrouter.ai/api");
        assertThat(config.modelId()).isEqualTo("custom-review-model");
        assertThat(config.apiKey()).isEqualTo("request-api-key");
        assertThat(config.ready()).isTrue();
    }

    @Test
    void missingApiKeyKeepsConfigurationNotReady() {
        ModelConfigurationService service = service(new MockEnvironment());

        ResolvedAiModelConfig config = service.resolve(new AiModelConfigInput(null, null, null, null));

        assertThat(config.apiKeyConfigured()).isFalse();
        assertThat(config.ready()).isFalse();
        assertThat(config.readinessMessage()).contains("API Key", "DEEPSEEK_API_KEY");
    }

    @Test
    void rejectsUnknownProvider() {
        ModelConfigurationService service = service(new MockEnvironment());

        assertThatThrownBy(() -> service.resolve(new AiModelConfigInput("unknown", null, null, null)))
                .isInstanceOf(InvalidModelConfigurationException.class)
                .hasMessageContaining("Unknown model provider");
    }

    @Test
    void rejectsInvalidBaseUrl() {
        ModelConfigurationService service = service(new MockEnvironment());

        assertThatThrownBy(() -> service.resolve(new AiModelConfigInput("deepseek", "not-a-url", null, "test-api-key")))
                .isInstanceOf(InvalidModelConfigurationException.class)
                .hasMessageContaining("Base URL");
    }

    private static ModelConfigurationService service(MockEnvironment environment) {
        return new ModelConfigurationService(
                new AppProperties(
                        "AI PR Review Assistant",
                        new AppProperties.GitHub(false),
                        new AppProperties.Model("deepseek", List.of(DEEPSEEK, CUSTOM)),
                        null
                ),
                environment
        );
    }
}
