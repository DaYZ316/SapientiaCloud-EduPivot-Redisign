-- 课程表
CREATE TABLE IF NOT EXISTS edu_course
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    title VARCHAR
(
    200
) NOT NULL,
    description TEXT,
    teacher_id UUID NOT NULL,
    level SMALLINT NOT NULL DEFAULT 1,
    cover_url TEXT,
    max_students INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

-- 索引
CREATE INDEX IF NOT EXISTS idx_edu_course_teacher_id ON edu_course(teacher_id);
CREATE INDEX IF NOT EXISTS idx_edu_course_status ON edu_course(status);

-- 注释
COMMENT
ON TABLE edu_course IS '课程表';
COMMENT
ON COLUMN edu_course.id IS '课程ID';
COMMENT
ON COLUMN edu_course.title IS '课程标题';
COMMENT
ON COLUMN edu_course.description IS '课程描述';
COMMENT
ON COLUMN edu_course.teacher_id IS '教师用户ID';
COMMENT
ON COLUMN edu_course.level IS '难度等级: 1=初级, 2=中级, 3=高级';
COMMENT
ON COLUMN edu_course.cover_url IS '封面图片URL';
COMMENT
ON COLUMN edu_course.max_students IS '最大学生数, 0=不限';
COMMENT
ON COLUMN edu_course.status IS '状态: 0=草稿, 1=已发布, 2=已归档';
COMMENT
ON COLUMN edu_course.created_at IS '创建时间';
COMMENT
ON COLUMN edu_course.updated_at IS '更新时间';
COMMENT
ON COLUMN edu_course.deleted IS '逻辑删除: 0=正常, 1=已删除';
