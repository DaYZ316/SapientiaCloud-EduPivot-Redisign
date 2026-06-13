-- ============================================================
-- V2026061301: 创建练习会话相关表
-- ============================================================

-- 练习会话
CREATE TABLE IF NOT EXISTS edu_practice_session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_bank_id UUID NOT NULL,
    course_id        UUID NOT NULL,
    sys_user_id      UUID NOT NULL,
    session_type     SMALLINT NOT NULL DEFAULT 0,
    total_questions  INT NOT NULL DEFAULT 0,
    answered_count   INT NOT NULL DEFAULT 0,
    correct_count    INT NOT NULL DEFAULT 0,
    total_score      DECIMAL(10,2) NOT NULL DEFAULT 0,
    earned_score     DECIMAL(10,2) NOT NULL DEFAULT 0,
    started_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP,
    status           SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_edu_practice_session_user_id ON edu_practice_session(sys_user_id);
CREATE INDEX IF NOT EXISTS idx_edu_practice_session_bank_id ON edu_practice_session(question_bank_id);
CREATE INDEX IF NOT EXISTS idx_edu_practice_session_course_id ON edu_practice_session(course_id);

COMMENT ON TABLE edu_practice_session IS '练习会话';
COMMENT ON COLUMN edu_practice_session.session_type IS '会话类型: 0=顺序练习, 1=随机练习';
COMMENT ON COLUMN edu_practice_session.status IS '状态: 0=进行中, 1=已完成, 2=已放弃';

-- 练习答案记录
CREATE TABLE IF NOT EXISTS edu_practice_answer (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL,
    question_id         UUID NOT NULL,
    selected_option_ids JSONB,
    text_answer         TEXT,
    is_correct          SMALLINT NOT NULL DEFAULT 0,
    earned_score        DECIMAL(10,2) NOT NULL DEFAULT 0,
    answered_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_edu_practice_answer_session_id ON edu_practice_answer(session_id);
CREATE INDEX IF NOT EXISTS idx_edu_practice_answer_question_id ON edu_practice_answer(question_id);

COMMENT ON TABLE edu_practice_answer IS '练习答案记录';
COMMENT ON COLUMN edu_practice_answer.is_correct IS '是否正确: 0=错误, 1=正确';
