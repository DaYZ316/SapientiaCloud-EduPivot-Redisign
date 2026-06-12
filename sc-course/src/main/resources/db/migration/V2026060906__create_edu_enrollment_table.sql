-- 选课表
CREATE TABLE IF NOT EXISTS edu_enrollment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID NOT NULL,
    student_id      UUID NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 0,
    enrolled_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    UNIQUE(course_id, student_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_course_id ON edu_enrollment(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_student_id ON edu_enrollment(student_id);
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_status ON edu_enrollment(status);

-- 注释
COMMENT ON TABLE edu_enrollment IS '选课表';
COMMENT ON COLUMN edu_enrollment.id IS '选课ID';
COMMENT ON COLUMN edu_enrollment.course_id IS '课程ID';
COMMENT ON COLUMN edu_enrollment.student_id IS '学生用户ID';
COMMENT ON COLUMN edu_enrollment.status IS '状态: 0=待审核, 1=已选课, 2=已完成, 3=已退课';
COMMENT ON COLUMN edu_enrollment.enrolled_at IS '选课时间';
COMMENT ON COLUMN edu_enrollment.completed_at IS '完成时间';
COMMENT ON COLUMN edu_enrollment.created_at IS '创建时间';
COMMENT ON COLUMN edu_enrollment.updated_at IS '更新时间';
COMMENT ON COLUMN edu_enrollment.deleted IS '逻辑删除: 0=正常, 1=已删除';
