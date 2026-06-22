/**
 * KafkaTopicConstants 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
package com.dayz.sc.common.events.config;

public final class KafkaTopicConstants {
    public static final String COURSE_EVENTS = "sc.course.events";
    public static final String USER_EVENTS = "sc.user.events";
    public static final String AI_GRADING_REQUESTS = "sc.ai.grading.requests";
    public static final String AI_GRADING_RESULTS = "sc.ai.grading.results";
    public static final String QUESTION_GENERATION_REQUESTS = "sc.ai.question-generation.requests";
    public static final String QUESTION_GENERATION_PROGRESS = "sc.ai.question-generation.progress";
    public static final String QUESTION_GENERATION_RESPONSES = "sc.ai.question-generation.responses";

    /**
     * Dead-letter topic 后缀，DeadLetterPublishingRecoverer 自动追加到原 topic 名后
     */
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaTopicConstants() {
    }
}
