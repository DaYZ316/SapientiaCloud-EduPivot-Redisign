CREATE TABLE IF NOT EXISTS storage_object (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket VARCHAR(128) NOT NULL,
    object_key TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    etag VARCHAR(191),
    sha256 VARCHAR(128),
    usage VARCHAR(64) NOT NULL,
    visibility VARCHAR(64) NOT NULL,
    owner_user_id UUID NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_id UUID,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_storage_object_bucket_key
    ON storage_object(bucket, object_key);
CREATE INDEX IF NOT EXISTS idx_storage_object_owner
    ON storage_object(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_storage_object_scope
    ON storage_object(scope_type, scope_id);
CREATE INDEX IF NOT EXISTS idx_storage_object_usage_status
    ON storage_object(usage, status);

CREATE TABLE IF NOT EXISTS storage_upload_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id UUID NOT NULL,
    method VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    max_size_bytes BIGINT NOT NULL,
    allowed_content_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_storage_upload_session_object
        FOREIGN KEY(object_id) REFERENCES storage_object(id)
);

CREATE INDEX IF NOT EXISTS idx_storage_upload_session_object
    ON storage_upload_session(object_id);
CREATE INDEX IF NOT EXISTS idx_storage_upload_session_status_expire
    ON storage_upload_session(status, expires_at);
