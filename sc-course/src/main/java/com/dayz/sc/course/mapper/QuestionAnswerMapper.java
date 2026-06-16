package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.QuestionAnswer;
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
public interface QuestionAnswerMapper extends BaseMapper<QuestionAnswer> {

    @Insert("""
            <script>
            INSERT INTO edu_question_answer (id, question_id, course_id, answer_content, explanation, score, sort_order)
            VALUES
            <foreach item="item" collection="list" separator=",">
              (#{item.id}, #{item.questionId}, #{item.courseId}, #{item.answerContent}, #{item.explanation}, #{item.score}, #{item.sortOrder})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<QuestionAnswer> list);
}
