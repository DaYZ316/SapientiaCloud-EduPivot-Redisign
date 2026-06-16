-- ============================================================
-- V2026061005: 创建题库相关表
-- @author DaYZ
-- @since 2026-06-10
-- ============================================================

-- 课程题库
CREATE TABLE IF NOT EXISTS edu_question_bank
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
    sys_user_id UUID NOT NULL,
    bank_name VARCHAR
(
    200
) NOT NULL,
    description VARCHAR
(
    2000
),
    bank_type SMALLINT NOT NULL DEFAULT 0,
    tags JSONB,
    difficulty SMALLINT NOT NULL DEFAULT 2,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_question_bank_course_id ON edu_question_bank(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_question_bank_course_type ON edu_question_bank(course_id, bank_type);

COMMENT
ON TABLE edu_question_bank IS '课程题库';
COMMENT
ON COLUMN edu_question_bank.bank_type IS '题库类型: 0=练习, 1=考试, 2=作业';
COMMENT
ON COLUMN edu_question_bank.tags IS '标签列表 (JSON数组)';
COMMENT
ON COLUMN edu_question_bank.difficulty IS '难度: 1=简单, 2=中等, 3=困难';

-- 题目
CREATE TABLE IF NOT EXISTS edu_question
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    question_bank_id UUID NOT NULL,
    course_id UUID NOT NULL,
    sys_user_id UUID NOT NULL,
    question_title VARCHAR
(
    500
) NOT NULL,
    question_content TEXT,
    question_type SMALLINT NOT NULL DEFAULT 0,
    difficulty SMALLINT NOT NULL DEFAULT 2,
    score DECIMAL
(
    10,
    2
) NOT NULL DEFAULT 0,
    estimated_time INT,
    tags JSONB,
    image_urls JSONB,
    allow_partial_credit SMALLINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_question_bank_id ON edu_question(question_bank_id);
CREATE INDEX IF NOT EXISTS idx_edu_question_course_id ON edu_question(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_question_bank_type ON edu_question(question_bank_id, question_type);
CREATE INDEX IF NOT EXISTS idx_edu_question_bank_status ON edu_question(question_bank_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_question_difficulty ON edu_question(question_bank_id, difficulty);

COMMENT
ON TABLE edu_question IS '题目';
COMMENT
ON COLUMN edu_question.question_type IS '题目类型: 0=单选, 1=多选, 2=判断, 3=填空, 4=简答';
COMMENT
ON COLUMN edu_question.difficulty IS '难度: 1=简单, 2=中等, 3=困难';
COMMENT
ON COLUMN edu_question.score IS '分值';
COMMENT
ON COLUMN edu_question.estimated_time IS '预估答题时间 (分钟)';
COMMENT
ON COLUMN edu_question.allow_partial_credit IS '允许部分得分: 0=否, 1=是';
COMMENT
ON COLUMN edu_question.status IS '状态: 0=草稿, 1=已发布, 2=已禁用';

-- 题目选项 (选择题)
CREATE TABLE IF NOT EXISTS edu_question_option
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    question_id UUID NOT NULL,
    course_id UUID NOT NULL,
    option_content VARCHAR
(
    2000
) NOT NULL,
    option_label VARCHAR
(
    10
) NOT NULL,
    is_correct SMALLINT NOT NULL DEFAULT 0,
    score DECIMAL
(
    10,
    2
),
    image_urls JSONB,
    explanation VARCHAR
(
    2000
),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_question_option_question_id ON edu_question_option(question_id);

COMMENT
ON TABLE edu_question_option IS '题目选项';
COMMENT
ON COLUMN edu_question_option.option_label IS '选项标签: A, B, C, D 等';
COMMENT
ON COLUMN edu_question_option.is_correct IS '是否正确: 0=错误, 1=正确';
COMMENT
ON COLUMN edu_question_option.score IS '部分得分 (多选题)';

-- 题目答案 (填空/简答)
CREATE TABLE IF NOT EXISTS edu_question_answer
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    question_id UUID NOT NULL,
    course_id UUID NOT NULL,
    answer_content TEXT NOT NULL,
    explanation VARCHAR
(
    2000
),
    score DECIMAL
(
    10,
    2
),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_question_answer_question_id ON edu_question_answer(question_id);

COMMENT
ON TABLE edu_question_answer IS '题目答案';
COMMENT
ON COLUMN edu_question_answer.answer_content IS '答案内容';
COMMENT
ON COLUMN edu_question_answer.score IS '该空/该题得分';
COMMENT
ON COLUMN edu_question_answer.sort_order IS '填空序号';
