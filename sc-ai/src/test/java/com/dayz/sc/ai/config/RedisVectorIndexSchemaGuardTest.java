package com.dayz.sc.ai.config;

import com.dayz.sc.ai.service.ChatVectorMemoryService;
import com.dayz.sc.ai.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.schemafields.SchemaField;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisVectorIndexSchemaGuardTest {

    @Test
    void ensureIndexSchemaShouldAlterStaleIndexWithoutDeletingDocuments() {
        RedisClient redisClient = mock(RedisClient.class);
        when(redisClient.ftInfo("idx")).thenReturn(Map.of(
                "attributes", List.of(
                        List.of("identifier", "$.content", "attribute", "content", "type", "TEXT"),
                        List.of("identifier", "$.embedding", "attribute", "embedding", "type", "VECTOR"),
                        List.of("identifier", "$.userId", "attribute", KnowledgeBaseService.META_USER_ID, "type", "TAG"),
                        List.of("identifier", "$.docId", "attribute", KnowledgeBaseService.META_DOC_ID, "type", "TAG")
                )
        ));
        RedisVectorIndexSchemaGuard guard = new RedisVectorIndexSchemaGuard(redisClient, "idx");

        guard.ensureIndexSchema();

        verify(redisClient).ftAlter(org.mockito.ArgumentMatchers.eq("idx"),
                org.mockito.ArgumentMatchers.<List<SchemaField>>argThat(fields ->
                        fields.stream().anyMatch(field -> field.getName().equals("$.sourceType")
                                && field.getFieldName().getAttribute().equals("sourceType"))
                                && fields.stream().anyMatch(field -> field.getName().equals("$.conversationId")
                                && field.getFieldName().getAttribute().equals("conversationId"))));
        verify(redisClient, never()).ftDropIndex("idx");
        verify(redisClient, never()).ftDropIndexDD("idx");
    }

    @Test
    void ensureIndexSchemaShouldKeepCurrentIndex() {
        RedisClient redisClient = mock(RedisClient.class);
        when(redisClient.ftInfo("idx")).thenReturn(Map.of(
                "attributes", List.of(
                        Map.of("attribute", KnowledgeBaseService.META_USER_ID),
                        Map.of("attribute", KnowledgeBaseService.META_DOC_ID),
                        Map.of("attribute", KnowledgeBaseService.META_SOURCE_TYPE),
                        Map.of("attribute", ChatVectorMemoryService.META_CONVERSATION_ID),
                        Map.of("attribute", ChatVectorMemoryService.META_USER_MESSAGE_ID),
                        Map.of("attribute", ChatVectorMemoryService.META_ASSISTANT_MESSAGE_ID),
                        Map.of("attribute", ChatVectorMemoryService.META_COURSE_ID),
                        Map.of("attribute", ChatVectorMemoryService.META_DELETED)
                )
        ));
        RedisVectorIndexSchemaGuard guard = new RedisVectorIndexSchemaGuard(redisClient, "idx");

        guard.ensureIndexSchema();

        verify(redisClient, never()).ftAlter(org.mockito.ArgumentMatchers.eq("idx"),
                org.mockito.ArgumentMatchers.<Iterable<SchemaField>>any());
        verify(redisClient, never()).ftDropIndex("idx");
    }

    @Test
    void ensureIndexSchemaShouldIgnoreMissingIndex() {
        RedisClient redisClient = mock(RedisClient.class);
        when(redisClient.ftInfo("idx")).thenThrow(new JedisDataException("Unknown index name"));
        RedisVectorIndexSchemaGuard guard = new RedisVectorIndexSchemaGuard(redisClient, "idx");

        assertThatCode(guard::ensureIndexSchema).doesNotThrowAnyException();

        verify(redisClient, never()).ftDropIndex("idx");
    }
}
