package com.dayz.sc.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.notification.model.entity.NotificationReadStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通知已读状态 Mapper。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Mapper
public interface NotificationReadStatusMapper extends BaseMapper<NotificationReadStatus> {

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
     * 一次查询返回全部未读计数（total / system / teaching）。
     * 使用 UNION ALL 将广播通知与指定用户通知拆分为两段独立查询，
     * 避免 OR 条件阻碍索引选择。
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
