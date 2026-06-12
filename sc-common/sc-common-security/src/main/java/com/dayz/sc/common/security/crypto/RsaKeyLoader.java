package com.dayz.sc.common.security.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 从 PEM 格式字符串加载 RSA 公私钥。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public final class RsaKeyLoader {

    private static final String PEM_HEADER_PATTERN = "-----BEGIN %s-----";
    private static final String PEM_FOOTER_PATTERN = "-----END %s-----";

    private RsaKeyLoader() {
    }

    /**
     * 从 PEM 格式字符串加载 RSA 私钥。
     *
     * @param pem PEM 格式私钥（支持带/不带 PEM 头尾）
     * @return RSAPrivateKey
     */
    public static RSAPrivateKey loadPrivateKey(String pem) {
        try {
            String cleanPem = stripPemHeaders(pem);
            byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("无法加载 RSA 私钥，请检查 edupivot.security.jwt.private-key 配置", e);
        }
    }

    /**
     * 从 PEM 格式字符串加载 RSA 公钥。
     *
     * @param pem PEM 格式公钥（支持带/不带 PEM 头尾）
     * @return RSAPublicKey
     */
    public static RSAPublicKey loadPublicKey(String pem) {
        try {
            String cleanPem = stripPemHeaders(pem);
            byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("无法加载 RSA 公钥，请检查 edupivot.security.jwt.public-key 配置", e);
        }
    }

    private static String stripPemHeaders(String pem) {
        if (pem == null) {
            throw new IllegalArgumentException("PEM 字符串不能为 null");
        }
        String cleaned = pem.trim()
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("PEM 内容为空");
        }
        return cleaned;
    }
}
