ALTER TABLE storage_object
    ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_storage_object_deleted
    ON storage_object(deleted);

COMMENT
ON COLUMN storage_object.deleted IS '逻辑删除: 0=正常, 1=已删除';
