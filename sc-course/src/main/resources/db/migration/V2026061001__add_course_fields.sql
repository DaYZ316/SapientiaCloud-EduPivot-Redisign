-- 添加课程新字段：助教列表、学期、地点、课程类型、是否公开

ALTER TABLE edu_course
    ADD COLUMN IF NOT EXISTS assistant_ids JSONB,
    ADD COLUMN IF NOT EXISTS semester VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location VARCHAR(100),
    ADD COLUMN IF NOT EXISTS course_type SMALLINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_public SMALLINT NOT NULL DEFAULT 0;

-- 索引
CREATE INDEX IF NOT EXISTS idx_edu_course_semester ON edu_course(semester);
CREATE INDEX IF NOT EXISTS idx_edu_course_course_type ON edu_course(course_type);
CREATE INDEX IF NOT EXISTS idx_edu_course_is_public ON edu_course(is_public);

-- 注释
COMMENT ON COLUMN edu_course.assistant_ids IS '助教用户ID列表 (JSON数组)';
COMMENT ON COLUMN edu_course.semester IS '开设学期 (例如: 2025秋季)';
COMMENT ON COLUMN edu_course.location IS '上课地点';
COMMENT ON COLUMN edu_course.course_type IS '课程类型: 0=必修, 1=选修';
COMMENT ON COLUMN edu_course.is_public IS '是否公开: 0=仅课程成员, 1=公开';
