package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MyBatis-Plus Mapper 接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 按题库ID分组统计题目数量
     *
     * @param bankIds 题库ID列表
     * @return 包含question_bank_id和cnt字段的Map列表
     */
    @Select("<script>" +
            "SELECT question_bank_id, COUNT(*) AS cnt FROM edu_question " +
            "WHERE question_bank_id IN " +
            "<foreach item='id' collection='bankIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY question_bank_id" +
            "</script>")
    List<Map<String, Object>> countByQuestionBankIds(@Param("bankIds") List<UUID> bankIds);
}
