package com.dayz.sc.common.security.token;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeysUsageTest {

    @Test
    void productionSecurityCode_shouldNotUseBlockingRedisKeysCommand() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            var offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains(".keys(") || source.contains("redisTemplate.keys(");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }
}
