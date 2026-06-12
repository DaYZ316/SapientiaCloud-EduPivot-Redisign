-- ============================================================
-- V2026061003: 创建课程章节表
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

CREATE TABLE IF NOT EXISTS edu_chapter (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID NOT NULL,
    teacher_id      UUID NOT NULL,
    chapter_name    VARCHAR(200) NOT NULL,
    parent_chapter_id UUID,
    description     VARCHAR(2000),
    content         TEXT,
    attachment_urls JSONB,
    sort_order      INT NOT NULL DEFAULT 0,
    status          SMALLINT NOT NULL DEFAULT 0,
    view_count      BIGINT NOT NULL DEFAULT 0,
    like_count      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_edu_chapter_course_id ON edu_chapter(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_chapter_parent ON edu_chapter(parent_chapter_id);
CREATE INDEX IF NOT EXISTS idx_edu_chapter_course_sort ON edu_chapter(course_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_edu_chapter_course_status ON edu_chapter(course_id, status);

-- 注释
COMMENT ON TABLE edu_chapter IS '课程章节';
COMMENT ON COLUMN edu_chapter.id IS '章节ID';
COMMENT ON COLUMN edu_chapter.course_id IS '所属课程ID';
COMMENT ON COLUMN edu_chapter.teacher_id IS '创建教师ID';
COMMENT ON COLUMN edu_chapter.chapter_name IS '章节名称';
COMMENT ON COLUMN edu_chapter.parent_chapter_id IS '父章节ID (树形结构)';
COMMENT ON COLUMN edu_chapter.description IS '章节描述';
COMMENT ON COLUMN edu_chapter.content IS '富文本内容';
COMMENT ON COLUMN edu_chapter.attachment_urls IS '附件URL列表 (JSON数组)';
COMMENT ON COLUMN edu_chapter.sort_order IS '排序权重';
COMMENT ON COLUMN edu_chapter.status IS '状态: 0=草稿, 1=已发布';
COMMENT ON COLUMN edu_chapter.view_count IS '浏览次数';
COMMENT ON COLUMN edu_chapter.like_count IS '点赞次数';
COMMENT ON COLUMN edu_chapter.created_at IS '创建时间';
COMMENT ON COLUMN edu_chapter.updated_at IS '更新时间';
COMMENT ON COLUMN edu_chapter.deleted IS '逻辑删除: 0=正常, 1=已删除';
