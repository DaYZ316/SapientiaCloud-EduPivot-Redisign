-- ============================================================
-- V2026061601: 创建 AI 教学助手（Celestial Hub）相关表
-- @author DaYZ
-- @since 2026-06-16
-- ============================================================

-- ----------------------------
-- 会话表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    pinned SMALLINT NOT NULL DEFAULT 0,
    favorited SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user
    ON ai_conversation(user_id);

COMMENT ON TABLE ai_conversation IS 'AI 助手会话';
COMMENT ON COLUMN ai_conversation.user_id IS '所属用户 ID';
COMMENT ON COLUMN ai_conversation.title IS '会话标题';
COMMENT ON COLUMN ai_conversation.pinned IS '是否置顶 0=否 1=是';
COMMENT ON COLUMN ai_conversation.favorited IS '是否收藏 0=否 1=是';
COMMENT ON COLUMN ai_conversation.deleted IS '软删除 0=正常 1=已删除';

-- ----------------------------
-- 消息表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_message_ai_conversation
        FOREIGN KEY(conversation_id) REFERENCES ai_conversation(id)
);

CREATE INDEX IF NOT EXISTS idx_ai_message_conversation
    ON ai_message(conversation_id);

COMMENT ON TABLE ai_message IS 'AI 助手对话消息';
COMMENT ON COLUMN ai_message.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN ai_message.role IS '角色 USER/ASSISTANT/SYSTEM';
COMMENT ON COLUMN ai_message.content IS '消息内容';
COMMENT ON COLUMN ai_message.deleted IS '软删除 0=正常 1=已删除';

-- ----------------------------
-- 知识库文档表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    storage_object_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_doc_user
    ON ai_knowledge_doc(user_id);

COMMENT ON TABLE ai_knowledge_doc IS 'AI 知识库文档';
COMMENT ON COLUMN ai_knowledge_doc.user_id IS '上传用户 ID';
COMMENT ON COLUMN ai_knowledge_doc.storage_object_id IS 'sc-storage 文件对象 ID';
COMMENT ON COLUMN ai_knowledge_doc.filename IS '文件名';
COMMENT ON COLUMN ai_knowledge_doc.status IS '索引状态 PENDING/INDEXED/FAILED';
COMMENT ON COLUMN ai_knowledge_doc.chunk_count IS '切分入库的片段数';
COMMENT ON COLUMN ai_knowledge_doc.deleted IS '软删除 0=正常 1=已删除';
