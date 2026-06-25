-- ============================================================
-- V2026062501: Add live practice AI grading requirements
-- ============================================================

ALTER TABLE edu_live_practice_group
    ADD COLUMN IF NOT EXISTS ai_grading_enabled SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_grading_requirement TEXT;

COMMENT ON COLUMN edu_live_practice_group.ai_grading_enabled IS 'AI grading enabled for short answer questions: 0=off, 1=on';
COMMENT ON COLUMN edu_live_practice_group.ai_grading_requirement IS 'Teacher-provided AI grading requirement';
