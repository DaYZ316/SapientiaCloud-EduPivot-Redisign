-- Create course-teacher relation table and migrate legacy assistant_ids.

CREATE TABLE IF NOT EXISTS edu_course_teacher
(
    course_id
    UUID
    NOT
    NULL,
    teacher_id
    UUID
    NOT
    NULL,
    created_at
    TIMESTAMP
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP,
    PRIMARY
    KEY
(
    course_id,
    teacher_id
)
    );

CREATE INDEX IF NOT EXISTS idx_edu_course_teacher_teacher_id
    ON edu_course_teacher(teacher_id);

COMMENT
ON TABLE edu_course_teacher IS 'Course-teacher relation table';
COMMENT
ON COLUMN edu_course_teacher.course_id IS 'Course ID';
COMMENT
ON COLUMN edu_course_teacher.teacher_id IS 'Teacher user ID';
COMMENT
ON COLUMN edu_course_teacher.created_at IS 'Created time';

INSERT INTO edu_course_teacher (course_id, teacher_id)
SELECT id, teacher_id
FROM edu_course
WHERE deleted = 0 ON CONFLICT (course_id, teacher_id) DO NOTHING;

DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'edu_course'
          AND column_name = 'assistant_ids'
    ) THEN
        INSERT INTO edu_course_teacher (course_id, teacher_id)
SELECT c.id, elem.value::uuid
FROM edu_course c
         CROSS JOIN LATERAL jsonb_array_elements_text(c.assistant_ids) elem
WHERE c.deleted = 0
  AND c.assistant_ids IS NOT NULL
  AND jsonb_typeof(c.assistant_ids) = 'array'
  AND jsonb_array_length(c.assistant_ids)
    > 0
ON CONFLICT (course_id, teacher_id) DO NOTHING;
END IF;
END $$;

ALTER TABLE edu_course DROP COLUMN IF EXISTS assistant_ids;
