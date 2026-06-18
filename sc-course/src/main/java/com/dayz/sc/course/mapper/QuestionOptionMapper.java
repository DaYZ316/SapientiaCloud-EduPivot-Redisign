package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.QuestionOption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis-Plus Mapper 接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface QuestionOptionMapper extends BaseMapper<QuestionOption> {

    /**
     * 批量插入题目选项
     *
     * @param list 题目选项列表
     * @return 受影响的行数
     */
    @Insert("""
            <script>
            INSERT INTO edu_question_option (id, question_id, course_id, option_content, option_label, is_correct, score, image_urls, explanation)
            VALUES
            <foreach item="item" collection="list" separator=",">
              (#{item.id}, #{item.questionId}, #{item.courseId}, #{item.optionContent}, #{item.optionLabel}, #{item.isCorrect}, #{item.score}, #{item.imageUrls,jdbcType=OTHER,typeHandler=com.dayz.sc.course.config.PostgresJsonbStringListTypeHandler}, #{item.explanation})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<QuestionOption> list);
}
