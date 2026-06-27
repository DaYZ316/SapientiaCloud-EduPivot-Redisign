-- Add non-destructive indexes for storage lookup and cleanup query paths.

CREATE INDEX IF NOT EXISTS idx_storage_object_object_key
    ON storage_object(object_key)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_storage_object_scope_usage
    ON storage_object(scope_type, scope_id, usage)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_storage_object_pending_created
    ON storage_object(created_at)
    WHERE deleted = 0 AND status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_storage_upload_session_object_status_created
    ON storage_upload_session(object_id, status, created_at DESC);
