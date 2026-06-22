package com.dayz.sc.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 课程服务启动类
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@SpringBootApplication
@EnableScheduling
public class ScCourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScCourseApplication.class, args);
    }
}
