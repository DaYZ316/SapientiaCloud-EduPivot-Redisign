package com.dayz.sc.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.notification.model.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
