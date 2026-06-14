CREATE TABLE IF NOT EXISTS edu_chapter_like (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id  UUID        NOT NULL,
    course_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_chapter_like_chapter_user
    ON edu_chapter_like (chapter_id, user_id);

CREATE INDEX IF NOT EXISTS idx_edu_chapter_like_chapter_id
    ON edu_chapter_like (chapter_id);

CREATE INDEX IF NOT EXISTS idx_edu_chapter_like_user_id
    ON edu_chapter_like (user_id);

COMMENT ON TABLE  edu_chapter_like                IS '章节点赞关系表';
COMMENT ON COLUMN edu_chapter_like.id             IS '主键';
COMMENT ON COLUMN edu_chapter_like.chapter_id     IS '章节ID';
COMMENT ON COLUMN edu_chapter_like.course_id      IS '课程ID（冗余，方便按课程查询）';
COMMENT ON COLUMN edu_chapter_like.user_id        IS '用户ID';
COMMENT ON COLUMN edu_chapter_like.created_at     IS '点赞时间';
