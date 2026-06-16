-- 添加密码哈希字段，支持账号密码登录
ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR (128);
