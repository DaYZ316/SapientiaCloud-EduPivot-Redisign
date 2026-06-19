-- Rich virtual simulation seed data for SapientiaCloud EduPivot.
--
-- This script is intentionally not a Flyway migration. Run it manually after
-- the auth, course, and notification services have applied their migrations.
--
-- Content grounding, paraphrased into original test content:
-- - Unity Learn: https://learn.unity.com/
-- - Unreal Engine Documentation: https://dev.epicgames.com/documentation/unreal-engine/
-- - Blender Manual: https://docs.blender.org/manual/en/latest/
-- - MDN Learn Web Development: https://developer.mozilla.org/en-US/docs/Learn_web_development
-- - Google Machine Learning Crash Course: https://developers.google.com/machine-learning/crash-course
-- - Ableton Learning Music: https://learningmusic.ableton.com/
-- - Krita Manual: https://docs.krita.org/en/
--
-- Date policy: every generated timestamp is in 2026 and no later than
-- 2026-06-19 23:59:59+08 (Asia/Shanghai).
--
-- Login policy: all seeded teachers and students use the same password as the
-- admin account, zhaosheng123.

BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5(seed), 1, 8) || '-' ||
        substr(md5(seed), 9, 4) || '-' ||
        '7' || substr(md5(seed), 14, 3) || '-' ||
        'a' || substr(md5(seed), 18, 3) || '-' ||
        substr(md5(seed), 21, 12)
    )::UUID;
$$;

CREATE OR REPLACE FUNCTION pg_temp.seed_course_uuid(course_seq INT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT pg_temp.seed_uuid(
        CASE
            WHEN course_seq > 15 THEN 'zh-course-' || (course_seq - 15)
            ELSE 'course-' || course_seq
        END
    );
$$;

CREATE OR REPLACE FUNCTION pg_temp.seed_course_scoped_uuid(
    old_prefix TEXT,
    zh_prefix TEXT,
    course_seq INT,
    suffix TEXT DEFAULT ''
)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT pg_temp.seed_uuid(
        CASE
            WHEN course_seq > 15 THEN zh_prefix || (course_seq - 15) || suffix
            ELSE old_prefix || course_seq || suffix
        END
    );
$$;

CREATE OR REPLACE FUNCTION pg_temp.seed_rand(seed TEXT)
RETURNS DOUBLE PRECISION
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        get_byte(decode(md5(seed), 'hex'), 0)::BIGINT * 16777216
        + get_byte(decode(md5(seed), 'hex'), 1)::BIGINT * 65536
        + get_byte(decode(md5(seed), 'hex'), 2)::BIGINT * 256
        + get_byte(decode(md5(seed), 'hex'), 3)::BIGINT
    )::DOUBLE PRECISION / 4294967295.0;
$$;

CREATE OR REPLACE FUNCTION pg_temp.seed_ts(
    seed TEXT,
    latest TIMESTAMPTZ DEFAULT TIMESTAMPTZ '2026-06-19 23:59:59+08'
)
RETURNS TIMESTAMPTZ
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT TIMESTAMPTZ '2026-01-01 00:00:00+08'
           + (
               floor(
                   pg_temp.seed_rand(seed)
                   * EXTRACT(EPOCH FROM latest - TIMESTAMPTZ '2026-01-01 00:00:00+08')
               ) * INTERVAL '1 second'
           );
$$;

CREATE TEMP TABLE seed_teacher (
    seq INT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email_prefix TEXT NOT NULL,
    department TEXT NOT NULL,
    title TEXT NOT NULL,
    school TEXT NOT NULL,
    bio TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_teacher (seq, full_name, email_prefix, department, title, school, bio) VALUES
(1, 'Li Wenhao', 'liwenhao', 'Game Technology', 'Professor', 'Sapientia School of Interactive Media', 'Researches gameplay systems, rapid prototyping, and learning-centered simulation design.'),
(2, 'Chen Yuxin', 'chenyuxin', 'Digital Art', 'Associate Professor', 'Sapientia School of Art and Design', 'Focuses on concept art pipelines, visual storytelling, and critique-based studio teaching.'),
(3, 'Zhang Mingrui', 'zhangmingrui', 'Computer Graphics', 'Professor', 'Sapientia School of Computing', 'Works on rendering, procedural tools, terrain systems, and real-time optimization.'),
(4, 'Wang Siyu', 'wangsiyu', 'Music Technology', 'Lecturer', 'Sapientia Conservatory Lab', 'Designs courses around rhythm, synthesis, interactive audio, and music production practice.'),
(5, 'Liu Jiayi', 'liujiayi', 'Web Engineering', 'Associate Professor', 'Sapientia School of Software', 'Teaches Web platform fundamentals, accessible interfaces, and browser graphics.'),
(6, 'Zhao Zihan', 'zhaozihan', 'Artificial Intelligence', 'Professor', 'Sapientia AI Institute', 'Researches ML evaluation, game AI, recommendation systems, and responsible model use.'),
(7, 'Huang Rui', 'huangrui', 'Virtual Production', 'Senior Lecturer', 'Sapientia XR Studio', 'Builds virtual production and XR simulation workflows for interdisciplinary teams.'),
(8, 'Wu Jingyi', 'wujingyi', 'Level Design', 'Lecturer', 'Sapientia Game Lab', 'Specializes in player guidance, encounter pacing, and playtest-driven iteration.'),
(9, 'Xu Haoran', 'xuhaoran', '3D Asset Creation', 'Associate Professor', 'Sapientia School of Digital Media', 'Leads Blender modeling, geometry nodes, rigging, and animation studios.'),
(10, 'Sun Meilin', 'sunmeilin', 'Creative Coding', 'Lecturer', 'Sapientia Creative Computing Lab', 'Combines generative visuals, interactive installations, and front-end engineering.'),
(11, 'Olivia Chen', 'oliviachen', 'Game Technology', 'Assistant Professor', 'Sapientia School of Interactive Media', 'Mentors student teams on gameplay feel, telemetry, and systems debugging.'),
(12, 'Ethan Li', 'ethanli', 'Web Engineering', 'Lecturer', 'Sapientia School of Software', 'Teaches component architecture, state management, testing, and production deployment.'),
(13, 'Sophia Wang', 'sophiawang', 'Digital Art', 'Professor', 'Sapientia School of Art and Design', 'Works on digital painting pedagogy, color scripts, and portfolio critique.'),
(14, 'Noah Zhang', 'noahzhang', 'Computer Graphics', 'Associate Professor', 'Sapientia School of Computing', 'Researches GPU pipelines, WebGPU experiments, and performance diagnostics.'),
(15, 'Emma Liu', 'emmaliu', 'Virtual Production', 'Lecturer', 'Sapientia XR Studio', 'Supports mixed reality rehearsals, capture planning, and scene blocking.'),
(16, 'Mason Zhao', 'masonzhao', 'Artificial Intelligence', 'Assistant Professor', 'Sapientia AI Institute', 'Studies embodied agents, behavior trees, and analytics for learning games.'),
(17, 'Ava Huang', 'avahuang', 'Music Technology', 'Associate Professor', 'Sapientia Conservatory Lab', 'Develops curriculum for arrangement, sound design, and interactive music systems.'),
(18, 'Lucas Wu', 'lucaswu', '3D Asset Creation', 'Lecturer', 'Sapientia School of Digital Media', 'Coaches topology, texturing, stylized materials, and asset review.'),
(19, 'Mia Xu', 'miaxu', 'Level Design', 'Assistant Professor', 'Sapientia Game Lab', 'Studies spatial readability, difficulty curves, and onboarding in virtual worlds.'),
(20, 'James Sun', 'jamessun', 'Creative Coding', 'Professor', 'Sapientia Creative Computing Lab', 'Builds generative tools for education, web graphics, and interactive exhibits.');

CREATE TEMP TABLE seed_student (
    seq INT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email_prefix TEXT NOT NULL,
    major TEXT NOT NULL,
    grade TEXT NOT NULL,
    school TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_student (seq, full_name, email_prefix, major, grade, school) VALUES
(1, 'Zhang Yichen', 'zhangyichen', 'Game Design', '2023', 'Sapientia School of Interactive Media'),
(2, 'Li Muyang', 'limuyang', 'Digital Media Art', '2024', 'Sapientia School of Art and Design'),
(3, 'Wang Ruoxi', 'wangruoxi', 'Software Engineering', '2023', 'Sapientia School of Software'),
(4, 'Chen Haoyu', 'chenhaoyu', 'Computer Graphics', '2024', 'Sapientia School of Computing'),
(5, 'Liu Xinyi', 'liuxinyi', 'Music Technology', '2025', 'Sapientia Conservatory Lab'),
(6, 'Zhao Yuhan', 'zhaoyuhan', 'Artificial Intelligence', '2023', 'Sapientia AI Institute'),
(7, 'Huang Zimo', 'huangzimo', 'Virtual Production', '2024', 'Sapientia XR Studio'),
(8, 'Wu Siyuan', 'wusiyuan', 'Game Design', '2025', 'Sapientia School of Interactive Media'),
(9, 'Xu Nian', 'xunian', '3D Animation', '2023', 'Sapientia School of Digital Media'),
(10, 'Sun Qiming', 'sunqiming', 'Creative Computing', '2024', 'Sapientia Creative Computing Lab'),
(11, 'Zhou Keyi', 'zhoukeyi', 'Web Engineering', '2025', 'Sapientia School of Software'),
(12, 'Yang Jiarui', 'yangjiarui', 'Game Design', '2023', 'Sapientia School of Interactive Media'),
(13, 'He Zixuan', 'hezixuan', 'Digital Media Art', '2024', 'Sapientia School of Art and Design'),
(14, 'Guo Tianyi', 'guotianyi', 'Computer Graphics', '2025', 'Sapientia School of Computing'),
(15, 'Lin Yinuo', 'linyinuo', 'Artificial Intelligence', '2023', 'Sapientia AI Institute'),
(16, 'Ma Cheng', 'macheng', 'Music Technology', '2024', 'Sapientia Conservatory Lab'),
(17, 'Luo Xiaohan', 'luoxiaohan', 'Virtual Production', '2025', 'Sapientia XR Studio'),
(18, 'Deng Yuxuan', 'dengyuxuan', '3D Animation', '2023', 'Sapientia School of Digital Media'),
(19, 'Gao Ruilin', 'gaoruilin', 'Creative Computing', '2024', 'Sapientia Creative Computing Lab'),
(20, 'Cao Shuyue', 'caoshuyue', 'Web Engineering', '2025', 'Sapientia School of Software'),
(21, 'Tang Zeyu', 'tangzeyu', 'Game Design', '2023', 'Sapientia School of Interactive Media'),
(22, 'Feng Yiran', 'fengyiran', 'Digital Media Art', '2024', 'Sapientia School of Art and Design'),
(23, 'Yu Chenxi', 'yuchenxi', 'Software Engineering', '2025', 'Sapientia School of Software'),
(24, 'Jiang Yihan', 'jiangyihan', 'Computer Graphics', '2023', 'Sapientia School of Computing'),
(25, 'Xie Mingze', 'xiemingze', 'Music Technology', '2024', 'Sapientia Conservatory Lab'),
(26, 'Han Ruining', 'hanruining', 'Artificial Intelligence', '2025', 'Sapientia AI Institute'),
(27, 'Ren Yuxi', 'renyuxi', 'Virtual Production', '2023', 'Sapientia XR Studio'),
(28, 'Pan Boyuan', 'panboyuan', 'Game Design', '2024', 'Sapientia School of Interactive Media'),
(29, 'Wei Qing', 'weiqing', '3D Animation', '2025', 'Sapientia School of Digital Media'),
(30, 'Ding Xiaoyu', 'dingxiaoyu', 'Creative Computing', '2023', 'Sapientia Creative Computing Lab'),
(31, 'Song Jiayi', 'songjiayi', 'Web Engineering', '2024', 'Sapientia School of Software'),
(32, 'Shen Yu', 'shenyu', 'Game Design', '2025', 'Sapientia School of Interactive Media'),
(33, 'Du Zihan', 'duzihan', 'Digital Media Art', '2023', 'Sapientia School of Art and Design'),
(34, 'Jin Haoran', 'jinhaoran', 'Software Engineering', '2024', 'Sapientia School of Software'),
(35, 'Qian Muxi', 'qianmuxi', 'Computer Graphics', '2025', 'Sapientia School of Computing'),
(36, 'Bai Yiming', 'baiyiming', 'Music Technology', '2023', 'Sapientia Conservatory Lab'),
(37, 'Tian Yue', 'tianyue', 'Artificial Intelligence', '2024', 'Sapientia AI Institute'),
(38, 'Kong Lingxi', 'konglingxi', 'Virtual Production', '2025', 'Sapientia XR Studio'),
(39, 'Yao Zhen', 'yaozhen', '3D Animation', '2023', 'Sapientia School of Digital Media'),
(40, 'Lu Sichen', 'lusichen', 'Creative Computing', '2024', 'Sapientia Creative Computing Lab'),
(41, 'Alice Lin', 'alicelin', 'Web Engineering', '2025', 'Sapientia School of Software'),
(42, 'Benjamin Wu', 'benjaminwu', 'Game Design', '2023', 'Sapientia School of Interactive Media'),
(43, 'Charlotte Xu', 'charlottexu', 'Digital Media Art', '2024', 'Sapientia School of Art and Design'),
(44, 'Daniel Huang', 'danielhuang', 'Software Engineering', '2025', 'Sapientia School of Software'),
(45, 'Emily Zhao', 'emilyzhao', 'Computer Graphics', '2023', 'Sapientia School of Computing'),
(46, 'Felix Chen', 'felixchen', 'Music Technology', '2024', 'Sapientia Conservatory Lab'),
(47, 'Grace Liu', 'graceliu', 'Artificial Intelligence', '2025', 'Sapientia AI Institute'),
(48, 'Henry Zhang', 'henryzhang', 'Virtual Production', '2023', 'Sapientia XR Studio'),
(49, 'Isabella Wang', 'isabellawang', 'Game Design', '2024', 'Sapientia School of Interactive Media'),
(50, 'Jack Li', 'jackli', '3D Animation', '2025', 'Sapientia School of Digital Media'),
(51, 'Katherine Sun', 'katherinesun', 'Creative Computing', '2023', 'Sapientia Creative Computing Lab'),
(52, 'Leo Yang', 'leoyang', 'Web Engineering', '2024', 'Sapientia School of Software'),
(53, 'Nora Zhou', 'norazhou', 'Game Design', '2025', 'Sapientia School of Interactive Media'),
(54, 'Owen He', 'owenhe', 'Digital Media Art', '2023', 'Sapientia School of Art and Design'),
(55, 'Phoebe Guo', 'phoebeguo', 'Software Engineering', '2024', 'Sapientia School of Software'),
(56, 'Ryan Lin', 'ryanlin', 'Computer Graphics', '2025', 'Sapientia School of Computing'),
(57, 'Stella Ma', 'stellama', 'Music Technology', '2023', 'Sapientia Conservatory Lab'),
(58, 'Thomas Luo', 'thomasluo', 'Artificial Intelligence', '2024', 'Sapientia AI Institute'),
(59, 'Uma Deng', 'umadeng', 'Virtual Production', '2025', 'Sapientia XR Studio'),
(60, 'Victor Gao', 'victorgao', '3D Animation', '2023', 'Sapientia School of Digital Media'),
(61, 'Wendy Cao', 'wendycao', 'Creative Computing', '2024', 'Sapientia Creative Computing Lab'),
(62, 'Xavier Tang', 'xaviertang', 'Web Engineering', '2025', 'Sapientia School of Software'),
(63, 'Yvonne Feng', 'yvonnefeng', 'Game Design', '2023', 'Sapientia School of Interactive Media'),
(64, 'Aaron Yu', 'aaronyu', 'Digital Media Art', '2024', 'Sapientia School of Art and Design'),
(65, 'Bella Jiang', 'bellajiang', 'Software Engineering', '2025', 'Sapientia School of Software'),
(66, 'Caleb Xie', 'calebxie', 'Computer Graphics', '2023', 'Sapientia School of Computing'),
(67, 'Daisy Han', 'daisyhan', 'Music Technology', '2024', 'Sapientia Conservatory Lab'),
(68, 'Eric Ren', 'ericren', 'Artificial Intelligence', '2025', 'Sapientia AI Institute'),
(69, 'Fiona Pan', 'fionapan', 'Virtual Production', '2023', 'Sapientia XR Studio'),
(70, 'Gavin Wei', 'gavinwei', 'Game Design', '2024', 'Sapientia School of Interactive Media'),
(71, 'Helen Ding', 'helending', '3D Animation', '2025', 'Sapientia School of Digital Media'),
(72, 'Ian Song', 'iansong', 'Creative Computing', '2023', 'Sapientia Creative Computing Lab'),
(73, 'Julia Shen', 'juliashen', 'Web Engineering', '2024', 'Sapientia School of Software'),
(74, 'Kevin Du', 'kevindu', 'Game Design', '2025', 'Sapientia School of Interactive Media'),
(75, 'Lily Jin', 'lilyjin', 'Digital Media Art', '2023', 'Sapientia School of Art and Design'),
(76, 'Martin Qian', 'martinqian', 'Software Engineering', '2024', 'Sapientia School of Software'),
(77, 'Nina Bai', 'ninabai', 'Computer Graphics', '2025', 'Sapientia School of Computing'),
(78, 'Oscar Tian', 'oscartian', 'Music Technology', '2023', 'Sapientia Conservatory Lab'),
(79, 'Paula Kong', 'paulakong', 'Artificial Intelligence', '2024', 'Sapientia AI Institute'),
(80, 'Quentin Yao', 'quentinyao', 'Virtual Production', '2025', 'Sapientia XR Studio');

CREATE TEMP TABLE seed_course (
    seq INT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    primary_teacher_seq INT NOT NULL,
    is_public INT NOT NULL,
    level INT NOT NULL,
    course_type INT NOT NULL,
    max_students INT NOT NULL,
    total_class_hours INT NOT NULL,
    location TEXT NOT NULL,
    toolchain TEXT NOT NULL,
    topic_a TEXT NOT NULL,
    topic_b TEXT NOT NULL,
    topic_c TEXT NOT NULL,
    capstone TEXT NOT NULL,
    source_refs TEXT[] NOT NULL,
    cover_url TEXT NOT NULL,
    semester TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_course (
    seq, title, description, primary_teacher_seq, is_public, level, course_type,
    max_students, total_class_hours, location, toolchain, topic_a, topic_b,
    topic_c, capstone, source_refs, cover_url, semester
) VALUES
(1, 'Unity Real-Time Game Development Studio', 'A production-style Unity course covering playable loops, scene composition, physics interactions, UI feedback, and iterative playtesting for small teams.', 1, 1, 2, 1, 160, 72, 'Cloud Studio A / Unity Lab', 'Unity', 'playable prototype loops', 'physics-driven interaction', 'UI feedback and build pipeline', 'vertical slice arcade exploration game', ARRAY['Unity Learn'], 'https://loremflickr.com/1200/675/game,development?lock=101', '2026-01-14 - 2026-06-19'),
(2, 'Unreal Engine 5 Virtual Worlds and Level Design', 'A UE5 studio focused on landscapes, lighting, Blueprints, world partition thinking, and readable encounter spaces for virtual simulation.', 3, 1, 3, 1, 140, 80, 'XR Lab 2 / UE Stage', 'Unreal Engine 5', 'Blueprint gameplay scripting', 'landscape material layering', 'lighting and player guidance', 'walkable virtual campus level', ARRAY['Unreal Engine Documentation'], 'https://loremflickr.com/1200/675/virtual,reality?lock=102', '2026-05-29 - 2026-06-19'),
(3, 'Blender Procedural Modeling and Animation', 'A Blender course for mesh modeling, geometry nodes, sculpting review, materials, animation blocking, and asset delivery to real-time engines.', 9, 1, 2, 1, 120, 72, 'Digital Media Studio B', 'Blender', 'geometry nodes systems', 'clean topology and UV planning', 'animation blocking and export', 'procedural modular environment kit', ARRAY['Blender Manual'], 'https://loremflickr.com/1200/675/3d,modeling?lock=103', '2026-03-23 - 2026-06-19'),
(4, 'Terrain Editing for Games and Simulations', 'A terrain-focused course spanning heightfields, erosion language, vegetation placement, nav readability, and playable open-area iteration.', 8, 1, 3, 1, 120, 72, 'Spatial Computing Lab', 'Unity Terrain and Unreal Landscape', 'heightfield sculpting', 'biome and material rules', 'navigation readability', 'explorable mountain training area', ARRAY['Unity Learn', 'Unreal Engine Documentation'], 'https://loremflickr.com/1200/675/mountain,terrain?lock=104', '2026-01-09 - 2026-06-19'),
(5, 'Modern Web Development and WebGPU Interfaces', 'A web engineering course that moves from semantic HTML and CSS to interactive TypeScript interfaces, API integration, and WebGPU visual experiments.', 5, 1, 2, 1, 180, 72, 'Web Engineering Lab', 'Web Platform and WebGPU', 'semantic accessible UI', 'client state and API flows', 'browser graphics experiments', 'interactive portfolio dashboard', ARRAY['MDN Learn Web Development'], 'https://loremflickr.com/1200/675/web,design?lock=105', '2026-03-09 - 2026-06-19'),
(6, 'Game AI: Agents, Search, and Learning Loops', 'A practical AI course for behavior trees, utility scoring, pathfinding, reward thinking, and evaluation in playable game scenarios.', 6, 1, 3, 1, 100, 80, 'AI Simulation Lab', 'Game AI Toolkit', 'behavior tree decisions', 'pathfinding and steering', 'evaluation metrics', 'cooperative NPC mission prototype', ARRAY['Google Machine Learning Crash Course'], 'https://loremflickr.com/1200/675/artificial,intelligence?lock=106', '2026-03-23 - 2026-06-19'),
(7, 'Music Production for Interactive Media', 'A music technology course that treats rhythm, melody, harmony, arrangement, synthesis, and adaptive stems as design materials for games.', 4, 1, 1, 1, 120, 64, 'Music Technology Room', 'Ableton Live and Web Audio', 'rhythm and groove design', 'melodic motif development', 'adaptive audio stems', 'interactive soundtrack for a short game', ARRAY['Ableton Learning Music'], 'https://loremflickr.com/1200/675/music,studio?lock=107', '2026-06-09 - 2026-06-19'),
(8, 'Digital Painting and Concept Art Pipeline', 'A visual development course on thumbnails, value grouping, color keys, character sheets, prop callouts, and critique-ready portfolio pages.', 2, 1, 2, 1, 120, 72, 'Concept Art Studio', 'Krita and Blender Reference', 'thumbnail ideation', 'color script decisions', 'portfolio critique workflow', 'concept package for a stylized world', ARRAY['Krita Manual', 'Blender Manual'], 'https://loremflickr.com/1200/675/digital,art?lock=108', '2026-02-03 - 2026-06-19'),
(9, 'XR Virtual Classroom and Simulation Design', 'A course for designing presence, classroom affordances, interaction safety, avatar behavior, and assessment loops in virtual learning spaces.', 7, 1, 3, 1, 90, 80, 'XR Classroom Stage', 'WebXR and Unity XR', 'presence and comfort', 'multi-user interaction rules', 'assessment telemetry', 'virtual lab lesson simulation', ARRAY['Unity Learn', 'MDN Learn Web Development'], 'https://loremflickr.com/1200/675/virtual,classroom?lock=109', '2026-05-12 - 2026-06-19'),
(10, 'Creative Coding: Generative Visual Systems', 'A studio course for algorithmic visuals, interaction sketches, data-driven composition, shader thinking, and public-facing web installations.', 10, 1, 2, 1, 150, 72, 'Creative Computing Gallery', 'TypeScript Canvas and WebGL', 'generative composition', 'input-driven animation', 'deployment polish', 'interactive generative exhibition piece', ARRAY['MDN Learn Web Development'], 'https://loremflickr.com/1200/675/generative,art?lock=110', '2026-04-17 - 2026-06-19'),
(11, 'Private Studio: Advanced Unity Multiplayer Lab', 'Invitation-only Unity lab for networked interactions, state reconciliation, session UX, and team playtest instrumentation.', 11, 0, 3, 1, 45, 80, 'Private Game Lab 1', 'Unity Multiplayer', 'networked state models', 'latency-aware interaction', 'telemetry review', 'small cooperative multiplayer prototype', ARRAY['Unity Learn'], 'https://loremflickr.com/1200/675/multiplayer,game?lock=111', '2026-04-25 - 2026-06-19'),
(12, 'Private Studio: Unreal Cinematic Production', 'Invitation-only UE course for sequencer planning, shot blocking, lighting passes, camera language, and real-time cinematic review.', 12, 0, 3, 1, 40, 80, 'Virtual Production Stage', 'Unreal Engine 5 Sequencer', 'shot blocking', 'real-time lighting passes', 'camera and edit rhythm', 'one-minute real-time cinematic', ARRAY['Unreal Engine Documentation'], 'https://loremflickr.com/1200/675/cinema,production?lock=112', '2026-02-20 - 2026-06-19'),
(13, 'Private Studio: Blender Technical Art Tools', 'Invitation-only technical art lab for node tools, procedural constraints, asset validation, naming conventions, and export automation.', 13, 0, 3, 1, 36, 80, 'Technical Art Lab', 'Blender Python and Geometry Nodes', 'procedural node tools', 'asset validation', 'export automation', 'technical art toolkit for modular assets', ARRAY['Blender Manual'], 'https://loremflickr.com/1200/675/3d,design?lock=113', '2026-02-23 - 2026-06-19'),
(14, 'Private Studio: AI-Assisted Learning Game Design', 'Invitation-only course for designing learning games with transparent AI behaviors, evaluation rubrics, and human review loops.', 14, 0, 3, 1, 36, 80, 'AI Learning Game Studio', 'Game AI and Analytics', 'transparent AI behavior', 'learning objective alignment', 'rubric-based evaluation', 'adaptive learning game prototype', ARRAY['Google Machine Learning Crash Course'], 'https://loremflickr.com/1200/675/learning,game?lock=114', '2026-03-27 - 2026-06-19'),
(15, 'Private Studio: Procedural Terrain and Worldbuilding', 'Invitation-only worldbuilding lab for procedural terrain, biome grammars, landmark placement, and player orientation.', 15, 0, 3, 1, 36, 80, 'Worldbuilding Lab', 'Unity Terrain, UE Landscape, and Blender', 'procedural terrain grammar', 'landmark composition', 'streaming-friendly zones', 'procedural island exploration level', ARRAY['Unity Learn', 'Unreal Engine Documentation', 'Blender Manual'], 'https://loremflickr.com/1200/675/island,landscape?lock=115', '2026-04-14 - 2026-06-19'),
(16, '中文游戏原型与关卡叙事实训', '面向中文语境的游戏原型课程，围绕核心循环、关卡节奏、任务提示和测试证据完成一段可试玩关卡。', 1, 1, 2, 1, 120, 72, '云端游戏实验室 C / Unity 与 UE 联合机房', 'Unity 与 Unreal Engine', '核心循环原型', '关卡节奏与任务引导', '中文叙事反馈', '可试玩的国风解谜关卡', ARRAY['Unity Learn', 'Unreal Engine Documentation'], 'https://loremflickr.com/1200/675/chinese,game?lock=201', '2026-06-11 - 2026-06-19'),
(17, 'UE5国风虚拟制片与镜头调度', '以 UE5 实时渲染、Sequencer、灯光和镜头语言为核心，制作一段具有国风视觉线索的实时短片。', 12, 0, 3, 1, 42, 80, '虚拟制片棚 / UE Stage', 'Unreal Engine 5 Sequencer', '镜头阻塞与场面调度', '实时灯光与材质气氛', '国风视觉叙事', '一分钟实时国风虚拟短片', ARRAY['Unreal Engine Documentation', 'Krita Manual'], 'https://loremflickr.com/1200/675/chinese,architecture?lock=203', '2026-04-24 - 2026-06-19'),
(18, 'Blender数字文物资产与程序化建模', '使用 Blender 建模、Geometry Nodes、材质和资产校验流程，构建可进入实时引擎的数字文物资产包。', 9, 1, 2, 1, 110, 72, '数字媒体工作室 D / Blender Lab', 'Blender 与 Geometry Nodes', '程序化纹样与结构生成', '拓扑、UV 与材质整理', '资产校验与引擎导出', '一组可复用的数字文物展示资产', ARRAY['Blender Manual', 'Krita Manual'], 'https://loremflickr.com/1200/675/3d,design?lock=113', '2026-05-17 - 2026-06-19'),
(19, 'WebGPU智慧城市可视化工程', '从语义化前端、TypeScript 状态管理到 WebGPU 可视化，完成一个城市运行数据的交互式看板原型。', 5, 1, 3, 1, 150, 80, 'Web 工程实验室 / 数据可视化教室', 'TypeScript、WebGPU 与 Web 平台', '可访问信息架构', '城市数据映射', '浏览器图形性能', '智慧城市三维数据驾驶舱', ARRAY['MDN Learn Web Development', 'Google Machine Learning Crash Course'], 'https://loremflickr.com/1200/675/city,digital?lock=202', '2026-01-04 - 2026-06-19'),
(20, '互动音乐舞台与声音设计', '结合 Ableton、Web Audio 与互动媒体设计，训练节奏、动机、声音反馈和舞台触发系统的制作能力。', 4, 0, 2, 1, 40, 72, '音乐科技空间 / Interactive Audio Lab', 'Ableton Live 与 Web Audio', '节奏动机与音色设计', '互动触发与状态反馈', '舞台演示与混音控制', '可交互的虚拟音乐舞台片段', ARRAY['Ableton Learning Music', 'MDN Learn Web Development'], 'https://loremflickr.com/1200/675/music,studio?lock=107', '2026-04-02 - 2026-06-19');

CREATE TEMP TABLE seed_module_template (
    module_seq INT PRIMARY KEY,
    main_title TEXT NOT NULL,
    sub_title_a TEXT NOT NULL,
    sub_title_b TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_module_template (module_seq, main_title, sub_title_a, sub_title_b) VALUES
(1, 'Orientation, references, and production brief', 'Toolchain setup and source review', 'Measurable learning goals and constraints'),
(2, 'Core systems and asset pipeline', 'Building the first reusable system', 'Asset naming, import, and validation'),
(3, 'Interaction patterns and composition', 'Player or learner feedback loops', 'Spatial, visual, or sonic hierarchy'),
(4, 'Optimization, accessibility, and iteration', 'Performance budgets and diagnostics', 'Accessibility review and inclusive testing'),
(5, 'Studio sprint and playtest evidence', 'Milestone planning and task slicing', 'Playtest notes, telemetry, and critique'),
(6, 'Portfolio release and reflective review', 'Release checklist and documentation', 'Demo narrative and postmortem writing');

CREATE TEMP TABLE seed_bank_template (
    bank_idx INT PRIMARY KEY,
    bank_type INT NOT NULL,
    bank_label TEXT NOT NULL,
    description TEXT NOT NULL,
    difficulty INT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_bank_template (bank_idx, bank_type, bank_label, description, difficulty) VALUES
(1, 0, 'Concept Check', 'Checks vocabulary, decision quality, and basic transfer from references into studio work.', 1),
(2, 2, 'Studio Lab', 'Evaluates applied production choices, debugging habits, and evidence from iteration.', 2),
(3, 1, 'Capstone Review', 'Reviews synthesis, critique readiness, documentation, and release-level tradeoffs.', 3);

CREATE TEMP TABLE seed_question_template (
    q_no INT PRIMARY KEY,
    question_type INT NOT NULL,
    difficulty INT NOT NULL,
    score NUMERIC(10, 2) NOT NULL,
    estimated_time INT NOT NULL,
    title_t TEXT NOT NULL,
    content_t TEXT NOT NULL,
    option_a_t TEXT,
    option_b_t TEXT,
    option_c_t TEXT,
    option_d_t TEXT,
    option_e_t TEXT,
    correct_labels TEXT[] NOT NULL,
    answer_t TEXT NOT NULL,
    explanation_t TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_question_template (
    q_no, question_type, difficulty, score, estimated_time, title_t, content_t,
    option_a_t, option_b_t, option_c_t, option_d_t, option_e_t,
    correct_labels, answer_t, explanation_t
) VALUES
(1, 0, 1, 5, 4, 'Prototype order for %s', 'A team is starting %s work for %s. Which workflow reduces rework before production polish?',
 'Build a tiny playable loop, test the core feedback, then add art and polish.',
 'Create final textures and cinematic lighting before any interaction works.',
 'Delay testing until the whole capstone is assembled.',
 'Ask every teammate to work on separate final assets without a shared scene.',
 NULL, ARRAY['A'], 'Start with a small playable loop and test the core feedback.', 'A small playable loop exposes control, feedback, and scope issues before expensive polish.'),
(2, 1, 2, 8, 6, 'Evidence for %s iteration', 'Select the evidence that should guide iteration for %s in a %s project.',
 'Observed user behavior during a timed playtest.',
 'A clear bug list with reproduction steps.',
 'Personal preference from one teammate without test evidence.',
 'Performance metrics captured on the target device.',
 'A claim that the first idea must be kept because it was first.', ARRAY['A','B','D'], 'Use playtest observation, reproducible bugs, and target-device metrics.', 'Reliable iteration combines qualitative observation with reproducible technical evidence.'),
(3, 2, 1, 4, 3, 'Readable hierarchy in %s', 'True or false: %s should make important actions, landmarks, or sounds more legible than decorative details.',
 'True',
 'False',
 NULL,
 NULL,
 NULL, ARRAY['A'], 'True', 'Readable hierarchy helps learners and players understand what matters first.'),
(4, 3, 1, 5, 4, 'Name one diagnostic habit', 'Fill in one diagnostic habit that helps improve %s when working with %s.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'Record the test condition, expected result, actual result, and next change.', 'A diagnostic habit makes each iteration traceable instead of relying on memory.'),
(5, 4, 2, 10, 8, 'Explain a tradeoff in %s', 'In 4 to 6 sentences, explain one tradeoff between %s and %s for the capstone %s.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'A strong answer names the tradeoff, explains the affected user experience, gives evidence, and proposes a next test.', 'Short answers should connect design intent, technical constraint, evidence, and a concrete next step.'),
(6, 0, 2, 6, 5, 'Scope control for %s', 'Which scope decision best protects the schedule of %s?',
 'Keep one complete vertical slice and defer optional features to a backlog.',
 'Add every requested feature before checking whether the core loop works.',
 'Replace the brief each week to keep the team energized.',
 'Avoid milestones so the team can stay flexible.',
 NULL, ARRAY['A'], 'Protect one complete vertical slice and move optional work to a backlog.', 'A vertical slice gives a complete testable experience while preserving scope control.'),
(7, 1, 2, 8, 6, 'Quality gates for %s assets', 'Which checks belong in an asset or content quality gate for %s?',
 'File names and folder placement match the team convention.',
 'The asset loads in the target scene without missing references.',
 'The asset is approved only because it looks complex.',
 'The asset meets the agreed performance or size budget.',
 'The asset bypasses review if it was made by a senior teammate.', ARRAY['A','B','D'], 'Check naming, loadability, and budget compliance.', 'Quality gates prevent avoidable integration failures.'),
(8, 2, 1, 4, 3, 'Accessibility review for %s', 'True or false: accessibility review should happen only after the final build is complete.',
 'True',
 'False',
 NULL,
 NULL,
 NULL, ARRAY['B'], 'False', 'Early accessibility checks are cheaper and reveal design assumptions before they harden.'),
(9, 3, 2, 6, 5, 'Define a performance budget', 'Fill in a concise performance budget for %s on the target device.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'Example: maintain stable frame pacing, limit heavy effects in the main view, and document the target hardware.', 'A performance budget must name the target condition and the constraint being measured.'),
(10, 4, 3, 12, 10, 'Critique plan for %s', 'Write a critique plan for %s that helps teammates improve %s without vague feedback.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'A strong critique plan identifies criteria, evidence, risk, and a specific revision task.', 'Actionable critique separates taste from evidence and converts feedback into a next edit.'),
(11, 0, 1, 5, 4, 'Source-grounded learning for %s', 'How should a team use official documentation or lessons while building %s?',
 'Translate the reference into a small local test and record what changed.',
 'Copy large sections into the project without checking the context.',
 'Ignore documentation once a tutorial has been watched.',
 'Use references only to decorate the final report.',
 NULL, ARRAY['A'], 'Translate references into a local test and record the result.', 'Grounding content in a test makes the reference useful for the team project.'),
(12, 1, 2, 8, 6, 'Team handoff for %s', 'Which handoff artifacts help another teammate continue %s work?',
 'A short README with setup steps and known limitations.',
 'A scene or demo file that opens to the tested state.',
 'A private chat message with no task context.',
 'A list of open risks with owners.',
 'A folder of unnamed experimental files.', ARRAY['A','B','D'], 'Use a README, tested demo state, and owned risk list.', 'Good handoff artifacts reduce ambiguity and preserve context.'),
(13, 2, 1, 4, 3, 'Testing %s assumptions', 'True or false: if a prototype looks impressive, it no longer needs user testing.',
 'True',
 'False',
 NULL,
 NULL,
 NULL, ARRAY['B'], 'False', 'Visual polish does not prove usability, learning value, or technical stability.'),
(14, 3, 2, 6, 5, 'Milestone language', 'Fill in a milestone statement for %s that is testable by the end of one week.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'By Friday, the prototype lets a tester complete one guided task and records the main failure point.', 'A testable milestone names a user action, a date, and observable evidence.'),
(15, 4, 3, 12, 10, 'Postmortem for %s', 'Write a concise postmortem paragraph explaining what changed in %s after testing %s.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'A strong postmortem names the initial hypothesis, evidence, change made, and remaining risk.', 'Postmortems should make learning visible rather than simply narrating effort.'),
(16, 0, 2, 6, 5, 'Risk response for %s', 'A team discovers that %s is unstable two days before review. What response is most responsible?',
 'Reduce scope, keep the tested path, and document the risk honestly.',
 'Hide the issue and hope it does not appear during review.',
 'Rewrite the entire project without a test plan.',
 'Delete the feature without telling stakeholders.',
 NULL, ARRAY['A'], 'Reduce scope, preserve the tested path, and document the risk.', 'Responsible delivery makes risk visible and protects a coherent demo.'),
(17, 1, 2, 8, 6, 'Release checklist for %s', 'Which items belong on the release checklist for %s?',
 'A clean start state for the demo.',
 'Known issues and recovery steps.',
 'Only the names of people who worked the latest night.',
 'Credits and source acknowledgements.',
 'Unreviewed experimental files in the main build.', ARRAY['A','B','D'], 'Prepare demo state, known issues, recovery steps, credits, and acknowledgements.', 'A release checklist supports repeatable review and transparent attribution.'),
(18, 2, 1, 4, 3, 'Private-course workflow for %s', 'True or false: an invitation-only studio should still keep review evidence and clear assessment criteria.',
 'True',
 'False',
 NULL,
 NULL,
 NULL, ARRAY['A'], 'True', 'Restricted access changes enrollment, not the need for transparent learning evidence.'),
(19, 3, 2, 6, 5, 'Rubric criterion for %s', 'Fill in one rubric criterion for evaluating %s in the capstone %s.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'The demo communicates intent, runs reliably on the target device, and includes evidence from at least two iterations.', 'Rubrics should combine intent, reliability, and evidence.'),
(20, 4, 3, 12, 10, 'Transfer reflection for %s', 'Explain how a method from %s could transfer to another course or project area.',
 NULL, NULL, NULL, NULL, NULL, ARRAY[]::TEXT[], 'A strong reflection names the method, the new context, the adaptation needed, and a risk to retest.', 'Transfer requires adapting a method, not merely repeating it unchanged.');

WITH teacher_users AS (
    SELECT
        t.*,
        pg_temp.seed_uuid('teacher-user-' || t.seq) AS user_id,
        pg_temp.seed_ts('teacher-created-' || t.seq) AS created_ts
    FROM seed_teacher t
)
INSERT INTO auth_users (
    id, email, email_verified, display_name, avatar_url, locale, status,
    created_at, updated_at, last_login_at, created_provider, created_ip,
    last_login_provider, last_login_ip, login_count, password_hash, role,
    phone, bio, gender, birthday, theme, notification_enabled, avatar_file_id
)
SELECT
    user_id,
    email_prefix || '@edupivot.xyz',
    TRUE,
    full_name,
    NULL,
    'zh-CN',
    'ACTIVE',
    created_ts,
    GREATEST(created_ts, pg_temp.seed_ts('teacher-updated-' || seq)),
    NULL,
    'LOCAL',
    NULL,
    NULL,
    NULL,
    0,
    '$2a$10$OD.UnXvZxFVabRAWFB15le6ZCSzAPGe3.DNytrEkkIv0eFGpGmJx2',
    2,
    '138' || lpad(seq::TEXT, 8, '0'),
    bio,
    CASE WHEN seq % 3 = 0 THEN 2 WHEN seq % 3 = 1 THEN 1 ELSE 0 END,
    DATE '1980-01-01' + ((seq * 241) % 6200),
    CASE WHEN seq % 3 = 0 THEN 'dark' WHEN seq % 3 = 1 THEN 'light' ELSE 'system' END,
    TRUE,
    NULL
FROM teacher_users
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    email_verified = EXCLUDED.email_verified,
    display_name = EXCLUDED.display_name,
    locale = EXCLUDED.locale,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    phone = EXCLUDED.phone,
    bio = EXCLUDED.bio,
    gender = EXCLUDED.gender,
    birthday = EXCLUDED.birthday,
    theme = EXCLUDED.theme,
    notification_enabled = EXCLUDED.notification_enabled;

WITH student_users AS (
    SELECT
        s.*,
        pg_temp.seed_uuid('student-user-' || s.seq) AS user_id,
        pg_temp.seed_ts('student-created-' || s.seq) AS created_ts
    FROM seed_student s
)
INSERT INTO auth_users (
    id, email, email_verified, display_name, avatar_url, locale, status,
    created_at, updated_at, last_login_at, created_provider, created_ip,
    last_login_provider, last_login_ip, login_count, password_hash, role,
    phone, bio, gender, birthday, theme, notification_enabled, avatar_file_id
)
SELECT
    user_id,
    email_prefix || '@edupivot.xyz',
    TRUE,
    full_name,
    NULL,
    CASE WHEN seq > 40 THEN 'en-US' ELSE 'zh-CN' END,
    'ACTIVE',
    created_ts,
    GREATEST(created_ts, pg_temp.seed_ts('student-updated-' || seq)),
    NULL,
    'LOCAL',
    NULL,
    NULL,
    NULL,
    0,
    '$2a$10$OD.UnXvZxFVabRAWFB15le6ZCSzAPGe3.DNytrEkkIv0eFGpGmJx2',
    1,
    '139' || lpad(seq::TEXT, 8, '0'),
    'Student in ' || major || ', interested in project-based learning, critique, and virtual simulation practice.',
    CASE WHEN seq % 3 = 0 THEN 2 WHEN seq % 3 = 1 THEN 1 ELSE 0 END,
    DATE '2001-01-01' + ((seq * 73) % 2100),
    CASE WHEN seq % 3 = 0 THEN 'dark' WHEN seq % 3 = 1 THEN 'light' ELSE 'system' END,
    TRUE,
    NULL
FROM student_users
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    email_verified = EXCLUDED.email_verified,
    display_name = EXCLUDED.display_name,
    locale = EXCLUDED.locale,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    phone = EXCLUDED.phone,
    bio = EXCLUDED.bio,
    gender = EXCLUDED.gender,
    birthday = EXCLUDED.birthday,
    theme = EXCLUDED.theme,
    notification_enabled = EXCLUDED.notification_enabled;

INSERT INTO edu_teacher (
    id, user_id, employee_no, department, title, school, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_uuid('teacher-profile-' || seq),
    pg_temp.seed_uuid('teacher-user-' || seq),
    'T2026' || lpad(seq::TEXT, 3, '0'),
    department,
    title,
    school,
    pg_temp.seed_ts('teacher-profile-created-' || seq),
    pg_temp.seed_ts('teacher-profile-updated-' || seq),
    0
FROM seed_teacher
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    employee_no = EXCLUDED.employee_no,
    department = EXCLUDED.department,
    title = EXCLUDED.title,
    school = EXCLUDED.school,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_student (
    id, user_id, student_no, grade, major, school, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_uuid('student-profile-' || seq),
    pg_temp.seed_uuid('student-user-' || seq),
    'S2026' || lpad(seq::TEXT, 3, '0'),
    grade,
    major,
    school,
    pg_temp.seed_ts('student-profile-created-' || seq),
    pg_temp.seed_ts('student-profile-updated-' || seq),
    0
FROM seed_student
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    student_no = EXCLUDED.student_no,
    grade = EXCLUDED.grade,
    major = EXCLUDED.major,
    school = EXCLUDED.school,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_course (
    id, title, description, teacher_id, level, cover_url, cover_file_id, semester,
    location, course_type, max_students, total_class_hours, is_public, status,
    created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_uuid(seq),
    title,
    description || E'\n\nSource grounding: ' || array_to_string(source_refs, ', ')
        || E'.\nCapstone: ' || capstone || '.',
    pg_temp.seed_uuid('teacher-user-' || primary_teacher_seq),
    level,
    cover_url,
    NULL,
    semester,
    location,
    course_type,
    max_students,
    total_class_hours,
    is_public,
    1,
    pg_temp.seed_ts('course-created-' || seq),
    pg_temp.seed_ts('course-updated-' || seq),
    0
FROM seed_course
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    teacher_id = EXCLUDED.teacher_id,
    level = EXCLUDED.level,
    cover_url = EXCLUDED.cover_url,
    semester = EXCLUDED.semester,
    location = EXCLUDED.location,
    course_type = EXCLUDED.course_type,
    max_students = EXCLUDED.max_students,
    total_class_hours = EXCLUDED.total_class_hours,
    is_public = EXCLUDED.is_public,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_course_teacher ON COMMIT DROP AS
SELECT seq AS course_seq, primary_teacher_seq AS teacher_seq, 0 AS assistant_order, TRUE AS primary_role
FROM seed_course
UNION ALL
SELECT seq, ((seq + 2) % 20) + 1, 1, FALSE
FROM seed_course
UNION ALL
SELECT seq, ((seq + 8) % 20) + 1, 2, FALSE
FROM seed_course;

INSERT INTO edu_course_teacher (id, course_id, teacher_id, created_at)
SELECT
    pg_temp.seed_course_scoped_uuid('course-teacher-', 'zh-course-teacher-', course_seq, '-' || teacher_seq),
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('teacher-user-' || teacher_seq),
    pg_temp.seed_ts('course-teacher-created-' || course_seq || '-' || teacher_seq)
FROM seed_course_teacher
ON CONFLICT (course_id, teacher_id) DO UPDATE SET
    teacher_id = EXCLUDED.teacher_id,
    created_at = EXCLUDED.created_at;

INSERT INTO edu_course_invitation (
    id, course_id, inviter_id, invitee_id, status, message, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('accepted-assistant-invitation-', 'zh-assistant-invite-', course_seq, '-' || teacher_seq),
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq),
    pg_temp.seed_uuid('teacher-user-' || teacher_seq),
    1,
    'Please join this course as an assistant for studio critique, attendance support, and project review.',
    pg_temp.seed_ts('assistant-invitation-created-' || course_seq || '-' || teacher_seq),
    pg_temp.seed_ts('assistant-invitation-updated-' || course_seq || '-' || teacher_seq),
    0
FROM seed_course_teacher sct
JOIN seed_course c ON c.seq = sct.course_seq
WHERE sct.primary_role = FALSE
ON CONFLICT (course_id, invitee_id) WHERE deleted = 0 DO UPDATE SET
    id = EXCLUDED.id,
    inviter_id = EXCLUDED.inviter_id,
    status = EXCLUDED.status,
    message = EXCLUDED.message,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

WITH extra_invitation AS (
    SELECT
        c.seq AS course_seq,
        c.primary_teacher_seq,
        ((c.seq + 4) % 20) + 1 AS invitee_seq,
        CASE c.seq % 3 WHEN 0 THEN 0 WHEN 1 THEN 2 ELSE 3 END AS invitation_status
    FROM seed_course c
)
INSERT INTO edu_course_invitation (
    id, course_id, inviter_id, invitee_id, status, message, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('workflow-invitation-', 'zh-workflow-invite-', course_seq),
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('teacher-user-' || primary_teacher_seq),
    pg_temp.seed_uuid('teacher-user-' || invitee_seq),
    invitation_status,
    CASE invitation_status
        WHEN 0 THEN 'Pending assistant invite kept for workflow testing.'
        WHEN 2 THEN 'Declined invite used to test refusal history.'
        ELSE 'Withdrawn invite used to test cancellation history.'
    END,
    pg_temp.seed_ts('workflow-invitation-created-' || course_seq),
    pg_temp.seed_ts('workflow-invitation-updated-' || course_seq),
    0
FROM extra_invitation
ON CONFLICT (course_id, invitee_id) WHERE deleted = 0 DO UPDATE SET
    id = EXCLUDED.id,
    inviter_id = EXCLUDED.inviter_id,
    status = EXCLUDED.status,
    message = EXCLUDED.message,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_enrollment ON COMMIT DROP AS
SELECT DISTINCT
    c.seq AS course_seq,
    s.seq AS student_seq,
    CASE
        WHEN pg_temp.seed_rand('enroll-status-' || c.seq || '-' || s.seq) < 0.08 THEN 0
        WHEN pg_temp.seed_rand('enroll-status-' || c.seq || '-' || s.seq) < 0.14 THEN 3
        WHEN pg_temp.seed_rand('enroll-status-' || c.seq || '-' || s.seq) < 0.30 THEN 2
        ELSE 1
    END AS status
FROM seed_course c
CROSS JOIN seed_student s
WHERE
    (
        c.is_public = 1
        AND pg_temp.seed_rand('enroll-public-' || c.seq || '-' || s.seq) < 0.46
    )
    OR (
        c.is_public = 0
        AND pg_temp.seed_rand('enroll-private-' || c.seq || '-' || s.seq) < 0.22
    )
    OR c.seq = ((s.seq - 1) % 20) + 1
    OR (c.is_public = 1 AND c.seq = ((s.seq + 6) % 10) + 1);

INSERT INTO edu_enrollment (
    id, course_id, student_id, status, enrolled_at, completed_at, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('enrollment-', 'zh-enrollment-', course_seq, '-' || student_seq),
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('student-user-' || student_seq),
    status,
    enrolled_at,
    CASE WHEN status = 2 THEN LEAST(enrolled_at + INTERVAL '28 days', TIMESTAMPTZ '2026-06-19 23:59:59+08') ELSE NULL END,
    GREATEST(enrolled_at - INTERVAL '10 minutes', TIMESTAMPTZ '2026-01-01 00:00:00+08'),
    LEAST(enrolled_at + INTERVAL '30 days', TIMESTAMPTZ '2026-06-19 23:59:59+08'),
    0
FROM (
    SELECT
        se.*,
        pg_temp.seed_ts('enrolled-at-' || course_seq || '-' || student_seq, TIMESTAMPTZ '2026-05-20 23:59:59+08') AS enrolled_at
    FROM seed_enrollment se
) enrollment_times
ON CONFLICT (course_id, student_id) DO UPDATE SET
    status = EXCLUDED.status,
    enrolled_at = EXCLUDED.enrolled_at,
    completed_at = EXCLUDED.completed_at,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_chapter ON COMMIT DROP AS
SELECT
    c.seq AS course_seq,
    m.module_seq,
    0 AS sub_idx,
    pg_temp.seed_course_scoped_uuid('chapter-main-', 'zh-chapter-main-', c.seq, '-' || m.module_seq) AS chapter_id,
    NULL::UUID AS parent_chapter_id,
    (m.module_seq || '. ' || m.main_title || ': ' || c.toolchain) AS chapter_name,
    'Module ' || m.module_seq || ' frames ' || c.topic_a || ', ' || c.topic_b || ', and ' || c.topic_c || ' for ' || c.capstone || '.' AS description,
    'Learners connect official references from ' || array_to_string(c.source_refs, ', ')
        || ' with a local studio task. The chapter asks students to define success evidence, build a small test, review constraints, and record decisions for ' || c.capstone || '.' AS content,
    m.module_seq * 100 AS sort_order
FROM seed_course c
CROSS JOIN seed_module_template m
UNION ALL
SELECT
    c.seq,
    m.module_seq,
    sub.sub_idx,
    pg_temp.seed_course_scoped_uuid('chapter-sub-', 'zh-chapter-sub-', c.seq, '-' || m.module_seq || '-' || sub.sub_idx),
    pg_temp.seed_course_scoped_uuid('chapter-main-', 'zh-chapter-main-', c.seq, '-' || m.module_seq),
    (m.module_seq || '.' || sub.sub_idx || ' ' || CASE WHEN sub.sub_idx = 1 THEN m.sub_title_a ELSE m.sub_title_b END),
    CASE WHEN sub.sub_idx = 1
        THEN 'A focused workshop on ' || c.topic_a || ' using ' || c.toolchain || '.'
        ELSE 'A review workshop connecting ' || c.topic_b || ' with ' || c.topic_c || '.'
    END,
    CASE WHEN sub.sub_idx = 1
        THEN 'Students build a constrained exercise, capture screenshots or notes, and compare the result against the brief for ' || c.capstone || '.'
        ELSE 'Students perform peer critique, identify one measurable flaw, and schedule a correction that can be retested before release.'
    END,
    m.module_seq * 100 + sub.sub_idx
FROM seed_course c
CROSS JOIN seed_module_template m
CROSS JOIN (VALUES (1), (2)) AS sub(sub_idx);

INSERT INTO edu_chapter (
    id, course_id, teacher_id, chapter_name, parent_chapter_id, description,
    content, attachment_urls, sort_order, status, view_count, like_count,
    created_at, updated_at, deleted
)
SELECT
    sc.chapter_id,
    pg_temp.seed_course_uuid(sc.course_seq),
    pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq),
    sc.chapter_name,
    sc.parent_chapter_id,
    sc.description,
    sc.content,
    '[]'::JSONB,
    sc.sort_order,
    1,
    60 + floor(pg_temp.seed_rand('chapter-view-' || sc.course_seq || '-' || sc.module_seq || '-' || sc.sub_idx) * 480)::BIGINT,
    0,
    pg_temp.seed_ts('chapter-created-' || sc.course_seq || '-' || sc.module_seq || '-' || sc.sub_idx),
    pg_temp.seed_ts('chapter-updated-' || sc.course_seq || '-' || sc.module_seq || '-' || sc.sub_idx),
    0
FROM seed_chapter sc
JOIN seed_course c ON c.seq = sc.course_seq
ON CONFLICT (id) DO UPDATE SET
    course_id = EXCLUDED.course_id,
    teacher_id = EXCLUDED.teacher_id,
    chapter_name = EXCLUDED.chapter_name,
    parent_chapter_id = EXCLUDED.parent_chapter_id,
    description = EXCLUDED.description,
    content = EXCLUDED.content,
    attachment_urls = EXCLUDED.attachment_urls,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    view_count = EXCLUDED.view_count,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_chapter_like (id, chapter_id, course_id, user_id, created_at)
SELECT
    pg_temp.seed_course_scoped_uuid('chapter-like-', 'zh-chapter-like-', ch.course_seq, '-' || ch.module_seq || '-' || ch.sub_idx || '-' || e.student_seq),
    ch.chapter_id,
    pg_temp.seed_course_uuid(ch.course_seq),
    pg_temp.seed_uuid('student-user-' || e.student_seq),
    pg_temp.seed_ts('chapter-like-created-' || ch.course_seq || '-' || ch.module_seq || '-' || ch.sub_idx || '-' || e.student_seq)
FROM seed_chapter ch
JOIN seed_enrollment e ON e.course_seq = ch.course_seq
WHERE e.status IN (1, 2)
  AND pg_temp.seed_rand('chapter-like-' || ch.course_seq || '-' || ch.module_seq || '-' || ch.sub_idx || '-' || e.student_seq) < 0.20
ON CONFLICT (chapter_id, user_id) DO NOTHING;

UPDATE edu_chapter c
SET like_count = counts.like_count
FROM (
    SELECT chapter_id, count(*)::BIGINT AS like_count
    FROM edu_chapter_like
    WHERE chapter_id IN (SELECT chapter_id FROM seed_chapter)
    GROUP BY chapter_id
) counts
WHERE c.id = counts.chapter_id;

INSERT INTO edu_question_bank (
    id, course_id, sys_user_id, bank_name, description, bank_type, tags,
    difficulty, created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('bank-', 'zh-bank-', c.seq, '-' || b.bank_idx),
    pg_temp.seed_course_uuid(c.seq),
    pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq),
    c.title || ' - ' || b.bank_label,
    b.description || ' Course focus: ' || c.topic_a || ', ' || c.topic_b || ', ' || c.topic_c || '.',
    b.bank_type,
    to_jsonb(ARRAY[c.toolchain, c.topic_a, c.topic_b, b.bank_label]),
    b.difficulty,
    pg_temp.seed_ts('bank-created-' || c.seq || '-' || b.bank_idx),
    pg_temp.seed_ts('bank-updated-' || c.seq || '-' || b.bank_idx),
    0
FROM seed_course c
CROSS JOIN seed_bank_template b
ON CONFLICT (id) DO UPDATE SET
    course_id = EXCLUDED.course_id,
    sys_user_id = EXCLUDED.sys_user_id,
    bank_name = EXCLUDED.bank_name,
    description = EXCLUDED.description,
    bank_type = EXCLUDED.bank_type,
    tags = EXCLUDED.tags,
    difficulty = EXCLUDED.difficulty,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_question ON COMMIT DROP AS
SELECT
    c.seq AS course_seq,
    b.bank_idx,
    qt.q_no,
    pg_temp.seed_course_scoped_uuid('question-', 'zh-question-', c.seq, '-' || b.bank_idx || '-' || qt.q_no) AS question_id,
    pg_temp.seed_course_scoped_uuid('bank-', 'zh-bank-', c.seq, '-' || b.bank_idx) AS bank_id,
    qt.question_type,
    GREATEST(qt.difficulty, b.difficulty - 1) AS difficulty,
    qt.score + ((b.bank_idx - 1) * 2) AS score,
    qt.estimated_time + b.bank_idx AS estimated_time,
    format(qt.title_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) AS question_title,
    format(qt.content_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) AS question_content,
    CASE WHEN qt.option_a_t IS NULL THEN NULL ELSE format(qt.option_a_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) END AS option_a,
    CASE WHEN qt.option_b_t IS NULL THEN NULL ELSE format(qt.option_b_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) END AS option_b,
    CASE WHEN qt.option_c_t IS NULL THEN NULL ELSE format(qt.option_c_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) END AS option_c,
    CASE WHEN qt.option_d_t IS NULL THEN NULL ELSE format(qt.option_d_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) END AS option_d,
    CASE WHEN qt.option_e_t IS NULL THEN NULL ELSE format(qt.option_e_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) END AS option_e,
    qt.correct_labels,
    format(qt.answer_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) AS answer_content,
    format(qt.explanation_t, c.toolchain, c.topic_a, c.topic_b, c.topic_c, c.capstone) AS explanation,
    c.primary_teacher_seq,
    c.toolchain,
    c.topic_a,
    c.topic_b,
    c.topic_c
FROM seed_course c
CROSS JOIN seed_bank_template b
CROSS JOIN seed_question_template qt;

INSERT INTO edu_question (
    id, question_bank_id, course_id, sys_user_id, question_title, question_content,
    question_type, difficulty, score, estimated_time, tags, image_urls,
    allow_partial_credit, view_count, status, created_at, updated_at, deleted
)
SELECT
    question_id,
    bank_id,
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('teacher-user-' || primary_teacher_seq),
    question_title,
    question_content,
    question_type,
    difficulty,
    score,
    estimated_time,
    to_jsonb(ARRAY[toolchain, topic_a, topic_b, 'simulation']),
    '[]'::JSONB,
    CASE WHEN question_type IN (1, 4) THEN 1 ELSE 0 END,
    10 + floor(pg_temp.seed_rand('question-view-' || course_seq || '-' || bank_idx || '-' || q_no) * 200)::BIGINT,
    1,
    pg_temp.seed_ts('question-created-' || course_seq || '-' || bank_idx || '-' || q_no),
    pg_temp.seed_ts('question-updated-' || course_seq || '-' || bank_idx || '-' || q_no),
    0
FROM seed_question
ON CONFLICT (id) DO UPDATE SET
    question_bank_id = EXCLUDED.question_bank_id,
    course_id = EXCLUDED.course_id,
    sys_user_id = EXCLUDED.sys_user_id,
    question_title = EXCLUDED.question_title,
    question_content = EXCLUDED.question_content,
    question_type = EXCLUDED.question_type,
    difficulty = EXCLUDED.difficulty,
    score = EXCLUDED.score,
    estimated_time = EXCLUDED.estimated_time,
    tags = EXCLUDED.tags,
    image_urls = EXCLUDED.image_urls,
    allow_partial_credit = EXCLUDED.allow_partial_credit,
    view_count = EXCLUDED.view_count,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_question_option ON COMMIT DROP AS
SELECT
    q.course_seq,
    q.bank_idx,
    q.q_no,
    opt.option_label,
    pg_temp.seed_course_scoped_uuid('option-', 'zh-option-', q.course_seq, '-' || q.bank_idx || '-' || q.q_no || '-' || opt.option_label) AS option_id,
    q.question_id,
    opt.option_content,
    CASE WHEN opt.option_label = ANY(q.correct_labels) THEN 1 ELSE 0 END AS is_correct,
    CASE
        WHEN opt.option_label = ANY(q.correct_labels) AND cardinality(q.correct_labels) > 0
            THEN round(q.score / cardinality(q.correct_labels), 2)
        ELSE 0
    END AS option_score,
    q.explanation
FROM seed_question q
CROSS JOIN LATERAL (
    VALUES
        ('A', q.option_a),
        ('B', q.option_b),
        ('C', q.option_c),
        ('D', q.option_d),
        ('E', q.option_e)
) AS opt(option_label, option_content)
WHERE q.question_type IN (0, 1, 2)
  AND opt.option_content IS NOT NULL;

INSERT INTO edu_question_option (
    id, question_id, course_id, option_content, option_label, is_correct,
    score, image_urls, explanation, created_at, updated_at, deleted
)
SELECT
    option_id,
    question_id,
    pg_temp.seed_course_uuid(course_seq),
    option_content,
    option_label,
    is_correct,
    option_score,
    '[]'::JSONB,
    explanation,
    pg_temp.seed_ts('option-created-' || course_seq || '-' || bank_idx || '-' || q_no || '-' || option_label),
    pg_temp.seed_ts('option-updated-' || course_seq || '-' || bank_idx || '-' || q_no || '-' || option_label),
    0
FROM seed_question_option
ON CONFLICT (id) DO UPDATE SET
    question_id = EXCLUDED.question_id,
    course_id = EXCLUDED.course_id,
    option_content = EXCLUDED.option_content,
    option_label = EXCLUDED.option_label,
    is_correct = EXCLUDED.is_correct,
    score = EXCLUDED.score,
    image_urls = EXCLUDED.image_urls,
    explanation = EXCLUDED.explanation,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_question_answer (
    id, question_id, course_id, answer_content, explanation, score, sort_order,
    created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('answer-', 'zh-answer-', course_seq, '-' || bank_idx || '-' || q_no),
    question_id,
    pg_temp.seed_course_uuid(course_seq),
    answer_content,
    explanation,
    score,
    1,
    pg_temp.seed_ts('answer-created-' || course_seq || '-' || bank_idx || '-' || q_no),
    pg_temp.seed_ts('answer-updated-' || course_seq || '-' || bank_idx || '-' || q_no),
    0
FROM seed_question
WHERE question_type IN (3, 4)
ON CONFLICT (id) DO UPDATE SET
    question_id = EXCLUDED.question_id,
    course_id = EXCLUDED.course_id,
    answer_content = EXCLUDED.answer_content,
    explanation = EXCLUDED.explanation,
    score = EXCLUDED.score,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_class_session ON COMMIT DROP AS
SELECT
    c.seq AS course_seq,
    class_idx,
    pg_temp.seed_course_scoped_uuid('class-session-', 'zh-class-session-', c.seq, '-' || class_idx) AS session_id,
    pg_temp.seed_course_uuid(c.seq) AS course_id,
    pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq) AS teacher_id,
    c.title || ' - Live Studio ' || class_idx AS title,
    'Live workshop for ' || c.topic_a || ', ' || c.topic_b || ', and critique toward ' || c.capstone || '.' AS description,
    pg_temp.seed_ts('class-start-' || c.seq || '-' || class_idx, TIMESTAMPTZ '2026-06-19 20:00:00+08') AS scheduled_start_at,
    c.level AS room_size
FROM seed_course c
CROSS JOIN generate_series(1, 4) AS class_idx;

INSERT INTO edu_class_session (
    id, course_id, teacher_id, title, description, scheduled_start_at,
    scheduled_end_at, published_at, room_size, live_room_name, created_at,
    updated_at, deleted
)
SELECT
    session_id,
    course_id,
    teacher_id,
    title,
    description,
    scheduled_start_at,
    scheduled_start_at + INTERVAL '110 minutes',
    GREATEST(
        TIMESTAMPTZ '2026-01-01 00:00:00+08',
        scheduled_start_at - ((1 + floor(pg_temp.seed_rand('publish-gap-' || course_seq || '-' || class_idx) * 21))::TEXT || ' days')::INTERVAL
    ),
    LEAST(room_size, 3),
    'edupivot-course-' || course_seq || '-session-' || class_idx,
    pg_temp.seed_ts('class-created-' || course_seq || '-' || class_idx),
    pg_temp.seed_ts('class-updated-' || course_seq || '-' || class_idx),
    0
FROM seed_class_session
ON CONFLICT (id) DO UPDATE SET
    course_id = EXCLUDED.course_id,
    teacher_id = EXCLUDED.teacher_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    scheduled_start_at = EXCLUDED.scheduled_start_at,
    scheduled_end_at = EXCLUDED.scheduled_end_at,
    published_at = EXCLUDED.published_at,
    room_size = EXCLUDED.room_size,
    live_room_name = EXCLUDED.live_room_name,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_class_participant ON COMMIT DROP AS
SELECT
    cs.course_seq,
    cs.class_idx,
    cs.session_id,
    sct.teacher_seq AS user_seq,
    pg_temp.seed_uuid('teacher-user-' || sct.teacher_seq) AS user_id,
    0 AS role,
    row_number() OVER (PARTITION BY cs.session_id ORDER BY sct.assistant_order, sct.teacher_seq) AS seat_index,
    cs.scheduled_start_at
FROM seed_class_session cs
JOIN seed_course_teacher sct ON sct.course_seq = cs.course_seq
UNION ALL
SELECT
    cs.course_seq,
    cs.class_idx,
    cs.session_id,
    e.student_seq,
    pg_temp.seed_uuid('student-user-' || e.student_seq),
    1,
    10 + row_number() OVER (PARTITION BY cs.session_id ORDER BY e.student_seq),
    cs.scheduled_start_at
FROM seed_class_session cs
JOIN seed_enrollment e ON e.course_seq = cs.course_seq
WHERE e.status IN (1, 2)
  AND pg_temp.seed_rand('attendance-' || cs.course_seq || '-' || cs.class_idx || '-' || e.student_seq) < 0.45;

INSERT INTO edu_class_participant (
    id, session_id, user_id, role, seat_index, x, y, z, joined_at
)
SELECT
    pg_temp.seed_course_scoped_uuid('class-participant-', 'zh-class-participant-', course_seq, '-' || class_idx || '-' || role || '-' || user_seq),
    session_id,
    user_id,
    role,
    seat_index,
    ((seat_index - 1) % 8) * 1.60,
    floor((seat_index - 1) / 8) * 1.45,
    0,
    scheduled_start_at + (floor(pg_temp.seed_rand('joined-at-' || course_seq || '-' || class_idx || '-' || user_seq) * 600) * INTERVAL '1 second')
FROM seed_class_participant
ON CONFLICT (session_id, user_id) DO UPDATE SET
    role = EXCLUDED.role,
    seat_index = EXCLUDED.seat_index,
    x = EXCLUDED.x,
    y = EXCLUDED.y,
    z = EXCLUDED.z,
    joined_at = EXCLUDED.joined_at;

INSERT INTO edu_class_barrage (id, session_id, sender_id, content, sent_at, deleted)
SELECT
    pg_temp.seed_course_scoped_uuid('class-barrage-', 'zh-class-barrage-', cs.course_seq, '-' || cs.class_idx || '-' || msg_idx),
    cs.session_id,
    sender.user_id,
    CASE msg_idx
        WHEN 1 THEN 'I can reproduce the issue after switching scenes; adding it to the test notes.'
        WHEN 2 THEN 'The feedback loop is clearer after reducing visual noise around the main action.'
        WHEN 3 THEN 'Can we compare this against the rubric before the next playtest?'
        ELSE 'Performance is stable on my laptop, but the target lab machine still needs a check.'
    END,
    cs.scheduled_start_at + ((12 + msg_idx * 9) * INTERVAL '1 minute'),
    0
FROM seed_class_session cs
CROSS JOIN generate_series(1, 4) AS msg_idx
JOIN LATERAL (
    SELECT user_id
    FROM seed_class_participant p
    WHERE p.session_id = cs.session_id
    ORDER BY role DESC, seat_index
    OFFSET (msg_idx - 1)
    LIMIT 1
) sender ON TRUE
ON CONFLICT (id) DO UPDATE SET
    session_id = EXCLUDED.session_id,
    sender_id = EXCLUDED.sender_id,
    content = EXCLUDED.content,
    sent_at = EXCLUDED.sent_at,
    deleted = 0;

CREATE TEMP TABLE seed_live_group ON COMMIT DROP AS
SELECT
    course_seq,
    class_idx,
    session_id,
    pg_temp.seed_course_scoped_uuid('live-group-', 'zh-live-group-', course_seq, '-' || class_idx) AS group_id,
    course_id,
    teacher_id,
    title || ' - five-question checkpoint' AS title,
    scheduled_start_at + INTERVAL '25 minutes' AS available_start_at,
    scheduled_start_at + INTERVAL '55 minutes' AS available_end_at
FROM seed_class_session;

INSERT INTO edu_live_practice_group (
    id, course_id, class_session_id, teacher_id, title, available_start_at,
    available_end_at, allow_late_submission, publish_order, published_at,
    created_at, updated_at, deleted
)
SELECT
    group_id,
    course_id,
    session_id,
    teacher_id,
    title,
    available_start_at,
    available_end_at,
    1,
    1,
    available_start_at - INTERVAL '5 minutes',
    pg_temp.seed_ts('live-group-created-' || course_seq || '-' || class_idx),
    pg_temp.seed_ts('live-group-updated-' || course_seq || '-' || class_idx),
    0
FROM seed_live_group
ON CONFLICT (id) DO UPDATE SET
    course_id = EXCLUDED.course_id,
    class_session_id = EXCLUDED.class_session_id,
    teacher_id = EXCLUDED.teacher_id,
    title = EXCLUDED.title,
    available_start_at = EXCLUDED.available_start_at,
    available_end_at = EXCLUDED.available_end_at,
    allow_late_submission = EXCLUDED.allow_late_submission,
    publish_order = EXCLUDED.publish_order,
    published_at = EXCLUDED.published_at,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

CREATE TEMP TABLE seed_live_question ON COMMIT DROP AS
SELECT
    lg.course_seq,
    lg.class_idx,
    lg.group_id,
    lg.session_id,
    q.q_no,
    pg_temp.seed_course_scoped_uuid('live-question-', 'zh-live-question-', lg.course_seq, '-' || lg.class_idx || '-' || q.q_no) AS live_question_id,
    q.question_id AS source_question_id,
    q.question_title,
    q.question_content,
    q.question_type,
    q.difficulty,
    q.score,
    q.estimated_time,
    q.toolchain,
    q.topic_a,
    q.topic_b
FROM seed_live_group lg
JOIN seed_question q ON q.course_seq = lg.course_seq
                    AND q.bank_idx = 1
                    AND q.q_no BETWEEN 1 AND 5;

INSERT INTO edu_live_practice_question (
    id, group_id, course_id, class_session_id, source_question_id, question_order,
    question_title, question_content, question_type, difficulty, score,
    estimated_time, tags, image_urls, allow_partial_credit, options_snapshot,
    answers_snapshot, ai_grading_enabled, created_at, updated_at, deleted
)
SELECT
    lq.live_question_id,
    lq.group_id,
    pg_temp.seed_course_uuid(lq.course_seq),
    lq.session_id,
    lq.source_question_id,
    lq.q_no,
    lq.question_title,
    lq.question_content,
    lq.question_type,
    lq.difficulty,
    lq.score,
    lq.estimated_time,
    to_jsonb(ARRAY[lq.toolchain, lq.topic_a, lq.topic_b, 'live-practice']),
    '[]'::JSONB,
    CASE WHEN lq.question_type IN (1, 4) THEN 1 ELSE 0 END,
    COALESCE((
        SELECT jsonb_agg(
            jsonb_build_object(
                'id', qo.option_id,
                'optionContent', qo.option_content,
                'optionLabel', qo.option_label,
                'isCorrect', qo.is_correct,
                'score', qo.option_score,
                'imageUrls', '[]'::JSONB,
                'explanation', qo.explanation
            )
            ORDER BY qo.option_label
        )
        FROM seed_question_option qo
        WHERE qo.question_id = lq.source_question_id
    ), '[]'::JSONB),
    COALESCE((
        SELECT jsonb_agg(
            jsonb_build_object(
                'id', pg_temp.seed_course_scoped_uuid('answer-', 'zh-answer-', lq.course_seq, '-1-' || lq.q_no),
                'answerContent', q.answer_content,
                'explanation', q.explanation,
                'score', q.score,
                'sortOrder', 1
            )
        )
        FROM seed_question q
        WHERE q.question_id = lq.source_question_id
          AND q.question_type IN (3, 4)
    ), '[]'::JSONB),
    0,
    pg_temp.seed_ts('live-question-created-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no),
    pg_temp.seed_ts('live-question-updated-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no),
    0
FROM seed_live_question lq
ON CONFLICT (id) DO UPDATE SET
    group_id = EXCLUDED.group_id,
    course_id = EXCLUDED.course_id,
    class_session_id = EXCLUDED.class_session_id,
    source_question_id = EXCLUDED.source_question_id,
    question_order = EXCLUDED.question_order,
    question_title = EXCLUDED.question_title,
    question_content = EXCLUDED.question_content,
    question_type = EXCLUDED.question_type,
    difficulty = EXCLUDED.difficulty,
    score = EXCLUDED.score,
    estimated_time = EXCLUDED.estimated_time,
    tags = EXCLUDED.tags,
    image_urls = EXCLUDED.image_urls,
    allow_partial_credit = EXCLUDED.allow_partial_credit,
    options_snapshot = EXCLUDED.options_snapshot,
    answers_snapshot = EXCLUDED.answers_snapshot,
    ai_grading_enabled = EXCLUDED.ai_grading_enabled,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_live_practice_submission (
    id, group_id, question_snapshot_id, course_id, class_session_id, student_id,
    selected_option_ids, text_answer, submit_status, is_correct, earned_score,
    submitted_at, created_at, ai_grading_status, ai_grading_feedback,
    ai_grading_error, ai_graded_at
)
SELECT
    pg_temp.seed_course_scoped_uuid('live-submission-', 'zh-live-submission-', lq.course_seq, '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq),
    lq.group_id,
    lq.live_question_id,
    pg_temp.seed_course_uuid(lq.course_seq),
    lq.session_id,
    pg_temp.seed_uuid('student-user-' || st.seq),
    CASE WHEN lq.question_type IN (0, 1, 2) THEN
        CASE WHEN pg_temp.seed_rand('live-correct-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.70
            THEN (
                SELECT COALESCE(jsonb_agg(opt->>'id'), '[]'::JSONB)
                FROM jsonb_array_elements(lpq.options_snapshot) opt
                WHERE (opt->>'isCorrect')::INT = 1
            )
            ELSE (
                SELECT COALESCE(jsonb_agg(opt->>'id'), '[]'::JSONB)
                FROM (
                    SELECT opt
                    FROM jsonb_array_elements(lpq.options_snapshot) opt
                    WHERE (opt->>'isCorrect')::INT = 0
                    ORDER BY opt->>'optionLabel'
                    LIMIT 1
                ) wrong
            )
        END
        ELSE NULL
    END,
    CASE WHEN lq.question_type IN (3, 4) THEN
        CASE WHEN pg_temp.seed_rand('live-correct-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.70
            THEN 'I would define the target behavior, run a small test, document evidence, and adjust the next milestone.'
            ELSE 'The idea is promising but I need more evidence and a clearer test condition.'
        END
        ELSE NULL
    END,
    CASE WHEN pg_temp.seed_rand('live-late-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.08 THEN 2 ELSE 1 END,
    CASE WHEN pg_temp.seed_rand('live-correct-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.70 THEN 1 ELSE 0 END,
    CASE WHEN pg_temp.seed_rand('live-correct-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.70 THEN lq.score ELSE round(lq.score * 0.25, 2) END,
    lg.available_start_at + ((5 + lq.q_no * 3 + (st.seq % 5)) * INTERVAL '1 minute'),
    lg.available_start_at + ((5 + lq.q_no * 3 + (st.seq % 5)) * INTERVAL '1 minute'),
    'NOT_REQUIRED',
    NULL,
    NULL,
    NULL
FROM seed_live_question lq
JOIN seed_live_group lg ON lg.group_id = lq.group_id
JOIN edu_live_practice_question lpq ON lpq.id = lq.live_question_id
JOIN seed_student st ON TRUE
JOIN seed_class_participant cp ON cp.session_id = lq.session_id
                              AND cp.user_id = pg_temp.seed_uuid('student-user-' || st.seq)
                              AND cp.role = 1
WHERE pg_temp.seed_rand('live-submit-' || lq.course_seq || '-' || lq.class_idx || '-' || lq.q_no || '-' || st.seq) < 0.68
ON CONFLICT (group_id, question_snapshot_id, student_id) DO UPDATE SET
    selected_option_ids = EXCLUDED.selected_option_ids,
    text_answer = EXCLUDED.text_answer,
    submit_status = EXCLUDED.submit_status,
    is_correct = EXCLUDED.is_correct,
    earned_score = EXCLUDED.earned_score,
    submitted_at = EXCLUDED.submitted_at,
    created_at = EXCLUDED.created_at,
    ai_grading_status = EXCLUDED.ai_grading_status,
    ai_grading_feedback = EXCLUDED.ai_grading_feedback,
    ai_grading_error = EXCLUDED.ai_grading_error,
    ai_graded_at = EXCLUDED.ai_graded_at;

CREATE TEMP TABLE seed_practice_session ON COMMIT DROP AS
WITH candidates AS (
    SELECT e.course_seq, e.student_seq, b.bank_idx
    FROM seed_enrollment e
    CROSS JOIN seed_bank_template b
    WHERE e.status IN (1, 2)
      AND b.bank_idx IN (1, 2)
      AND (
          (b.bank_idx = 1 AND pg_temp.seed_rand('practice-session-' || e.course_seq || '-' || e.student_seq || '-' || b.bank_idx) < 0.42)
          OR
          (b.bank_idx = 2 AND pg_temp.seed_rand('practice-session-' || e.course_seq || '-' || e.student_seq || '-' || b.bank_idx) < 0.18)
      )
),
question_scores AS (
    SELECT
        c.course_seq,
        c.student_seq,
        c.bank_idx,
        count(*) AS total_questions,
        sum(q.score) AS total_score,
        count(*) FILTER (
            WHERE pg_temp.seed_rand('practice-correct-' || c.course_seq || '-' || c.student_seq || '-' || c.bank_idx || '-' || q.q_no) < 0.72
        ) AS correct_count,
        sum(
            CASE WHEN pg_temp.seed_rand('practice-correct-' || c.course_seq || '-' || c.student_seq || '-' || c.bank_idx || '-' || q.q_no) < 0.72
                THEN q.score
                ELSE round(q.score * 0.25, 2)
            END
        ) AS earned_score
    FROM candidates c
    JOIN seed_question q ON q.course_seq = c.course_seq
                        AND q.bank_idx = c.bank_idx
                        AND q.q_no BETWEEN 1 AND 10
    GROUP BY c.course_seq, c.student_seq, c.bank_idx
)
SELECT
    qs.*,
    pg_temp.seed_course_scoped_uuid('practice-session-', 'zh-practice-session-', course_seq, '-' || student_seq || '-' || bank_idx) AS session_id,
    pg_temp.seed_ts('practice-start-' || course_seq || '-' || student_seq || '-' || bank_idx, TIMESTAMPTZ '2026-06-19 22:00:00+08') AS started_at
FROM question_scores qs;

INSERT INTO edu_practice_session (
    id, question_bank_id, course_id, sys_user_id, session_type, total_questions,
    answered_count, correct_count, total_score, earned_score, started_at,
    completed_at, status
)
SELECT
    session_id,
    pg_temp.seed_course_scoped_uuid('bank-', 'zh-bank-', course_seq, '-' || bank_idx),
    pg_temp.seed_course_uuid(course_seq),
    pg_temp.seed_uuid('student-user-' || student_seq),
    CASE WHEN bank_idx = 1 THEN 0 ELSE 1 END,
    total_questions,
    total_questions,
    correct_count,
    total_score,
    earned_score,
    started_at,
    started_at + INTERVAL '45 minutes',
    1
FROM seed_practice_session
ON CONFLICT (id) DO UPDATE SET
    question_bank_id = EXCLUDED.question_bank_id,
    course_id = EXCLUDED.course_id,
    sys_user_id = EXCLUDED.sys_user_id,
    session_type = EXCLUDED.session_type,
    total_questions = EXCLUDED.total_questions,
    answered_count = EXCLUDED.answered_count,
    correct_count = EXCLUDED.correct_count,
    total_score = EXCLUDED.total_score,
    earned_score = EXCLUDED.earned_score,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at,
    status = EXCLUDED.status;

INSERT INTO edu_practice_answer (
    id, session_id, question_id, selected_option_ids, text_answer,
    is_correct, earned_score, answered_at
)
SELECT
    pg_temp.seed_course_scoped_uuid('practice-answer-', 'zh-practice-answer-', ps.course_seq, '-' || ps.student_seq || '-' || ps.bank_idx || '-' || q.q_no),
    ps.session_id,
    q.question_id,
    CASE WHEN q.question_type IN (0, 1, 2) THEN
        CASE WHEN pg_temp.seed_rand('practice-correct-' || ps.course_seq || '-' || ps.student_seq || '-' || ps.bank_idx || '-' || q.q_no) < 0.72
            THEN (
                SELECT COALESCE(jsonb_agg(option_id::TEXT), '[]'::JSONB)
                FROM seed_question_option qo
                WHERE qo.question_id = q.question_id
                  AND qo.is_correct = 1
            )
            ELSE (
                SELECT COALESCE(jsonb_agg(option_id::TEXT), '[]'::JSONB)
                FROM (
                    SELECT option_id
                    FROM seed_question_option qo
                    WHERE qo.question_id = q.question_id
                      AND qo.is_correct = 0
                    ORDER BY option_label
                    LIMIT 1
                ) wrong
            )
        END
        ELSE NULL
    END,
    CASE WHEN q.question_type IN (3, 4) THEN
        CASE WHEN pg_temp.seed_rand('practice-correct-' || ps.course_seq || '-' || ps.student_seq || '-' || ps.bank_idx || '-' || q.q_no) < 0.72
            THEN q.answer_content
            ELSE 'Partial answer: I can name the goal, but the evidence and next test need to be sharper.'
        END
        ELSE NULL
    END,
    CASE WHEN pg_temp.seed_rand('practice-correct-' || ps.course_seq || '-' || ps.student_seq || '-' || ps.bank_idx || '-' || q.q_no) < 0.72 THEN 1 ELSE 0 END,
    CASE WHEN pg_temp.seed_rand('practice-correct-' || ps.course_seq || '-' || ps.student_seq || '-' || ps.bank_idx || '-' || q.q_no) < 0.72 THEN q.score ELSE round(q.score * 0.25, 2) END,
    ps.started_at + ((q.q_no * 3) * INTERVAL '1 minute')
FROM seed_practice_session ps
JOIN seed_question q ON q.course_seq = ps.course_seq
                    AND q.bank_idx = ps.bank_idx
                    AND q.q_no BETWEEN 1 AND 10
ON CONFLICT (id) DO UPDATE SET
    session_id = EXCLUDED.session_id,
    question_id = EXCLUDED.question_id,
    selected_option_ids = EXCLUDED.selected_option_ids,
    text_answer = EXCLUDED.text_answer,
    is_correct = EXCLUDED.is_correct,
    earned_score = EXCLUDED.earned_score,
    answered_at = EXCLUDED.answered_at;

CREATE TEMP TABLE seed_forum_post ON COMMIT DROP AS
SELECT
    c.seq AS course_seq,
    posts.post_idx,
    pg_temp.seed_course_scoped_uuid('forum-post-', 'zh-forum-post-', c.seq, '-' || posts.post_idx) AS post_id,
    pg_temp.seed_course_uuid(c.seq) AS course_id,
    COALESCE(author.user_id, pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq)) AS author_id,
    CASE posts.post_idx
        WHEN 1 THEN 'Reading notes for ' || c.toolchain || ': what changed our production plan'
        WHEN 2 THEN 'Playtest thread: where ' || c.topic_a || ' still feels unclear'
        WHEN 3 THEN 'Asset and scene review checklist for ' || c.capstone
        WHEN 4 THEN 'Question: balancing ' || c.topic_b || ' with ' || c.topic_c
        ELSE 'Release risk log for the next ' || c.toolchain || ' milestone'
    END AS title,
    CASE posts.post_idx
        WHEN 1 THEN 'I compared the official references with our current scene. The biggest change is that we need a smaller testable path before adding more content.'
        WHEN 2 THEN 'During the latest test, two people missed the main action. I suggest adding a clearer cue, then retesting with the same task prompt.'
        WHEN 3 THEN 'Please review naming, import settings, scene load state, and whether the artifact supports the capstone goal before marking it ready.'
        WHEN 4 THEN 'The tradeoff is real: improving one part may make the other harder to maintain. What evidence should decide this?'
        ELSE 'Known risks: schedule compression, target-device performance, unclear ownership, and insufficient documentation for the final demo.'
    END AS content
FROM seed_course c
CROSS JOIN generate_series(1, 5) AS posts(post_idx)
LEFT JOIN LATERAL (
    SELECT pg_temp.seed_uuid('student-user-' || e.student_seq) AS user_id
    FROM seed_enrollment e
    WHERE e.course_seq = c.seq
      AND e.status IN (1, 2)
    ORDER BY pg_temp.seed_rand('forum-author-' || c.seq || '-' || posts.post_idx || '-' || e.student_seq)
    LIMIT 1
) author ON posts.post_idx > 1;

INSERT INTO edu_forum_post (
    id, course_id, sys_user_id, title, content, post_type, is_anonymous,
    attachment_urls, image_urls, tags, view_count, like_count, reply_count,
    share_count, is_top, is_essence, is_locked, last_reply_id,
    last_reply_time, last_reply_user_id, status, chapter_id, created_at,
    updated_at, deleted
)
SELECT
    fp.post_id,
    fp.course_id,
    fp.author_id,
    fp.title,
    fp.content,
    CASE WHEN fp.post_idx = 1 THEN 1 ELSE 0 END,
    0,
    '[]'::JSONB,
    '[]'::JSONB,
    to_jsonb(ARRAY[c.toolchain, c.topic_a, 'discussion']),
    30 + floor(pg_temp.seed_rand('forum-view-' || fp.course_seq || '-' || fp.post_idx) * 260)::BIGINT,
    floor(pg_temp.seed_rand('forum-like-' || fp.course_seq || '-' || fp.post_idx) * 40)::BIGINT,
    0,
    floor(pg_temp.seed_rand('forum-share-' || fp.course_seq || '-' || fp.post_idx) * 8)::BIGINT,
    CASE WHEN fp.post_idx = 1 THEN 1 ELSE 0 END,
    CASE WHEN fp.post_idx IN (2, 3) THEN 1 ELSE 0 END,
    0,
    NULL,
    NULL,
    NULL,
    0,
    pg_temp.seed_course_scoped_uuid('chapter-main-', 'zh-chapter-main-', fp.course_seq, '-' || ((fp.post_idx - 1) % 6 + 1)),
    pg_temp.seed_ts('forum-post-created-' || fp.course_seq || '-' || fp.post_idx),
    pg_temp.seed_ts('forum-post-updated-' || fp.course_seq || '-' || fp.post_idx),
    0
FROM seed_forum_post fp
JOIN seed_course c ON c.seq = fp.course_seq
ON CONFLICT (id) DO UPDATE SET
    course_id = EXCLUDED.course_id,
    sys_user_id = EXCLUDED.sys_user_id,
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    post_type = EXCLUDED.post_type,
    attachment_urls = EXCLUDED.attachment_urls,
    image_urls = EXCLUDED.image_urls,
    tags = EXCLUDED.tags,
    view_count = EXCLUDED.view_count,
    like_count = EXCLUDED.like_count,
    share_count = EXCLUDED.share_count,
    is_top = EXCLUDED.is_top,
    is_essence = EXCLUDED.is_essence,
    is_locked = EXCLUDED.is_locked,
    status = EXCLUDED.status,
    chapter_id = EXCLUDED.chapter_id,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO edu_forum_reply (
    id, post_id, course_id, sys_user_id, content, parent_reply_id,
    reply_to_user_id, is_anonymous, attachment_urls, image_urls, like_count,
    reply_count, is_accepted, floor_number, status, ip_address, user_agent,
    created_at, updated_at, deleted
)
SELECT
    pg_temp.seed_course_scoped_uuid('forum-reply-', 'zh-forum-reply-', fp.course_seq, '-' || fp.post_idx || '-' || reply_idx),
    fp.post_id,
    fp.course_id,
    COALESCE(author.user_id, fp.author_id),
    CASE reply_idx
        WHEN 1 THEN 'I tried this in my local scene and the first failure was not the code, but the unclear success condition.'
        WHEN 2 THEN 'The rubric should mention target-device behavior because the classroom machine exposed a different bottleneck.'
        ELSE 'I agree with keeping the next change small. One test task and one measurable observation would be enough.'
    END,
    NULL,
    fp.author_id,
    0,
    '[]'::JSONB,
    '[]'::JSONB,
    floor(pg_temp.seed_rand('forum-reply-like-' || fp.course_seq || '-' || fp.post_idx || '-' || reply_idx) * 14)::BIGINT,
    0,
    CASE WHEN fp.post_idx = 4 AND reply_idx = 3 THEN 1 ELSE 0 END,
    reply_idx,
    0,
    '127.0.0.1',
    'EduPivotSeed/2026',
    LEAST(p.created_at + (reply_idx * INTERVAL '2 hours'), TIMESTAMPTZ '2026-06-19 23:59:59+08'),
    LEAST(p.created_at + (reply_idx * INTERVAL '2 hours') + INTERVAL '15 minutes', TIMESTAMPTZ '2026-06-19 23:59:59+08'),
    0
FROM seed_forum_post fp
JOIN edu_forum_post p ON p.id = fp.post_id
CROSS JOIN generate_series(1, 3) AS reply_idx
LEFT JOIN LATERAL (
    SELECT pg_temp.seed_uuid('student-user-' || e.student_seq) AS user_id
    FROM seed_enrollment e
    WHERE e.course_seq = fp.course_seq
      AND e.status IN (1, 2)
    ORDER BY pg_temp.seed_rand('forum-reply-author-' || fp.course_seq || '-' || fp.post_idx || '-' || reply_idx || '-' || e.student_seq)
    LIMIT 1
) author ON TRUE
ON CONFLICT (id) DO UPDATE SET
    post_id = EXCLUDED.post_id,
    course_id = EXCLUDED.course_id,
    sys_user_id = EXCLUDED.sys_user_id,
    content = EXCLUDED.content,
    parent_reply_id = EXCLUDED.parent_reply_id,
    reply_to_user_id = EXCLUDED.reply_to_user_id,
    attachment_urls = EXCLUDED.attachment_urls,
    image_urls = EXCLUDED.image_urls,
    like_count = EXCLUDED.like_count,
    reply_count = EXCLUDED.reply_count,
    is_accepted = EXCLUDED.is_accepted,
    floor_number = EXCLUDED.floor_number,
    status = EXCLUDED.status,
    ip_address = EXCLUDED.ip_address,
    user_agent = EXCLUDED.user_agent,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

UPDATE edu_forum_post p
SET
    reply_count = r.reply_count,
    last_reply_id = r.last_reply_id,
    last_reply_time = r.last_reply_time,
    last_reply_user_id = r.last_reply_user_id,
    updated_at = GREATEST(p.updated_at, r.last_reply_time)
FROM (
    SELECT
        post_id,
        count(*)::BIGINT AS reply_count,
        (array_agg(id ORDER BY created_at DESC))[1] AS last_reply_id,
        max(created_at) AS last_reply_time,
        (array_agg(sys_user_id ORDER BY created_at DESC))[1] AS last_reply_user_id
    FROM edu_forum_reply
    WHERE post_id IN (SELECT post_id FROM seed_forum_post)
      AND deleted = 0
    GROUP BY post_id
) r
WHERE p.id = r.post_id;

CREATE TEMP TABLE seed_notification ON COMMIT DROP AS
SELECT
    idx AS notification_idx,
    pg_temp.seed_uuid('system-notification-' || idx) AS notification_id,
    1 AS type,
    CASE idx
        WHEN 1 THEN '2026 virtual simulation test semester is open'
        WHEN 2 THEN 'Studio evidence reminder'
        WHEN 3 THEN 'Portfolio review week checklist'
        WHEN 4 THEN 'Target-device testing notice'
        ELSE 'Community discussion guidelines'
    END AS title,
    CASE idx
        WHEN 1 THEN 'The simulation semester data set is available. Use the seeded courses, discussions, questions, live classes, and notifications for end-to-end testing.'
        WHEN 2 THEN 'Please attach evidence to project decisions: screenshots, build notes, playtest observations, or short critique summaries.'
        WHEN 3 THEN 'Before portfolio review, verify that each demo has a clean start state, source acknowledgements, and known issue notes.'
        WHEN 4 THEN 'Run at least one test on the target classroom machine before marking an interactive scene ready.'
        ELSE 'Keep discussion feedback specific, respectful, and connected to observable project evidence.'
    END AS content,
    NULL::UUID AS sender_id,
    0 AS target_type,
    pg_temp.seed_ts('system-notification-created-' || idx) AS created_at
FROM generate_series(1, 5) AS idx
UNION ALL
SELECT
    2051,
    pg_temp.seed_uuid('zh-system-notification-1'),
    1,
    '中文虚拟仿真实训课程已上线',
    '新增五门中文课程，覆盖中文游戏原型、UE5国风虚拟制片、Blender数字文物、WebGPU智慧城市和互动音乐舞台。',
    NULL::UUID,
    0,
    pg_temp.seed_ts('zh-system-notification-created-1')
UNION ALL
SELECT
    CASE WHEN c.seq > 15 THEN 2000 + (c.seq - 15) * 10 + n.idx ELSE 100 + c.seq * 10 + n.idx END,
    pg_temp.seed_course_scoped_uuid('course-notification-', 'zh-course-notification-', c.seq, '-' || n.idx),
    2,
    CASE n.idx
        WHEN 1 THEN c.title || ': live studio checkpoint'
        ELSE c.title || ': capstone evidence request'
    END,
    CASE n.idx
        WHEN 1 THEN 'The next live studio will focus on ' || c.topic_a || '. Bring a small test case and one question about the current blocker.'
        ELSE 'Upload or prepare evidence for ' || c.capstone || ': expected behavior, actual behavior, and one revision priority.'
    END,
    pg_temp.seed_uuid('teacher-user-' || c.primary_teacher_seq),
    1,
    pg_temp.seed_ts('course-notification-created-' || c.seq || '-' || n.idx)
FROM seed_course c
CROSS JOIN (VALUES (1), (2)) AS n(idx);

INSERT INTO ntf_notification (
    id, type, title, content, sender_id, target_type, created_at, updated_at, deleted
)
SELECT
    notification_id,
    type,
    title,
    content,
    sender_id,
    target_type,
    created_at,
    LEAST(created_at + INTERVAL '20 minutes', TIMESTAMPTZ '2026-06-19 23:59:59+08'),
    0
FROM seed_notification
ON CONFLICT (id) DO UPDATE SET
    type = EXCLUDED.type,
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    sender_id = EXCLUDED.sender_id,
    target_type = EXCLUDED.target_type,
    updated_at = EXCLUDED.updated_at,
    deleted = 0;

INSERT INTO ntf_notification_target (id, notification_id, user_id, deleted)
SELECT
    pg_temp.seed_uuid(CASE WHEN n.notification_idx >= 2000 THEN 'zh-notification-target-' ELSE 'notification-target-student-' END || n.notification_idx || '-' || e.student_seq),
    n.notification_id,
    pg_temp.seed_uuid('student-user-' || e.student_seq),
    0
FROM seed_notification n
JOIN seed_course c ON n.notification_idx BETWEEN 100 + c.seq * 10 AND 100 + c.seq * 10 + 9
JOIN seed_enrollment e ON e.course_seq = c.seq
WHERE n.target_type = 1
  AND e.status <> 3
UNION ALL
SELECT
    pg_temp.seed_uuid(CASE WHEN n.notification_idx >= 2000 THEN 'zh-notification-target-' ELSE 'notification-target-teacher-' END || n.notification_idx || '-' || sct.teacher_seq),
    n.notification_id,
    pg_temp.seed_uuid('teacher-user-' || sct.teacher_seq),
    0
FROM seed_notification n
JOIN seed_course c ON n.notification_idx BETWEEN 100 + c.seq * 10 AND 100 + c.seq * 10 + 9
JOIN seed_course_teacher sct ON sct.course_seq = c.seq
WHERE n.target_type = 1
ON CONFLICT (notification_id, user_id) DO UPDATE SET
    deleted = 0;

INSERT INTO ntf_read_status (id, notification_id, user_id, read_at)
SELECT
    pg_temp.seed_uuid('notification-read-target-' || t.notification_id || '-' || t.user_id),
    t.notification_id,
    t.user_id,
    LEAST(n.created_at + (
        floor(pg_temp.seed_rand('notification-read-target-' || t.notification_id || '-' || t.user_id) * 7200)
        * INTERVAL '1 second'
    ), TIMESTAMPTZ '2026-06-19 23:59:59+08')
FROM ntf_notification_target t
JOIN ntf_notification n ON n.id = t.notification_id
WHERE t.notification_id IN (SELECT notification_id FROM seed_notification)
  AND t.deleted = 0
  AND pg_temp.seed_rand('read-target-' || t.notification_id || '-' || t.user_id) < 0.56
UNION ALL
SELECT
    pg_temp.seed_uuid('notification-read-broadcast-' || n.notification_id || '-' || u.user_id),
    n.notification_id,
    u.user_id,
    LEAST(stored.created_at + (
        floor(pg_temp.seed_rand('notification-read-broadcast-' || n.notification_id || '-' || u.user_id) * 7200)
        * INTERVAL '1 second'
    ), TIMESTAMPTZ '2026-06-19 23:59:59+08')
FROM seed_notification n
JOIN ntf_notification stored ON stored.id = n.notification_id
CROSS JOIN (
    SELECT pg_temp.seed_uuid('teacher-user-' || seq) AS user_id FROM seed_teacher
    UNION ALL
    SELECT pg_temp.seed_uuid('student-user-' || seq) FROM seed_student
) u
WHERE n.target_type = 0
  AND pg_temp.seed_rand('read-broadcast-' || n.notification_id || '-' || u.user_id) < 0.36
ON CONFLICT (notification_id, user_id) DO UPDATE SET
    read_at = EXCLUDED.read_at;

SELECT 'teachers' AS metric, count(*)::TEXT AS value
FROM edu_teacher
WHERE user_id IN (SELECT pg_temp.seed_uuid('teacher-user-' || seq) FROM seed_teacher)
UNION ALL
SELECT 'students', count(*)::TEXT
FROM edu_student
WHERE user_id IN (SELECT pg_temp.seed_uuid('student-user-' || seq) FROM seed_student)
UNION ALL
SELECT 'courses_total', count(*)::TEXT
FROM edu_course
WHERE id IN (SELECT pg_temp.seed_course_uuid(seq) FROM seed_course)
UNION ALL
SELECT 'courses_public', count(*)::TEXT
FROM edu_course
WHERE id IN (SELECT pg_temp.seed_course_uuid(seq) FROM seed_course)
  AND is_public = 1
UNION ALL
SELECT 'courses_private', count(*)::TEXT
FROM edu_course
WHERE id IN (SELECT pg_temp.seed_course_uuid(seq) FROM seed_course)
  AND is_public = 0
UNION ALL
SELECT 'chapters', count(*)::TEXT
FROM edu_chapter
WHERE id IN (SELECT chapter_id FROM seed_chapter)
UNION ALL
SELECT 'question_banks', count(*)::TEXT
FROM edu_question_bank
WHERE id IN (
    SELECT pg_temp.seed_course_scoped_uuid('bank-', 'zh-bank-', c.seq, '-' || b.bank_idx)
    FROM seed_course c
    CROSS JOIN seed_bank_template b
)
UNION ALL
SELECT 'questions', count(*)::TEXT
FROM edu_question
WHERE id IN (SELECT question_id FROM seed_question)
UNION ALL
SELECT 'class_sessions', count(*)::TEXT
FROM edu_class_session
WHERE id IN (SELECT session_id FROM seed_class_session)
UNION ALL
SELECT 'assistant_invitations_accepted', count(*)::TEXT
FROM edu_course_invitation
WHERE id IN (
    SELECT pg_temp.seed_course_scoped_uuid('accepted-assistant-invitation-', 'zh-assistant-invite-', course_seq, '-' || teacher_seq)
    FROM seed_course_teacher
    WHERE primary_role = FALSE
)
UNION ALL
SELECT 'enrollments', count(*)::TEXT
FROM edu_enrollment
WHERE id IN (
    SELECT pg_temp.seed_course_scoped_uuid('enrollment-', 'zh-enrollment-', course_seq, '-' || student_seq)
    FROM seed_enrollment
)
UNION ALL
SELECT 'practice_sessions', count(*)::TEXT
FROM edu_practice_session
WHERE id IN (SELECT session_id FROM seed_practice_session)
UNION ALL
SELECT 'live_practice_groups', count(*)::TEXT
FROM edu_live_practice_group
WHERE id IN (SELECT group_id FROM seed_live_group)
UNION ALL
SELECT 'forum_posts', count(*)::TEXT
FROM edu_forum_post
WHERE id IN (SELECT post_id FROM seed_forum_post)
UNION ALL
SELECT 'notifications', count(*)::TEXT
FROM ntf_notification
WHERE id IN (SELECT notification_id FROM seed_notification);

COMMIT;
