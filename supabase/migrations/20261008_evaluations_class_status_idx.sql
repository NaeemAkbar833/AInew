-- Supports class results and report queries and class-based deletion.
CREATE INDEX IF NOT EXISTS idx_evaluations_class_status_updated_at
ON public.student_paper_evaluations (class_id, status, updated_at ASC);
