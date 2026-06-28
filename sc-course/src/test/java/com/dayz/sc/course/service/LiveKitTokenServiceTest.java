package com.dayz.sc.course.service;

import com.dayz.sc.course.config.LiveKitProperties;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveKitTokenServiceTest {

    @Test
    void createToken_shouldAllowDataPublishingWithoutMediaPublishing() throws Exception {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setUrl("wss://live.test");
        properties.setApiKey("test-key");
        properties.setApiSecret("01234567890123456789012345678901");
        properties.setTokenTtl(Duration.ofHours(1));
        LiveKitTokenService service = new LiveKitTokenService(properties);

        String token = service.createToken(UUID.randomUUID(), "class-session-test", false).token();

        Map<?, ?> videoClaims = (Map<?, ?>) SignedJWT.parse(token).getJWTClaimsSet().getClaim("video");
        assertThat(videoClaims.get("canPublish")).isEqualTo(false);
        assertThat(videoClaims.get("canPublishData")).isEqualTo(true);
    }
}
