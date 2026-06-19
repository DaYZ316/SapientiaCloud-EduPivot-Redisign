ALTER TABLE edu_course
    ALTER COLUMN semester TYPE VARCHAR(64);

COMMENT ON COLUMN edu_course.semester IS 'Course semester label, for example: 2026 Spring Simulation';
