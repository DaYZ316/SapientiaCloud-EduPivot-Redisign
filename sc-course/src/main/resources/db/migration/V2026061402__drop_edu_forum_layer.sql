-- Drop the legacy forum partition layer. Course discussions are now owned by course_id.

DROP INDEX IF EXISTS idx_edu_forum_course_id;
DROP INDEX IF EXISTS idx_edu_forum_course_type;
DROP INDEX IF EXISTS idx_edu_forum_course_status;

DROP INDEX IF EXISTS idx_edu_forum_post_forum_id;
DROP INDEX IF EXISTS idx_edu_forum_post_forum_status;
DROP INDEX IF EXISTS idx_edu_forum_post_top_essence;
DROP INDEX IF EXISTS idx_edu_forum_post_forum_top_created;

DROP INDEX IF EXISTS idx_edu_forum_reply_forum_id;

ALTER TABLE edu_forum_post DROP COLUMN IF EXISTS forum_id;
ALTER TABLE edu_forum_reply DROP COLUMN IF EXISTS forum_id;

CREATE INDEX IF NOT EXISTS idx_edu_forum_post_course_top_created
    ON edu_forum_post(course_id, is_top DESC, created_at DESC);

DROP TRIGGER IF EXISTS set_updated_at ON edu_forum;
DROP TABLE IF EXISTS edu_forum;
