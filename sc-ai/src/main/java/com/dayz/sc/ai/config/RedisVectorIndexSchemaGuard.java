package com.dayz.sc.ai.config;

import com.dayz.sc.ai.service.ChatVectorMemoryService;
import com.dayz.sc.ai.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.FieldName;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RedisVectorIndexSchemaGuard.
 *
 * @author DaYZ
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.redis", name = "initialize-schema",
        havingValue = "true", matchIfMissing = true)
public class RedisVectorIndexSchemaGuard implements ApplicationRunner {

    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String ATTRIBUTE_KEY = "attribute";
    private static final Set<String> REQUIRED_ATTRIBUTES = Set.of(
            KnowledgeBaseService.META_USER_ID,
            KnowledgeBaseService.META_DOC_ID,
            KnowledgeBaseService.META_SOURCE_TYPE,
            ChatVectorMemoryService.META_CONVERSATION_ID,
            ChatVectorMemoryService.META_USER_MESSAGE_ID,
            ChatVectorMemoryService.META_ASSISTANT_MESSAGE_ID,
            ChatVectorMemoryService.META_COURSE_ID,
            ChatVectorMemoryService.META_DELETED
    );

    private final RedisClient redisClient;
    private final String indexName;

    public RedisVectorIndexSchemaGuard(
            RedisClient redisClient,
            @Value("${spring.ai.vectorstore.redis.index-name:edupivot-ai-idx}") String indexName) {
        this.redisClient = redisClient;
        this.indexName = indexName;
    }

    static Set<String> indexedAttributes(Object attributes) {
        Set<String> values = new LinkedHashSet<>();
        collectAttributeNames(attributes, values);
        return values;
    }

    private static void collectAttributeNames(Object value, Set<String> values) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (ATTRIBUTE_KEY.equals(String.valueOf(entry.getKey())) && entry.getValue() != null) {
                    values.add(String.valueOf(entry.getValue()));
                }
                collectAttributeNames(entry.getValue(), values);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            Object previous = null;
            for (Object item : collection) {
                if (ATTRIBUTE_KEY.equals(String.valueOf(previous)) && item != null) {
                    values.add(String.valueOf(item));
                }
                collectAttributeNames(item, values);
                previous = item;
            }
        }
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        try {
            ensureIndexSchema();
        } catch (Exception e) {
            log.warn("AI Redis vector index schema check failed indexName={}", indexName, e);
        }
    }

    void ensureIndexSchema() {
        Map<String, Object> info;
        try {
            info = redisClient.ftInfo(indexName);
        } catch (JedisDataException e) {
            if (isUnknownIndex(e)) {
                log.info("AI Redis vector index does not exist yet indexName={}", indexName);
                return;
            }
            throw e;
        }

        Set<String> indexedAttributes = indexedAttributes(info.get(ATTRIBUTES_KEY));
        Set<String> missingAttributes = new LinkedHashSet<>(REQUIRED_ATTRIBUTES);
        missingAttributes.removeAll(indexedAttributes);
        if (missingAttributes.isEmpty()) {
            log.debug("AI Redis vector index schema is up to date indexName={}", indexName);
            return;
        }

        redisClient.ftAlter(indexName, tagFields(missingAttributes));
        log.warn("AI Redis vector index schema was stale and has been altered "
                        + "indexName={} addedAttributes={}",
                indexName, missingAttributes);
    }

    private boolean isUnknownIndex(JedisDataException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("unknown index");
    }

    private List<SchemaField> tagFields(Set<String> attributes) {
        return attributes.stream()
                .map(attribute -> TagField.of(new FieldName("$." + attribute, attribute)))
                .collect(Collectors.toList());
    }
}
