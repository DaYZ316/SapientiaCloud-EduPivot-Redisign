DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_ai_live_transcript_sequence'
            AND conrelid = 'ai_live_transcript_segment'::regclass
    )
        AND NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'uk_ai_live_transcript_sequence'
                AND conrelid = 'ai_live_transcript_segment'::regclass
        ) THEN
        ALTER TABLE ai_live_transcript_segment
            RENAME CONSTRAINT uq_ai_live_transcript_sequence TO uk_ai_live_transcript_sequence;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_ai_live_summary_snapshot_sequence'
            AND conrelid = 'ai_live_summary_snapshot'::regclass
    )
        AND NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'uk_ai_live_summary_snapshot_sequence'
                AND conrelid = 'ai_live_summary_snapshot'::regclass
        ) THEN
        ALTER TABLE ai_live_summary_snapshot
            RENAME CONSTRAINT uq_ai_live_summary_snapshot_sequence TO uk_ai_live_summary_snapshot_sequence;
    END IF;
END $$;
