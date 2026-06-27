-- Add non-destructive indexes for auth/user profile query paths.

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_student_student_no
    ON edu_student(student_no)
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_teacher_employee_no
    ON edu_teacher(employee_no)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_auth_users_status_role_updated_created
    ON auth_users(status, role, updated_at DESC, created_at DESC);
