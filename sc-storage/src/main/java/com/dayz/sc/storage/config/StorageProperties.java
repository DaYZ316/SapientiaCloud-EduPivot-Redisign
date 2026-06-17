package com.dayz.sc.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 属性配置类
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@ConfigurationProperties(prefix = "edupivot.storage")
public class StorageProperties {

    private final Minio minio = new Minio();
    private final Bucket bucket = new Bucket();
    private final Expiry expiry = new Expiry();
    private final Limits limits = new Limits();
    private final Conversion conversion = new Conversion();

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String externalEndpoint;
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";
    }

    @Getter
    @Setter
    public static class Bucket {
        private String media = "edupivot-media";
        private String course = "edupivot-course";
        private String ai = "edupivot-ai";
    }

    @Getter
    @Setter
    public static class Expiry {
        private int uploadMinutes = 10;
        private int downloadMinutes = 30;
    }

    @Getter
    @Setter
    public static class Limits {
        private long avatarBytes = 2L * 1024 * 1024;
        private long courseCoverBytes = 5L * 1024 * 1024;
        private long forumImageBytes = 5L * 1024 * 1024;
        private long courseFileBytes = 200L * 1024 * 1024;
        private long aiFileBytes = 100L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Conversion {
        private String officeHome;
        private int maxTasksPerProcess = 200;
        private long taskTimeoutMs = 120000;
    }
}
