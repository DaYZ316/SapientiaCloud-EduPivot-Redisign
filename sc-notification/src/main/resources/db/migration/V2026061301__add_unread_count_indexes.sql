-- ============================================================
-- V2026061301: 优化未读计数查询索引
-- 配合 UNION ALL 拆分广播与指定用户通知查询
-- 注意：生产环境建议用 CREATE INDEX CONCURRENTLY 替代
-- @author DaYZ
-- @since 2026-06-13
-- ============================================================

-- 覆盖广播通知分支：WHERE deleted=0 AND target_type=0 的查询
-- 包含 type 和 sender_id 用于过滤，包含 id 用于覆盖索引
CREATE INDEX IF NOT EXISTS idx_ntf_notification_unread_broadcast
    ON ntf_notification(target_type, type, sender_id, id)
    WHERE deleted = 0;

-- 覆盖指定用户通知分支：EXISTS 子查询中 user_id + deleted + notification_id
-- 替代原有的 idx_ntf_target_user_deleted（只覆盖 user_id, deleted）
CREATE INDEX IF NOT EXISTS idx_ntf_notification_target_unread
    ON ntf_notification_target(user_id, deleted, notification_id);
