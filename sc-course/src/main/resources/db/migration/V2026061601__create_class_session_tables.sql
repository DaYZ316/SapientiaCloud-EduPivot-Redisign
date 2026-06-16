-- ============================================================
-- V2026061601: Create class session tables
-- ============================================================

CREATE TABLE IF NOT EXISTS edu_class_session
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
    teacher_id UUID NOT NULL,
    title VARCHAR
(
    200
) NOT NULL,
    description TEXT,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    room_size SMALLINT NOT NULL DEFAULT 0,
    live_room_name VARCHAR
(
    120
) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_edu_class_session_time CHECK
(
    scheduled_end_at >
    scheduled_start_at
)
    );

CREATE INDEX IF NOT EXISTS idx_edu_class_session_course_id ON edu_class_session(course_id);
CREATE INDEX IF NOT EXISTS idx_edu_class_session_teacher_id ON edu_class_session(teacher_id);
CREATE INDEX IF NOT EXISTS idx_edu_class_session_published_at ON edu_class_session(published_at);
CREATE INDEX IF NOT EXISTS idx_edu_class_session_start_at ON edu_class_session(scheduled_start_at);

DROP TRIGGER IF EXISTS set_updated_at ON edu_class_session;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON edu_class_session
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

COMMENT
ON TABLE edu_class_session IS '上课记录';
COMMENT
ON COLUMN edu_class_session.published_at IS '发布时间，NULL 表示备课草稿';
COMMENT
ON COLUMN edu_class_session.room_size IS '3D 教室规格: 0=小型, 1=中型, 2=大型, 3=超大型';
COMMENT
ON COLUMN edu_class_session.live_room_name IS 'LiveKit 房间名称';

CREATE TABLE IF NOT EXISTS edu_class_participant
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role SMALLINT NOT NULL,
    x NUMERIC
(
    10,
    2
) NOT NULL DEFAULT 0,
    y NUMERIC
(
    10,
    2
) NOT NULL DEFAULT 0,
    z NUMERIC
(
    10,
    2
) NOT NULL DEFAULT 0,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    UNIQUE
(
    session_id,
    user_id
)
    );

CREATE INDEX IF NOT EXISTS idx_edu_class_participant_session_id ON edu_class_participant(session_id);
CREATE INDEX IF NOT EXISTS idx_edu_class_participant_user_id ON edu_class_participant(user_id);

COMMENT
ON TABLE edu_class_participant IS '课堂参与者';
COMMENT
ON COLUMN edu_class_participant.role IS '参与者角色: 0=教师, 1=学生';
COMMENT
ON COLUMN edu_class_participant.x IS '教室 2D 平面 X 坐标';
COMMENT
ON COLUMN edu_class_participant.y IS '教室 2D 平面 Y 坐标';
COMMENT
ON COLUMN edu_class_participant.z IS '预留 3D 坐标，当前固定为 0';

CREATE TABLE IF NOT EXISTS edu_class_barrage
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    session_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR
(
    300
) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    deleted SMALLINT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_edu_class_barrage_session_sent ON edu_class_barrage(session_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_edu_class_barrage_sender_id ON edu_class_barrage(sender_id);

COMMENT
ON TABLE edu_class_barrage IS '课堂弹幕';
COMMENT
ON COLUMN edu_class_barrage.content IS '弹幕内容，最多 300 字符';
