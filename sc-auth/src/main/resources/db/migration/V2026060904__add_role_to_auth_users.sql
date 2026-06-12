-- ============================================================
-- V2026060904: 给用户表添加角色字段
-- @author DaYZ
-- @since 2026-06-09
-- ============================================================

-- 添加角色字段：0=管理员，1=学生，2=教师
ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS role SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN auth_users.role IS '用户角色：0=管理员，1=学生，2=教师';

-- 创建角色索引
CREATE INDEX IF NOT EXISTS idx_auth_users_role ON auth_users(role);
