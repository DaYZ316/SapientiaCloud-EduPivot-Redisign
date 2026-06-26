package com.dayz.sc.common.security.support;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayJwtTest {

    @Test
    void fromHeaders_shouldCreateJwtWhenRoleHeaderIsMissing() {
        UUID userId = UUID.randomUUID();

        GatewayJwt jwt = GatewayJwt.fromHeaders(userId.toString(), null);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getUserId()).isEqualTo(userId);
        assertThat(jwt.getRole()).isNull();
        assertThat(jwt.getClaims()).doesNotContainKey("role");
    }
}
