-- ============================================================
-- V2026061007: 删除课程表的 category 字段
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

DROP INDEX IF EXISTS idx_edu_course_category;
ALTER TABLE edu_course DROP COLUMN IF EXISTS category;
