package com.codereview.assistant.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codereview.assistant.config.AppProperties;
import org.junit.jupiter.api.Test;

class HealthControllerTests {

    @Test
    void returnsHealthyServiceStatus() {
        HealthController controller = new HealthController(new AppProperties(null, null, null, null));

        HealthController.HealthResponse response = controller.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("AI PR Review Assistant");
        assertThat(response.checkedAt()).isNotNull();
    }
}
