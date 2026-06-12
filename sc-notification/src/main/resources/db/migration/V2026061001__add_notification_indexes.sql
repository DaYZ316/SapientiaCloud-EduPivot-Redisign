-- ============================================================
-- V2026061001: 添加复合索引优化通知查询
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

-- 覆盖通知列表按类型+时间排序
CREATE INDEX IF NOT EXISTS idx_ntf_notification_type_created
    ON ntf_notification(type, created_at DESC);

-- 覆盖未读数和已读状态查询：user_id 优先
CREATE INDEX IF NOT EXISTS idx_ntf_read_user_notification
    ON ntf_read_status(user_id, notification_id);
