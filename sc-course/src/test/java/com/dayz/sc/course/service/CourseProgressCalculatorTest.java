package com.dayz.sc.course.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseProgressCalculatorTest {

    @Test
    void calculate_shouldUsePublishedSessionsOverTotalClassHours() {
        assertThat(CourseProgressCalculator.calculate(10, 3)).isEqualTo(30);
    }

    @Test
    void calculate_shouldCapAtOneHundred() {
        assertThat(CourseProgressCalculator.calculate(3, 5)).isEqualTo(100);
    }

    @Test
    void calculate_shouldReturnZero_whenTotalClassHoursMissing() {
        assertThat(CourseProgressCalculator.calculate(null, 3)).isZero();
        assertThat(CourseProgressCalculator.calculate(0, 3)).isZero();
    }
}
