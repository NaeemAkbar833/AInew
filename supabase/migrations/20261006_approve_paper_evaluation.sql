-- Migration: Add Secure Teacher Approval RPC for Student Paper Evaluations
-- Step: Final Teacher Approval Workflow

-- ==============================================================================
-- Function: public.approve_paper_evaluation
-- Description: Allows an authenticated teacher to explicitly approve a provisional
--              evaluation (status: 'completed' or 'needs_review') for their student paper.
--              Atomically transitions status to 'approved', clears review_required,
--              updates updated_at, preserves all historical marks/reasons/questions untouched,
--              and prevents re-approval or concurrent approval collisions.
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.approve_paper_evaluation(
    p_evaluation_id UUID,
    p_paper_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_evaluation RECORD;
    v_updated_evaluation RECORD;
    v_result JSONB;
BEGIN
    -- 1. Authenticate caller
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Caller must be an authenticated user.';
    END IF;

    -- 2. Lock the evaluation row with FOR UPDATE to prevent concurrent approvals
    SELECT * INTO v_evaluation
    FROM public.student_paper_evaluations
    WHERE id = p_evaluation_id
      AND paper_id = p_paper_id
    FOR UPDATE;

    -- 3. Verify the evaluation exists
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Evaluation not found for evaluation_id % and paper_id %.', p_evaluation_id, p_paper_id;
    END IF;

    -- Verify caller ownership of this evaluation
    IF v_evaluation.teacher_id != v_caller_id THEN
        RAISE EXCEPTION 'Unauthorized: You do not own this evaluation.';
    END IF;

    -- Verify that the associated student paper belongs to the same teacher
    IF NOT EXISTS (
        SELECT 1 FROM public.student_papers sp
        WHERE sp.id = p_paper_id
          AND sp.teacher_id = v_caller_id
          AND sp.student_id = v_evaluation.student_id
          AND sp.class_id = v_evaluation.class_id
    ) THEN
        RAISE EXCEPTION 'Security violation: student paper does not exist or relation mismatch with teacher.';
    END IF;

    -- 4. Verify valid provisional state
    -- Reject already approved evaluations (cannot be modified or re-approved)
    IF v_evaluation.status = 'approved' THEN
        RAISE EXCEPTION 'Evaluation is already approved. Approved evaluations cannot be modified or re-approved.';
    END IF;

    -- Reject evaluations in processing, failed, or any other non-provisional state
    IF v_evaluation.status NOT IN ('completed', 'needs_review') THEN
        RAISE EXCEPTION 'Evaluation is not ready for approval. Current status is %.', v_evaluation.status;
    END IF;

    -- 5. Atomically approve parent evaluation record
    -- Only updates status, review_required, and updated_at.
    -- Does NOT touch total_marks_obtained, total_marks, percentage, or review_reason.
    -- Does NOT touch or delete any student_paper_evaluation_questions rows.
    UPDATE public.student_paper_evaluations
    SET
        status = 'approved',
        review_required = false,
        updated_at = now()
    WHERE id = p_evaluation_id
    RETURNING * INTO v_updated_evaluation;

    -- 6. Return the approved evaluation summary as JSON
    SELECT jsonb_build_object(
        'id', v_updated_evaluation.id,
        'paper_id', v_updated_evaluation.paper_id,
        'student_id', v_updated_evaluation.student_id,
        'class_id', v_updated_evaluation.class_id,
        'teacher_id', v_updated_evaluation.teacher_id,
        'status', v_updated_evaluation.status,
        'total_marks_obtained', v_updated_evaluation.total_marks_obtained,
        'total_marks', v_updated_evaluation.total_marks,
        'percentage', v_updated_evaluation.percentage,
        'review_required', v_updated_evaluation.review_required,
        'review_reason', v_updated_evaluation.review_reason,
        'created_at', v_updated_evaluation.created_at,
        'updated_at', v_updated_evaluation.updated_at
    ) INTO v_result;

    RETURN v_result;
END;
$$;

-- Revoke all public / anonymous permissions
REVOKE ALL ON FUNCTION public.approve_paper_evaluation(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_paper_evaluation(UUID, UUID) FROM anon;

-- Grant execution strictly to authenticated teachers and service_role
GRANT EXECUTE ON FUNCTION public.approve_paper_evaluation(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.approve_paper_evaluation(UUID, UUID) TO service_role;
