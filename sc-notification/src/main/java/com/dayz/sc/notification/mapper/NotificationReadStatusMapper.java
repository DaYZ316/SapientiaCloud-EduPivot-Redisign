package com.dayz.sc.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.notification.model.entity.NotificationReadStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Insert("""
            <script>
            INSERT INTO ntf_read_status (id, notification_id, user_id, read_at)
            SELECT gen_random_uuid(), n.id, #{userId}, CURRENT_TIMESTAMP
            FROM ntf_notification n
            WHERE n.deleted = 0
            <if test="type != null">
              AND n.type = #{type}
            </if>
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
}
