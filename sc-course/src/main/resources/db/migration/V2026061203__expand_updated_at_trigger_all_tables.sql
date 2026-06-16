-- ============================================================
-- V2026061203: 为所有含 updated_at 的表添加自动更新触发器
--              并将 TIMESTAMP 转换为 TIMESTAMPTZ
-- @author DaYZ
-- @since 2026-06-12
-- ============================================================

-- 确保触发器函数存在（可能在 V2026061101 中已创建）
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

-- edu_enrollment
UPDATE edu_enrollment
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_enrollment;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_enrollment
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_chapter
UPDATE edu_chapter
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_chapter;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_chapter
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_forum
UPDATE edu_forum
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_forum;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_forum
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_forum_post
UPDATE edu_forum_post
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_forum_post;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_forum_post
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_forum_reply
UPDATE edu_forum_reply
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_forum_reply;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_forum_reply
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_question_bank
UPDATE edu_question_bank
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_question_bank;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_question_bank
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_question
UPDATE edu_question
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_question;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_question
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_question_option
UPDATE edu_question_option
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_question_option;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_question_option
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_question_answer
UPDATE edu_question_answer
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_question_answer;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_question_answer
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- edu_course_file
UPDATE edu_course_file
SET updated_at = created_at
WHERE updated_at IS NULL;
DROP TRIGGER IF EXISTS set_updated_at ON edu_course_file;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_course_file
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- 将 TIMESTAMP 列转换为 TIMESTAMPTZ（PostgreSQL 会自动转换时区数据）
-- edu_course
ALTER TABLE edu_course ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_course ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_enrollment
ALTER TABLE edu_enrollment ALTER COLUMN enrolled_at TYPE TIMESTAMPTZ USING enrolled_at AT TIME ZONE 'UTC';
ALTER TABLE edu_enrollment ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';
ALTER TABLE edu_enrollment ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_enrollment ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_chapter
ALTER TABLE edu_chapter ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_chapter ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_course_file
ALTER TABLE edu_course_file ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_course_file ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_course_teacher
ALTER TABLE edu_course_teacher ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- edu_forum
ALTER TABLE edu_forum ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_forum ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_forum_post
ALTER TABLE edu_forum_post ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_forum_post ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE edu_forum_post ALTER COLUMN last_reply_time TYPE TIMESTAMPTZ USING last_reply_time AT TIME ZONE 'UTC';

-- edu_forum_reply
ALTER TABLE edu_forum_reply ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_forum_reply ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_question_bank
ALTER TABLE edu_question_bank ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_question_bank ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_question
ALTER TABLE edu_question ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_question ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_question_option
ALTER TABLE edu_question_option ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_question_option ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- edu_question_answer
ALTER TABLE edu_question_answer ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE edu_question_answer ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
