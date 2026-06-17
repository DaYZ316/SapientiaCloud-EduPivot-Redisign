package com.dayz.sc.common.security.support;

import lombok.Getter;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serial;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 从 Gateway 注入的请求头构建的轻量级 Jwt 对象。
 * <p>
 * 供下游服务在信任 Gateway 已完成 JWT 验证的场景下使用，
 * 避免重复进行 RS256 签名验证和 JWKS 公钥获取。
 * <p>
 * 仅包含 Gateway 注入的两个声明：{@code sub}（userId）和 {@code role}，
 * 不包含原始 JWT 的其他声明（如 jti、iat 等）
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Getter
public class GatewayJwt extends Jwt {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final Integer role;

    public GatewayJwt(UUID userId, Integer role) {
        super(
                "gateway-trusted",
                Instant.EPOCH,
                Instant.now().plusSeconds(86400),
                Map.of("alg", "gateway-trusted"),
                Map.of("sub", userId.toString(), "role", role)
        );
        this.userId = userId;
        this.role = role;
    }

    /**
     * 从请求头值构建 GatewayJwt。
     *
     * @param userIdHeader X-User-Id 请求头值
     * @param roleHeader   X-User-Role 请求头值
     * @return GatewayJwt 实象，如果任一头部缺失或无效则返回 null
     */
    public static GatewayJwt fromHeaders(String userIdHeader, String roleHeader) {
        if (userIdHeader == null || userIdHeader.isBlank() || roleHeader == null || roleHeader.isBlank()) {
            return null;
        }
        try {
            UUID userId = UUID.fromString(userIdHeader.trim());
            Integer role = Integer.parseInt(roleHeader.trim());
            return new GatewayJwt(userId, role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
