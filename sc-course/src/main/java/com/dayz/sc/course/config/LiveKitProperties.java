package com.dayz.sc.course.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LiveKit connection settings.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "edupivot.livekit")
public class LiveKitProperties {

    private String url;
    private String serverUrl;
    private String apiKey;
    private String apiSecret;
    private Duration tokenTtl = Duration.ofHours(2);

    public boolean configured() {
        return hasText(url) && hasText(apiKey) && hasText(apiSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
