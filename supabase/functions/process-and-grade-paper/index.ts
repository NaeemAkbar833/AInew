import { createClient } from "https://esm.sh/@supabase/supabase-js@2.48.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

/**
 * Memory-safe Base64 conversion without quadratic string reallocations.
 * Uses native Buffer if present in Deno runtime, or bounded chunking with join().
 */
function toBase64(uint8Array: Uint8Array): string {
  if (typeof (globalThis as any).Buffer !== "undefined") {
    return (globalThis as any).Buffer.from(uint8Array).toString("base64");
  }
  const chunks: string[] = [];
  const chunkSize = 32768;
  for (let i = 0; i < uint8Array.length; i += chunkSize) {
    const chunk = uint8Array.subarray(i, Math.min(i + chunkSize, uint8Array.length));
    chunks.push(String.fromCharCode.apply(null, chunk as unknown as number[]));
  }
  return btoa(chunks.join(""));
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

class PermanentGeminiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "PermanentGeminiError";
  }
}

class TemporaryGeminiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "TemporaryGeminiError";
  }
}

async function callGeminiWithRetry(geminiApiKey: string, payload: unknown, timeoutMs = 120000): Promise<any> {
  const maxAttempts = 3;
  const retryDelays = [2000, 5000];
  const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${geminiApiKey}`;

  // Serialize payload to string ONCE before the retry loop to prevent repetitive multi-MB memory allocations
  const bodyString = typeof payload === "string" ? payload : JSON.stringify(payload);

  let lastError: Error = new TemporaryGeminiError("Gemini API call failed.", 500);

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const resp = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: bodyString,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (resp.ok) {
        return await resp.json();
      }

      const statusCode = resp.status;
      const errorText = await resp.text().catch(() => "");

      // Permanent client errors (400, 401, 403, 404): DO NOT RETRY under any circumstances
      const isPermanent = statusCode === 400 || statusCode === 401 || statusCode === 403 || statusCode === 404;
      if (isPermanent) {
        throw new PermanentGeminiError(
          `Gemini permanent error (HTTP ${statusCode}): ${errorText.slice(0, 300)}`,
          statusCode
        );
      }

      // Temporary transient errors (429, 500, 502, 503, 504)
      lastError = new TemporaryGeminiError(
        `Gemini temporary service error (HTTP ${statusCode}): ${errorText.slice(0, 300)}`,
        statusCode
      );
      console.warn(`[process-and-grade-paper] Gemini attempt ${attempt} failed with temporary HTTP ${statusCode}`);

      if (attempt === maxAttempts) {
        throw lastError;
      }

      const delay = retryDelays[attempt - 1] ?? 5000;
      await sleep(delay);
    } catch (err: unknown) {
      clearTimeout(timeoutId);

      // If this is a permanent error, re-throw immediately and do NOT retry!
      if (err instanceof PermanentGeminiError) {
        throw err;
      }

      const isAbort = err instanceof Error && err.name === "AbortError";
      lastError = isAbort
        ? new TemporaryGeminiError("Gemini API call timed out.", 504)
        : (err instanceof TemporaryGeminiError ? err : new TemporaryGeminiError(err instanceof Error ? err.message : String(err), 500));

      if (attempt === maxAttempts) {
        throw lastError;
      }

      const delay = retryDelays[attempt - 1] ?? 5000;
      console.log(`[process-and-grade-paper] Retrying Gemini call after transient failure in ${delay}ms...`);
      await sleep(delay);
    }
  }

  throw lastError;
}

interface RawExtractedQuestion {
  page_number?: number | null;
  question_number?: string | null;
  question_text?: string | null;
  maximum_marks?: number | null;
  expected_answer?: string | null;
  marking_criteria?: string | null;
  review_required?: boolean;
  review_reason?: string | null;
}

interface RawGradingResult {
  question_number?: string | null;
  student_answer?: string | null;
  expected_answer?: string | null;
  marking_criteria?: string | null;
  expected_answer_source?: "question_paper" | "ai_generated" | "missing" | null;
  marking_criteria_source?: "question_paper" | "ai_generated" | "missing" | null;
  awarded_marks?: number;
  feedback?: string | null;
  review_required?: boolean;
  review_reason?: string | null;
}

function isMissingCriteriaReason(reason: string | null | undefined): boolean {
  if (!reason) return false;
  const lower = reason.toLowerCase().trim();
  return lower.includes("expected answer or marking criteria") ||
         lower.includes("marking criteria could not be reliably established") ||
         lower.includes("expected answer could not be reliably established") ||
         lower.includes("marking criteria unavailable") ||
         lower.includes("criteria unavailable") ||
         lower.includes("criteria could not be reliably established");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ status: "failed", error: "Method not allowed. Only POST is supported." }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  let requestPaperId: string | null = null;
  let adminClient: any = null;

  try {
    // 1. Authenticate Teacher Token
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Unauthorized: Missing Authorization header." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const geminiApiKey = Deno.env.get("GEMINI_API_KEY");

    if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Server configuration error: Missing Supabase credentials." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!geminiApiKey) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Server configuration error: Missing GEMINI_API_KEY secret." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: authError } = await authClient.auth.getUser();

    if (authError || !user) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Unauthorized: Invalid or expired teacher token." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const teacherId = user.id;

    // 2. Parse & Validate Request Body
    const body = await req.json().catch(() => null);
    const rawPaperId = body?.paper_id ?? body?.paperId;
    if (!body || typeof body !== "object" || !rawPaperId || typeof rawPaperId !== "string") {
      return new Response(
        JSON.stringify({ status: "failed", error: "Validation error: 'paper_id' (or 'paperId') string is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const paperId = rawPaperId.trim();
    requestPaperId = paperId;

    // 3. Ownership & Relations Verification
    adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    const { data: paper, error: paperError } = await adminClient
      .from("student_papers")
      .select("id, student_id, class_id, teacher_id, total_pages")
      .eq("id", paperId)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (paperError || !paper) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Student paper not found or access denied." }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Safety: Protect Approved Final Results from Overwrite (HTTP 409)
    const { data: existingEval } = await adminClient
      .from("student_paper_evaluations")
      .select("id, status")
      .eq("paper_id", paperId)
      .maybeSingle();

    if (existingEval && existingEval.status === "approved") {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "This student paper has already been finalized and approved by the teacher. Approved results cannot be overwritten.",
        }),
        { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Get Class Record & Verify Teacher Ownership
    const { data: classRoom, error: classError } = await adminClient
      .from("classes")
      .select("id, subject_name, total_marks, question_paper_path")
      .eq("id", paper.class_id)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (classError || !classRoom) {
      return new Response(
        JSON.stringify({ status: "failed", error: "Class associated with paper not found." }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Validate Question Paper Pages Configuration (Multi-Page table first, fallback to classes.question_paper_path)
    const { data: multiQpPages, error: multiQpError } = await adminClient
      .from("class_question_paper_pages")
      .select("page_number, storage_path")
      .eq("class_id", paper.class_id)
      .eq("teacher_id", teacherId)
      .order("page_number", { ascending: true });

    let qpPagePaths: { pageNumber: number; storagePath: string }[] = [];
    if (!multiQpError && multiQpPages && multiQpPages.length > 0) {
      qpPagePaths = multiQpPages.map((p: any) => ({
        pageNumber: Number(p.page_number),
        storagePath: String(p.storage_path).trim(),
      }));
    } else if (classRoom.question_paper_path && classRoom.question_paper_path.trim().length > 0) {
      qpPagePaths = [{ pageNumber: 1, storagePath: classRoom.question_paper_path.trim() }];
    }

    if (qpPagePaths.length === 0) {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "No question paper found for this class. Please upload a question paper in Class settings before running evaluation.",
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Sort question paper pages ascending
    qpPagePaths.sort((a, b) => a.pageNumber - b.pageNumber);

    // Validate Question Paper Pages & Gap Detection
    const seenQpPages = new Set<number>();
    let qpSequenceWarning: string | null = null;
    let isQpUnclear = false;

    for (const qp of qpPagePaths) {
      if (!qp.storagePath || qp.storagePath.length === 0) {
        throw new Error(`Question paper page ${qp.pageNumber} has an empty storage path.`);
      }
      if (isNaN(qp.pageNumber) || qp.pageNumber <= 0) {
        qpSequenceWarning = `Question paper has invalid page number: ${qp.pageNumber}`;
        isQpUnclear = true;
      }
      if (seenQpPages.has(qp.pageNumber)) {
        qpSequenceWarning = `Duplicate question paper page number: ${qp.pageNumber}`;
        isQpUnclear = true;
      }
      seenQpPages.add(qp.pageNumber);
    }

    if (qpPagePaths.length > 1) {
      const maxPage = Math.max(...qpPagePaths.map((p) => p.pageNumber));
      const missingPages: number[] = [];
      for (let i = 1; i <= maxPage; i++) {
        if (!seenQpPages.has(i)) {
          missingPages.push(i);
        }
      }

      if (missingPages.length > 0) {
        qpSequenceWarning = `Question paper page sequence contains missing page(s): ${missingPages.join(", ")}`;
        isQpUnclear = true;
      }
    }

    // Get All Student Paper Pages & Validate Continuous Sequence 1..N = total_pages
    const { data: studentPages, error: studentPagesError } = await adminClient
      .from("student_paper_pages")
      .select("id, page_number, storage_path, ocr_text, ocr_status")
      .eq("paper_id", paperId)
      .eq("teacher_id", teacherId)
      .order("page_number", { ascending: true });

    if (studentPagesError || !studentPages || studentPages.length === 0) {
      return new Response(
        JSON.stringify({ status: "failed", error: "This student paper has no scanned pages to process." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const expectedTotalPages = Number(paper.total_pages) || studentPages.length;
    const studentPageNumbers = studentPages.map((p: any) => Number(p.page_number));
    const seenStudentPages = new Set<number>();
    let studentSequenceInvalid = false;

    for (const pNum of studentPageNumbers) {
      if (isNaN(pNum) || pNum <= 0 || seenStudentPages.has(pNum)) {
        studentSequenceInvalid = true;
      }
      seenStudentPages.add(pNum);
    }

    // Verify continuous 1..expectedTotalPages
    for (let i = 1; i <= expectedTotalPages; i++) {
      if (!seenStudentPages.has(i)) {
        studentSequenceInvalid = true;
        break;
      }
    }

    if (studentSequenceInvalid || studentPages.length !== expectedTotalPages) {
      const errorMsg = `Student paper page sequence is incomplete or invalid. Expected continuous pages 1 through ${expectedTotalPages}, but found: ${studentPageNumbers.join(", ")}`;
      return new Response(
        JSON.stringify({ status: "failed", error: errorMsg }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Mark Paper-Level OCR as Processing
    await adminClient
      .from("student_papers")
      .update({ ocr_status: "processing", ocr_error: null })
      .eq("id", paperId);

    // =========================================================================
    // 5. CLASS-LEVEL QUESTION PAPER CACHE & REUSE
    //    Check public.class_question_paper_extractions for this class.
    //    If completed questions exist, load from public.class_question_paper_questions.
    //    DO NOT download QP images, DO NOT call Gemini Vision.
    // =========================================================================
    let { data: classExtraction } = await adminClient
      .from("class_question_paper_extractions")
      .select("id, status, total_pages, total_questions, extracted_total_marks, marks_match_class_total, unclear_question_paper, review_required, review_reason, updated_at")
      .eq("class_id", paper.class_id)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    let rawQuestions: RawExtractedQuestion[] = [];
    let qpReviewReason: string | null = null;
    let extractedTotalMarks = 0;

    // Concurrency Rule: If another request is currently processing this class's question paper, wait for it
    if (classExtraction && classExtraction.status === "processing") {
      const isStale = classExtraction.updated_at && (Date.now() - new Date(classExtraction.updated_at).getTime() > 180000); // 3 minutes timeout
      if (!isStale) {
        console.log(`[process-and-grade-paper] Class ${paper.class_id} question extraction is currently processing by another request. Waiting...`);
        const maxWaitMs = 60000;
        const pollInterval = 2500;
        const startWait = Date.now();

        while (Date.now() - startWait < maxWaitMs) {
          await sleep(pollInterval);
          const { data: pollExt } = await adminClient
            .from("class_question_paper_extractions")
            .select("id, status, total_pages, total_questions, extracted_total_marks, marks_match_class_total, unclear_question_paper, review_required, review_reason, updated_at")
            .eq("class_id", paper.class_id)
            .eq("teacher_id", teacherId)
            .maybeSingle();

          if (pollExt && (pollExt.status === "completed" || pollExt.status === "needs_review")) {
            classExtraction = pollExt;
            break;
          }
          if (pollExt && pollExt.status === "failed") {
            classExtraction = pollExt;
            break;
          }
        }
      }
    }

    // Check if a completed/needs_review extraction is available
    if (classExtraction && (classExtraction.status === "completed" || classExtraction.status === "needs_review")) {
      console.log(`[process-and-grade-paper] Reusing cached class questions from public.class_question_paper_questions for class ${paper.class_id}...`);
      const { data: cachedQRows, error: qFetchErr } = await adminClient
        .from("class_question_paper_questions")
        .select("page_number, question_number, question_text, maximum_marks, expected_answer, marking_criteria, review_required, review_reason, sort_order")
        .eq("class_id", paper.class_id)
        .eq("teacher_id", teacherId)
        .order("sort_order", { ascending: true });

      if (!qFetchErr && cachedQRows && cachedQRows.length > 0) {
        rawQuestions = cachedQRows.map((q: any) => ({
          page_number: q.page_number !== null && q.page_number !== undefined ? Number(q.page_number) : null,
          question_number: q.question_number ?? null,
          question_text: q.question_text ?? null,
          maximum_marks: q.maximum_marks !== null && q.maximum_marks !== undefined ? Number(q.maximum_marks) : null,
          expected_answer: q.expected_answer ?? null,
          marking_criteria: q.marking_criteria ?? null,
          review_required: Boolean(q.review_required),
          review_reason: q.review_reason ?? null,
        }));

        const isQpCriteriaOnly = isMissingCriteriaReason(classExtraction.review_reason);
        isQpUnclear = Boolean(classExtraction.unclear_question_paper) || (Boolean(classExtraction.review_required) && !isQpCriteriaOnly);
        qpReviewReason = isQpCriteriaOnly ? null : (classExtraction.review_reason || null);
        extractedTotalMarks = Number(classExtraction.extracted_total_marks) || 0;
      } else {
        console.warn("[process-and-grade-paper] Class extraction status was completed but no questions were found in table. Re-extracting...");
        classExtraction = null;
      }
    }

    // If no questions are cached, perform extraction once and persist to class cache
    // CRITICAL MEMORY RULE: Process question paper pages ONE BY ONE to ensure at most 1 image in memory.
    if (rawQuestions.length === 0) {
      console.log(`[process-and-grade-paper] No valid cache found for class ${paper.class_id}. Performing one-time question paper extraction...`);

      let extractionRecordId: string;
      if (!classExtraction) {
        const { data: newExt, error: newExtErr } = await adminClient
          .from("class_question_paper_extractions")
          .insert({
            class_id: paper.class_id,
            teacher_id: teacherId,
            status: "processing",
            total_pages: qpPagePaths.length,
            updated_at: new Date().toISOString(),
          })
          .select("id")
          .maybeSingle();

        if (newExtErr || !newExt) {
          const { data: existingAfterConflict } = await adminClient
            .from("class_question_paper_extractions")
            .select("id, status")
            .eq("class_id", paper.class_id)
            .eq("teacher_id", teacherId)
            .maybeSingle();

          if (existingAfterConflict && (existingAfterConflict.status === "completed" || existingAfterConflict.status === "needs_review")) {
            const { data: qAfter } = await adminClient
              .from("class_question_paper_questions")
              .select("page_number, question_number, question_text, maximum_marks, expected_answer, marking_criteria, review_required, review_reason, sort_order")
              .eq("class_id", paper.class_id)
              .eq("teacher_id", teacherId)
              .order("sort_order", { ascending: true });

            if (qAfter && qAfter.length > 0) {
              rawQuestions = qAfter.map((q: any) => ({
                page_number: q.page_number !== null && q.page_number !== undefined ? Number(q.page_number) : null,
                question_number: q.question_number ?? null,
                question_text: q.question_text ?? null,
                maximum_marks: q.maximum_marks !== null && q.maximum_marks !== undefined ? Number(q.maximum_marks) : null,
                expected_answer: q.expected_answer ?? null,
                marking_criteria: q.marking_criteria ?? null,
                review_required: Boolean(q.review_required),
                review_reason: q.review_reason ?? null,
              }));
            }
          }

          if (rawQuestions.length === 0) {
            throw new Error(`Could not initiate question paper extraction for class: ${newExtErr?.message || "Concurrent extraction lock error"}`);
          }
          extractionRecordId = existingAfterConflict?.id ?? "";
        } else {
          extractionRecordId = newExt.id;
        }
      } else {
        extractionRecordId = classExtraction.id;
        await adminClient
          .from("class_question_paper_extractions")
          .update({
            status: "processing",
            error_message: null,
            total_pages: qpPagePaths.length,
            updated_at: new Date().toISOString(),
          })
          .eq("id", extractionRecordId);
      }

      // If rawQuestions is still empty, run sequential page-by-page extraction (1 image at a time)
      if (rawQuestions.length === 0) {
        try {
          const allExtractedQuestions: RawExtractedQuestion[] = [];

          for (const qp of qpPagePaths) {
            console.log(`[process-and-grade-paper] Downloading and extracting question paper page ${qp.pageNumber}...`);

            const { data: qpBlob, error: qpDownloadError } = await adminClient
              .storage
              .from("question-papers")
              .download(qp.storagePath);

            if (qpDownloadError || !qpBlob) {
              throw new Error(`Failed to download question paper page ${qp.pageNumber} (${qp.storagePath}): ${qpDownloadError?.message}`);
            }

            const qpArrayBuffer = await qpBlob.arrayBuffer();
            const qpBase64 = toBase64(new Uint8Array(qpArrayBuffer));
            const qpMime = qpBlob.type || "image/jpeg";

            const pageExtractionPrompt = `You are a meticulous exam question paper digitizer.
Analyze this single page of an exam question paper (Page ${qp.pageNumber}) and extract all questions and subquestions present on this page.

STRICT EXTRACTION RULES:
1. Extract ONLY questions visibly present on this page image. DO NOT invent or assume any questions.
2. Extract the exact question numbering (e.g., "1", "2(a)", "3.1").
   - If a question has NO visible question number, return null for question_number. DO NOT invent "1", "[Unknown]", or any replacement number.
3. Extract the complete question text visible on this page.
   - If question text is unreadable, cut off, or missing, return null for question_text. DO NOT invent question text.
4. Extract ONLY maximum marks visibly written for each question.
   - NEVER invent marks.
   - If marks are not written, illegible, or cut off, set maximum_marks to null, set review_required to true, and set review_reason to "Maximum marks not specified in question paper".
5. For expected answers and marking criteria:
   - If the question paper provides an answer key, formula, options, rubric, or specific criteria, extract it.
   - If the question paper does not provide an expected answer or marking criteria, leave expected_answer and marking_criteria as null (do NOT invent them at this extraction stage).
6. If a question is blurry, cut off, or illegible, set review_required to true and explain in review_reason.
7. If the overall page is unreadable or cut off, set unclear_page to true.

Return strict JSON matching this schema:
{
  "unclear_page": boolean,
  "questions": [
    {
      "question_number": string | null,
      "question_text": string | null,
      "maximum_marks": number | null,
      "expected_answer": string | null,
      "marking_criteria": string | null,
      "review_required": boolean,
      "review_reason": string | null
    }
  ]
}`;

            const qpPagePayload = JSON.stringify({
              contents: [
                {
                  parts: [
                    { inlineData: { mimeType: qpMime, data: qpBase64 } },
                    { text: pageExtractionPrompt },
                  ],
                },
              ],
              generationConfig: {
                responseMimeType: "application/json",
                temperature: 0.1,
              },
            });

            const qpResponse = await callGeminiWithRetry(geminiApiKey, qpPagePayload);
            const qpJsonText = qpResponse.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? "{}";

            let parsedPageQp: { unclear_page?: boolean; questions?: RawExtractedQuestion[] };
            try {
              parsedPageQp = JSON.parse(qpJsonText);
            } catch {
              throw new Error(`Gemini returned invalid JSON for question paper page ${qp.pageNumber}.`);
            }

            if (Boolean(parsedPageQp.unclear_page)) {
              isQpUnclear = true;
            }

            const pageQuestions: RawExtractedQuestion[] = parsedPageQp.questions ?? [];
            for (const q of pageQuestions) {
              q.page_number = qp.pageNumber;
              allExtractedQuestions.push(q);
            }

            // Pacing delay between question paper pages to keep worker compute footprint small
            await sleep(300);
          }

          if (allExtractedQuestions.length === 0) {
            throw new Error("No questions could be extracted from the question paper. Please verify the uploaded image.");
          }

          // Programmatic Validation of Extracted Questions: Check Duplicates, Unknown Numbers, Empty fields, Marks
          const seenQNumbers = new Set<string>();
          const duplicateQNumbers = new Set<string>();

          for (const q of allExtractedQuestions) {
            const rawNum = q.question_number ? String(q.question_number).trim() : "";
            if (!rawNum) {
              q.question_number = null;
              q.review_required = true;
              q.review_reason = q.review_reason || "Question number could not be determined from question paper.";
              isQpUnclear = true;
            } else {
              q.question_number = rawNum;
              const norm = rawNum.toUpperCase();
              if (seenQNumbers.has(norm)) {
                duplicateQNumbers.add(norm);
              } else {
                seenQNumbers.add(norm);
              }
            }

            const rawText = q.question_text ? String(q.question_text).trim() : "";
            if (!rawText) {
              q.question_text = null;
              q.review_required = true;
              q.review_reason = q.review_reason || "Question text is empty or missing in question paper.";
              isQpUnclear = true;
            } else {
              q.question_text = rawText;
            }

            const rawMax = q.maximum_marks;
            if (rawMax === null || rawMax === undefined || typeof rawMax !== "number" || isNaN(rawMax) || !isFinite(rawMax) || rawMax <= 0) {
              q.maximum_marks = null;
              q.review_required = true;
              q.review_reason = q.review_reason || "Maximum marks not specified or invalid in question paper.";
              isQpUnclear = true;
            }

            const hasCriteria = Boolean(
              (q.expected_answer && q.expected_answer.trim().length > 0) ||
              (q.marking_criteria && q.marking_criteria.trim().length > 0)
            );
            if (!hasCriteria) {
              q.expected_answer = null;
              q.marking_criteria = null;
            }
          }

          if (duplicateQNumbers.size > 0) {
            isQpUnclear = true;
            for (const q of allExtractedQuestions) {
              if (q.question_number) {
                const norm = q.question_number.trim().toUpperCase();
                if (duplicateQNumbers.has(norm)) {
                  q.review_required = true;
                  q.review_reason = `Duplicate question number '${q.question_number}' detected in question paper.`;
                }
              }
            }
          }

          // Compute total extracted marks if all marks are valid numbers
          let sumMarks = 0;
          let allMarksPresent = true;
          for (const q of allExtractedQuestions) {
            if (typeof q.maximum_marks === "number" && q.maximum_marks > 0) {
              sumMarks += q.maximum_marks;
            } else {
              allMarksPresent = false;
            }
          }
          extractedTotalMarks = allMarksPresent ? Math.round(sumMarks * 100) / 100 : 0;
          const officialClassTotal = Number(classRoom.total_marks) || 0;
          const marksMatch = allMarksPresent && officialClassTotal > 0 && Math.round(sumMarks * 100) === Math.round(officialClassTotal * 100);

          qpReviewReason = qpSequenceWarning || (isQpUnclear ? "Question paper was unclear or incomplete." : null);

          // Delete any existing questions for this class (in case of re-try)
          await adminClient
            .from("class_question_paper_questions")
            .delete()
            .eq("class_id", paper.class_id);

          // Save extracted questions into public.class_question_paper_questions
          const questionsToInsert = allExtractedQuestions.map((q, idx) => ({
            class_id: paper.class_id,
            extraction_id: extractionRecordId,
            teacher_id: teacherId,
            page_number: q.page_number !== undefined && q.page_number !== null ? Number(q.page_number) : null,
            question_number: q.question_number || null,
            question_text: q.question_text || null,
            maximum_marks: q.maximum_marks !== null && q.maximum_marks !== undefined ? q.maximum_marks : null,
            expected_answer: q.expected_answer || null,
            marking_criteria: q.marking_criteria || null,
            review_required: Boolean(q.review_required),
            review_reason: q.review_reason || null,
            sort_order: idx + 1,
          }));

          const { error: insQuestionsErr } = await adminClient
            .from("class_question_paper_questions")
            .insert(questionsToInsert);

          if (insQuestionsErr) {
            throw new Error(`Failed to save extracted questions to class_question_paper_questions: ${insQuestionsErr.message}`);
          }

          // Mark class extraction header completed or needs_review
          const extractionFinalStatus = isQpUnclear ? "needs_review" : "completed";
          const { error: updExtErr } = await adminClient
            .from("class_question_paper_extractions")
            .update({
              status: extractionFinalStatus,
              total_questions: allExtractedQuestions.length,
              extracted_total_marks: allMarksPresent ? extractedTotalMarks : null,
              marks_match_class_total: marksMatch,
              unclear_question_paper: isQpUnclear,
              review_required: isQpUnclear,
              review_reason: qpReviewReason,
              extracted_at: new Date().toISOString(),
              updated_at: new Date().toISOString(),
            })
            .eq("id", extractionRecordId);

          if (updExtErr) {
            throw new Error(`Failed to update class_question_paper_extractions: ${updExtErr.message}`);
          }

          rawQuestions = allExtractedQuestions;
        } catch (extErr: unknown) {
          const extErrMsg = extErr instanceof Error ? extErr.message : String(extErr);
          console.error("[process-and-grade-paper] Extraction failure:", extErrMsg);
          if (extractionRecordId) {
            try {
              await adminClient
                .from("class_question_paper_extractions")
                .update({
                  status: "failed",
                  error_message: extErrMsg,
                  updated_at: new Date().toISOString(),
                })
                .eq("id", extractionRecordId);
            } catch (ignore) {}
          }
          throw extErr;
        }
      }
    }

    // 6. Sequential Student Page OCR (Controlled Concurrency + Reject Empty OCR + Free Images Immediately)
    console.log(`[process-and-grade-paper] Processing ${studentPages.length} student answer page(s) sequentially...`);
    const transcribedPages: { pageNumber: number; text: string }[] = [];

    for (const page of studentPages) {
      if (page.ocr_text && page.ocr_text.trim().length > 0 && page.ocr_status === "completed") {
        console.log(`[process-and-grade-paper] Reusing existing OCR for student page ${page.page_number}`);
        transcribedPages.push({ pageNumber: page.page_number, text: page.ocr_text.trim() });
        continue;
      }

      console.log(`[process-and-grade-paper] Downloading & transcribing student page ${page.page_number}...`);
      const { data: pageBlob, error: pageDownloadErr } = await adminClient
        .storage
        .from("student-papers")
        .download(page.storage_path);

      if (pageDownloadErr || !pageBlob) {
        throw new Error(`Failed to download student page ${page.page_number} (${page.storage_path}): ${pageDownloadErr?.message}`);
      }

      const pageBytes = await pageBlob.arrayBuffer();
      const pageBase64 = toBase64(new Uint8Array(pageBytes));
      const pageMime = pageBlob.type || "image/jpeg";

      const ocrSystemInstruction = `You are a high-precision optical character recognition (OCR) engine specialized in transcribing handwritten student examination answer sheets.
Your task is to transcribe what the student actually wrote verbatim.

STRICT OCR RULES:
1. Transcribe the handwriting exactly as written.
2. Preserve original spelling, capitalization, punctuation, and student mistakes.
3. Preserve numbers, mathematical equations, and scientific notation as accurately as possible.
4. If a word or phrase is crossed out, omit it unless legible and clearly intended as the final answer.
5. If handwriting is illegible, smudged, cut off, or unclear, transcribe it as "[unclear]". DO NOT GUESS words.
6. If the page contains no handwriting or answer at all, output exactly: "[No answer detected]".
7. DO NOT return an empty response.
8. DO NOT solve, answer, correct, complete, or grade any questions.
9. Output raw transcribed text only.`;

      const pagePayloadString = JSON.stringify({
        systemInstruction: { parts: [{ text: ocrSystemInstruction }] },
        contents: [
          {
            parts: [
              { inlineData: { mimeType: pageMime, data: pageBase64 } },
              { text: `Transcribe all handwritten student answers from Page ${page.page_number} verbatim according to instructions:` },
            ],
          },
        ],
        generationConfig: {
          temperature: 0.0,
          maxOutputTokens: 4096,
        },
      });

      const pageOcrResp = await callGeminiWithRetry(geminiApiKey, pagePayloadString);
      const transcribed = pageOcrResp.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? "";

      // Reject Empty OCR Response
      if (!transcribed || transcribed.length === 0) {
        const ocrEmptyError = `OCR engine returned empty or unreadable text for page ${page.page_number}. Cannot proceed with evaluation.`;
        await adminClient
          .from("student_paper_pages")
          .update({ ocr_status: "failed", ocr_error: ocrEmptyError })
          .eq("id", page.id);
        throw new Error(ocrEmptyError);
      }

      // Persistence Check: Save OCR result to database
      const { error: pageUpdateErr } = await adminClient
        .from("student_paper_pages")
        .update({
          ocr_text: transcribed,
          ocr_status: "completed",
          ocr_error: null,
          ocr_processed_at: new Date().toISOString(),
        })
        .eq("id", page.id);

      if (pageUpdateErr) {
        throw new Error(
          `Failed to save OCR result to student_paper_pages for page ${page.page_number}: ${pageUpdateErr.message}`
        );
      }

      transcribedPages.push({ pageNumber: page.page_number, text: transcribed });
      await sleep(400); // 400ms pacing delay between page calls
    }

    transcribedPages.sort((a, b) => a.pageNumber - b.pageNumber);
    const combinedStudentAnswers = transcribedPages
      .map((p) => `=== STUDENT ANSWER PAGE ${p.pageNumber} ===\n${p.text}`)
      .join("\n\n");

    // Update Paper-Level OCR Record to Completed
    await adminClient
      .from("student_papers")
      .update({
        ocr_status: "completed",
        combined_ocr_text: combinedStudentAnswers,
        ocr_completed_at: new Date().toISOString(),
        ocr_error: null,
      })
      .eq("id", paperId);

    // 7. Student Answer Matching & Provisional AI Grading (Pure Text-Only, Zero Images)
    console.log("[process-and-grade-paper] Running AI answer matching and grading (text-only)...");

    const gradingSystemPrompt = `You are an objective, rigorous academic examination grader implementing a HYBRID grading system.
You will receive:
1. EXAM QUESTIONS: The list of extracted questions, their question numbers, question text, maximum marks, and any existing expected answers or marking criteria from the question paper.
2. STUDENT'S TRANSCRIBED ANSWER PAPER: Verbatim transcription of the student's handwritten answers across all exam pages.

HYBRID GRADING WORKFLOW FOR EACH QUESTION:
1. DETERMINE EXPECTED ANSWER & MARKING CRITERIA:
   - If the question paper already provides an explicit expected answer, use it and set expected_answer_source = "question_paper".
   - If the question paper already provides explicit marking criteria, use it and set marking_criteria_source = "question_paper".
   - If expected_answer is missing or null:
     * If maximum_marks is reliably known (> 0) and question_text is clear, GENERATE a provisional expected answer based on standard academic knowledge.
       Format: State the answer clearly, prefixed as a provisional reference answer (e.g., "[Provisional Reference Answer]: ...").
       Set expected_answer_source = "ai_generated".
     * If the question is too ambiguous, unclear, or maximum_marks is missing: set expected_answer = null, expected_answer_source = "missing", review_required = true, and explain in review_reason.
   - If marking_criteria is missing or null:
     * If maximum_marks is reliably known (> 0) and question_text is clear, GENERATE provisional marking criteria allocating marks across key points summing exactly to maximum_marks.
       Set marking_criteria_source = "ai_generated".
     * If the question is too ambiguous, unclear, or maximum_marks is missing: set marking_criteria = null, marking_criteria_source = "missing", review_required = true, and explain in review_reason.
   - Never claim that an AI-generated answer came from the teacher or question paper. Clearly treat all AI-generated answers and criteria as PROVISIONAL.

EXAMPLE OF PROVISIONAL GENERATION & GRADING:
Question: "What is RAM?" | Maximum marks: 5
If question paper lacks expected answer/criteria:
  expected_answer: "[Provisional Reference Answer]: Random Access Memory is temporary computer memory used to store data and instructions currently being used by the computer."
  marking_criteria: "[Provisional Criteria]: - Identifies RAM as computer memory: 2 marks\\n- Explains temporary/current-use nature: 1 mark\\n- Explains storage of data/instructions: 2 marks"
  expected_answer_source: "ai_generated"
  marking_criteria_source: "ai_generated"
If student answer is: "Random Access Memory"
  Student receives 2 marks for identifying RAM as computer memory, 0 for temporary nature, 0 for storage.
  awarded_marks: 2
  feedback: "Correctly identified RAM as computer memory (2/2 marks). Did not explain its temporary nature or data/instruction storage role (0/3 marks)."
  (Do NOT give 0 simply because the question paper lacked an expected answer!)

2. MATCH STUDENT ANSWER:
   - Match student answers to questions based on question numbering (e.g., "1", "2(a)", "Q3"), question text keywords, subquestions, and sequence.
   - If no answer is written for a question:
     * student_answer = "[No answer detected]"
     * awarded_marks = 0
     * review_required = true
     * review_reason = "No answer detected in student paper."
     * feedback = "No answer written for this question."
   - If handwriting contains "[unclear]" or is partially illegible:
     * Do NOT guess or invent words.
     * Evaluate legible parts objectively.
     * Set review_required = true.
     * Set review_reason = "Student handwriting contains [unclear] segments or is ambiguous."
   - If answer matching is ambiguous or multiple conflicting attempts exist:
     * Set review_required = true.
     * Set review_reason = "Ambiguous answer matching or multiple attempts detected."

3. EVALUATE & AWARD MARKS:
   - Grade the student's answer against the question and the established (or provisional) marking criteria.
   - Award full, partial, or zero marks based on actual conceptual and semantic meaning, NOT keyword matching only.
   - Accept academically valid alternative wording, synonyms, and equivalent formulations.
   - Award full marks for completely correct answers meeting all criteria points.
   - Award proportional partial marks when only some criteria points are met.
   - Award 0 marks if incorrect, irrelevant, or missing.
   - NEVER exceed maximum_marks. NEVER award negative marks.
   - If maximum_marks is missing (null or <= 0): awarded_marks MUST be 0, review_required MUST be true, review_reason = "Maximum marks not specified in question paper. Marks cannot be awarded without a defined maximum."
   - If expected answer and marking criteria could NOT be established (source is "missing"): awarded_marks MUST be 0, review_required MUST be true, review_reason = "Question is too ambiguous or incomplete to establish reliable expected answer or criteria."
   - IMPORTANT: If expected_answer_source = "ai_generated" or marking_criteria_source = "ai_generated", this fact ALONE does NOT require review_required to be true! NEVER set review_required = true or set review_reason = "Expected answer or marking criteria could not be reliably established from question paper" when you generated provisional expected answers or marking criteria. Only set review_required = true if there is an actual defect: missing question text/number, missing maximum marks, empty student answer ("[No answer detected]"), unclear handwriting ("[unclear]"), or ambiguous answer matching.
   - The entire evaluation remains provisional until approved by the teacher.

4. FEEDBACK:
   - Provide clear, constructive feedback explaining why marks were awarded or deducted based on the criteria.

Return strict JSON matching this schema:
{
  "evaluations": [
    {
      "question_number": string | null,
      "student_answer": string | null,
      "expected_answer": string | null,
      "marking_criteria": string | null,
      "expected_answer_source": "question_paper" | "ai_generated" | "missing",
      "marking_criteria_source": "question_paper" | "ai_generated" | "missing",
      "awarded_marks": number,
      "feedback": string | null,
      "review_required": boolean,
      "review_reason": string | null
    }
  ]
}`;

    const sanitizedQuestionsForGrading = rawQuestions.map((q) => ({
      question_number: q.question_number ?? null,
      question_text: q.question_text ?? null,
      maximum_marks: q.maximum_marks ?? null,
      expected_answer: q.expected_answer ?? null,
      marking_criteria: q.marking_criteria ?? null,
    }));

    const gradingUserPrompt = `EXAM QUESTIONS & CRITERIA:
${JSON.stringify(sanitizedQuestionsForGrading, null, 2)}

STUDENT'S TRANSCRIBED ANSWER PAPER:
${combinedStudentAnswers}`;

    const gradingPayloadString = JSON.stringify({
      systemInstruction: { parts: [{ text: gradingSystemPrompt }] },
      contents: [{ parts: [{ text: gradingUserPrompt }] }],
      generationConfig: {
        responseMimeType: "application/json",
        temperature: 0.1,
      },
    });

    const gradingResponse = await callGeminiWithRetry(geminiApiKey, gradingPayloadString);
    const gradingJsonText = gradingResponse.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? "{}";

    let parsedGrading: { evaluations?: RawGradingResult[] };
    try {
      parsedGrading = JSON.parse(gradingJsonText);
    } catch {
      throw new Error("Gemini returned invalid JSON for student answer grading.");
    }

    const rawEvaluations = parsedGrading.evaluations ?? [];

    // Check for Unexpected Grading Question Numbers Returned by AI
    const extractedNumSet = new Set(
      rawQuestions
        .filter((q) => q.question_number !== null && q.question_number !== undefined)
        .map((q) => String(q.question_number).trim().toUpperCase())
    );

    let unexpectedAiQuestionsWarning: string | null = null;
    for (const g of rawEvaluations) {
      if (g.question_number) {
        const gNum = String(g.question_number).trim().toUpperCase();
        if (!extractedNumSet.has(gNum)) {
          unexpectedAiQuestionsWarning = `AI returned a grading result for question number '${g.question_number}' which was not found in the extracted question paper.`;
          isQpUnclear = true;
          console.warn(`[process-and-grade-paper] ${unexpectedAiQuestionsWarning}`);
        }
      }
    }

    // 8. Strict Alignment, Category Distinction, and Mark Clamping
    let totalMarksObtained = 0;
    let computedExtractedTotalMarks = 0;
    let allQuestionMarksReliable = true;
    let anyQuestionReviewRequired = false;

    const qpHasGenuineIssue = isQpUnclear && !isMissingCriteriaReason(qpReviewReason);
    let evaluationReviewReason: string | null = qpSequenceWarning ||
      unexpectedAiQuestionsWarning ||
      (qpHasGenuineIssue ? (qpReviewReason || "Question paper was unclear or incomplete.") : null);

    const evaluationQuestionsToInsert = rawQuestions.map((q) => {
      const rawQNum = q.question_number ? String(q.question_number).trim() : null;
      const rawQText = q.question_text ? String(q.question_text).trim() : null;
      const normQNum = rawQNum ? rawQNum.toUpperCase() : null;

      const matchingResults = normQNum
        ? rawEvaluations.filter(
            (e) => e.question_number && String(e.question_number).trim().toUpperCase() === normQNum
          )
        : [];

      const maxMarks = typeof q.maximum_marks === "number" && !isNaN(q.maximum_marks) && isFinite(q.maximum_marks) && q.maximum_marks > 0
        ? q.maximum_marks
        : 0;

      // Base review status from question extraction:
      // Discard any past flag that was solely about missing criteria in question paper
      let isReview = false;
      let reviewReason = "";

      if (q.review_required && q.review_reason && !isMissingCriteriaReason(q.review_reason)) {
        isReview = true;
        reviewReason = q.review_reason;
      }

      let awarded = 0;
      let studentAns: string | null = null;
      let feedback = "";
      let expectedAns: string | null = q.expected_answer || null;
      let markingCrit: string | null = q.marking_criteria || null;
      let expectedAnswerSource: "question_paper" | "ai_generated" | "missing" = q.expected_answer ? "question_paper" : "missing";
      let markingCriteriaSource: "question_paper" | "ai_generated" | "missing" = q.marking_criteria ? "question_paper" : "missing";

      if (!rawQNum) {
        studentAns = null;
        awarded = 0;
        feedback = "Question number is unreadable or unknown on question paper. Marks cannot be awarded.";
        isReview = true;
        reviewReason = reviewReason ? `${reviewReason} | Question number could not be determined from question paper.` : "Question number could not be determined from question paper.";
        allQuestionMarksReliable = false;
      } else if (!rawQText) {
        studentAns = null;
        awarded = 0;
        feedback = "Question text is unreadable or missing on question paper. Marks cannot be awarded.";
        isReview = true;
        reviewReason = reviewReason ? `${reviewReason} | Question text is unreadable or missing in question paper.` : "Question text is unreadable or missing in question paper.";
        allQuestionMarksReliable = false;
      } else if (maxMarks === 0) {
        studentAns = null;
        awarded = 0;
        feedback = "Maximum marks not specified on question paper. Marks cannot be awarded without a defined maximum.";
        isReview = true;
        reviewReason = reviewReason ? `${reviewReason} | Maximum marks not specified in question paper.` : "Maximum marks not specified in question paper. Marks cannot be awarded without a defined maximum.";
        allQuestionMarksReliable = false;
      } else if (matchingResults.length === 0) {
        studentAns = null;
        awarded = 0;
        feedback = "AI did not return an evaluation for this question.";
        isReview = true;
        reviewReason = reviewReason ? `${reviewReason} | AI did not return a grading result for this question.` : "AI did not return a grading result for this question.";
        allQuestionMarksReliable = false;
      } else if (matchingResults.length > 1) {
        studentAns = matchingResults[0].student_answer?.trim() || null;
        awarded = 0;
        feedback = "AI returned multiple conflicting grading results for this question.";
        isReview = true;
        reviewReason = `Multiple contradictory grading results returned by AI for question ${rawQNum}.`;
        allQuestionMarksReliable = false;
      } else {
        const match = matchingResults[0];
        studentAns = match.student_answer?.trim() || null;
        feedback = match.feedback?.trim() || "";

        // Resolve expected answer: Question paper takes precedence, then AI-generated provisional
        if (q.expected_answer && q.expected_answer.trim().length > 0) {
          expectedAns = q.expected_answer.trim();
          expectedAnswerSource = "question_paper";
        } else if (match.expected_answer && match.expected_answer.trim().length > 0) {
          expectedAns = match.expected_answer.trim();
          expectedAnswerSource = match.expected_answer_source === "question_paper" ? "question_paper" : "ai_generated";
        } else {
          expectedAns = null;
          expectedAnswerSource = "missing";
        }

        // Resolve marking criteria: Question paper takes precedence, then AI-generated provisional
        if (q.marking_criteria && q.marking_criteria.trim().length > 0) {
          markingCrit = q.marking_criteria.trim();
          markingCriteriaSource = "question_paper";
        } else if (match.marking_criteria && match.marking_criteria.trim().length > 0) {
          markingCrit = match.marking_criteria.trim();
          markingCriteriaSource = match.marking_criteria_source === "question_paper" ? "question_paper" : "ai_generated";
        } else {
          markingCrit = null;
          markingCriteriaSource = "missing";
        }

        const hasReliableCriteria = Boolean(
          (expectedAns && expectedAns.trim().length > 0) ||
          (markingCrit && markingCrit.trim().length > 0)
        );

        if (!hasReliableCriteria) {
          awarded = 0;
          isReview = true;
          reviewReason = reviewReason ? `${reviewReason} | Expected answer or marking criteria could not be reliably established or generated.` : "Expected answer or marking criteria could not be reliably established or generated. Marks cannot be awarded without reliable criteria.";
          feedback = feedback ? `${feedback} (0 marks awarded: Marking criteria unavailable.)` : "0 marks awarded: Expected answer or marking criteria unavailable.";
          allQuestionMarksReliable = false;
        } else {
          let rawAwarded = match.awarded_marks;
          if (typeof rawAwarded !== "number" || isNaN(rawAwarded) || !isFinite(rawAwarded)) {
            rawAwarded = 0;
            isReview = true;
            reviewReason = reviewReason ? `${reviewReason} | AI returned an invalid numeric mark.` : "AI returned an invalid numeric mark.";
          }

          if (rawAwarded < 0) {
            awarded = 0;
            isReview = true;
            reviewReason = reviewReason ? `${reviewReason} | Negative marks received from AI; adjusted to 0.` : "Negative marks received from AI; adjusted to 0.";
          } else if (rawAwarded > maxMarks) {
            awarded = maxMarks;
            isReview = true;
            reviewReason = reviewReason ? `${reviewReason} | AI awarded marks (${rawAwarded}) exceeded maximum marks (${maxMarks}); clamped.` : `AI awarded marks (${rawAwarded}) exceeded maximum marks (${maxMarks}); clamped.`;
          } else {
            awarded = Math.round(rawAwarded * 100) / 100;
          }
        }

        // Check match review reasons:
        // Do NOT flag review if the reason is solely missing criteria from question paper when we have AI-generated/valid criteria!
        if (match.review_required) {
          const matchReason = match.review_reason?.trim() || "";
          const isCriteriaOnlyMatchReason = isMissingCriteriaReason(matchReason);
          if (!isCriteriaOnlyMatchReason) {
            isReview = true;
            if (matchReason) {
              reviewReason = reviewReason ? `${reviewReason} | ${matchReason}` : matchReason;
            }
          }
        }

        if (!studentAns || studentAns === "[No answer detected]") {
          studentAns = "[No answer detected]";
          awarded = 0;
          isReview = true;
          reviewReason = reviewReason ? `${reviewReason} | No answer detected in student paper.` : "No answer detected in student paper.";
        } else if (studentAns.includes("[unclear]")) {
          isReview = true;
          reviewReason = reviewReason ? `${reviewReason} | Student handwriting contains [unclear] segments or is ambiguous.` : "Student handwriting contains [unclear] segments or is ambiguous.";
        }
      }

      if (isReview) {
        anyQuestionReviewRequired = true;
      }

      totalMarksObtained += awarded;
      computedExtractedTotalMarks += maxMarks;

      return {
        question_number: rawQNum,
        question_text: rawQText,
        maximum_marks: maxMarks,
        student_answer: studentAns,
        expected_answer: expectedAns,
        marking_criteria: markingCrit,
        expected_answer_source: expectedAnswerSource,
        marking_criteria_source: markingCriteriaSource,
        awarded_marks: awarded,
        feedback: feedback || (studentAns ? "Evaluated." : "No answer written for this question."),
        review_required: isReview,
        review_reason: isReview ? (reviewReason || "Flagged for teacher review.") : null,
      };
    });

    // 9. Handle Class Total Marks & Percentage Reliability Safely
    const officialClassTotal = Number(classRoom.total_marks) || 0;
    const finalExtractedMarks = extractedTotalMarks > 0 ? extractedTotalMarks : computedExtractedTotalMarks;
    const marksMatchClassTotal = allQuestionMarksReliable && officialClassTotal > 0 && Math.round(finalExtractedMarks * 100) === Math.round(officialClassTotal * 100);

    let percentageAvailable = false;
    let calculatedPercentage: number | null = null;
    let overallReviewRequired = anyQuestionReviewRequired || qpHasGenuineIssue;

    // Percentage can be calculated only when:
    // - all questions have reliable maximum marks
    // - total extracted question marks match class total marks
    // - every question has reliable expected answer/criteria
    // - no unresolved review issue exists
    if (marksMatchClassTotal && !overallReviewRequired) {
      percentageAvailable = true;
      calculatedPercentage = Math.round((totalMarksObtained / officialClassTotal) * 10000) / 100;
    } else {
      percentageAvailable = false;
      calculatedPercentage = null;
      overallReviewRequired = true;

      let mismatchMsg: string;
      if (!allQuestionMarksReliable) {
        mismatchMsg = "Some question maximum marks or marking criteria could not be reliably determined.";
      } else if (!marksMatchClassTotal) {
        mismatchMsg = `Extracted question marks (${finalExtractedMarks}) do not reliably match the configured class total marks (${officialClassTotal}).`;
      } else {
        mismatchMsg = "Evaluation requires teacher review before final percentage calculation.";
      }

      evaluationReviewReason = evaluationReviewReason
        ? `${evaluationReviewReason} | ${mismatchMsg}`
        : mismatchMsg;
    }

    const finalEvaluationStatus = overallReviewRequired ? "needs_review" : "completed";

    // 10. Atomic Database Persistence via PostgreSQL Transactional RPC
    const { data: savedEvaluation, error: rpcErr } = await adminClient.rpc(
      "save_provisional_paper_evaluation",
      {
        p_paper_id: paper.id,
        p_student_id: paper.student_id,
        p_class_id: paper.class_id,
        p_teacher_id: teacherId,
        p_status: finalEvaluationStatus,
        p_total_marks_obtained: Math.round(totalMarksObtained * 100) / 100,
        p_total_marks: officialClassTotal,
        p_percentage: calculatedPercentage,
        p_review_required: overallReviewRequired,
        p_review_reason: evaluationReviewReason || null,
        p_questions: evaluationQuestionsToInsert,
      }
    );

    if (rpcErr || !savedEvaluation) {
      throw new Error(`Atomic evaluation persistence failed: ${rpcErr?.message || "Unknown database error"}`);
    }

    // 11. Return Structured Provisional Result (HTTP 200)
    // Augment savedEvaluation questions with source flags for JSON response
    const questionsWithSources = (savedEvaluation.questions || []).map((savedQ: any) => {
      const match = evaluationQuestionsToInsert.find(
        (eq) => eq.question_number && savedQ.question_number &&
          String(eq.question_number).trim().toUpperCase() === String(savedQ.question_number).trim().toUpperCase()
      );
      return {
        ...savedQ,
        expected_answer_source: match?.expected_answer_source || (savedQ.expected_answer ? "ai_generated" : "missing"),
        marking_criteria_source: match?.marking_criteria_source || (savedQ.marking_criteria ? "ai_generated" : "missing"),
      };
    });

    return new Response(
      JSON.stringify({
        status: finalEvaluationStatus,
        percentageAvailable: percentageAvailable,
        evaluation: {
          id: savedEvaluation.id,
          paperId: savedEvaluation.paper_id,
          studentId: savedEvaluation.student_id,
          classId: savedEvaluation.class_id,
          teacherId: savedEvaluation.teacher_id,
          status: savedEvaluation.status,
          totalMarksObtained: savedEvaluation.total_marks_obtained,
          totalMarks: savedEvaluation.total_marks,
          extractedQuestionTotalMarks: finalExtractedMarks,
          percentage: savedEvaluation.percentage,
          percentageAvailable: percentageAvailable,
          reviewRequired: savedEvaluation.review_required,
          reviewReason: savedEvaluation.review_reason,
          createdAt: savedEvaluation.created_at,
          updatedAt: savedEvaluation.updated_at,
          questions: questionsWithSources,
        },
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: unknown) {
    console.error("[process-and-grade-paper] Pipeline error:", err);
    const errorMessage = err instanceof Error ? err.message : String(err);

    if (requestPaperId && adminClient) {
      try {
        await adminClient
          .from("student_papers")
          .update({ ocr_status: "failed", ocr_error: errorMessage })
          .eq("id", requestPaperId);
      } catch (dbErr) {
        console.warn("[process-and-grade-paper] Failed to update student_papers error status:", dbErr);
      }

      try {
        await adminClient
          .from("student_paper_evaluations")
          .update({
            status: "failed",
            review_required: true,
            review_reason: errorMessage,
            updated_at: new Date().toISOString(),
          })
          .eq("paper_id", requestPaperId);
      } catch (dbErr) {
        console.warn("[process-and-grade-paper] Failed to update student_paper_evaluations error status:", dbErr);
      }
    }

    let responseStatus = 500;
    let clientErrorMessage = errorMessage || "An unexpected error occurred during paper evaluation.";

    if (err instanceof TemporaryGeminiError) {
      if (err.status === 503) {
        responseStatus = 503;
        clientErrorMessage = "The AI grading service (Gemini) is temporarily experiencing high demand and is unavailable (HTTP 503). Retries were exhausted. Please wait a moment and try again.";
      } else if (err.status === 429) {
        responseStatus = 429;
        clientErrorMessage = "The AI grading service rate limit was exceeded (HTTP 429). Please wait a moment and try again.";
      } else if (err.status === 504) {
        responseStatus = 504;
        clientErrorMessage = "The AI grading service (Gemini) request timed out (HTTP 504). Retries were exhausted. Please wait a moment and try again.";
      }
    } else if (err instanceof PermanentGeminiError) {
      responseStatus = err.status;
    }

    return new Response(
      JSON.stringify({
        status: "failed",
        error: clientErrorMessage,
      }),
      { status: responseStatus, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
