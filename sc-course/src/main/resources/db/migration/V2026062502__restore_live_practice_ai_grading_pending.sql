-- ============================================================
-- V2026062502: Restore AI grading submissions failed by read-side timeout
-- ============================================================

UPDATE edu_live_practice_submission
SET ai_grading_status = 'PENDING',
    ai_grading_error = NULL,
    ai_graded_at = NULL
WHERE ai_grading_status = 'FAILED'
  AND ai_grading_error = 'AI 批改超时，请确认 AI 服务正在运行后重新提交或联系教师处理';
