package com.dayz.sc.course.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LiveKit configuration.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitConfiguration {
}
