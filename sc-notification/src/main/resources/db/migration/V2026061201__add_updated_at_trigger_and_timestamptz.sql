-- ============================================================
-- V2026061201: 为通知表添加 updated_at 触发器并转换为 TIMESTAMPTZ
-- @author DaYZ
-- @since 2026-06-12
-- ============================================================

-- 创建触发器函数
CREATE
OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

-- ntf_notification
UPDATE ntf_notification
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON ntf_notification;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON ntf_notification
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- 将 TIMESTAMP 转换为 TIMESTAMPTZ
ALTER TABLE ntf_notification ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE ntf_notification ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE ntf_read_status ALTER COLUMN read_at TYPE TIMESTAMPTZ USING read_at AT TIME ZONE 'UTC';
