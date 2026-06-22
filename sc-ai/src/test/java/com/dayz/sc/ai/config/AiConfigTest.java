package com.dayz.sc.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @Test
    void objectMapperShouldSerializeInstantAsIsoString() throws Exception {
        ObjectMapper objectMapper = new AiConfig().objectMapper();

        String json = objectMapper.writeValueAsString(
                Map.of("timestamp", Instant.parse("2026-06-21T11:42:36.248Z")));

        assertThat(json).contains("\"timestamp\":\"2026-06-21T11:42:36.248Z\"");
    }
}
