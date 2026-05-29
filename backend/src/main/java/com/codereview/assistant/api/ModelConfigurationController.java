package com.codereview.assistant.api;

import com.codereview.assistant.ai.AiModelConfigInput;
import com.codereview.assistant.ai.AiModelProvider;
import com.codereview.assistant.ai.ModelConfigurationService;
import com.codereview.assistant.ai.ResolvedAiModelConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigurationController {

    private final ModelConfigurationService configurationService;

    public ModelConfigurationController(ModelConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ModelConfigurationOptionsResponse options() {
        var options = configurationService.options();
        return new ModelConfigurationOptionsResponse(
                options.defaultProviderId(),
                options.providers().stream()
                        .map(ModelConfigurationController::toResponse)
                        .toList()
        );
    }

    @PostMapping("/resolve")
    public ResolvedModelConfigurationResponse resolve(@Valid @RequestBody ModelConfigurationRequest request) {
        return toResponse(configurationService.resolve(new AiModelConfigInput(
                request.providerId(),
                request.baseUrl(),
                request.modelId(),
                request.apiKey()
        )));
    }

    private static ModelProviderResponse toResponse(AiModelProvider provider) {
        return new ModelProviderResponse(
                provider.id(),
                provider.displayName(),
                provider.baseUrl(),
                provider.modelId(),
                provider.apiKeyEnv(),
                provider.apiKeyAvailable()
        );
    }

    private static ResolvedModelConfigurationResponse toResponse(ResolvedAiModelConfig config) {
        return new ResolvedModelConfigurationResponse(
                config.providerId(),
                config.displayName(),
                config.baseUrl(),
                config.modelId(),
                config.apiKeyConfigured(),
                config.ready(),
                config.readinessMessage()
        );
    }

    public record ModelConfigurationRequest(
            @Size(max = 80, message = "Provider ID is too long.") String providerId,
            @Size(max = 300, message = "Base URL is too long.") String baseUrl,
            @Size(max = 120, message = "Model ID is too long.") String modelId,
            @Size(max = 500, message = "API Key is too long.") String apiKey
    ) {
    }

    public record ModelConfigurationOptionsResponse(
            String defaultProviderId,
            List<ModelProviderResponse> providers
    ) {
    }

    public record ModelProviderResponse(
            String id,
            String displayName,
            String baseUrl,
            String modelId,
            String apiKeyEnv,
            boolean apiKeyAvailable
    ) {
    }

    public record ResolvedModelConfigurationResponse(
            String providerId,
            String displayName,
            String baseUrl,
            String modelId,
            boolean apiKeyConfigured,
            boolean ready,
            String message
    ) {
    }
}
