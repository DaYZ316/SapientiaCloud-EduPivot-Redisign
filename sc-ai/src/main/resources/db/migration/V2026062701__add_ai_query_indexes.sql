-- Add non-destructive indexes for AI conversation, document, message, and live summary query paths.

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user_updated
    ON ai_conversation(user_id, updated_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_message_conversation_created
    ON ai_message(conversation_id, created_at)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_doc_user_created
    ON ai_knowledge_doc(user_id, created_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_live_summary_session_running_created
    ON ai_live_summary_session(class_session_id, created_at DESC)
    WHERE deleted = 0 AND status = 'RUNNING';
