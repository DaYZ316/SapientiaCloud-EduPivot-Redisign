package com.dayz.sc.common.events.config;

public final class KafkaTopicConstants {
    public static final String COURSE_EVENTS = "sc.course.events";
    public static final String USER_EVENTS = "sc.user.events";

    /** Dead-letter topic 后缀，DeadLetterPublishingRecoverer 自动追加到原 topic 名后 */
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaTopicConstants() {}
}
