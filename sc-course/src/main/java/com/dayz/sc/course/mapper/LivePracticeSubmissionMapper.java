package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.LivePracticeSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 随堂练习提交记录Mapper
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Mapper
public interface LivePracticeSubmissionMapper extends BaseMapper<LivePracticeSubmission> {
}
