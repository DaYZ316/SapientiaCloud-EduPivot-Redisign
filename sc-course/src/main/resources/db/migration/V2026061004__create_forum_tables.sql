-- ============================================================
-- V2026061004: 创建论坛相关表
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

-- 课程论坛
CREATE TABLE IF NOT EXISTS edu_forum
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    course_id UUID NOT NULL,
    forum_name VARCHAR
(
    200
) NOT NULL,
    description VARCHAR
(
    2000
),
    forum_type SMALLINT NOT NULL DEFAULT 0,
    allow_anonymous SMALLINT NOT NULL DEFAULT 0,
    post_count BIGINT NOT NULL DEFAULT 0,
    reply_count BIGINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    tags JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_forum_course_id ON edu_forum(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_course_type ON edu_forum(course_id, forum_type);
CREATE INDEX IF NOT EXISTS idx_edu_forum_course_status ON edu_forum(course_id, status);

COMMENT
ON TABLE edu_forum IS '课程论坛';
COMMENT
ON COLUMN edu_forum.forum_type IS '论坛类型: 0=讨论, 1=问答, 2=作业, 3=公告';
COMMENT
ON COLUMN edu_forum.allow_anonymous IS '允许匿名: 0=否, 1=是';
COMMENT
ON COLUMN edu_forum.status IS '状态: 0=正常, 1=关闭, 2=维护中';
COMMENT
ON COLUMN edu_forum.tags IS '标签列表 (JSON数组)';

-- 论坛帖子
CREATE TABLE IF NOT EXISTS edu_forum_post
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    forum_id UUID NOT NULL,
    course_id UUID NOT NULL,
    sys_user_id UUID NOT NULL,
    title VARCHAR
(
    500
) NOT NULL,
    content TEXT NOT NULL,
    post_type SMALLINT NOT NULL DEFAULT 0,
    is_anonymous SMALLINT NOT NULL DEFAULT 0,
    attachment_urls JSONB,
    image_urls JSONB,
    tags JSONB,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    reply_count BIGINT NOT NULL DEFAULT 0,
    share_count BIGINT NOT NULL DEFAULT 0,
    is_top SMALLINT NOT NULL DEFAULT 0,
    is_essence SMALLINT NOT NULL DEFAULT 0,
    is_locked SMALLINT NOT NULL DEFAULT 0,
    last_reply_id UUID,
    last_reply_time TIMESTAMP,
    last_reply_user_id UUID,
    status SMALLINT NOT NULL DEFAULT 0,
    chapter_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_forum_post_course_id ON edu_forum_post(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_user_id ON edu_forum_post(sys_user_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_course_status ON edu_forum_post(course_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_chapter ON edu_forum_post(chapter_id);

DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edu_forum_post'
          AND column_name = 'forum_id'
    ) THEN
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_forum_id ON edu_forum_post(forum_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_forum_status ON edu_forum_post(forum_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_forum_post_top_essence ON edu_forum_post(forum_id, is_top DESC, is_essence DESC);
END IF;
END $$;

COMMENT
ON TABLE edu_forum_post IS '论坛帖子';
COMMENT
ON COLUMN edu_forum_post.post_type IS '帖子类型: 0=普通, 1=公告';
COMMENT
ON COLUMN edu_forum_post.is_anonymous IS '是否匿名: 0=实名, 1=匿名';
COMMENT
ON COLUMN edu_forum_post.is_top IS '是否置顶: 0=否, 1=是';
COMMENT
ON COLUMN edu_forum_post.is_essence IS '是否精华: 0=否, 1=是';
COMMENT
ON COLUMN edu_forum_post.is_locked IS '是否锁定: 0=否, 1=是';
COMMENT
ON COLUMN edu_forum_post.status IS '状态: 0=正常, 1=已删除, 2=审核中, 3=审核失败';
COMMENT
ON COLUMN edu_forum_post.chapter_id IS '关联章节ID (可选)';

-- 论坛回复
CREATE TABLE IF NOT EXISTS edu_forum_reply
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    post_id UUID NOT NULL,
    forum_id UUID NOT NULL,
    course_id UUID NOT NULL,
    sys_user_id UUID NOT NULL,
    content TEXT NOT NULL,
    parent_reply_id UUID,
    reply_to_user_id UUID,
    is_anonymous SMALLINT NOT NULL DEFAULT 0,
    attachment_urls JSONB,
    image_urls JSONB,
    like_count BIGINT NOT NULL DEFAULT 0,
    reply_count BIGINT NOT NULL DEFAULT 0,
    is_accepted SMALLINT NOT NULL DEFAULT 0,
    floor_number INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    ip_address VARCHAR
(
    50
),
    user_agent VARCHAR
(
    500
),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_post_id ON edu_forum_reply(post_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_course_id ON edu_forum_reply(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_user_id ON edu_forum_reply(sys_user_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_parent ON edu_forum_reply(parent_reply_id);
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_post_status ON edu_forum_reply(post_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_post_floor ON edu_forum_reply(post_id, floor_number);

DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'edu_forum_reply'
          AND column_name = 'forum_id'
    ) THEN
CREATE INDEX IF NOT EXISTS idx_edu_forum_reply_forum_id ON edu_forum_reply(forum_id);
END IF;
END $$;

COMMENT
ON TABLE edu_forum_reply IS '论坛回复';
COMMENT
ON COLUMN edu_forum_reply.parent_reply_id IS '父回复ID (树形结构)';
COMMENT
ON COLUMN edu_forum_reply.reply_to_user_id IS '回复目标用户ID';
COMMENT
ON COLUMN edu_forum_reply.is_accepted IS '是否被采纳: 0=否, 1=是 (问答模式)';
COMMENT
ON COLUMN edu_forum_reply.floor_number IS '楼层号';
COMMENT
ON COLUMN edu_forum_reply.status IS '状态: 0=正常, 1=已删除, 2=审核中, 3=审核失败';
