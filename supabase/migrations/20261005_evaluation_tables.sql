-- Migration: Add Multi-Page Question Paper Support & Atomic Provisional Evaluation Schema
-- Step 2: Reliable Provisional AI Grading Backend

-- ==============================================================================
-- 1. Extend student_papers for Paper-Level OCR Tracking
-- ==============================================================================
ALTER TABLE public.student_papers
ADD COLUMN IF NOT EXISTS ocr_status TEXT DEFAULT 'pending',
ADD COLUMN IF NOT EXISTS combined_ocr_text TEXT,
ADD COLUMN IF NOT EXISTS ocr_completed_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS ocr_error TEXT;

-- ==============================================================================
-- 2. Multi-Page Question Paper Support (Backward compatible with classes.question_paper_path)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.class_question_paper_pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_class_qp_page UNIQUE (class_id, page_number)
);

ALTER TABLE public.class_question_paper_pages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Teachers can view their class question paper pages" ON public.class_question_paper_pages;
CREATE POLICY "Teachers can view their class question paper pages"
ON public.class_question_paper_pages
FOR SELECT
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = class_question_paper_pages.class_id
          AND c.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can insert question paper pages for their classes" ON public.class_question_paper_pages;
CREATE POLICY "Teachers can insert question paper pages for their classes"
ON public.class_question_paper_pages
FOR INSERT
WITH CHECK (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = class_question_paper_pages.class_id
          AND c.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can update their class question paper pages" ON public.class_question_paper_pages;
CREATE POLICY "Teachers can update their class question paper pages"
ON public.class_question_paper_pages
FOR UPDATE
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = class_question_paper_pages.class_id
          AND c.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can delete their class question paper pages" ON public.class_question_paper_pages;
CREATE POLICY "Teachers can delete their class question paper pages"
ON public.class_question_paper_pages
FOR DELETE
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = class_question_paper_pages.class_id
          AND c.teacher_id = auth.uid()
    )
);

CREATE INDEX IF NOT EXISTS idx_qp_pages_class_id ON public.class_question_paper_pages(class_id);
CREATE INDEX IF NOT EXISTS idx_qp_pages_teacher_id ON public.class_question_paper_pages(teacher_id);

-- ==============================================================================
-- 3. Provisional Student Paper Evaluations Table
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.student_paper_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id UUID NOT NULL REFERENCES public.student_papers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('processing', 'completed', 'failed', 'needs_review', 'approved')) DEFAULT 'processing',
    total_marks_obtained NUMERIC(6, 2) DEFAULT 0,
    total_marks NUMERIC(6, 2) DEFAULT 0,
    percentage NUMERIC(5, 2),
    review_required BOOLEAN DEFAULT false,
    review_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_paper_evaluation UNIQUE (paper_id)
);

ALTER TABLE public.student_paper_evaluations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Teachers can view evaluations for their papers" ON public.student_paper_evaluations;
CREATE POLICY "Teachers can view evaluations for their papers"
ON public.student_paper_evaluations
FOR SELECT
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.student_papers p
        WHERE p.id = student_paper_evaluations.paper_id
          AND p.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can insert evaluations for their papers" ON public.student_paper_evaluations;
CREATE POLICY "Teachers can insert evaluations for their papers"
ON public.student_paper_evaluations
FOR INSERT
WITH CHECK (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.student_papers p
        WHERE p.id = student_paper_evaluations.paper_id
          AND p.teacher_id = auth.uid()
          AND p.student_id = student_paper_evaluations.student_id
          AND p.class_id = student_paper_evaluations.class_id
    )
    AND EXISTS (
        SELECT 1 FROM public.students s
        WHERE s.id = student_paper_evaluations.student_id
          AND s.teacher_id = auth.uid()
          AND s.class_id = student_paper_evaluations.class_id
    )
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = student_paper_evaluations.class_id
          AND c.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can update evaluations for their papers" ON public.student_paper_evaluations;
CREATE POLICY "Teachers can update evaluations for their papers"
ON public.student_paper_evaluations
FOR UPDATE
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.student_papers p
        WHERE p.id = student_paper_evaluations.paper_id
          AND p.teacher_id = auth.uid()
          AND p.student_id = student_paper_evaluations.student_id
          AND p.class_id = student_paper_evaluations.class_id
    )
    AND EXISTS (
        SELECT 1 FROM public.students s
        WHERE s.id = student_paper_evaluations.student_id
          AND s.teacher_id = auth.uid()
          AND s.class_id = student_paper_evaluations.class_id
    )
    AND EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = student_paper_evaluations.class_id
          AND c.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can delete evaluations for their papers" ON public.student_paper_evaluations;
CREATE POLICY "Teachers can delete evaluations for their papers"
ON public.student_paper_evaluations
FOR DELETE
USING (
    auth.uid() = teacher_id
    AND EXISTS (
        SELECT 1 FROM public.student_papers p
        WHERE p.id = student_paper_evaluations.paper_id
          AND p.teacher_id = auth.uid()
    )
);

CREATE INDEX IF NOT EXISTS idx_evaluations_paper_id ON public.student_paper_evaluations(paper_id);
CREATE INDEX IF NOT EXISTS idx_evaluations_student_id ON public.student_paper_evaluations(student_id);
CREATE INDEX IF NOT EXISTS idx_evaluations_teacher_id ON public.student_paper_evaluations(teacher_id);

-- ==============================================================================
-- 4. Detailed Question-Level Evaluation Results Table
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.student_paper_evaluation_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id UUID NOT NULL REFERENCES public.student_paper_evaluations(id) ON DELETE CASCADE,
    question_number TEXT,
    question_text TEXT,
    maximum_marks NUMERIC(6, 2) NOT NULL DEFAULT 0,
    student_answer TEXT,
    expected_answer TEXT,
    marking_criteria TEXT,
    awarded_marks NUMERIC(6, 2) NOT NULL DEFAULT 0,
    feedback TEXT,
    review_required BOOLEAN DEFAULT false,
    review_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Ensure question_number and question_text are nullable for existing deployments
ALTER TABLE public.student_paper_evaluation_questions
    ALTER COLUMN question_number DROP NOT NULL,
    ALTER COLUMN question_text DROP NOT NULL;

ALTER TABLE public.student_paper_evaluation_questions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Teachers can view questions for their evaluations" ON public.student_paper_evaluation_questions;
CREATE POLICY "Teachers can view questions for their evaluations"
ON public.student_paper_evaluation_questions
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.student_paper_evaluations e
        WHERE e.id = student_paper_evaluation_questions.evaluation_id
        AND e.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can insert questions for their evaluations" ON public.student_paper_evaluation_questions;
CREATE POLICY "Teachers can insert questions for their evaluations"
ON public.student_paper_evaluation_questions
FOR INSERT
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.student_paper_evaluations e
        WHERE e.id = student_paper_evaluation_questions.evaluation_id
        AND e.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can update questions for their evaluations" ON public.student_paper_evaluation_questions;
CREATE POLICY "Teachers can update questions for their evaluations"
ON public.student_paper_evaluation_questions
FOR UPDATE
USING (
    EXISTS (
        SELECT 1 FROM public.student_paper_evaluations e
        WHERE e.id = student_paper_evaluation_questions.evaluation_id
        AND e.teacher_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Teachers can delete questions for their evaluations" ON public.student_paper_evaluation_questions;
CREATE POLICY "Teachers can delete questions for their evaluations"
ON public.student_paper_evaluation_questions
FOR DELETE
USING (
    EXISTS (
        SELECT 1 FROM public.student_paper_evaluations e
        WHERE e.id = student_paper_evaluation_questions.evaluation_id
        AND e.teacher_id = auth.uid()
    )
);

CREATE INDEX IF NOT EXISTS idx_eval_questions_evaluation_id ON public.student_paper_evaluation_questions(evaluation_id);

-- ==============================================================================
-- 5. Atomic PostgreSQL RPC Function for Transactional Evaluation Persistence
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.save_provisional_paper_evaluation(
    p_paper_id UUID,
    p_student_id UUID,
    p_class_id UUID,
    p_teacher_id UUID,
    p_status TEXT,
    p_total_marks_obtained NUMERIC,
    p_total_marks NUMERIC,
    p_percentage NUMERIC,
    p_review_required BOOLEAN,
    p_review_reason TEXT,
    p_questions JSONB
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_existing_status TEXT;
    v_evaluation_id UUID;
    v_saved_evaluation JSONB;
BEGIN
    -- 1. Security Check: Authenticated caller identity & relationship verification
    IF auth.role() = 'authenticated' AND (auth.uid() IS NULL OR auth.uid() != p_teacher_id) THEN
        RAISE EXCEPTION 'Unauthorized: Caller identity does not match the teacher ID.';
    END IF;

    -- Verify student_papers exists and belongs to p_teacher_id, p_student_id, and p_class_id
    IF NOT EXISTS (
        SELECT 1 FROM public.student_papers sp
        WHERE sp.id = p_paper_id
          AND sp.teacher_id = p_teacher_id
          AND sp.student_id = p_student_id
          AND sp.class_id = p_class_id
    ) THEN
        RAISE EXCEPTION 'Security violation: student_paper does not exist or relation mismatch with teacher, student, or class.';
    END IF;

    -- Verify students exists and belongs to p_teacher_id and p_class_id
    IF NOT EXISTS (
        SELECT 1 FROM public.students s
        WHERE s.id = p_student_id
          AND s.teacher_id = p_teacher_id
          AND s.class_id = p_class_id
    ) THEN
        RAISE EXCEPTION 'Security violation: student does not exist or relation mismatch with teacher or class.';
    END IF;

    -- Verify classes exists and belongs to p_teacher_id
    IF NOT EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.id = p_class_id
          AND c.teacher_id = p_teacher_id
    ) THEN
        RAISE EXCEPTION 'Security violation: class does not exist or relation mismatch with teacher.';
    END IF;

    -- 2. Concurrency-Safe Overwrite Protection: Lock row FOR UPDATE
    SELECT status, id INTO v_existing_status, v_evaluation_id
    FROM public.student_paper_evaluations
    WHERE paper_id = p_paper_id
    FOR UPDATE;

    IF v_existing_status = 'approved' THEN
        RAISE EXCEPTION 'Approved evaluations cannot be overwritten.';
    END IF;

    -- 3. Upsert parent evaluation record
    INSERT INTO public.student_paper_evaluations (
        paper_id,
        student_id,
        class_id,
        teacher_id,
        status,
        total_marks_obtained,
        total_marks,
        percentage,
        review_required,
        review_reason,
        updated_at
    ) VALUES (
        p_paper_id,
        p_student_id,
        p_class_id,
        p_teacher_id,
        p_status,
        p_total_marks_obtained,
        p_total_marks,
        p_percentage,
        p_review_required,
        p_review_reason,
        now()
    )
    ON CONFLICT (paper_id) DO UPDATE SET
        status = EXCLUDED.status,
        total_marks_obtained = EXCLUDED.total_marks_obtained,
        total_marks = EXCLUDED.total_marks,
        percentage = EXCLUDED.percentage,
        review_required = EXCLUDED.review_required,
        review_reason = EXCLUDED.review_reason,
        updated_at = now()
    RETURNING id INTO v_evaluation_id;

    -- 4. Delete previous question rows for this evaluation
    DELETE FROM public.student_paper_evaluation_questions
    WHERE evaluation_id = v_evaluation_id;

    -- 5. Atomically insert all new question rows
    IF p_questions IS NOT NULL AND jsonb_array_length(p_questions) > 0 THEN
        INSERT INTO public.student_paper_evaluation_questions (
            evaluation_id,
            question_number,
            question_text,
            maximum_marks,
            student_answer,
            expected_answer,
            marking_criteria,
            awarded_marks,
            feedback,
            review_required,
            review_reason
        )
        SELECT
            v_evaluation_id,
            elem->>'question_number',
            elem->>'question_text',
            COALESCE((elem->>'maximum_marks')::NUMERIC, 0),
            elem->>'student_answer',
            elem->>'expected_answer',
            elem->>'marking_criteria',
            COALESCE((elem->>'awarded_marks')::NUMERIC, 0),
            elem->>'feedback',
            COALESCE((elem->>'review_required')::BOOLEAN, false),
            elem->>'review_reason'
        FROM jsonb_array_elements(p_questions) AS elem;
    END IF;

    -- 6. Return complete evaluation record with questions as JSON
    SELECT jsonb_build_object(
        'id', e.id,
        'paper_id', e.paper_id,
        'student_id', e.student_id,
        'class_id', e.class_id,
        'teacher_id', e.teacher_id,
        'status', e.status,
        'total_marks_obtained', e.total_marks_obtained,
        'total_marks', e.total_marks,
        'percentage', e.percentage,
        'review_required', e.review_required,
        'review_reason', e.review_reason,
        'created_at', e.created_at,
        'updated_at', e.updated_at,
        'questions', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', q.id,
                'evaluation_id', q.evaluation_id,
                'question_number', q.question_number,
                'question_text', q.question_text,
                'maximum_marks', q.maximum_marks,
                'student_answer', q.student_answer,
                'expected_answer', q.expected_answer,
                'marking_criteria', q.marking_criteria,
                'awarded_marks', q.awarded_marks,
                'feedback', q.feedback,
                'review_required', q.review_required,
                'review_reason', q.review_reason,
                'created_at', q.created_at
            ))
            FROM public.student_paper_evaluation_questions q
            WHERE q.evaluation_id = e.id
        ), '[]'::jsonb)
    ) INTO v_saved_evaluation
    FROM public.student_paper_evaluations e
    WHERE e.id = v_evaluation_id;

    RETURN v_saved_evaluation;
END;
$$;

REVOKE ALL ON FUNCTION public.save_provisional_paper_evaluation(UUID, UUID, UUID, UUID, TEXT, NUMERIC, NUMERIC, NUMERIC, BOOLEAN, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.save_provisional_paper_evaluation(UUID, UUID, UUID, UUID, TEXT, NUMERIC, NUMERIC, NUMERIC, BOOLEAN, TEXT, JSONB) FROM anon;
GRANT EXECUTE ON FUNCTION public.save_provisional_paper_evaluation(UUID, UUID, UUID, UUID, TEXT, NUMERIC, NUMERIC, NUMERIC, BOOLEAN, TEXT, JSONB) TO service_role, authenticated;
