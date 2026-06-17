package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.ClassParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for class participants.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public interface ClassParticipantRepository {

    /**
     * 根据课堂会话ID和用户ID查询参与者。
     *
     * @param sessionId 课堂会话ID
     * @param userId    用户ID
     * @return 参与者实体，可能为空
     */
    Optional<ClassParticipant> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    List<ClassParticipant> findBySessionId(UUID sessionId);

    Optional<ClassParticipant> findBySessionIdAndSeatIndex(UUID sessionId, Integer seatIndex);

    /**
     * 检查用户是否已参与指定课堂会话。
     *
     * @param sessionId 课堂会话ID
     * @param userId    用户ID
     * @return 是否已参与
     */
    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    /**
     * 保存课堂参与者。
     *
     * @param participant 参与者实体
     */
    void save(ClassParticipant participant);

    /**
     * 更新课堂参与者。
     *
     * @param participant 参与者实体
     */
    void update(ClassParticipant participant);

    void deleteBySessionIdAndUserId(UUID sessionId, UUID userId);
}
