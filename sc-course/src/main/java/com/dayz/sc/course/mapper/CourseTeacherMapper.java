package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.CourseTeacher;
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
public interface CourseTeacherMapper extends BaseMapper<CourseTeacher> {

    /**
     * 批量插入课程教师关联。
     *
     * @param list 课程教师关联列表
     * @return 受影响的行数
     */
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
