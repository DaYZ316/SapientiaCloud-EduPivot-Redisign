package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request to update a class session draft.
 */
public record UpdateClassSessionRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Integer roomSize
) {}
