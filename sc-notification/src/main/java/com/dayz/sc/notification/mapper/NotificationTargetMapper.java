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
