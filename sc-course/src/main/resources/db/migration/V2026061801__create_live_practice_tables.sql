-- ============================================================
-- V2026061801: Create live practice tables
-- ============================================================

CREATE TABLE IF NOT EXISTS edu_live_practice_group
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL,
    class_session_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    available_start_at TIMESTAMPTZ NOT NULL,
    available_end_at TIMESTAMPTZ NOT NULL,
    allow_late_submission SMALLINT NOT NULL DEFAULT 0,
    publish_order INT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_edu_live_practice_group_time CHECK (available_end_at > available_start_at)
);

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_group_course_id
    ON edu_live_practice_group (course_id);
CREATE INDEX IF NOT EXISTS idx_edu_live_practice_group_session_id
    ON edu_live_practice_group (class_session_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_live_practice_group_session_order
    ON edu_live_practice_group (class_session_id, publish_order)
    WHERE deleted = 0;

DROP TRIGGER IF EXISTS set_updated_at ON edu_live_practice_group;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_live_practice_group
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

COMMENT ON TABLE edu_live_practice_group IS '随堂练习发布组';
COMMENT ON COLUMN edu_live_practice_group.allow_late_submission IS '是否允许补交: 0=否, 1=是';
COMMENT ON COLUMN edu_live_practice_group.publish_order IS '同一开课记录内的发布序号，最大 5';

CREATE TABLE IF NOT EXISTS edu_live_practice_question
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    course_id UUID NOT NULL,
    class_session_id UUID NOT NULL,
    source_question_id UUID,
    question_order INT NOT NULL,
    question_title VARCHAR(500) NOT NULL,
    question_content TEXT,
    question_type SMALLINT NOT NULL,
    difficulty SMALLINT NOT NULL DEFAULT 2,
    score DECIMAL(10, 2) NOT NULL DEFAULT 0,
    estimated_time INT,
    tags JSONB,
    image_urls JSONB,
    allow_partial_credit SMALLINT NOT NULL DEFAULT 0,
    options_snapshot JSONB,
    answers_snapshot JSONB,
    ai_grading_enabled SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_question_group_id
    ON edu_live_practice_question (group_id);
CREATE INDEX IF NOT EXISTS idx_edu_live_practice_question_course_id
    ON edu_live_practice_question (course_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_live_practice_question_group_order
    ON edu_live_practice_question (group_id, question_order)
    WHERE deleted = 0;

DROP TRIGGER IF EXISTS set_updated_at ON edu_live_practice_question;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_live_practice_question
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

COMMENT ON TABLE edu_live_practice_question IS '随堂练习题目快照';
COMMENT ON COLUMN edu_live_practice_question.options_snapshot IS '发布时选项快照';
COMMENT ON COLUMN edu_live_practice_question.answers_snapshot IS '发布时答案快照';
COMMENT ON COLUMN edu_live_practice_question.ai_grading_enabled IS 'AI 判分: 0=关闭，1=开启（预留）';

CREATE TABLE IF NOT EXISTS edu_live_practice_submission
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    question_snapshot_id UUID NOT NULL,
    course_id UUID NOT NULL,
    class_session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    selected_option_ids JSONB,
    text_answer TEXT,
    submit_status SMALLINT NOT NULL,
    is_correct SMALLINT,
    earned_score DECIMAL(10, 2) NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_group_id
    ON edu_live_practice_submission (group_id);
CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_question_id
    ON edu_live_practice_submission (question_snapshot_id);
CREATE INDEX IF NOT EXISTS idx_edu_live_practice_submission_student_id
    ON edu_live_practice_submission (student_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_live_practice_submission_once
    ON edu_live_practice_submission (group_id, question_snapshot_id, student_id);

COMMENT ON TABLE edu_live_practice_submission IS '随堂练习学生提交记录';
COMMENT ON COLUMN edu_live_practice_submission.submit_status IS '提交状态: 1=已提交, 2=补交';
COMMENT ON COLUMN edu_live_practice_submission.is_correct IS '是否正确: NULL=待批阅, 0=错误, 1=正确';
