-- ============================================================
-- V2026061101: 创建通知目标用户表
-- @author DaYZ
-- @since 2026-06-11
-- ============================================================

CREATE TABLE IF NOT EXISTS ntf_notification_target
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    notification_id UUID NOT NULL,
    user_id UUID NOT NULL
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_ntf_target_notification_user
    ON ntf_notification_target(notification_id, user_id);

CREATE INDEX IF NOT EXISTS idx_ntf_target_user
    ON ntf_notification_target(user_id);

COMMENT
ON TABLE ntf_notification_target IS '通知目标用户表';
COMMENT
ON COLUMN ntf_notification_target.notification_id IS '通知ID';
COMMENT
ON COLUMN ntf_notification_target.user_id IS '目标用户ID';
