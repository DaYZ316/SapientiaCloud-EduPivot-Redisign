-- ============================================================
-- V2026061201: 为学生/教师表添加 updated_at 触发器并转换为 TIMESTAMPTZ
-- @author DaYZ
-- @since 2026-06-12
-- ============================================================

-- 创建触发器函数
CREATE
OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

-- edu_student
UPDATE edu_student
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_student;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_student
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_teacher
UPDATE edu_teacher
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_teacher;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_teacher
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- 将 TIMESTAMP 转换为 TIMESTAMPTZ
ALTER TABLE edu_student ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_student ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE edu_teacher ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_teacher ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
