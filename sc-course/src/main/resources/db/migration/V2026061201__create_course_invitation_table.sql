-- 课程助教邀请表
CREATE TABLE IF NOT EXISTS edu_course_invitation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID NOT NULL,
    inviter_id      UUID NOT NULL,
    invitee_id      UUID NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 0,
    message         VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE edu_course_invitation IS '课程助教邀请表';
COMMENT ON COLUMN edu_course_invitation.course_id IS '课程ID';
COMMENT ON COLUMN edu_course_invitation.inviter_id IS '邀请人ID（主讲教师）';
COMMENT ON COLUMN edu_course_invitation.invitee_id IS '被邀请人ID（助教）';
COMMENT ON COLUMN edu_course_invitation.status IS '邀请状态: 0=待处理, 1=已接受, 2=已拒绝, 3=已撤回';
COMMENT ON COLUMN edu_course_invitation.message IS '邀请留言';
COMMENT ON COLUMN edu_course_invitation.deleted IS '软删除: 0=正常, 1=已删除';

-- 唯一约束：同一课程对同一助教只能有一个未删除的邀请
CREATE UNIQUE INDEX uk_course_invitation_course_invitee
    ON edu_course_invitation(course_id, invitee_id) WHERE deleted = 0;

-- 索引：按被邀请人查询邀请
CREATE INDEX idx_course_invitation_invitee_status
    ON edu_course_invitation(invitee_id, status) WHERE deleted = 0;

-- 索引：按课程查询邀请
CREATE INDEX idx_course_invitation_course
    ON edu_course_invitation(course_id) WHERE deleted = 0;
