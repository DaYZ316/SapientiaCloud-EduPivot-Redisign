package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.ClassParticipant;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for class participants.
 */
public interface ClassParticipantRepository {

    Optional<ClassParticipant> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    void save(ClassParticipant participant);

    void update(ClassParticipant participant);
}
