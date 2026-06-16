-- Add id column to edu_course_teacher and fix primary key
ALTER TABLE edu_course_teacher
    ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
UPDATE edu_course_teacher
SET id = gen_random_uuid()
WHERE id IS NULL;
ALTER TABLE edu_course_teacher
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE edu_course_teacher DROP CONSTRAINT IF EXISTS edu_course_teacher_pkey;
ALTER TABLE edu_course_teacher
    ADD PRIMARY KEY (id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_teacher_course_teacher ON edu_course_teacher(course_id, teacher_id);