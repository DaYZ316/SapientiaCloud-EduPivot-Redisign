-- ============================================================
-- V2026061802: Add live practice AI grading status
-- ============================================================

ALTER TABLE edu_live_practice_submission
    ADD COLUMN IF NOT EXISTS ai_grading_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS ai_grading_feedback TEXT,
    ADD COLUMN IF NOT EXISTS ai_grading_error TEXT,
    ADD COLUMN IF NOT EXISTS ai_graded_at TIMESTAMPTZ;

COMMENT ON COLUMN edu_live_practice_submission.ai_grading_status IS 'AI grading status: NOT_REQUIRED, PENDING, COMPLETED, FAILED';
COMMENT ON COLUMN edu_live_practice_submission.ai_grading_feedback IS 'AI grading feedback';
COMMENT ON COLUMN edu_live_practice_submission.ai_grading_error IS 'AI grading error';
COMMENT ON COLUMN edu_live_practice_submission.ai_graded_at IS 'AI grading completed time';
