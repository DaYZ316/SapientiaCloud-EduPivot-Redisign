package com.dayz.sc.course.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LiveKit configuration.
 */
@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitConfiguration {
}
