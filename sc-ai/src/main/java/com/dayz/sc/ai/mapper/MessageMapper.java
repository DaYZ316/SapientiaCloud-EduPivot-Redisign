package com.dayz.sc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.ai.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Mapper
public interface MessageMapper extends BaseMapper<ChatMessage> {
}
