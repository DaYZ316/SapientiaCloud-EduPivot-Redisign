-- ============================================================
-- V2026061103: 为 ntf_notification.deleted 添加索引优化未读计数查询
-- @author DaYZ
-- @since 2026-06-11
-- ============================================================

-- 覆盖未读计数查询的 WHERE n.deleted = 0 条件
CREATE INDEX IF NOT EXISTS idx_ntf_notification_deleted
    ON ntf_notification(deleted) WHERE deleted = 0;
