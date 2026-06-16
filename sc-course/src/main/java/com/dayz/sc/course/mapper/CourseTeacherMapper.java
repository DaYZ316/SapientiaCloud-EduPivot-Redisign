package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.CourseTeacher;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis-Plus Mapper 接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface CourseTeacherMapper extends BaseMapper<CourseTeacher> {

    @Insert("""
            <script>
            INSERT INTO edu_course_teacher (id, course_id, teacher_id)
            VALUES
            <foreach item="item" collection="list" separator=",">
              (#{item.id}, #{item.courseId}, #{item.teacherId})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<CourseTeacher> list);
}
