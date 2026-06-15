CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION demo_uuidv7()
RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    ts_ms bigint := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
    b bytea := gen_random_bytes(16);
    hex text;
BEGIN
    b := set_byte(b, 0, ((ts_ms >> 40) & 255)::int);
    b := set_byte(b, 1, ((ts_ms >> 32) & 255)::int);
    b := set_byte(b, 2, ((ts_ms >> 24) & 255)::int);
    b := set_byte(b, 3, ((ts_ms >> 16) & 255)::int);
    b := set_byte(b, 4, ((ts_ms >> 8) & 255)::int);
    b := set_byte(b, 5, (ts_ms & 255)::int);
    b := set_byte(b, 6, (get_byte(b, 6) & 15) | 112);
    b := set_byte(b, 8, (get_byte(b, 8) & 63) | 128);
    hex := encode(b, 'hex');
    RETURN (
        substr(hex, 1, 8) || '-' ||
        substr(hex, 9, 4) || '-' ||
        substr(hex, 13, 4) || '-' ||
        substr(hex, 17, 4) || '-' ||
        substr(hex, 21, 12)
    )::uuid;
END;
$$;

WITH student_seed(no, name, major, grade, school) AS (
    VALUES
        (1, U&'\6797\4E00\8BFA', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2023', 'SapientiaCloud University'),
        (2, U&'\9648\82E5\66E6', U&'\8F6F\4EF6\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (3, U&'\738B\661F\6CB3', U&'\4EBA\5DE5\667A\80FD', '2024', 'SapientiaCloud University'),
        (4, U&'\674E\77E5\590F', U&'\6570\636E\79D1\5B66', '2024', 'SapientiaCloud University'),
        (5, U&'\8D75\660E\8F69', U&'\7F51\7EDC\5DE5\7A0B', '2022', 'SapientiaCloud University'),
        (6, U&'\5468\96E8\6850', U&'\4FE1\606F\5B89\5168', '2022', 'SapientiaCloud University'),
        (7, U&'\5434\4EA6\8FB0', U&'\6570\5B57\5A92\4F53\6280\672F', '2023', 'SapientiaCloud University'),
        (8, U&'\90D1\4E66\7476', U&'\7535\5B50\5546\52A1', '2024', 'SapientiaCloud University'),
        (9, U&'\5B59\666F\884C', U&'\7269\8054\7F51\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (10, U&'\80E1\5B89\7136', U&'\6559\80B2\6280\672F\5B66', '2022', 'SapientiaCloud University'),
        (11, 'Alex Carter', 'Computer Science', '2023', 'SapientiaCloud University'),
        (12, 'Mia Johnson', 'Software Engineering', '2024', 'SapientiaCloud University'),
        (13, 'Noah Smith', 'Data Science', '2023', 'SapientiaCloud University'),
        (14, 'Emma Davis', 'Cybersecurity', '2024', 'SapientiaCloud University'),
        (15, 'Liam Wilson', 'Artificial Intelligence', '2022', 'SapientiaCloud University'),
        (16, 'Olivia Brown', 'Cloud Computing', '2023', 'SapientiaCloud University'),
        (17, 'Ethan Miller', 'Information Systems', '2024', 'SapientiaCloud University'),
        (18, 'Sophia Garcia', 'Digital Media', '2022', 'SapientiaCloud University'),
        (19, 'Lucas Martinez', 'Educational Technology', '2023', 'SapientiaCloud University'),
        (20, 'Ava Anderson', 'Network Engineering', '2024', 'SapientiaCloud University'),
        (21, U&'\9AD8\5B50\58A8', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2023', 'SapientiaCloud University'),
        (22, U&'\6881\6E05\8D8A', U&'\8F6F\4EF6\5DE5\7A0B', '2024', 'SapientiaCloud University'),
        (23, U&'\5510\6C90\9633', U&'\4EBA\5DE5\667A\80FD', '2022', 'SapientiaCloud University'),
        (24, U&'\8BB8\5FF5\6148', U&'\6570\636E\79D1\5B66', '2023', 'SapientiaCloud University'),
        (25, U&'\97E9\666F\884C', U&'\4FE1\606F\5B89\5168', '2024', 'SapientiaCloud University'),
        (26, 'Jackson Moore', 'Software Engineering', '2022', 'SapientiaCloud University'),
        (27, 'Isabella Taylor', 'Computer Science', '2023', 'SapientiaCloud University'),
        (28, 'Aiden Thomas', 'Data Science', '2024', 'SapientiaCloud University'),
        (29, 'Charlotte Lee', 'Cybersecurity', '2022', 'SapientiaCloud University'),
        (30, 'Benjamin White', 'Artificial Intelligence', '2023', 'SapientiaCloud University'),
        (31, U&'\5B8B\82B7\6674', U&'\4E91\8BA1\7B97\6280\672F', '2024', 'SapientiaCloud University'),
        (32, U&'\9646\8FDC\822A', U&'\7F51\7EDC\5DE5\7A0B', '2022', 'SapientiaCloud University'),
        (33, U&'\6C88\5609\6811', U&'\7269\8054\7F51\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (34, U&'\4F55\661F\6F9C', U&'\6570\5B57\5A92\4F53\6280\672F', '2024', 'SapientiaCloud University'),
        (35, U&'\53F6\521D\68E0', U&'\6559\80B2\6280\672F\5B66', '2022', 'SapientiaCloud University'),
        (36, 'Amelia Harris', 'Cloud Computing', '2023', 'SapientiaCloud University'),
        (37, 'Henry Clark', 'Information Systems', '2024', 'SapientiaCloud University'),
        (38, 'Harper Lewis', 'Digital Media', '2022', 'SapientiaCloud University'),
        (39, 'Daniel Walker', 'Educational Technology', '2023', 'SapientiaCloud University'),
        (40, 'Ella Young', 'Network Engineering', '2024', 'SapientiaCloud University'),
        (41, U&'\848B\4E91\8212', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2022', 'SapientiaCloud University'),
        (42, U&'\9093\6E05\91CE', U&'\8F6F\4EF6\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (43, U&'\51AF\77E5\8FDC', U&'\4EBA\5DE5\667A\80FD', '2024', 'SapientiaCloud University'),
        (44, U&'\859B\6620\96EA', U&'\6570\636E\79D1\5B66', '2022', 'SapientiaCloud University'),
        (45, U&'\7A0B\781A\79CB', U&'\4FE1\606F\5B89\5168', '2023', 'SapientiaCloud University'),
        (46, 'Grace Hall', 'Cloud Computing', '2024', 'SapientiaCloud University'),
        (47, 'Matthew Allen', 'Information Systems', '2022', 'SapientiaCloud University'),
        (48, 'Victoria King', 'Digital Media', '2023', 'SapientiaCloud University'),
        (49, 'Samuel Wright', 'Educational Technology', '2024', 'SapientiaCloud University'),
        (50, 'Scarlett Scott', 'Network Engineering', '2022', 'SapientiaCloud University')
)
INSERT INTO auth_users (
    id, email, email_verified, display_name, avatar_url, locale, status,
    created_at, updated_at, created_provider, login_count, role, password_hash,
    theme, notification_enabled
)
    SELECT
        demo_uuidv7(),
    'student' || lpad(no::text, 2, '0') || '@demo.edupivot.local',
    true,
    name,
    '/assets/avatar-student-default.png',
    CASE WHEN no <= 25 THEN 'zh-CN' ELSE 'en-US' END,
    'ACTIVE',
    now(),
    now(),
    'LOCAL',
    0,
    1,
    '$2a$10$huaOC4eWlgDi4nFxsxt.FubBYACth4Lu9r2ygmwsP7iw0/LPFHnJa',
    'system',
    true
FROM student_seed
WHERE NOT EXISTS (
    SELECT 1
    FROM auth_users existing
    WHERE existing.email = 'student' || lpad(student_seed.no::text, 2, '0') || '@demo.edupivot.local'
);

WITH student_seed(no, name, major, grade, school) AS (
    VALUES
        (1, U&'\6797\4E00\8BFA', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2023', 'SapientiaCloud University'),
        (2, U&'\9648\82E5\66E6', U&'\8F6F\4EF6\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (3, U&'\738B\661F\6CB3', U&'\4EBA\5DE5\667A\80FD', '2024', 'SapientiaCloud University'),
        (4, U&'\674E\77E5\590F', U&'\6570\636E\79D1\5B66', '2024', 'SapientiaCloud University'),
        (5, U&'\8D75\660E\8F69', U&'\7F51\7EDC\5DE5\7A0B', '2022', 'SapientiaCloud University'),
        (6, U&'\5468\96E8\6850', U&'\4FE1\606F\5B89\5168', '2022', 'SapientiaCloud University'),
        (7, U&'\5434\4EA6\8FB0', U&'\6570\5B57\5A92\4F53\6280\672F', '2023', 'SapientiaCloud University'),
        (8, U&'\90D1\4E66\7476', U&'\7535\5B50\5546\52A1', '2024', 'SapientiaCloud University'),
        (9, U&'\5B59\666F\884C', U&'\7269\8054\7F51\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (10, U&'\80E1\5B89\7136', U&'\6559\80B2\6280\672F\5B66', '2022', 'SapientiaCloud University'),
        (11, 'Alex Carter', 'Computer Science', '2023', 'SapientiaCloud University'),
        (12, 'Mia Johnson', 'Software Engineering', '2024', 'SapientiaCloud University'),
        (13, 'Noah Smith', 'Data Science', '2023', 'SapientiaCloud University'),
        (14, 'Emma Davis', 'Cybersecurity', '2024', 'SapientiaCloud University'),
        (15, 'Liam Wilson', 'Artificial Intelligence', '2022', 'SapientiaCloud University'),
        (16, 'Olivia Brown', 'Cloud Computing', '2023', 'SapientiaCloud University'),
        (17, 'Ethan Miller', 'Information Systems', '2024', 'SapientiaCloud University'),
        (18, 'Sophia Garcia', 'Digital Media', '2022', 'SapientiaCloud University'),
        (19, 'Lucas Martinez', 'Educational Technology', '2023', 'SapientiaCloud University'),
        (20, 'Ava Anderson', 'Network Engineering', '2024', 'SapientiaCloud University'),
        (21, U&'\9AD8\5B50\58A8', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2023', 'SapientiaCloud University'),
        (22, U&'\6881\6E05\8D8A', U&'\8F6F\4EF6\5DE5\7A0B', '2024', 'SapientiaCloud University'),
        (23, U&'\5510\6C90\9633', U&'\4EBA\5DE5\667A\80FD', '2022', 'SapientiaCloud University'),
        (24, U&'\8BB8\5FF5\6148', U&'\6570\636E\79D1\5B66', '2023', 'SapientiaCloud University'),
        (25, U&'\97E9\666F\884C', U&'\4FE1\606F\5B89\5168', '2024', 'SapientiaCloud University'),
        (26, 'Jackson Moore', 'Software Engineering', '2022', 'SapientiaCloud University'),
        (27, 'Isabella Taylor', 'Computer Science', '2023', 'SapientiaCloud University'),
        (28, 'Aiden Thomas', 'Data Science', '2024', 'SapientiaCloud University'),
        (29, 'Charlotte Lee', 'Cybersecurity', '2022', 'SapientiaCloud University'),
        (30, 'Benjamin White', 'Artificial Intelligence', '2023', 'SapientiaCloud University'),
        (31, U&'\5B8B\82B7\6674', U&'\4E91\8BA1\7B97\6280\672F', '2024', 'SapientiaCloud University'),
        (32, U&'\9646\8FDC\822A', U&'\7F51\7EDC\5DE5\7A0B', '2022', 'SapientiaCloud University'),
        (33, U&'\6C88\5609\6811', U&'\7269\8054\7F51\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (34, U&'\4F55\661F\6F9C', U&'\6570\5B57\5A92\4F53\6280\672F', '2024', 'SapientiaCloud University'),
        (35, U&'\53F6\521D\68E0', U&'\6559\80B2\6280\672F\5B66', '2022', 'SapientiaCloud University'),
        (36, 'Amelia Harris', 'Cloud Computing', '2023', 'SapientiaCloud University'),
        (37, 'Henry Clark', 'Information Systems', '2024', 'SapientiaCloud University'),
        (38, 'Harper Lewis', 'Digital Media', '2022', 'SapientiaCloud University'),
        (39, 'Daniel Walker', 'Educational Technology', '2023', 'SapientiaCloud University'),
        (40, 'Ella Young', 'Network Engineering', '2024', 'SapientiaCloud University'),
        (41, U&'\848B\4E91\8212', U&'\8BA1\7B97\673A\79D1\5B66\4E0E\6280\672F', '2022', 'SapientiaCloud University'),
        (42, U&'\9093\6E05\91CE', U&'\8F6F\4EF6\5DE5\7A0B', '2023', 'SapientiaCloud University'),
        (43, U&'\51AF\77E5\8FDC', U&'\4EBA\5DE5\667A\80FD', '2024', 'SapientiaCloud University'),
        (44, U&'\859B\6620\96EA', U&'\6570\636E\79D1\5B66', '2022', 'SapientiaCloud University'),
        (45, U&'\7A0B\781A\79CB', U&'\4FE1\606F\5B89\5168', '2023', 'SapientiaCloud University'),
        (46, 'Grace Hall', 'Cloud Computing', '2024', 'SapientiaCloud University'),
        (47, 'Matthew Allen', 'Information Systems', '2022', 'SapientiaCloud University'),
        (48, 'Victoria King', 'Digital Media', '2023', 'SapientiaCloud University'),
        (49, 'Samuel Wright', 'Educational Technology', '2024', 'SapientiaCloud University'),
        (50, 'Scarlett Scott', 'Network Engineering', '2022', 'SapientiaCloud University')
)
INSERT INTO edu_student (id, user_id, student_no, grade, major, school, created_at, updated_at, deleted)
SELECT
    demo_uuidv7(),
    u.id,
    'S2026' || lpad(s.no::text, 4, '0'),
    s.grade,
    s.major,
    s.school,
    now(),
    now(),
    0
FROM student_seed s
JOIN auth_users u ON u.email = 'student' || lpad(s.no::text, 2, '0') || '@demo.edupivot.local'
ON CONFLICT DO NOTHING;

WITH teacher_seed(no, name, department, title, school) AS (
    VALUES
        (1, U&'\5F20\660E\8FDC', U&'\8BA1\7B97\673A\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (2, U&'\5218\6E05\548C', U&'\8F6F\4EF6\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (3, U&'\9EC4\601D\6E90', U&'\4EBA\5DE5\667A\80FD\5B66\9662', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (4, U&'\9A6C\82E5\7433', U&'\6570\636E\79D1\5B66\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (5, U&'\90ED\4E91\5E06', U&'\7F51\7EDC\7A7A\95F4\5B89\5168\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (6, U&'\82CF\9526\7A0B', U&'\6559\80B2\6280\672F\5B66\9662', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (7, U&'\6797\521D\9633', U&'\6570\5B57\5A92\4F53\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (8, U&'\79E6\96E8\6CFD', U&'\4FE1\606F\5DE5\7A0B\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (9, U&'\8BB8\5B89\5B81', U&'\4E91\8BA1\7B97\7814\7A76\4E2D\5FC3', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (10, U&'\8881\77E5\5FAE', U&'\8F6F\4EF6\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (11, 'Dr. Emily Stone', 'Computer Science', 'Professor', 'SapientiaCloud University'),
        (12, 'Dr. Michael Reed', 'Software Engineering', 'Associate Professor', 'SapientiaCloud University'),
        (13, 'Dr. Sarah Brooks', 'Artificial Intelligence', 'Lecturer', 'SapientiaCloud University'),
        (14, 'Dr. David Morgan', 'Data Science', 'Professor', 'SapientiaCloud University'),
        (15, 'Dr. Laura Price', 'Cybersecurity', 'Associate Professor', 'SapientiaCloud University'),
        (16, 'Dr. James Bennett', 'Cloud Computing', 'Lecturer', 'SapientiaCloud University'),
        (17, 'Dr. Natalie Foster', 'Educational Technology', 'Professor', 'SapientiaCloud University'),
        (18, 'Dr. Ryan Cooper', 'Digital Media', 'Associate Professor', 'SapientiaCloud University'),
        (19, 'Dr. Chloe Parker', 'Information Systems', 'Lecturer', 'SapientiaCloud University'),
        (20, 'Dr. Owen Bailey', 'Network Engineering', 'Professor', 'SapientiaCloud University')
)
INSERT INTO auth_users (
    id, email, email_verified, display_name, avatar_url, locale, status,
    created_at, updated_at, created_provider, login_count, role, password_hash,
    theme, notification_enabled
)
SELECT
    demo_uuidv7(),
    'teacher' || lpad(no::text, 2, '0') || '@demo.edupivot.local',
    true,
    name,
    '/assets/avatar-teacher-default.png',
    CASE WHEN no <= 10 THEN 'zh-CN' ELSE 'en-US' END,
    'ACTIVE',
    now(),
    now(),
    'LOCAL',
    0,
    2,
    '$2a$10$huaOC4eWlgDi4nFxsxt.FubBYACth4Lu9r2ygmwsP7iw0/LPFHnJa',
    'system',
    true
FROM teacher_seed
WHERE NOT EXISTS (
    SELECT 1
    FROM auth_users existing
    WHERE existing.email = 'teacher' || lpad(teacher_seed.no::text, 2, '0') || '@demo.edupivot.local'
);

UPDATE auth_users
SET password_hash = '$2a$10$huaOC4eWlgDi4nFxsxt.FubBYACth4Lu9r2ygmwsP7iw0/LPFHnJa',
    updated_at = now()
WHERE email ~ '^(student|teacher)[0-9]{2}@demo\.edupivot\.local$';

WITH teacher_seed(no, name, department, title, school) AS (
    VALUES
        (1, U&'\5F20\660E\8FDC', U&'\8BA1\7B97\673A\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (2, U&'\5218\6E05\548C', U&'\8F6F\4EF6\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (3, U&'\9EC4\601D\6E90', U&'\4EBA\5DE5\667A\80FD\5B66\9662', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (4, U&'\9A6C\82E5\7433', U&'\6570\636E\79D1\5B66\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (5, U&'\90ED\4E91\5E06', U&'\7F51\7EDC\7A7A\95F4\5B89\5168\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (6, U&'\82CF\9526\7A0B', U&'\6559\80B2\6280\672F\5B66\9662', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (7, U&'\6797\521D\9633', U&'\6570\5B57\5A92\4F53\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (8, U&'\79E6\96E8\6CFD', U&'\4FE1\606F\5DE5\7A0B\5B66\9662', U&'\6559\6388', 'SapientiaCloud University'),
        (9, U&'\8BB8\5B89\5B81', U&'\4E91\8BA1\7B97\7814\7A76\4E2D\5FC3', U&'\8BB2\5E08', 'SapientiaCloud University'),
        (10, U&'\8881\77E5\5FAE', U&'\8F6F\4EF6\5B66\9662', U&'\526F\6559\6388', 'SapientiaCloud University'),
        (11, 'Dr. Emily Stone', 'Computer Science', 'Professor', 'SapientiaCloud University'),
        (12, 'Dr. Michael Reed', 'Software Engineering', 'Associate Professor', 'SapientiaCloud University'),
        (13, 'Dr. Sarah Brooks', 'Artificial Intelligence', 'Lecturer', 'SapientiaCloud University'),
        (14, 'Dr. David Morgan', 'Data Science', 'Professor', 'SapientiaCloud University'),
        (15, 'Dr. Laura Price', 'Cybersecurity', 'Associate Professor', 'SapientiaCloud University'),
        (16, 'Dr. James Bennett', 'Cloud Computing', 'Lecturer', 'SapientiaCloud University'),
        (17, 'Dr. Natalie Foster', 'Educational Technology', 'Professor', 'SapientiaCloud University'),
        (18, 'Dr. Ryan Cooper', 'Digital Media', 'Associate Professor', 'SapientiaCloud University'),
        (19, 'Dr. Chloe Parker', 'Information Systems', 'Lecturer', 'SapientiaCloud University'),
        (20, 'Dr. Owen Bailey', 'Network Engineering', 'Professor', 'SapientiaCloud University')
)
INSERT INTO edu_teacher (id, user_id, employee_no, department, title, school, created_at, updated_at, deleted)
SELECT
    demo_uuidv7(),
    u.id,
    'T2026' || lpad(t.no::text, 4, '0'),
    t.department,
    t.title,
    t.school,
    now(),
    now(),
    0
FROM teacher_seed t
JOIN auth_users u ON u.email = 'teacher' || lpad(t.no::text, 2, '0') || '@demo.edupivot.local'
ON CONFLICT DO NOTHING;

WITH course_seed(no, title, description, teacher_email, level, cover_url, max_students, semester, location, course_type) AS (
    VALUES
        (1, U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\9762\5411\672C\79D1\751F\7684\4EBA\5DE5\667A\80FD\4E0E\667A\80FD\7CFB\7EDF\57FA\7840\8BFE\7A0B\3002', 'teacher01@demo.edupivot.local', 1, 'https://images.unsplash.com/photo-1677442136019-21780ecad995?auto=format&fit=crop&w=1200&q=80', 120, '2026 Spring', 'A-301', 0),
        (2, U&'\4E91\539F\751F\5FAE\670D\52A1\5B9E\8DF5', U&'\56F4\7ED5\5BB9\5668\3001\670D\52A1\6CBB\7406\548C\53EF\89C2\6D4B\6027\7684\5DE5\7A0B\5B9E\8DF5\8BFE\7A0B\3002', 'teacher02@demo.edupivot.local', 2, 'https://images.unsplash.com/photo-1667372393119-3d4c48d07fc9?auto=format&fit=crop&w=1200&q=80', 90, '2026 Spring', 'B-204', 1),
        (3, U&'\6570\636E\53EF\89C6\5316\8BBE\8BA1', U&'\5B66\4E60\7528\89C6\89C9\7ED3\6784\8BB2\6E05\590D\6742\6570\636E\6545\4E8B\3002', 'teacher03@demo.edupivot.local', 2, 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=1200&q=80', 80, '2026 Summer', 'Design Lab', 1),
        (4, U&'\7F51\7EDC\5B89\5168\653B\9632\57FA\7840', U&'\8986\76D6\5E38\89C1\5B89\5168\5A01\80C1\3001\9632\62A4\7B56\7565\4E0E\5B9E\9A8C\73AF\5883\6F14\7EC3\3002', 'teacher04@demo.edupivot.local', 2, 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=1200&q=80', 70, '2026 Spring', 'Cyber Lab', 0),
        (5, U&'\6559\80B2\6280\672F\4E0E\5B66\4E60\5206\6790', U&'\7ED3\5408\5B66\4E60\5E73\53F0\6570\636E\7406\89E3\6559\5B66\6539\8FDB\65B9\6CD5\3002', 'teacher05@demo.edupivot.local', 1, 'https://images.unsplash.com/photo-1509062522246-3755977927d7?auto=format&fit=crop&w=1200&q=80', 100, '2026 Autumn', 'C-112', 1),
        (6, 'Advanced Software Architecture', 'Design resilient systems with modular boundaries and pragmatic tradeoffs.', 'teacher11@demo.edupivot.local', 3, 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?auto=format&fit=crop&w=1200&q=80', 75, '2026 Spring', 'E-502', 0),
        (7, 'Machine Learning Studio', 'A project-based studio for model design, evaluation, and deployment.', 'teacher12@demo.edupivot.local', 2, 'https://images.unsplash.com/photo-1555949963-aa79dcee981c?auto=format&fit=crop&w=1200&q=80', 64, '2026 Summer', 'AI Lab', 1),
        (8, 'Human-Computer Interaction', 'Study usable interfaces through research, prototyping, and critique.', 'teacher13@demo.edupivot.local', 1, 'https://images.unsplash.com/photo-1559028012-481c04fa702d?auto=format&fit=crop&w=1200&q=80', 88, '2026 Autumn', 'D-210', 1),
        (9, 'Distributed Database Systems', 'Explore storage engines, transactions, replication, and consistency.', 'teacher14@demo.edupivot.local', 3, 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?auto=format&fit=crop&w=1200&q=80', 60, '2026 Autumn', 'B-308', 0),
        (10, 'Digital Product Strategy', 'Connect product discovery, analytics, and delivery in modern teams.', 'teacher15@demo.edupivot.local', 2, 'https://images.unsplash.com/photo-1552664730-d307ca884978?auto=format&fit=crop&w=1200&q=80', 110, '2026 Spring', 'Studio 1', 1)
)
INSERT INTO edu_course (
    id, title, description, teacher_id, level, cover_url, semester, location,
    course_type, max_students, is_public, status, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(),
    c.title,
    c.description,
    u.id,
    c.level,
    c.cover_url,
    c.semester,
    c.location,
    c.course_type,
    c.max_students,
    1,
    1,
    now(),
    now(),
    0
FROM course_seed c
JOIN auth_users u ON u.email = c.teacher_email
WHERE NOT EXISTS (
    SELECT 1 FROM edu_course existing
    WHERE existing.title = c.title AND existing.deleted = 0
);

INSERT INTO edu_course_teacher (course_id, teacher_id)
SELECT c.id, c.teacher_id
FROM edu_course c
WHERE c.title IN (
    U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA',
    U&'\4E91\539F\751F\5FAE\670D\52A1\5B9E\8DF5',
    U&'\6570\636E\53EF\89C6\5316\8BBE\8BA1',
    U&'\7F51\7EDC\5B89\5168\653B\9632\57FA\7840',
    U&'\6559\80B2\6280\672F\4E0E\5B66\4E60\5206\6790',
    'Advanced Software Architecture',
    'Machine Learning Studio',
    'Human-Computer Interaction',
    'Distributed Database Systems',
    'Digital Product Strategy'
)
ON CONFLICT DO NOTHING;
