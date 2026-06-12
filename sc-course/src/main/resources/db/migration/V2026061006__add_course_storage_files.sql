ALTER TABLE edu_course
    ADD COLUMN IF NOT EXISTS cover_file_id UUID;

CREATE INDEX IF NOT EXISTS idx_edu_course_cover_file_id
    ON edu_course(cover_file_id);

CREATE TABLE IF NOT EXISTS edu_course_file (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL,
    storage_object_id UUID NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_edu_course_file_course_id
    ON edu_course_file(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_course_file_storage_object_id
    ON edu_course_file(storage_object_id);
CREATE INDEX IF NOT EXISTS idx_edu_course_file_course_visibility
    ON edu_course_file(course_id, visibility);
