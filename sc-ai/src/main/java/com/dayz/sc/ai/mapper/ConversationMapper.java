package com.dayz.sc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.ai.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
