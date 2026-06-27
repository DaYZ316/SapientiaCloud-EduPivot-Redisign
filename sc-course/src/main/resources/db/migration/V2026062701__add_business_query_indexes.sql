-- Add non-destructive indexes for course, classroom, practice, and invitation query paths.

-- Course ownership and teacher course lists.
CREATE INDEX IF NOT EXISTS idx_edu_course_teacher_created
    ON edu_course(teacher_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_course_teacher_teacher_course
    ON edu_course_teacher(teacher_id, course_id);

-- Enrollment list and membership checks.
CREATE INDEX IF NOT EXISTS idx_edu_enrollment_course_enrolled
    ON edu_enrollment(course_id, enrolled_at DESC);

CREATE INDEX IF NOT EXISTS idx_edu_enrollment_student_status_course
    ON edu_enrollment(student_id, status, course_id);

-- Class session lists and live-state scans.
CREATE INDEX IF NOT EXISTS idx_edu_class_session_course_start
    ON edu_class_session(course_id, scheduled_start_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_class_session_teacher_ongoing
    ON edu_class_session(teacher_id, scheduled_end_at, scheduled_start_at)
    WHERE deleted = 0 AND published_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edu_class_session_end_live_scan
    ON edu_class_session(scheduled_end_at, live_status)
    WHERE deleted = 0 AND published_at IS NOT NULL;

-- Course content lists.
CREATE INDEX IF NOT EXISTS idx_edu_course_file_course_sort_created
    ON edu_course_file(course_id, sort_order, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_forum_post_course_status_top_created
    ON edu_forum_post(course_id, status, is_top DESC, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_post_status_floor
    ON edu_forum_reply(post_id, status, floor_number)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_question_bank_course_created
    ON edu_question_bank(course_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_question_bank_course_type_created
    ON edu_question_bank(course_id, bank_type, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_question_course_status_created
    ON edu_question(course_id, status, created_at DESC)
    WHERE deleted = 0;

-- Detail ordering and existence checks.
CREATE INDEX IF NOT EXISTS idx_edu_question_option_question_label
    ON edu_question_option(question_id, option_label)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_question_answer_question_sort
    ON edu_question_answer(question_id, sort_order)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_practice_session_user_started
    ON edu_practice_session(sys_user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_edu_practice_session_bank_started
    ON edu_practice_session(question_bank_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_edu_practice_answer_session_answered
    ON edu_practice_answer(session_id, answered_at);

CREATE INDEX IF NOT EXISTS idx_edu_chapter_parent_sort
    ON edu_chapter(parent_chapter_id, sort_order)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_chapter_course_name
    ON edu_chapter(course_id, chapter_name)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_chapter_like_user_chapter
    ON edu_chapter_like(user_id, chapter_id);

-- Live practice query paths.
CREATE INDEX IF NOT EXISTS idx_edu_live_practice_group_course_published
    ON edu_live_practice_group(course_id, published_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_question_course_created
    ON edu_live_practice_question(course_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_group_submitted
    ON edu_live_practice_submission(group_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_course_student_submitted
    ON edu_live_practice_submission(course_id, student_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_pending_ai
    ON edu_live_practice_submission(submitted_at)
    WHERE ai_grading_status = 'PENDING' AND ai_graded_at IS NULL;

-- Course invitation inbox/outbox lists.
CREATE INDEX IF NOT EXISTS idx_edu_course_invitation_invitee_created
    ON edu_course_invitation(invitee_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_course_invitation_invitee_status_created
    ON edu_course_invitation(invitee_id, status, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_edu_course_invitation_inviter_created
    ON edu_course_invitation(inviter_id, created_at DESC)
    WHERE deleted = 0;
