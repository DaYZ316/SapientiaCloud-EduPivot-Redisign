package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.PracticeSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository {

    Optional<PracticeSession> findById(UUID id);

    void save(PracticeSession session);

    void update(PracticeSession session);

    List<PracticeSession> findBySysUserId(UUID sysUserId);

    List<PracticeSession> findByQuestionBankId(UUID questionBankId);

    long countByQuestionBankId(UUID questionBankId);

    long countBySysUserId(UUID sysUserId);
}
