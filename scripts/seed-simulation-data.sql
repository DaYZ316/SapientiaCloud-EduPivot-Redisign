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

WITH selected_courses AS (
    SELECT id, title, teacher_id
    FROM edu_course
    WHERE deleted = 0
      AND title IN ('Machine Learning Studio', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA')
),
assistant_candidates AS (
    SELECT c.id AS course_id, t.user_id AS teacher_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(t.user_id::text || c.id::text)) AS rn
    FROM selected_courses c
    JOIN edu_teacher t ON t.deleted = 0 AND t.user_id <> c.teacher_id
)
INSERT INTO edu_course_teacher (id, course_id, teacher_id, created_at)
SELECT demo_uuidv7(), course_id, teacher_id, now()
FROM assistant_candidates
WHERE rn <= 2
ON CONFLICT (course_id, teacher_id) DO NOTHING;

WITH chapter_seed(course_title, sort_order, chapter_name, description, content) AS (
    VALUES
        ('Machine Learning Studio', 1, 'Module 1: Problem Framing', 'Define the learning task, outcome metric, and baseline.', 'This module walks through problem statements, data assumptions, evaluation metrics, and a simple baseline workflow for studio projects.'),
        ('Machine Learning Studio', 2, 'Module 2: Data Preparation', 'Build reproducible datasets and feature pipelines.', 'Students clean raw records, create train-validation splits, and document leakage risks before model training.'),
        ('Machine Learning Studio', 3, 'Module 3: Model Evaluation', 'Compare models with practical validation techniques.', 'This module covers cross-validation, confusion matrices, calibration, error slicing, and model cards.'),
        ('Machine Learning Studio', 4, 'Module 4: Deployment Review', 'Package a model for a small production-like demo.', 'Teams prepare inference endpoints, monitoring notes, and a final presentation that explains tradeoffs.'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 1, U&'\7B2C1\7AE0 \667A\80FD\7CFB\7EDF\57FA\7840', U&'\7406\89E3\667A\80FD\4EE3\7406\3001\73AF\5883\548C\95EE\9898\5EFA\6A21\3002', U&'\672C\7AE0\4ECB\7ECD\667A\80FD\7CFB\7EDF\7684\57FA\672C\7EC4\6210\3001\611F\77E5-\51B3\7B56-\884C\52A8\5FAA\73AF\FF0C\4EE5\53CA\8BFE\7A0B\9879\76EE\7684\8BC4\4F30\65B9\5F0F\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 2, U&'\7B2C2\7AE0 \641C\7D22\4E0E\63A8\7406', U&'\5B66\4E60\72B6\6001\7A7A\95F4\641C\7D22\3001\542F\53D1\5F0F\51FD\6570\548C\7B80\5355\63A8\7406\65B9\6CD5\3002', U&'\901A\8FC7\8DEF\5F84\89C4\5212\548C\7EA6\675F\6C42\89E3\793A\4F8B\7406\89E3\641C\7D22\7B56\7565\7684\4EE3\4EF7\3001\5B8C\5907\6027\548C\6700\4F18\6027\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 3, U&'\7B2C3\7AE0 \673A\5668\5B66\4E60\5165\95E8', U&'\638C\63E1\76D1\7763\5B66\4E60\6D41\7A0B\3001\8BAD\7EC3\96C6\5212\5206\548C\6A21\578B\8BC4\4F30\6307\6807\3002', U&'\672C\7AE0\7528\5C0F\578B\6570\636E\96C6\6F14\793A\7279\5F81\3001\6807\7B7E\3001\8BAD\7EC3\3001\9A8C\8BC1\548C\8BEF\5DEE\5206\6790\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 4, U&'\7B2C4\7AE0 \9879\76EE\5B9E\8DF5', U&'\5B8C\6210\4E00\4E2A\53EF\6F14\793A\7684\667A\80FD\5E94\7528\539F\578B\3002', U&'\56F4\7ED5\771F\5B9E\573A\666F\5B9A\4E49\95EE\9898\3001\8BBE\8BA1\6570\636E\6D41\3001\5B9E\73B0\539F\578B\5E76\8FDB\884C\8BFE\5802\5C55\793A\3002')
)
INSERT INTO edu_chapter (
    id, course_id, teacher_id, chapter_name, description, content,
    attachment_urls, sort_order, status, view_count, like_count,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), c.id, c.teacher_id, s.chapter_name, s.description, s.content,
    '[]'::jsonb, s.sort_order, 1, 10 + s.sort_order * 7, 2 + s.sort_order,
    now(), now(), 0
FROM chapter_seed s
JOIN edu_course c ON c.title = s.course_title AND c.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_chapter existing
    WHERE existing.course_id = c.id
      AND existing.chapter_name = s.chapter_name
      AND existing.deleted = 0
);

WITH selected_courses AS (
    SELECT id, title, teacher_id
    FROM edu_course
    WHERE deleted = 0
      AND title IN ('Machine Learning Studio', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA')
),
student_pool AS (
    SELECT u.id AS user_id
    FROM auth_users u
    WHERE u.email LIKE 'student%@demo.edupivot.local'
),
ranked_students AS (
    SELECT c.id AS course_id, s.user_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(s.user_id::text || c.id::text)) AS rn
    FROM selected_courses c
    CROSS JOIN student_pool s
)
INSERT INTO edu_enrollment (
    id, course_id, student_id, status, enrolled_at, completed_at,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), course_id, user_id, 1,
    now() - ((rn % 12) || ' days')::interval,
    NULL,
    now(), now(), 0
FROM ranked_students
WHERE rn <= CASE
    WHEN course_id = (SELECT id FROM selected_courses WHERE title = 'Machine Learning Studio') THEN 18
    ELSE 16
END
ON CONFLICT (course_id, student_id) DO NOTHING;

WITH post_seed(course_title, post_no, title, content, tags, is_top, is_essence) AS (
    VALUES
        ('Machine Learning Studio', 1, 'How should we choose a baseline model?', 'For the first studio checkpoint, should the baseline be a simple logistic regression model, or is a decision tree acceptable if the dataset is tabular?', '["baseline","evaluation"]'::jsonb, 1, 1),
        ('Machine Learning Studio', 2, 'Dataset split strategy for imbalanced labels', 'Our positive class is only around 12 percent. I am considering stratified sampling for validation; does that match the course expectation?', '["data","validation"]'::jsonb, 0, 1),
        ('Machine Learning Studio', 3, 'Model card draft feedback', 'I uploaded a draft model card outline. I am unsure how detailed the limitation section should be for a classroom demo.', '["model-card","project"]'::jsonb, 0, 0),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 1, U&'\5982\4F55\533A\5206\667A\80FD\4EE3\7406\548C\666E\901A\7A0B\5E8F\FF1F', U&'\6211\7406\89E3\667A\80FD\4EE3\7406\9700\8981\611F\77E5\73AF\5883\5E76\91C7\53D6\884C\52A8\FF0C\4F46\5982\679C\4E00\4E2A\7A0B\5E8F\53EA\662F\6839\636E\89C4\5219\54CD\5E94\8F93\5165\FF0C\5B83\7B97\667A\80FD\4EE3\7406\5417\FF1F', '["agent","concept"]'::jsonb, 1, 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 2, U&'\9879\76EE\9009\9898\53EF\4EE5\505A\6821\56ED\52A9\624B\5417\FF1F', U&'\60F3\505A\4E00\4E2A\80FD\56DE\7B54\6821\56ED\670D\52A1\95EE\9898\7684\5C0F\52A9\624B\FF0C\6570\636E\89C4\6A21\4E0D\5927\FF0C\8FD9\4E2A\9009\9898\662F\5426\5408\9002\FF1F', '["project","idea"]'::jsonb, 0, 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 3, U&'\542F\53D1\5F0F\51FD\6570\4E00\5B9A\8981\4F4E\4F30\4EE3\4EF7\5417\FF1F', U&'\5728 A* \641C\7D22\91CC\FF0C\5982\679C\542F\53D1\5F0F\51FD\6570\5076\5C14\9AD8\4F30\FF0C\4F1A\5177\4F53\5F71\54CD\4EC0\4E48\FF1F', '["search","heuristic"]'::jsonb, 0, 0)
),
authors AS (
    SELECT c.id AS course_id, u.id AS user_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(u.id::text || c.id::text || 'post')) AS rn
    FROM edu_course c
    JOIN auth_users u ON u.email LIKE 'student%@demo.edupivot.local'
    WHERE c.deleted = 0
      AND c.title IN ('Machine Learning Studio', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA')
),
chapter_pick AS (
    SELECT course_id, id AS chapter_id,
           row_number() OVER (PARTITION BY course_id ORDER BY sort_order) AS rn
    FROM edu_chapter
    WHERE deleted = 0
)
INSERT INTO edu_forum_post (
    id, course_id, sys_user_id, title, content, post_type, is_anonymous,
    attachment_urls, image_urls, tags, view_count, like_count, reply_count,
    share_count, is_top, is_essence, is_locked, status, chapter_id,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), c.id, a.user_id, p.title, p.content, 0, 0,
    '[]'::jsonb, '[]'::jsonb, p.tags, 30 + p.post_no * 13, 2 + p.post_no, 0,
    p.post_no % 2, p.is_top, p.is_essence, 0, 0, cp.chapter_id,
    now() - ((8 - p.post_no) || ' days')::interval,
    now(), 0
FROM post_seed p
JOIN edu_course c ON c.title = p.course_title AND c.deleted = 0
JOIN authors a ON a.course_id = c.id AND a.rn = p.post_no
LEFT JOIN chapter_pick cp ON cp.course_id = c.id AND cp.rn = p.post_no
WHERE NOT EXISTS (
    SELECT 1 FROM edu_forum_post existing
    WHERE existing.course_id = c.id
      AND existing.title = p.title
      AND existing.deleted = 0
);

WITH reply_seed(course_title, post_title, floor_number, content, accepted) AS (
    VALUES
        ('Machine Learning Studio', 'How should we choose a baseline model?', 1, 'Start with the simplest defensible baseline. Logistic regression is a good default if your features are tabular and already encoded.', 1),
        ('Machine Learning Studio', 'How should we choose a baseline model?', 2, 'A decision tree is fine as a comparison model, but keep one very simple baseline for the report.', 0),
        ('Machine Learning Studio', 'Dataset split strategy for imbalanced labels', 1, 'Use stratified splitting and report both precision-recall and confusion matrix slices.', 1),
        ('Machine Learning Studio', 'Model card draft feedback', 1, 'For the limitation section, name the data boundary, likely failure cases, and what the demo does not claim to solve.', 0),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\5982\4F55\533A\5206\667A\80FD\4EE3\7406\548C\666E\901A\7A0B\5E8F\FF1F', 1, U&'\5173\952E\662F\662F\5426\6709\73AF\5883\72B6\6001\3001\76EE\6807\548C\884C\52A8\53CD\9988\FF0C\666E\901A\89C4\5219\7A0B\5E8F\4E5F\53EF\4EE5\4F5C\4E3A\5F88\5F31\7684\4EE3\7406\6765\5206\6790\3002', 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\9879\76EE\9009\9898\53EF\4EE5\505A\6821\56ED\52A9\624B\5417\FF1F', 1, U&'\53EF\4EE5\FF0C\5EFA\8BAE\628A\8303\56F4\7F29\5C0F\5230\4E00\4E2A\5177\4F53\573A\666F\FF0C\6BD4\5982\56FE\4E66\9986\670D\52A1\6216\8BFE\7A0B\63D0\9192\3002', 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\542F\53D1\5F0F\51FD\6570\4E00\5B9A\8981\4F4E\4F30\4EE3\4EF7\5417\FF1F', 1, U&'\9AD8\4F30\4F1A\7834\574F\6700\4F18\6027\4FDD\8BC1\FF0C\4F46\5728\67D0\4E9B\5DE5\7A0B\573A\666F\4E2D\53EF\80FD\6362\6765\66F4\5FEB\901F\5EA6\3002', 0)
),
reply_authors AS (
    SELECT c.id AS course_id, u.id AS user_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(u.id::text || c.id::text || 'reply')) AS rn
    FROM edu_course c
    JOIN auth_users u ON u.email LIKE 'student%@demo.edupivot.local'
    WHERE c.deleted = 0
      AND c.title IN ('Machine Learning Studio', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA')
)
INSERT INTO edu_forum_reply (
    id, post_id, course_id, sys_user_id, content, parent_reply_id,
    reply_to_user_id, is_anonymous, attachment_urls, image_urls,
    like_count, reply_count, is_accepted, floor_number, status,
    ip_address, user_agent, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), p.id, c.id, a.user_id, r.content, NULL,
    p.sys_user_id, 0, '[]'::jsonb, '[]'::jsonb,
    r.floor_number + 1, 0, r.accepted, r.floor_number, 0,
    '127.0.0.1', 'simulation-seed', now() - ((4 - r.floor_number) || ' days')::interval, now(), 0
FROM reply_seed r
JOIN edu_course c ON c.title = r.course_title AND c.deleted = 0
JOIN edu_forum_post p ON p.course_id = c.id AND p.title = r.post_title AND p.deleted = 0
JOIN reply_authors a ON a.course_id = c.id AND a.rn = r.floor_number + 4
WHERE NOT EXISTS (
    SELECT 1 FROM edu_forum_reply existing
    WHERE existing.post_id = p.id
      AND existing.content = r.content
      AND existing.deleted = 0
);

UPDATE edu_forum_post p
SET reply_count = counts.reply_count,
    last_reply_id = counts.last_reply_id,
    last_reply_time = counts.last_reply_time,
    last_reply_user_id = counts.last_reply_user_id,
    updated_at = now()
FROM (
    SELECT DISTINCT ON (r.post_id)
        r.post_id,
        count(*) OVER (PARTITION BY r.post_id) AS reply_count,
        r.id AS last_reply_id,
        r.created_at AS last_reply_time,
        r.sys_user_id AS last_reply_user_id
    FROM edu_forum_reply r
    WHERE r.deleted = 0
    ORDER BY r.post_id, r.created_at DESC
) counts
WHERE p.id = counts.post_id;

WITH bank_seed(course_title, bank_name, description, tags, difficulty) AS (
    VALUES
        ('Machine Learning Studio', 'Studio Practice Bank', 'Practice questions for model evaluation, dataset splits, and deployment readiness.', '["ml","practice","studio"]'::jsonb, 2),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA\7EC3\4E60\9898\5E93', U&'\8986\76D6\667A\80FD\4EE3\7406\3001\641C\7D22\3001\673A\5668\5B66\4E60\548C\9879\76EE\5B9E\8DF5\7684\57FA\7840\7EC3\4E60\3002', '["ai","practice","foundation"]'::jsonb, 2)
)
INSERT INTO edu_question_bank (
    id, course_id, sys_user_id, bank_name, description, bank_type,
    tags, difficulty, created_at, updated_at, deleted
)
SELECT demo_uuidv7(), c.id, c.teacher_id, b.bank_name, b.description, 0,
       b.tags, b.difficulty, now(), now(), 0
FROM bank_seed b
JOIN edu_course c ON c.title = b.course_title AND c.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_bank existing
    WHERE existing.course_id = c.id
      AND existing.bank_name = b.bank_name
      AND existing.deleted = 0
);

WITH question_seed(course_title, bank_name, q_no, title, content, q_type, difficulty, score, estimated_time, tags) AS (
    VALUES
        ('Machine Learning Studio', 'Studio Practice Bank', 1, 'Which metric is usually most informative for imbalanced binary classification?', 'Pick the best default metric for a rare-positive classification task.', 0, 2, 5.00, 4, '["metrics"]'::jsonb),
        ('Machine Learning Studio', 'Studio Practice Bank', 2, 'A validation set should be used for final unbiased reporting.', 'Decide whether this statement is true or false.', 2, 1, 4.00, 3, '["validation"]'::jsonb),
        ('Machine Learning Studio', 'Studio Practice Bank', 3, 'Name one common source of data leakage.', 'Provide a short phrase or sentence.', 3, 2, 6.00, 5, '["data"]'::jsonb),
        ('Machine Learning Studio', 'Studio Practice Bank', 4, 'What should a model card include for a classroom demo?', 'Summarize the most important sections.', 4, 2, 10.00, 8, '["model-card"]'::jsonb),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA\7EC3\4E60\9898\5E93', 1, U&'\667A\80FD\4EE3\7406\901A\5E38\5305\542B\54EA\4E09\4E2A\6838\5FC3\73AF\8282\FF1F', U&'\9009\62E9\6700\7B26\5408\8BFE\7A0B\5B9A\4E49\7684\4E00\9879\3002', 0, 1, 5.00, 4, '["agent"]'::jsonb),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA\7EC3\4E60\9898\5E93', 2, U&'A* \641C\7D22\4E2D\7684\542F\53D1\5F0F\51FD\6570\7528\4E8E\4F30\8BA1\4EC0\4E48\FF1F', U&'\586B\5199\6700\51C6\786E\7684\7B54\6848\3002', 3, 2, 6.00, 5, '["search"]'::jsonb),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA\7EC3\4E60\9898\5E93', 3, U&'\8BAD\7EC3\96C6\548C\9A8C\8BC1\96C6\5E94\8BE5\5B8C\5168\76F8\540C\3002', U&'\5224\65AD\8BE5\8BF4\6CD5\662F\5426\6B63\786E\3002', 2, 1, 4.00, 3, '["ml"]'::jsonb),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA\7EC3\4E60\9898\5E93', 4, U&'\7B80\8FF0\4E00\4E2A\9002\5408\672C\8BFE\7A0B\9879\76EE\7684\667A\80FD\5E94\7528\573A\666F\3002', U&'\8BF4\660E\95EE\9898\3001\8F93\5165\6570\636E\548C\9884\671F\8F93\51FA\3002', 4, 2, 10.00, 8, '["project"]'::jsonb)
)
INSERT INTO edu_question (
    id, question_bank_id, course_id, sys_user_id, question_title,
    question_content, question_type, difficulty, score, estimated_time,
    tags, image_urls, allow_partial_credit, view_count, status,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), qb.id, c.id, c.teacher_id, q.title,
    q.content, q.q_type, q.difficulty, q.score, q.estimated_time,
    q.tags, '[]'::jsonb, CASE WHEN q.q_type IN (3, 4) THEN 1 ELSE 0 END, 0, 1,
    now(), now(), 0
FROM question_seed q
JOIN edu_course c ON c.title = q.course_title AND c.deleted = 0
JOIN edu_question_bank qb ON qb.course_id = c.id AND qb.bank_name = q.bank_name AND qb.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question existing
    WHERE existing.question_bank_id = qb.id
      AND existing.question_title = q.title
      AND existing.deleted = 0
);

WITH option_seed(course_title, question_title, label, content, correct, explanation) AS (
    VALUES
        ('Machine Learning Studio', 'Which metric is usually most informative for imbalanced binary classification?', 'A', 'Accuracy only', 0, 'Accuracy can hide rare positive errors.'),
        ('Machine Learning Studio', 'Which metric is usually most informative for imbalanced binary classification?', 'B', 'Precision-recall metrics', 1, 'Precision and recall are useful when positives are rare.'),
        ('Machine Learning Studio', 'Which metric is usually most informative for imbalanced binary classification?', 'C', 'Training loss only', 0, 'Training loss does not show validation behavior by itself.'),
        ('Machine Learning Studio', 'Which metric is usually most informative for imbalanced binary classification?', 'D', 'Number of model parameters', 0, 'Parameter count is not a classification metric.'),
        ('Machine Learning Studio', 'A validation set should be used for final unbiased reporting.', 'A', 'True', 0, 'The final unbiased report should use a held-out test set.'),
        ('Machine Learning Studio', 'A validation set should be used for final unbiased reporting.', 'B', 'False', 1, 'Validation supports tuning; a separate test set is better for final reporting.'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\4EE3\7406\901A\5E38\5305\542B\54EA\4E09\4E2A\6838\5FC3\73AF\8282\FF1F', 'A', U&'\611F\77E5\3001\51B3\7B56\3001\884C\52A8', 1, U&'\667A\80FD\4EE3\7406\7684\6838\5FC3\662F\6301\7EED\611F\77E5\73AF\5883\3001\505A\51FA\51B3\7B56\5E76\6267\884C\884C\52A8\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\4EE3\7406\901A\5E38\5305\542B\54EA\4E09\4E2A\6838\5FC3\73AF\8282\FF1F', 'B', U&'\7F16\8BD1\3001\94FE\63A5\3001\90E8\7F72', 0, U&'\8FD9\662F\8F6F\4EF6\6784\5EFA\6D41\7A0B\FF0C\4E0D\662F\667A\80FD\4EE3\7406\5FAA\73AF\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\4EE3\7406\901A\5E38\5305\542B\54EA\4E09\4E2A\6838\5FC3\73AF\8282\FF1F', 'C', U&'\767B\5F55\3001\6CE8\518C\3001\652F\4ED8', 0, U&'\8FD9\662F\4E1A\52A1\529F\80FD\FF0C\4E0D\662F\4EE3\7406\7ED3\6784\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\667A\80FD\4EE3\7406\901A\5E38\5305\542B\54EA\4E09\4E2A\6838\5FC3\73AF\8282\FF1F', 'D', U&'\6392\5E8F\3001\5206\9875\3001\7F13\5B58', 0, U&'\8FD9\4E9B\662F\5E38\89C1\7CFB\7EDF\5B9E\73B0\6280\672F\FF0C\4E0D\662F\667A\80FD\4EE3\7406\5B9A\4E49\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\8BAD\7EC3\96C6\548C\9A8C\8BC1\96C6\5E94\8BE5\5B8C\5168\76F8\540C\3002', 'A', U&'\6B63\786E', 0, U&'\5982\679C\5B8C\5168\76F8\540C\FF0C\65E0\6CD5\8BC4\4F30\6CDB\5316\6548\679C\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\8BAD\7EC3\96C6\548C\9A8C\8BC1\96C6\5E94\8BE5\5B8C\5168\76F8\540C\3002', 'B', U&'\9519\8BEF', 1, U&'\9A8C\8BC1\96C6\7528\4E8E\8BC4\4F30\6CDB\5316\6548\679C\FF0C\4E0D\80FD\4E0E\8BAD\7EC3\96C6\5B8C\5168\76F8\540C\3002')
)
INSERT INTO edu_question_option (
    id, question_id, course_id, option_content, option_label,
    is_correct, score, image_urls, explanation, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), q.id, q.course_id, o.content, o.label,
    o.correct, CASE WHEN o.correct = 1 THEN q.score ELSE 0 END,
    '[]'::jsonb, o.explanation, now(), now(), 0
FROM option_seed o
JOIN edu_course c ON c.title = o.course_title AND c.deleted = 0
JOIN edu_question q ON q.course_id = c.id AND q.question_title = o.question_title AND q.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_option existing
    WHERE existing.question_id = q.id
      AND existing.option_label = o.label
      AND existing.deleted = 0
);

WITH answer_seed(course_title, question_title, answer, explanation, score, sort_order) AS (
    VALUES
        ('Machine Learning Studio', 'Name one common source of data leakage.', 'Using future information, target-derived features, or duplicate records across train and validation splits.', 'Any answer naming a leakage path from validation/test data or future labels is acceptable.', 6.00, 1),
        ('Machine Learning Studio', 'What should a model card include for a classroom demo?', 'Purpose, data summary, evaluation metrics, limitations, ethical risks, and usage boundaries.', 'The answer should show both performance and responsible-use context.', 10.00, 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'A* \641C\7D22\4E2D\7684\542F\53D1\5F0F\51FD\6570\7528\4E8E\4F30\8BA1\4EC0\4E48\FF1F', U&'\4ECE\5F53\524D\72B6\6001\5230\76EE\6807\72B6\6001\7684\5269\4F59\4EE3\4EF7', U&'\542F\53D1\5F0F\51FD\6570\4F30\8BA1\5269\4F59\4EE3\4EF7\FF0C\7528\4E8E\6307\5BFC\641C\7D22\4F18\5148\7EA7\3002', 6.00, 1),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', U&'\7B80\8FF0\4E00\4E2A\9002\5408\672C\8BFE\7A0B\9879\76EE\7684\667A\80FD\5E94\7528\573A\666F\3002', U&'\793A\4F8B\FF1A\6821\56ED\95EE\7B54\52A9\624B\FF0C\8F93\5165\4E3A\5B66\751F\95EE\9898\548C\77E5\8BC6\5E93\5185\5BB9\FF0C\8F93\51FA\4E3A\53EF\89E3\91CA\7684\670D\52A1\5EFA\8BAE\3002', U&'\9700\8981\8BF4\660E\573A\666F\3001\8F93\5165\548C\9884\671F\8F93\51FA\3002', 10.00, 1)
)
INSERT INTO edu_question_answer (
    id, question_id, course_id, answer_content, explanation,
    score, sort_order, created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), q.id, q.course_id, a.answer, a.explanation,
    a.score, a.sort_order, now(), now(), 0
FROM answer_seed a
JOIN edu_course c ON c.title = a.course_title AND c.deleted = 0
JOIN edu_question q ON q.course_id = c.id AND q.question_title = a.question_title AND q.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_question_answer existing
    WHERE existing.question_id = q.id
      AND existing.sort_order = a.sort_order
      AND existing.deleted = 0
);

WITH assistant_candidates AS (
    SELECT c.id AS course_id, t.user_id AS teacher_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(t.user_id::text || c.id::text || 'all-assistants')) AS rn
    FROM edu_course c
    JOIN edu_teacher t ON t.deleted = 0 AND t.user_id <> c.teacher_id
    WHERE c.deleted = 0
),
missing_assistants AS (
    SELECT a.course_id, a.teacher_id
    FROM assistant_candidates a
    WHERE a.rn <= 2
      AND NOT EXISTS (
          SELECT 1
          FROM edu_course_teacher existing
          WHERE existing.course_id = a.course_id
            AND existing.teacher_id = a.teacher_id
      )
)
INSERT INTO edu_course_teacher (id, course_id, teacher_id, created_at)
SELECT demo_uuidv7(), course_id, teacher_id, now()
FROM missing_assistants
ON CONFLICT (course_id, teacher_id) DO NOTHING;

WITH student_pool AS (
    SELECT id AS student_id
    FROM auth_users
    WHERE email LIKE 'student%@demo.edupivot.local'
),
ranked_students AS (
    SELECT c.id AS course_id, s.student_id,
           row_number() OVER (PARTITION BY c.id ORDER BY md5(s.student_id::text || c.id::text || 'all-enrollments')) AS rn,
           18 + (abs(('x' || substr(md5(c.id::text), 1, 8))::bit(32)::int) % 9) AS target_count
    FROM edu_course c
    CROSS JOIN student_pool s
    WHERE c.deleted = 0
)
INSERT INTO edu_enrollment (
    id, course_id, student_id, status, enrolled_at, completed_at,
    created_at, updated_at, deleted
)
SELECT
    demo_uuidv7(), course_id, student_id, 1,
    now() - ((rn % 21) || ' days')::interval,
    NULL,
    now(), now(), 0
FROM ranked_students
WHERE rn <= target_count
ON CONFLICT (course_id, student_id) DO NOTHING;

WITH detailed_chapter_seed(course_title, sort_order, chapter_name, description, content) AS (
    VALUES
        ('Machine Learning Studio', 1, 'Module 1: Supervised Learning Foundations', 'Frame a supervised learning problem and establish a simple baseline.', 'Based on common topics in Stanford CS229-style machine learning syllabi, this module starts with supervised learning, loss functions, model assumptions, and the purpose of a baseline. Students turn a vague product question into a prediction target, define input features and labels, choose a metric, and implement a minimal model before trying more complex approaches. Studio work includes a data dictionary, a target definition review, and a short baseline report explaining what the model can and cannot claim.'),
        ('Machine Learning Studio', 2, 'Module 2: Data Preparation and Leakage Control', 'Prepare reproducible train, validation, and test datasets.', 'This module follows practical guidance from production-oriented machine learning courses and Google ML training material: data quality matters before model complexity. Students build repeatable splits, check label balance, handle missing values, encode categorical features, and document leakage risks. The lab asks each team to identify at least three failure modes in the dataset, explain whether stratification is needed, and produce a small validation protocol that another team could reproduce.'),
        ('Machine Learning Studio', 3, 'Module 3: Evaluation, Error Analysis, and Model Cards', 'Evaluate models beyond a single headline score.', 'Students compare validation metrics, confusion matrices, precision-recall tradeoffs, calibration, and error slices. The chapter also introduces model cards as a compact reporting format: intended use, data summary, evaluation results, limitations, ethical risks, and monitoring notes. The goal is to make students justify model choices with evidence rather than leaderboard instinct. The studio checkpoint requires a model card draft and a concrete plan for improving the most important error segment.'),
        ('Machine Learning Studio', 4, 'Module 4: Deployment Readiness and Monitoring', 'Package the model into a small, reviewable demo.', 'The final module moves from notebook results to a small production-like workflow: inference interface, input validation, model/version metadata, latency expectations, and monitoring signals. Students discuss dataset shift, rollback plans, human review, and when a model should not answer. The final demo is evaluated on reproducibility, clarity of tradeoffs, responsible-use boundaries, and whether the system behavior matches the original problem framing.'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 1, U&'\7B2C1\7AE0 \667A\80FD\4F53\3001\73AF\5883\4E0E\95EE\9898\5EFA\6A21', U&'\4ECE\7406\6027\667A\80FD\4F53\51FA\53D1\FF0C\5EFA\7ACB\73AF\5883-\611F\77E5-\884C\52A8-\76EE\6807\7684\5206\6790\6846\67B6\3002', U&'\53C2\8003 MIT 6.034 \548C Berkeley CS188 \7B49 AI \5BFC\8BBA\8BFE\7684\5E38\89C1\4E3B\9898\FF0C\672C\7AE0\8BA8\8BBA\667A\80FD\4F53\5982\4F55\5728\73AF\5883\4E2D\6839\636E\76EE\6807\9009\62E9\884C\52A8\3002\5185\5BB9\5305\62EC\4EFB\52A1\73AF\5883\7279\5F81\3001\72B6\6001\8868\793A\3001\52A8\4F5C\4E0E\8F6C\79FB\6A21\578B\3001\4EE3\4EF7\51FD\6570\548C\76EE\6807\6D4B\8BD5\3002\8BFE\5802\7EC3\4E60\4F1A\628A\6821\56ED\5BFC\822A\3001\8BFE\7A0B\95EE\7B54\548C\81EA\52A8\6392\8BFE\62C6\89E3\6210\72B6\6001\3001\52A8\4F5C\3001\7EA6\675F\4E0E\8BC4\4EF7\6307\6807\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 2, U&'\7B2C2\7AE0 \641C\7D22\3001\7EA6\675F\6EE1\8DB3\4E0E\535A\5F08', U&'\5B66\4E60\65E0\4FE1\606F\641C\7D22\3001\542F\53D1\5F0F\641C\7D22\3001CSP \548C\5BF9\6297\641C\7D22\3002', U&'\672C\7AE0\56F4\7ED5\7ECF\5178 AI \8BFE\7A0B\7684\641C\7D22\4E3B\7EBF\5C55\5F00\FF1A\5BBD\5EA6\4F18\5148\3001\6DF1\5EA6\4F18\5148\3001\4E00\81F4\4EE3\4EF7\641C\7D22\3001A* \641C\7D22\3001\5C40\90E8\641C\7D22\548C\7EA6\675F\6EE1\8DB3\95EE\9898\3002\5B66\751F\9700\8981\6BD4\8F83\5B8C\5907\6027\3001\6700\4F18\6027\3001\65F6\95F4\590D\6742\5EA6\548C\7A7A\95F4\590D\6742\5EA6\3002\5B9E\9A8C\4F1A\4F7F\7528\5C0F\578B\5730\56FE\3001\516B\6570\7801\6216\8BFE\7A0B\6392\73ED\6848\4F8B\5B9E\73B0\72B6\6001\6269\5C55\3001\542F\53D1\5F0F\51FD\6570\548C\526A\679D\7B56\7565\3002\7AE0\8282\6700\540E\5F15\5165\535A\5F08\6811\3001\6781\5C0F\5316\6781\5927\548C\8BC4\4EF7\51FD\6570\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 3, U&'\7B2C3\7AE0 \4E0D\786E\5B9A\6027\63A8\7406\4E0E\673A\5668\5B66\4E60\57FA\7840', U&'\7406\89E3\6982\7387\63A8\7406\3001\8D1D\53F6\65AF\7F51\7EDC\548C\76D1\7763\5B66\4E60\5165\95E8\3002', U&'\627F\63A5 CS188 \7B49\8BFE\7A0B\4E2D\5173\4E8E\4E0D\786E\5B9A\6027\548C\5B66\4E60\7684\5355\5143\FF0C\672C\7AE0\4ECB\7ECD\6982\7387\6A21\578B\3001\8D1D\53F6\65AF\7F51\7EDC\3001\6761\4EF6\72EC\7ACB\3001\51B3\7B56\6811\3001\7EBF\6027\5206\7C7B\5668\548C\57FA\7840\6A21\578B\8BC4\4F30\3002\91CD\70B9\4E0D\662F\8FFD\6C42\7B97\6CD5\6570\91CF\FF0C\800C\662F\8BA9\5B66\751F\7406\89E3\771F\5B9E\667A\80FD\7CFB\7EDF\4E3A\4EC0\4E48\8981\5904\7406\566A\58F0\3001\4E0D\5B8C\6574\4FE1\606F\548C\6CDB\5316\8BEF\5DEE\3002\5B9E\9A8C\8981\6C42\5B66\751F\5B8C\6210\4E00\6B21\5C0F\578B\5206\7C7B\4EFB\52A1\FF0C\8BF4\660E\7279\5F81\3001\6807\7B7E\3001\8BAD\7EC3\96C6\3001\9A8C\8BC1\96C6\548C\9519\8BEF\5206\6790\3002'),
        (U&'\667A\80FD\7CFB\7EDF\5BFC\8BBA', 4, U&'\7B2C4\7AE0 \4ECE\89C4\5212\5230\771F\5B9E\667A\80FD\7CFB\7EDF\9879\76EE', U&'\5C06\641C\7D22\3001\63A8\7406\548C\5B66\4E60\6574\5408\5230\53EF\6F14\793A\7684\539F\578B\4E2D\3002', U&'\6700\540E\4E00\7AE0\628A\524D\9762\7684\641C\7D22\3001\63A8\7406\548C\5B66\4E60\7EC4\5408\5230\9879\76EE\4E2D\3002\5185\5BB9\5305\62EC\4EFB\52A1\89C4\5212\3001\5F3A\5316\5B66\4E60\601D\60F3\3001\611F\77E5\4E0E\8BED\8A00\7406\89E3\7684\5E94\7528\8FB9\754C\3001\7CFB\7EDF\8BC4\4F30\3001\5931\6548\6848\4F8B\5206\6790\4EE5\53CA AI \5B89\5168\548C\516C\5E73\6027\7684\57FA\672C\8BA8\8BBA\3002\6700\7EC8\9879\76EE\9700\8981\5B9A\4E49\4E00\4E2A\771F\5B9E\573A\666F\FF0C\8BF4\660E\8F93\5165\6570\636E\3001\7528\6237\76EE\6807\3001\51B3\7B56\903B\8F91\3001\53CD\9988\673A\5236\548C\8BC4\4EF7\65B9\6CD5\FF0C\5E76\7528\539F\578B\5C55\793A\667A\80FD\4F53\5982\4F55\5728\7EA6\675F\4E0B\884C\52A8\3002')
)
UPDATE edu_chapter ch
SET chapter_name = s.chapter_name,
    description = s.description,
    content = s.content,
    updated_at = now()
FROM detailed_chapter_seed s
JOIN edu_course c ON c.title = s.course_title AND c.deleted = 0
WHERE ch.course_id = c.id
  AND ch.sort_order = s.sort_order
  AND ch.deleted = 0;
