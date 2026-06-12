-- ============================================================
-- V2026061002: 添加复合索引优化列表查询
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

-- 覆盖课程列表查询：status + created_at DESC
CREATE INDEX IF NOT EXISTS idx_edu_course_status_created
    ON edu_course(status, created_at DESC);

-- 覆盖选课表按学生+状态查询
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_student_status
    ON edu_enrollment(student_id, status);
