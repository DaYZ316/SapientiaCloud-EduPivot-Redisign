-- ============================================================
-- V2026061202: 添加课程模块查询优化索引
-- @author DaYZ
-- @since 2026-06-12
-- ============================================================

-- 课程列表：按状态+公开+时间排序（覆盖 listCourses 常见查询）
CREATE INDEX IF NOT EXISTS idx_edu_course_status_public_created
    ON edu_course(status, is_public, created_at DESC);

-- 选课记录：按课程+状态（覆盖 countActiveByCourseIds 批量统计）
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_course_status
    ON edu_enrollment(course_id, status);

-- 选课记录：按学生+时间排序（覆盖 findByStudentId 分页）
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_student_enrolled
    ON edu_enrollment(student_id, enrolled_at DESC);

-- 论坛帖子：按论坛+置顶+时间排序（覆盖 findByForumId 分页）
DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edu_forum_post'
          AND column_name = 'forum_id'
    ) THEN
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_forum_top_created
    ON edu_forum_post(forum_id, is_top DESC, created_at DESC);
END IF;
END $$;

-- 论坛帖子：按课程+状态+时间（覆盖 findByCourseId 分页）
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_course_status_created
    ON edu_forum_post(course_id, status, created_at DESC);

-- 论坛回复：按帖子+楼层（覆盖 findByPostId 分页）
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_post_floor
    ON edu_forum_reply(post_id, floor_number);

-- 题目：按题库+时间排序（覆盖 findByQuestionBankId 分页）
CREATE INDEX IF NOT EXISTS idx_edu_question_bank_created
    ON edu_question(question_bank_id, created_at DESC);

-- 章节：按课程+排序（覆盖 findByCourseId 排序）
CREATE INDEX IF NOT EXISTS idx_edu_chapter_course_sort
    ON edu_chapter(course_id, sort_order);
