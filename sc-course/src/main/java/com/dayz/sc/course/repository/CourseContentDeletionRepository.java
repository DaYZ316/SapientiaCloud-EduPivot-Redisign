package com.dayz.sc.course.repository;

import java.util.UUID;

/**
 * Internal batch deletion operations for course-owned content.
 *
 * @author DaYZ
 */
public interface CourseContentDeletionRepository {

    /**
     * Deletes content that belongs to the specified course.
     *
     * @param courseId course identifier
     */
    void deleteCourseContent(UUID courseId);

    /**
     * Deletes questions and nested question content that belongs to the specified question bank.
     *
     * @param questionBankId question bank identifier
     */
    void deleteQuestionBankContent(UUID questionBankId);

    /**
     * Deletes classroom session dependent content that belongs to the specified class session.
     *
     * @param sessionId class session identifier
     */
    void deleteClassSessionContent(UUID sessionId);
}
