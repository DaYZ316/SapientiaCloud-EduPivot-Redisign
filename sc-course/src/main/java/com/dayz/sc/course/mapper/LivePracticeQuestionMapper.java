package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.LivePracticeQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 随堂练习题目快照Mapper
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Mapper
public interface LivePracticeQuestionMapper extends BaseMapper<LivePracticeQuestion> {
}
