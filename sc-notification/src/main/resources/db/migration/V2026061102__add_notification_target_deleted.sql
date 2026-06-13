-- ============================================================
-- V2026061102: 通知目标用户表添加软删除字段
-- @author DaYZ
-- @since 2026-06-11
-- ============================================================

ALTER TABLE ntf_notification_target ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ntf_target_user_deleted
    ON ntf_notification_target(user_id, deleted);

COMMENT ON COLUMN ntf_notification_target.deleted IS '软删除：0=正常, 1=已删除';
