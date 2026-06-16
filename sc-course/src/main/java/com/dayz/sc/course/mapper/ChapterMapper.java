package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * MyBatis-Plus Mapper 接口。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {

    @Update("UPDATE edu_chapter SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(UUID id);

    @Update("UPDATE edu_chapter SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementLikeCount(UUID id);

    @Update("UPDATE edu_chapter SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id} AND deleted = 0")
    int decrementLikeCount(UUID id);
}
