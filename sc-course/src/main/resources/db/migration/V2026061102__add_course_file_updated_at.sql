ALTER TABLE edu_course_file
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE edu_course_file
SET updated_at = created_at
WHERE updated_at IS NULL;
