-- ============================================================
-- V2026061001: 添加用户资料扩展字段
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

-- 添加个人信息字段
ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS phone VARCHAR (20),
    ADD COLUMN IF NOT EXISTS bio VARCHAR (500),
    ADD COLUMN IF NOT EXISTS gender SMALLINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS birthday DATE,
    ADD COLUMN IF NOT EXISTS theme VARCHAR (20) DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS notification_enabled BOOLEAN DEFAULT true;

-- 索引
CREATE INDEX IF NOT EXISTS idx_auth_users_phone ON auth_users(phone);

-- 注释
COMMENT
ON COLUMN auth_users.phone IS '电话号码';
COMMENT
ON COLUMN auth_users.bio IS '个人简介';
COMMENT
ON COLUMN auth_users.gender IS '性别: 0=未知, 1=男, 2=女';
COMMENT
ON COLUMN auth_users.birthday IS '生日';
COMMENT
ON COLUMN auth_users.theme IS '主题偏好: light, dark, system';
COMMENT
ON COLUMN auth_users.notification_enabled IS '是否开启通知';
