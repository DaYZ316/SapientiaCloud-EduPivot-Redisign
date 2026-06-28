package com.dayz.sc.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.notification.model.entity.NotificationTarget;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 通知目标用户 Mapper
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Mapper
public interface NotificationTargetMapper extends BaseMapper<NotificationTarget> {

    /**
     * Mark one notification as deleted for the user.
     *
     * @param notificationId notification ID
     * @param userId         user ID
     * @return affected row count
     */
    @Insert("""
            INSERT INTO ntf_notification_target (id, notification_id, user_id, deleted)
            VALUES (gen_random_uuid(), #{notificationId}, #{userId}, 1)
            ON CONFLICT (notification_id, user_id) DO UPDATE SET deleted = 1
            """)
    int markDeleted(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);

    /**
     * 将用户的所有通知目标标记为已删除
     *
     * @param userId 用户ID
     * @param type   通知类型，为null时匹配所有类型
     * @return 受影响的行数
     */
    @Update("""
            <script>
            UPDATE ntf_notification_target t
            SET deleted = 1
            WHERE t.user_id = #{userId}
              AND t.deleted = 0
              AND EXISTS (
                SELECT 1 FROM ntf_notification n
                WHERE n.id = t.notification_id AND n.deleted = 0
                <if test="type != null">
                  AND n.type = #{type}
                </if>
              )
            </script>
            """)
    int markAllDeleted(@Param("userId") UUID userId, @Param("type") Integer type);

    /**
     * Mark all broadcast notifications as deleted for the user.
     *
     * @param userId user ID
     * @param type   notification type; null matches all types
     * @return affected row count
     */
    @Insert("""
            <script>
            INSERT INTO ntf_notification_target (id, notification_id, user_id, deleted)
            SELECT gen_random_uuid(), n.id, #{userId}, 1
            FROM ntf_notification n
            WHERE n.deleted = 0
              AND n.target_type = 0
              AND (n.sender_id IS NULL OR n.sender_id != #{userId})
              <if test="type != null">
                AND n.type = #{type}
              </if>
            ON CONFLICT (notification_id, user_id) DO UPDATE SET deleted = 1
            </script>
            """)
    int markAllBroadcastDeleted(@Param("userId") UUID userId, @Param("type") Integer type);

    /**
     * 批量插入通知目标用户关联
     *
     * @param list 通知目标用户关联列表
     * @return 受影响的行数
     */
    @Insert("""
            <script>
            INSERT INTO ntf_notification_target (id, notification_id, user_id, deleted)
            VALUES
            <foreach item="item" collection="list" separator=",">
              (#{item.id}, #{item.notificationId}, #{item.userId}, 0)
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<NotificationTarget> list);
}
