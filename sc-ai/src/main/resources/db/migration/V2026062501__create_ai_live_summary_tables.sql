-- ============================================================
-- V2026062501: Create AI live summary tables
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_live_summary_session
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_session_id UUID NOT NULL,
    course_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    started_by UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    compressed_state TEXT,
    last_summarized_sequence_no INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_live_summary_session_class
    ON ai_live_summary_session(class_session_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_live_summary_session_running
    ON ai_live_summary_session(class_session_id, status)
    WHERE deleted = 0 AND status = 'RUNNING';

COMMENT ON TABLE ai_live_summary_session IS 'AI live class summary session';
COMMENT ON COLUMN ai_live_summary_session.status IS 'RUNNING/STOPPED/FAILED';
COMMENT ON COLUMN ai_live_summary_session.compressed_state IS 'Rolling compressed summary state';
COMMENT ON COLUMN ai_live_summary_session.last_summarized_sequence_no IS 'Last transcript sequence included in summaries';

CREATE TABLE IF NOT EXISTS ai_live_transcript_segment
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_session_id UUID NOT NULL,
    class_session_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    speaker_id UUID NOT NULL,
    text TEXT NOT NULL,
    begin_time_ms INTEGER,
    end_time_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_live_transcript_sequence UNIQUE(summary_session_id, sequence_no),
    CONSTRAINT fk_ai_live_transcript_summary_session
        FOREIGN KEY(summary_session_id) REFERENCES ai_live_summary_session(id)
);

CREATE INDEX IF NOT EXISTS idx_ai_live_transcript_session_sequence
    ON ai_live_transcript_segment(summary_session_id, sequence_no)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_live_transcript_class_created
    ON ai_live_transcript_segment(class_session_id, created_at DESC)
    WHERE deleted = 0;

COMMENT ON TABLE ai_live_transcript_segment IS 'Final ASR transcript segments for live class summaries';

CREATE TABLE IF NOT EXISTS ai_live_summary_snapshot
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_session_id UUID NOT NULL,
    class_session_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    transcript_until_sequence_no INTEGER NOT NULL,
    overview TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_live_summary_snapshot_sequence UNIQUE(summary_session_id, sequence_no),
    CONSTRAINT fk_ai_live_summary_snapshot_summary_session
        FOREIGN KEY(summary_session_id) REFERENCES ai_live_summary_session(id)
);

CREATE INDEX IF NOT EXISTS idx_ai_live_summary_snapshot_session_sequence
    ON ai_live_summary_snapshot(summary_session_id, sequence_no DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_live_summary_snapshot_class_created
    ON ai_live_summary_snapshot(class_session_id, created_at DESC)
    WHERE deleted = 0;

COMMENT ON TABLE ai_live_summary_snapshot IS 'Incremental AI live class summary snapshots';
COMMENT ON COLUMN ai_live_summary_snapshot.payload IS 'Structured overview/keyPoints/timeline/questions/mindMap JSON';
