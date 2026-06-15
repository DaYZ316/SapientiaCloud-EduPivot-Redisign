package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.QuestionOption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionOptionMapper extends BaseMapper<QuestionOption> {

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
