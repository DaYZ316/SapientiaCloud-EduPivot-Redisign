package com.dayz.sc.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.notification.model.entity.NotificationReadStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通知已读状态 Mapper
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Mapper
public interface NotificationReadStatusMapper extends BaseMapper<NotificationReadStatus> {

    /**
     * 统计用户的未读通知数量
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 未读通知数量
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ntf_notification n
            WHERE n.deleted = 0
            <if test="type != null">
              AND n.type = #{type}
            </if>
              AND (n.sender_id IS NULL OR n.sender_id != #{userId})
              AND (n.target_type = 0 OR EXISTS (
                SELECT 1 FROM ntf_notification_target t
                WHERE t.notification_id = n.id AND t.user_id = #{userId} AND t.deleted = 0
              ))
              AND NOT EXISTS (
                SELECT 1
                FROM ntf_read_status r
                WHERE r.notification_id = n.id
                  AND r.user_id = #{userId}
              )
            </script>
            """)
    long countUnread(@Param("userId") UUID userId, @Param("type") Integer type);

    /**
     * 一次查询返回全部未读计数（total / system / teaching）
     * 使用 UNION ALL 将广播通知与指定用户通知拆分为两段独立查询，
     * 避免 OR 条件阻碍索引选择
     *
     * @param userId 用户ID
     * @return 包含systemCount、teachingCount、totalCount的Map
     */
    @Select("""
            <script>
            SELECT
              SUM(CASE WHEN sub.type = 1 THEN 1 ELSE 0 END) AS system_count,
              SUM(CASE WHEN sub.type = 2 THEN 1 ELSE 0 END) AS teaching_count,
              COUNT(*) AS total_count
            FROM (
              -- 广播通知（target_type = 0）
              SELECT n.type
              FROM ntf_notification n
              WHERE n.deleted = 0
                AND n.target_type = 0
                AND (n.sender_id IS NULL OR n.sender_id != #{userId})
                AND NOT EXISTS (
                  SELECT 1 FROM ntf_read_status r
                  WHERE r.notification_id = n.id AND r.user_id = #{userId}
                )
              UNION ALL
              -- 指定用户通知（target_type = 1）
              SELECT n.type
              FROM ntf_notification n
              WHERE n.deleted = 0
                AND n.target_type = 1
                AND (n.sender_id IS NULL OR n.sender_id != #{userId})
                AND EXISTS (
                  SELECT 1 FROM ntf_notification_target t
                  WHERE t.notification_id = n.id AND t.user_id = #{userId} AND t.deleted = 0
                )
                AND NOT EXISTS (
                  SELECT 1 FROM ntf_read_status r
                  WHERE r.notification_id = n.id AND r.user_id = #{userId}
                )
            ) sub
            </script>
            """)
    @Results({
            @Result(property = "systemCount", column = "system_count"),
            @Result(property = "teachingCount", column = "teaching_count"),
            @Result(property = "totalCount", column = "total_count")
    })
    Map<String, Long> countUnreadAll(@Param("userId") UUID userId);

    /**
     * 将用户的所有通知标记为已读
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 受影响的行数
     */
    @Insert("""
            <script>
            INSERT INTO ntf_read_status (id, notification_id, user_id, read_at)
            SELECT gen_random_uuid(), n.id, #{userId}, CURRENT_TIMESTAMP
            FROM ntf_notification n
            WHERE n.deleted = 0
            <if test="type != null">
              AND n.type = #{type}
            </if>
              AND (n.sender_id IS NULL OR n.sender_id != #{userId})
              AND (n.target_type = 0 OR EXISTS (
                SELECT 1 FROM ntf_notification_target t
                WHERE t.notification_id = n.id AND t.user_id = #{userId} AND t.deleted = 0
              ))
              AND NOT EXISTS (
                SELECT 1
                FROM ntf_read_status r
                WHERE r.notification_id = n.id
                  AND r.user_id = #{userId}
              )
            ON CONFLICT (notification_id, user_id) DO NOTHING
            </script>
            """)
    int markAllAsRead(@Param("userId") UUID userId, @Param("type") Integer type);

    /**
     * 批量保存已读状态
     *
     * @param list 已读状态列表
     * @return 受影响的行数
     */
    @Insert("""
            <script>
            INSERT INTO ntf_read_status (id, notification_id, user_id, read_at)
            VALUES
            <foreach item="item" collection="list" separator=",">
              (#{item.id}, #{item.notificationId}, #{item.userId}, #{item.readAt})
            </foreach>
            ON CONFLICT (notification_id, user_id) DO NOTHING
            </script>
            """)
    int batchInsert(@Param("list") List<NotificationReadStatus> list);
}
