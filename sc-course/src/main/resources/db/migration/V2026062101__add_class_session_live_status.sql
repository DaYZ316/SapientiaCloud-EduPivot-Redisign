-- ============================================================
-- V2026062101: Add independent class live stream status
-- ============================================================

ALTER TABLE edu_class_session
    ADD COLUMN IF NOT EXISTS live_status SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS live_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS live_paused_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS live_ended_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_edu_class_session_live_end_scan
    ON edu_class_session(live_status, scheduled_end_at)
    WHERE deleted = 0 AND published_at IS NOT NULL;

COMMENT ON COLUMN edu_class_session.live_status IS 'Live stream status: 0=not started, 1=live, 2=paused, 3=ended';
COMMENT ON COLUMN edu_class_session.live_started_at IS 'Live stream start time';
COMMENT ON COLUMN edu_class_session.live_paused_at IS 'Current live stream pause time';
COMMENT ON COLUMN edu_class_session.live_ended_at IS 'Live stream end time';
