package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.PracticeAnswer;

import java.util.List;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public interface PracticeAnswerRepository {

    void save(PracticeAnswer answer);

    List<PracticeAnswer> findBySessionId(UUID sessionId);

    List<PracticeAnswer> findByQuestionId(UUID questionId);
}
