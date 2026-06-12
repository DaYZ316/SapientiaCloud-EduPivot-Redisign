package com.dayz.sc.common.security.endpoint;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.security.crypto.RsaKeyLoader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

/**
 * JWKS（JSON Web Key Set）端点。
 * <p>
 * 仅在配置了 {@code edupivot.security.jwt.private-key} 时激活。
 * 其他微服务通过此端点获取公钥以验证 JWT 签名。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@RestController
@ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "private-key")
public class JwksController {

    private final RSAKey rsaJwk;

    public JwksController(JwtProperties jwtProperties) {
        try {
            RSAPrivateKey privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.getPrivateKey());
            // 从私钥的模数和公钥指数提取公钥
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    privateKey.getModulus(),
                    java.math.BigInteger.valueOf(65537) // RSA 标准公钥指数
            );
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
            this.rsaJwk = new RSAKey.Builder(publicKey)
                    .keyID("sc-edupivot-rsa-key-1")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("无法从私钥提取公钥用于 JWKS 端点", e);
        }
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(rsaJwk).toJSONObject();
    }
}
