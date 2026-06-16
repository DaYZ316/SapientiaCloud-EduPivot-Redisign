-- ============================================================
-- V2026060903: 创建通知系统表
-- @author DaYZ
-- @since 2026-06-09
-- ============================================================

-- 通知表
CREATE TABLE IF NOT EXISTS ntf_notification
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    type SMALLINT NOT NULL,
    title VARCHAR
(
    128
) NOT NULL,
    content TEXT NOT NULL,
    sender_id UUID,
    target_type SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

-- 通知已读状态表
CREATE TABLE IF NOT EXISTS ntf_read_status
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
    user_id UUID NOT NULL,
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 唯一索引：一个用户对一条通知只有一条已读记录
CREATE UNIQUE INDEX IF NOT EXISTS uk_ntf_read_notification_user ON ntf_read_status(notification_id, user_id);

-- 查询用户未读数的索引
CREATE INDEX IF NOT EXISTS idx_ntf_read_user ON ntf_read_status(user_id);

-- 查询通知类型的索引
CREATE INDEX IF NOT EXISTS idx_ntf_notification_type ON ntf_notification(type);

COMMENT
ON TABLE ntf_notification IS '通知表';
COMMENT
ON TABLE ntf_read_status IS '通知已读状态表';

COMMENT
ON COLUMN ntf_notification.type IS '通知类型：1=系统公告，2=教学通知';
COMMENT
ON COLUMN ntf_notification.title IS '通知标题';
COMMENT
ON COLUMN ntf_notification.content IS '通知内容';
COMMENT
ON COLUMN ntf_notification.sender_id IS '发送者ID，系统公告为NULL';
COMMENT
ON COLUMN ntf_notification.target_type IS '目标类型：0=全员，1=指定用户';

COMMENT
ON COLUMN ntf_read_status.notification_id IS '通知ID';
COMMENT
ON COLUMN ntf_read_status.user_id IS '用户ID';
COMMENT
ON COLUMN ntf_read_status.read_at IS '阅读时间';
