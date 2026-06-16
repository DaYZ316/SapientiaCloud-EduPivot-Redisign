-- ============================================================
-- V2026060902: 创建学生和教师扩展表
-- @author DaYZ
-- @since 2026-06-09
-- ============================================================

-- 学生表
CREATE TABLE IF NOT EXISTS edu_student
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID NOT NULL,
    student_no VARCHAR
(
    32
) NOT NULL,
    grade VARCHAR
(
    16
),
    major VARCHAR
(
    64
),
    school VARCHAR
(
    128
),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

-- 教师表
CREATE TABLE IF NOT EXISTS edu_teacher
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID NOT NULL,
    employee_no VARCHAR
(
    32
) NOT NULL,
    department VARCHAR
(
    64
),
    title VARCHAR
(
    32
),
    school VARCHAR
(
    128
),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
    );

-- 唯一索引：一个用户只能关联一个学生/教师身份
CREATE UNIQUE INDEX IF NOT EXISTS uk_student_user_id ON edu_student(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_teacher_user_id ON edu_teacher(user_id);

-- 业务索引
CREATE INDEX IF NOT EXISTS idx_student_student_no ON edu_student(student_no);
CREATE INDEX IF NOT EXISTS idx_teacher_employee_no ON edu_teacher(employee_no);

COMMENT
ON TABLE edu_student IS '学生信息表';
COMMENT
ON TABLE edu_teacher IS '教师信息表';

COMMENT
ON COLUMN edu_student.student_no IS '学号';
COMMENT
ON COLUMN edu_student.grade IS '年级';
COMMENT
ON COLUMN edu_student.major IS '专业';
COMMENT
ON COLUMN edu_student.school IS '学校';

COMMENT
ON COLUMN edu_teacher.employee_no IS '工号';
COMMENT
ON COLUMN edu_teacher.department IS '院系';
COMMENT
ON COLUMN edu_teacher.title IS '职称';
COMMENT
ON COLUMN edu_teacher.school IS '学校';
