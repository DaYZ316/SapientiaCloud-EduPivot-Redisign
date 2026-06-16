-- 课程总课时字段
ALTER TABLE edu_course ADD COLUMN IF NOT EXISTS total_class_hours INTEGER;

COMMENT ON COLUMN edu_course.total_class_hours IS '课程总课时';
