package com.dayz.sc.storage.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * 配置类。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Configuration
@RequiredArgsConstructor
public class MinioConfiguration {

    private final StorageProperties storageProperties;

    @Bean
    @Primary
    public MinioClient minioClient() {
        StorageProperties.Minio minio = storageProperties.getMinio();
        return buildClient(minio.getEndpoint(), minio);
    }

    @Bean
    @Qualifier("presignMinioClient")
    public MinioClient presignMinioClient() {
        StorageProperties.Minio minio = storageProperties.getMinio();
        String endpoint = StringUtils.hasText(minio.getExternalEndpoint())
                ? minio.getExternalEndpoint()
                : minio.getEndpoint();
        return buildClient(endpoint, minio);
    }

    private MinioClient buildClient(String endpoint, StorageProperties.Minio minio) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(minio.getAccessKey(), minio.getSecretKey());
        if (StringUtils.hasText(minio.getRegion())) {
            builder.region(minio.getRegion());
        }
        return builder.build();
    }
}
