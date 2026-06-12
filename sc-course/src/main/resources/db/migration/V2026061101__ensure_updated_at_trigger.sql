-- ============================================================
-- V2026061101: 为 edu_course 添加 updated_at 自动更新触发器
-- @author DaYZ
-- @since 2026-06-11
-- ============================================================

-- 确保现有记录的 updated_at 不为 NULL
UPDATE edu_course SET updated_at = created_at WHERE updated_at IS NULL;

-- 创建触发器函数：自动设置 updated_at
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为 edu_course 创建 BEFORE UPDATE 触发器
DROP TRIGGER IF EXISTS set_updated_at ON edu_course;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON edu_course
    FOR EACH ROW
    EXECUTE FUNCTION trg_set_updated_at();

COMMENT ON FUNCTION trg_set_updated_at() IS '自动设置 updated_at 为当前时间戳';
