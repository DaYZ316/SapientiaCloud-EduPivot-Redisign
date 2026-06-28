ALTER TABLE ai_message
    DROP CONSTRAINT IF EXISTS fk_ai_message_ai_conversation;

ALTER TABLE ai_live_transcript_segment
    DROP CONSTRAINT IF EXISTS fk_ai_live_transcript_summary_session;

ALTER TABLE ai_live_summary_snapshot
    DROP CONSTRAINT IF EXISTS fk_ai_live_summary_snapshot_summary_session;
