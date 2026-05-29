package com.codereview.assistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codereview.assistant.ai.ModelConfigurationService;
import com.codereview.assistant.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModelConfigurationController.class)
@Import({
        ModelConfigurationController.class,
        ApiExceptionHandler.class,
        ModelConfigurationControllerTests.TestConfig.class
})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.model.default-provider-id=deepseek",
        "app.model.providers[0].id=deepseek",
        "app.model.providers[0].display-name=DeepSeek",
        "app.model.providers[0].base-url=https://api.deepseek.com",
        "app.model.providers[0].model-id=deepseek-chat",
        "app.model.providers[0].api-key-env=DEEPSEEK_API_KEY"
})
class ModelConfigurationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsConfiguredModelProviders() throws Exception {
        mockMvc.perform(get("/api/model-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProviderId").value("deepseek"))
                .andExpect(jsonPath("$.providers[0].id").value("deepseek"))
                .andExpect(jsonPath("$.providers[0].baseUrl").value("https://api.deepseek.com"))
                .andExpect(jsonPath("$.providers[0].modelId").value("deepseek-chat"))
                .andExpect(jsonPath("$.providers[0].apiKeyEnv").value("DEEPSEEK_API_KEY"));
    }

    @Test
    void resolvesRequestConfigurationWithoutEchoingApiKey() throws Exception {
        mockMvc.perform(post("/api/model-config/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "deepseek",
                                  "baseUrl": "https://api.deepseek.com/v1",
                                  "modelId": "deepseek-reasoner",
                                  "apiKey": "test-api-key"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("deepseek"))
                .andExpect(jsonPath("$.baseUrl").value("https://api.deepseek.com/v1"))
                .andExpect(jsonPath("$.modelId").value("deepseek-reasoner"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(content().string(not(containsString("test-api-key"))));
    }

    @Test
    void returnsBadRequestForInvalidModelConfig() throws Exception {
        mockMvc.perform(post("/api/model-config/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "deepseek",
                                  "baseUrl": "not-a-url",
                                  "modelId": "deepseek-chat",
                                  "apiKey": "test-api-key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MODEL_CONFIG"));
    }

    @Configuration
    static class TestConfig {

        @Bean
        ModelConfigurationService modelConfigurationService(AppProperties properties, org.springframework.core.env.Environment environment) {
            return new ModelConfigurationService(properties, environment);
        }
    }
}
