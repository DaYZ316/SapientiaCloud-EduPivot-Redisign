package com.dayz.sc.course.service;

final class CourseProgressCalculator {

    private CourseProgressCalculator() {
    }

    static int calculate(Integer totalClassHours, long publishedClassSessionCount) {
        if (totalClassHours == null || totalClassHours <= 0 || publishedClassSessionCount <= 0) {
            return 0;
        }

        long percent = Math.round((publishedClassSessionCount * 100.0) / totalClassHours);
        return (int) Math.min(percent, 100);
    }
}
