package com.dayz.sc.ai.config;

import com.dayz.sc.ai.model.vo.GenerationTraceEntry;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgresJsonbMapTypeHandlerTest {

    @Test
    void setNonNullParameterShouldSerializeGenerationTraceInstant() throws Exception {
        PostgresJsonbMapTypeHandler handler = new PostgresJsonbMapTypeHandler();
        PreparedStatement statement = mock(PreparedStatement.class);
        Instant timestamp = Instant.parse("2026-06-21T11:42:36.248Z");
        Map<String, Object> payload = Map.of("generationTrace", List.of(
                new GenerationTraceEntry(
                        "trace-1",
                        "CONTEXT_READY",
                        "question-generation",
                        "context_summary",
                        "上下文已准备",
                        "已收集题库样例",
                        Map.of("matchedCount", 1),
                        timestamp)));

        handler.setNonNullParameter(statement, 6, payload, JdbcType.OTHER);

        verify(statement).setObject(eq(6), org.mockito.ArgumentMatchers.argThat(value -> {
            assertThat(value).isInstanceOf(PGobject.class);
            PGobject jsonb = (PGobject) value;
            assertThat(jsonb.getType()).isEqualTo("jsonb");
            assertThat(jsonb.getValue())
                    .contains("\"generationTrace\"")
                    .contains("\"timestamp\":\"2026-06-21T11:42:36.248Z\"");
            return true;
        }));
    }
}
