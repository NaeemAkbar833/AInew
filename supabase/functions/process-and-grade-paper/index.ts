import { createClient } from "https://esm.sh/@supabase/supabase-js@2.48.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export const MAX_CONCURRENT_OCR = 3;

/**
 * Memory-safe Base64 conversion without quadratic string reallocations.
 * Uses native Buffer if present in Deno runtime, or bounded chunking with join().
 */
export function toBase64(uint8Array: Uint8Array): string {
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

/**
 * Bounded concurrency worker pool.
 * Processes items with at most `concurrencyLimit` active workers at a time,
 * preserving result array ordering corresponding to input `items`.
 */
export async function runWithConcurrency<T, R>(
  items: T[],
  concurrencyLimit: number,
  worker: (item: T, index: number) => Promise<R>
): Promise<R[]> {
  if (items.length === 0) return [];
  const limit = Math.max(1, Math.min(concurrencyLimit, items.length));
  const results: R[] = new Array(items.length);
  let nextIndex = 0;

  async function runWorker(): Promise<void> {
    while (nextIndex < items.length) {
      const currentIndex = nextIndex++;
      results[currentIndex] = await worker(items[currentIndex], currentIndex);
    }
  }

  const workers: Promise<void>[] = [];
  for (let w = 0; w < limit; w++) {
    workers.push(runWorker());
  }
  await Promise.all(workers);
  return results;
}

export class PermanentGeminiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(sanitizeErrorMessage(message));
    this.status = status;
    this.name = "PermanentGeminiError";
  }
}

export class TemporaryGeminiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(sanitizeErrorMessage(message));
    this.status = status;
    this.name = "TemporaryGeminiError";
  }
}

/**
 * Scrubs any potential API keys, Bearer tokens, JWTs, or Supabase keys from error/diagnostic strings
 * before logging or returning to the caller.
 */
export function sanitizeErrorMessage(raw: string | null | undefined): string {
  if (!raw) return "";
  return String(raw)
    .replace(/([?&]key=)[^&\s"']+/gi, "$1[REDACTED]")
    .replace(/\bAIza[0-9A-Za-z\-_]{20,}\b/g, "[REDACTED_API_KEY]")
    .replace(/\bBearer\s+[0-9A-Za-z\-._~+/]+=*\b/gi, "Bearer [REDACTED_TOKEN]")
    .replace(/\beyJ[0-9A-Za-z\-_]+\.[0-9A-Za-z\-_]+\.[0-9A-Za-z\-_]*\b/g, "[REDACTED_JWT]")
    .replace(/\b(?:sbp_|sb_secret_|sb_publishable_)[0-9A-Za-z\-_]+\b/g, "[REDACTED_SECRET]");
}

/**
 * Extracts concatenated text from all parts of the primary Gemini candidate response,
 * along with finishReason and promptFeedback blockReason.
 */
export function extractGeminiResponseText(resp: any): {
  text: string;
  finishReason: string | null;
  blockReason: string | null;
} {
  const blockReason = resp?.promptFeedback?.blockReason
    ? String(resp.promptFeedback.blockReason)
    : null;
  const candidate = resp?.candidates?.[0];
  const finishReason = candidate?.finishReason ? String(candidate.finishReason) : null;
  const parts = candidate?.content?.parts;
  if (!Array.isArray(parts) || parts.length === 0) {
    return { text: "", finishReason, blockReason };
  }
  const combined = parts
    .map((p: any) => (typeof p?.text === "string" ? p.text : ""))
    .join("")
    .trim();
  return { text: combined, finishReason, blockReason };
}

/**
 * Safely parses JSON from Gemini output, handling:
 * - Valid JSON
 * - JSON surrounded by Markdown code fences (```json ... ```)
 * - Leading/trailing non-JSON text around a balanced JSON object/array
 * - Unescaped control characters (\n, \r, \t) inside JSON string literals and trailing commas
 * Rejects empty responses and truncated/unbalanced JSON without ever inventing missing fields or closing braces.
 */
export function extractAndParseJson<T = any>(
  rawText: string | null | undefined
): { ok: true; value: T } | { ok: false; error: string; isTruncated: boolean } {
  if (rawText === null || rawText === undefined) {
    return { ok: false, error: "Gemini returned an empty response.", isTruncated: false };
  }

  let text = String(rawText).trim();
  if (text.length === 0) {
    return { ok: false, error: "Gemini returned an empty response.", isTruncated: false };
  }

  // 1. Strip surrounding Markdown code fences if present (e.g. ```json ... ``` or ``` ... ```)
  const fencedFullMatch = text.match(/^```(?:json|javascript|ts|typescript)?\s*\r?\n?([\s\S]*?)\r?\n?```\s*$/i);
  if (fencedFullMatch) {
    text = fencedFullMatch[1].trim();
  } else if (text.startsWith("```")) {
    // Opening fence without a closing fence indicates truncation mid-block
    const strippedOpen = text.replace(/^```(?:json|javascript|ts|typescript)?\s*\r?\n?/i, "");
    const closingFenceIdx = strippedOpen.lastIndexOf("```");
    if (closingFenceIdx !== -1) {
      text = strippedOpen.slice(0, closingFenceIdx).trim();
    } else {
      return {
        ok: false,
        error: "Gemini response was truncated inside a Markdown code block.",
        isTruncated: true,
      };
    }
  }

  if (text.length === 0) {
    return { ok: false, error: "Gemini returned an empty JSON block.", isTruncated: false };
  }

  // 2. Direct JSON.parse attempt
  try {
    const parsed = JSON.parse(text);
    if (parsed !== null && typeof parsed === "object") {
      return { ok: true, value: parsed as T };
    }
  } catch {
    // Fall through to balanced extraction and safe formatting repair
  }

  // 3. Locate the first '{' or '[' and trace balanced JSON structure respecting strings and escapes
  const firstBrace = text.indexOf("{");
  const firstBracket = text.indexOf("[");
  let startIdx = -1;
  if (firstBrace !== -1 && (firstBracket === -1 || firstBrace < firstBracket)) {
    startIdx = firstBrace;
  } else if (firstBracket !== -1) {
    startIdx = firstBracket;
  }

  if (startIdx === -1) {
    return {
      ok: false,
      error: "Gemini response did not contain a JSON object or array.",
      isTruncated: false,
    };
  }

  const stack: string[] = [];
  let inString = false;
  let escaped = false;
  let endIdx = -1;

  for (let i = startIdx; i < text.length; i++) {
    const ch = text[i];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (ch === "\\") {
        escaped = true;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }

    if (ch === '"') {
      inString = true;
    } else if (ch === "{") {
      stack.push("}");
    } else if (ch === "[") {
      stack.push("]");
    } else if (ch === "}" || ch === "]") {
      if (stack.length === 0 || stack[stack.length - 1] !== ch) {
        return {
          ok: false,
          error: "Gemini returned malformed JSON with mismatched brackets/braces.",
          isTruncated: false,
        };
      }
      stack.pop();
      if (stack.length === 0) {
        endIdx = i;
        break;
      }
    }
  }

  if (inString || stack.length > 0 || endIdx === -1) {
    return {
      ok: false,
      error: "Gemini returned truncated or incomplete JSON.",
      isTruncated: true,
    };
  }

  const extractedSlice = text.slice(startIdx, endIdx + 1);

  // Try parsing the balanced slice directly
  try {
    const parsed = JSON.parse(extractedSlice);
    if (parsed !== null && typeof parsed === "object") {
      return { ok: true, value: parsed as T };
    }
  } catch {
    // Fall through to safe string-literal control-char and trailing-comma repair
  }

  // 4. Repair unescaped literal control chars (\n, \r, \t) inside JSON string literals and trailing commas before } or ]
  const repairedChars: string[] = [];
  let sInString = false;
  let sEscaped = false;

  for (let i = 0; i < extractedSlice.length; i++) {
    const ch = extractedSlice[i];
    if (sInString) {
      if (sEscaped) {
        sEscaped = false;
        repairedChars.push(ch);
      } else if (ch === "\\") {
        sEscaped = true;
        repairedChars.push(ch);
      } else if (ch === '"') {
        sInString = false;
        repairedChars.push(ch);
      } else if (ch === "\n") {
        repairedChars.push("\\n");
      } else if (ch === "\r") {
        repairedChars.push("\\r");
      } else if (ch === "\t") {
        repairedChars.push("\\t");
      } else {
        repairedChars.push(ch);
      }
    } else {
      if (ch === '"') {
        sInString = true;
        repairedChars.push(ch);
      } else if (ch === ",") {
        // Check if only whitespace follows before '}' or ']'
        let j = i + 1;
        while (j < extractedSlice.length && /\s/.test(extractedSlice[j])) {
          j++;
        }
        if (j < extractedSlice.length && (extractedSlice[j] === "}" || extractedSlice[j] === "]")) {
          // Omit trailing comma
          continue;
        }
        repairedChars.push(ch);
      } else {
        repairedChars.push(ch);
      }
    }
  }

  const repairedJson = repairedChars.join("");
  try {
    const parsed = JSON.parse(repairedJson);
    if (parsed !== null && typeof parsed === "object") {
      return { ok: true, value: parsed as T };
    }
  } catch (err: unknown) {
    return {
      ok: false,
      error: `Gemini returned invalid JSON: ${sanitizeErrorMessage(err instanceof Error ? err.message : String(err))}`,
      isTruncated: false,
    };
  }

  return {
    ok: false,
    error: "Gemini returned non-object JSON.",
    isTruncated: false,
  };
}

/**
 * Validates that a parsed JSON value conforms to the expected grading schema (`{ evaluations: RawGradingResult[] }`)
 * without inventing missing answers, marks, or explanations.
 */
export function validateGradingResponseSchema(
  parsed: unknown,
  expectedQuestionNumbers: string[] = []
): { valid: boolean; evaluations: RawGradingResult[]; error?: string } {
  if (!parsed || typeof parsed !== "object") {
    return { valid: false, evaluations: [], error: "Grading response is not a JSON object." };
  }

  const rawList = Array.isArray(parsed)
    ? parsed
    : Array.isArray((parsed as any).evaluations)
    ? (parsed as any).evaluations
    : null;

  if (!rawList) {
    return {
      valid: false,
      evaluations: [],
      error: "Grading response is missing required 'evaluations' array.",
    };
  }

  if (expectedQuestionNumbers.length > 0 && rawList.length === 0) {
    return {
      valid: false,
      evaluations: [],
      error: "Grading response returned an empty 'evaluations' array for a non-empty question paper.",
    };
  }

  const validated: RawGradingResult[] = [];
  for (let i = 0; i < rawList.length; i++) {
    const item = rawList[i];
    if (!item || typeof item !== "object" || Array.isArray(item)) {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry at index ${i} is not a valid object.`,
      };
    }

    const qNum = item.question_number;
    if (qNum !== undefined && qNum !== null && typeof qNum !== "string" && typeof qNum !== "number") {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry at index ${i} has invalid 'question_number' type (${typeof qNum}).`,
      };
    }

    const normalizedQNum =
      qNum === undefined || qNum === null ? null : String(qNum).trim() || null;

    if (item.is_attempted !== undefined && item.is_attempted !== null && typeof item.is_attempted !== "boolean") {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has non-boolean 'is_attempted'.`,
      };
    }

    let awardedMarks: number | undefined = undefined;
    if (item.awarded_marks !== undefined && item.awarded_marks !== null) {
      if (typeof item.awarded_marks === "number" && Number.isFinite(item.awarded_marks)) {
        awardedMarks = item.awarded_marks;
      } else if (
        typeof item.awarded_marks === "string" &&
        item.awarded_marks.trim().length > 0 &&
        Number.isFinite(Number(item.awarded_marks.trim()))
      ) {
        awardedMarks = Number(item.awarded_marks.trim());
      } else {
        return {
          valid: false,
          evaluations: [],
          error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has invalid 'awarded_marks' value.`,
        };
      }
    } else if (item.is_attempted === true) {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry for attempted question '${normalizedQNum ?? i}' is missing numeric 'awarded_marks'.`,
      };
    }

    if (
      item.student_answer !== undefined &&
      item.student_answer !== null &&
      typeof item.student_answer !== "string"
    ) {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has non-string 'student_answer'.`,
      };
    }

    if (
      item.feedback !== undefined &&
      item.feedback !== null &&
      typeof item.feedback !== "string"
    ) {
      return {
        valid: false,
        evaluations: [],
        error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has non-string 'feedback'.`,
      };
    }

    let sourceSegments: SourceAnswerSegment[] | null = null;
    if (item.source_segments !== undefined && item.source_segments !== null) {
      if (!Array.isArray(item.source_segments)) {
        return {
          valid: false,
          evaluations: [],
          error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has non-array 'source_segments'.`,
        };
      }
      sourceSegments = [];
      for (const seg of item.source_segments) {
        if (!seg || typeof seg !== "object" || Array.isArray(seg)) {
          return {
            valid: false,
            evaluations: [],
            error: `Grading evaluation entry for question '${normalizedQNum ?? i}' contains an invalid segment in 'source_segments'.`,
          };
        }
        if (
          seg.verbatim_text !== undefined &&
          seg.verbatim_text !== null &&
          typeof seg.verbatim_text !== "string"
        ) {
          return {
            valid: false,
            evaluations: [],
            error: `Grading evaluation entry for question '${normalizedQNum ?? i}' has non-string 'verbatim_text' in 'source_segments'.`,
          };
        }
        sourceSegments.push({
          page_number:
            typeof seg.page_number === "number" && Number.isFinite(seg.page_number)
              ? seg.page_number
              : null,
          verbatim_text: typeof seg.verbatim_text === "string" ? seg.verbatim_text : null,
        });
      }
    }

    validated.push({
      question_number: normalizedQNum,
      is_attempted: typeof item.is_attempted === "boolean" ? item.is_attempted : null,
      source_segments: sourceSegments,
      student_answer: typeof item.student_answer === "string" ? item.student_answer : null,
      continuation_detected:
        typeof item.continuation_detected === "boolean" ? item.continuation_detected : null,
      mapping_ambiguous:
        typeof item.mapping_ambiguous === "boolean" ? item.mapping_ambiguous : null,
      mapping_review_reason:
        typeof item.mapping_review_reason === "string" ? item.mapping_review_reason : null,
      expected_answer: typeof item.expected_answer === "string" ? item.expected_answer : null,
      marking_criteria: typeof item.marking_criteria === "string" ? item.marking_criteria : null,
      expected_answer_source: item.expected_answer_source ?? null,
      marking_criteria_source: item.marking_criteria_source ?? null,
      awarded_marks: awardedMarks,
      feedback: typeof item.feedback === "string" ? item.feedback : null,
      review_required: typeof item.review_required === "boolean" ? item.review_required : undefined,
      review_reason: typeof item.review_reason === "string" ? item.review_reason : null,
    });
  }

  return { valid: true, evaluations: validated };
}

export interface GeminiRetryOptions {
  timeoutMs?: number;
  maxAttempts?: number;
  retryDelays?: number[];
  fetchImpl?: typeof fetch;
}

export async function callGeminiWithRetry(
  geminiApiKey: string,
  payload: unknown,
  timeoutMsOrOptions: number | GeminiRetryOptions = 120000
): Promise<any> {
  const opts: GeminiRetryOptions =
    typeof timeoutMsOrOptions === "number"
      ? { timeoutMs: timeoutMsOrOptions }
      : timeoutMsOrOptions;
  const timeoutMs = opts.timeoutMs ?? 120000;
  const maxAttempts = opts.maxAttempts ?? 3;
  const retryDelays = opts.retryDelays ?? [2000, 5000];
  const fetchFn = opts.fetchImpl ?? fetch;
  const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${geminiApiKey}`;

  // Serialize payload to string ONCE before the retry loop to prevent repetitive multi-MB memory allocations
  const bodyString = typeof payload === "string" ? payload : JSON.stringify(payload);

  let lastError: Error = new TemporaryGeminiError("Gemini API call failed.", 500);

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const resp = await fetchFn(geminiUrl, {
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
      const errorText = sanitizeErrorMessage(await resp.text().catch(() => ""));

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
      if (delay > 0) await sleep(delay);
    } catch (err: unknown) {
      clearTimeout(timeoutId);

      // If this is a permanent error, re-throw immediately and do NOT retry!
      if (err instanceof PermanentGeminiError) {
        throw err;
      }

      const isAbort = err instanceof Error && err.name === "AbortError";
      lastError = isAbort
        ? new TemporaryGeminiError("Gemini API call timed out.", 504)
        : err instanceof TemporaryGeminiError
        ? err
        : new TemporaryGeminiError(
            sanitizeErrorMessage(err instanceof Error ? err.message : String(err)),
            500
          );

      if (attempt === maxAttempts) {
        throw lastError;
      }

      const delay = retryDelays[attempt - 1] ?? 5000;
      console.log(`[process-and-grade-paper] Retrying Gemini call after transient failure in ${delay}ms...`);
      if (delay > 0) await sleep(delay);
    }
  }

  throw lastError;
}

/**
 * Executes a Gemini grading request with bounded retries for both transient HTTP errors
 * and malformed/truncated/empty JSON outputs, validating the response against the grading schema.
 * Never fabricates missing answers or turns a permanent failure into a fake successful result.
 */
export async function callGeminiGradingWithRetry(
  geminiApiKey: string,
  gradingPayload: unknown,
  expectedQuestionNumbers: string[] = [],
  options: GeminiRetryOptions = {}
): Promise<{ evaluations: RawGradingResult[] }> {
  const maxAttempts = options.maxAttempts ?? 3;
  const retryDelays = options.retryDelays ?? [2000, 5000];
  let lastError: Error = new Error("Gemini returned invalid JSON for student answer grading.");

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    let resp: any;
    try {
      // Perform single transport attempt per outer loop iteration so total attempts stay strictly bounded
      resp = await callGeminiWithRetry(geminiApiKey, gradingPayload, {
        ...options,
        maxAttempts: 1,
      });
    } catch (err: unknown) {
      if (err instanceof PermanentGeminiError) {
        throw err;
      }
      lastError =
        err instanceof Error
          ? err
          : new TemporaryGeminiError(sanitizeErrorMessage(String(err)), 500);
      if (attempt === maxAttempts) {
        throw lastError;
      }
      const delay = retryDelays[attempt - 1] ?? 2000;
      console.warn(
        `[process-and-grade-paper] Grading HTTP attempt ${attempt}/${maxAttempts} failed (${sanitizeErrorMessage(lastError.message)}). Retrying in ${delay}ms...`
      );
      if (delay > 0) await sleep(delay);
      continue;
    }

    const { text, finishReason, blockReason } = extractGeminiResponseText(resp);
    if (blockReason) {
      throw new PermanentGeminiError(
        `Gemini blocked the grading request (blockReason: ${blockReason}).`,
        400
      );
    }

    const parsedResult = extractAndParseJson(text);
    if (!parsedResult.ok) {
      const reasonSuffix =
        finishReason === "MAX_TOKENS" || parsedResult.isTruncated
          ? "Response was truncated before JSON completion."
          : parsedResult.error;
      lastError = new Error(
        sanitizeErrorMessage(`Gemini returned invalid JSON for student answer grading. ${reasonSuffix}`)
      );
    } else {
      const schemaCheck = validateGradingResponseSchema(
        parsedResult.value,
        expectedQuestionNumbers
      );
      if (schemaCheck.valid) {
        return { evaluations: schemaCheck.evaluations };
      }
      lastError = new Error(
        sanitizeErrorMessage(`Gemini returned invalid JSON for student answer grading. ${schemaCheck.error}`)
      );
    }

    if (attempt < maxAttempts) {
      const delay = retryDelays[attempt - 1] ?? 2000;
      console.warn(
        `[process-and-grade-paper] Grading JSON validation failed on attempt ${attempt}/${maxAttempts} (${lastError.message}). Retrying in ${delay}ms...`
      );
      if (delay > 0) await sleep(delay);
    }
  }

  throw lastError;
}


export interface SelectionRuleMeta {
  selectionGroup: string;
  requiredAttempts: number;
  totalInGroup: number;
  isOrChoice: boolean;
}

export interface RawExtractedQuestion {
  page_number?: number | null;
  question_number?: string | null;
  question_text?: string | null;
  maximum_marks?: number | null;
  section?: string | null;
  selection_group?: string | null;
  group_required_attempts?: number | null;
  group_total_questions?: number | null;
  is_or_choice?: boolean | null;
  or_partner_question_number?: string | null;
  expected_answer?: string | null;
  marking_criteria?: string | null;
  review_required?: boolean;
  review_reason?: string | null;
  sort_order?: number;
}

export interface RawPageExtractionResult {
  unclear_page?: boolean;
  page_instructions?: string[] | null;
  equal_marks_per_question?: number | null;
  required_attempts_in_exam?: number | null;
  total_available_questions_in_exam?: number | null;
  questions?: RawExtractedQuestion[];
}

export interface SourceAnswerSegment {
  page_number?: number | null;
  verbatim_text?: string | null;
}

export interface RawGradingResult {
  question_number?: string | null;
  is_attempted?: boolean | null;
  source_segments?: SourceAnswerSegment[] | null;
  student_answer?: string | null;
  continuation_detected?: boolean | null;
  mapping_ambiguous?: boolean | null;
  mapping_review_reason?: string | null;
  expected_answer?: string | null;
  marking_criteria?: string | null;
  expected_answer_source?: "question_paper" | "ai_generated" | "missing" | null;
  marking_criteria_source?: "question_paper" | "ai_generated" | "missing" | null;
  awarded_marks?: number;
  feedback?: string | null;
  review_required?: boolean;
  review_reason?: string | null;
}

export function isMissingCriteriaReason(reason: string | null | undefined): boolean {
  if (!reason) return false;
  const lower = reason.toLowerCase().trim();
  return lower.includes("expected answer or marking criteria") ||
         lower.includes("marking criteria could not be reliably established") ||
         lower.includes("expected answer could not be reliably established") ||
         lower.includes("marking criteria unavailable") ||
         lower.includes("criteria unavailable") ||
         lower.includes("criteria could not be reliably established");
}

const SELECTION_RULE_REGEX = /^\s*\[Selection Rule:\s*group=([^\s|\]]+)\s*\|\s*attempt=(\d+)\/(\d+)(?:\s*\|\s*type=(or|optional))?\]\s*/i;

export function parseSelectionRuleFromCriteria(criteria: string | null | undefined): SelectionRuleMeta | null {
  if (!criteria) return null;
  const match = criteria.match(SELECTION_RULE_REGEX);
  if (!match) return null;
  const selectionGroup = match[1].trim();
  const requiredAttempts = parseInt(match[2], 10);
  const totalInGroup = parseInt(match[3], 10);
  const typeStr = (match[4] || "").toLowerCase();
  const isOrChoice = typeStr === "or" || selectionGroup.toUpperCase().startsWith("OR_");
  if (isNaN(requiredAttempts) || isNaN(totalInGroup) || requiredAttempts <= 0 || totalInGroup <= 0) {
    return null;
  }
  return {
    selectionGroup,
    requiredAttempts,
    totalInGroup,
    isOrChoice,
  };
}

export function stripSelectionRuleTag(criteria: string | null | undefined): string | null {
  if (!criteria) return null;
  const stripped = criteria.replace(SELECTION_RULE_REGEX, "").trim();
  return stripped.length > 0 ? stripped : null;
}

export function attachSelectionRuleToCriteria(
  criteria: string | null | undefined,
  rule: SelectionRuleMeta | null
): string | null {
  const base = stripSelectionRuleTag(criteria);
  if (!rule || rule.requiredAttempts >= rule.totalInGroup) {
    return base;
  }
  const tag = `[Selection Rule: group=${rule.selectionGroup} | attempt=${rule.requiredAttempts}/${rule.totalInGroup}${rule.isOrChoice ? " | type=or" : ""}]`;
  return base ? `${tag}\n${base}` : tag;
}

export function getSelectionRuleForQuestion(q: RawExtractedQuestion): SelectionRuleMeta | null {
  const fromCriteria = parseSelectionRuleFromCriteria(q.marking_criteria);
  if (fromCriteria) return fromCriteria;

  if (
    q.selection_group &&
    typeof q.group_required_attempts === "number" &&
    typeof q.group_total_questions === "number" &&
    q.group_required_attempts > 0 &&
    q.group_total_questions > q.group_required_attempts
  ) {
    const grp = String(q.selection_group).trim().replace(/\s+/g, "_").toUpperCase();
    return {
      selectionGroup: grp,
      requiredAttempts: q.group_required_attempts,
      totalInGroup: q.group_total_questions,
      isOrChoice: Boolean(q.is_or_choice) || grp.startsWith("OR_"),
    };
  }
  return null;
}

const WORD_NUMBER_MAP: Record<string, number> = {
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  six: 6,
  seven: 7,
  eight: 8,
  nine: 9,
  ten: 10,
  eleven: 11,
  twelve: 12,
  thirteen: 13,
  fourteen: 14,
  fifteen: 15,
  sixteen: 16,
  seventeen: 17,
  eighteen: 18,
  nineteen: 19,
  twenty: 20,
};

const ROMAN_NUMERAL_MAP: Record<string, number> = {
  I: 1,
  II: 2,
  III: 3,
  IV: 4,
  V: 5,
  VI: 6,
  VII: 7,
  VIII: 8,
  IX: 9,
  X: 10,
  XI: 11,
  XII: 12,
};

function parseWordOrDigitCount(token: string | null | undefined): number | null {
  if (!token) return null;
  const clean = token.trim().toLowerCase();
  if (/^\d+$/.test(clean)) {
    const val = parseInt(clean, 10);
    return val > 0 ? val : null;
  }
  return WORD_NUMBER_MAP[clean] ?? null;
}

/**
 * Normalizes a question number label (e.g., "Q1", "1.", "2 (a)", "1. Q3") into a canonical form (e.g., "1", "2(A)", "3").
 */
export function normalizeQuestionNumber(raw: string | null | undefined): string {
  if (!raw) return "";
  let s = String(raw).trim();
  // Strip outer Markdown emphasis/heading tokens (e.g. "**Q1**", "### Q1", "*Q1*")
  s = s.replace(/^[#*_`~\s]+|[#*_`~\s]+$/g, "");
  // If prefixed by an attempt serial number followed by an explicit question header (e.g. "1. Q3", "2) Ans 1", "(3) Question 4"),
  // strip the leading attempt serial number so the actual question identity ("Q3", "Ans 1", "Question 4") is preserved.
  s = s.replace(
    /^(?:\(\s*\d+\s*\)|\[\s*\d+\s*\]|\d+\s*[\.\)\:\-\–\—]+)\s*(?=(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question\b|ques\b|que\b|q\.?\s*n\.?|q(?=[\s\.\:\-\#\d\(\[]))|(?:ans(?:wer)?|sol(?:ution)?)(?=[\s\.\:\-\#\d\(\[]))/i,
    ""
  );
  s = s.replace(
    /^(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:no|num|number)\.?)\s*[\.\:\-\#\)\]\–\—\s]*/i,
    ""
  );
  s = s.replace(
    /\s*(?:\(\s*(?:ans(?:wer)?|sol(?:ution)?|cont(?:d\.?|inued))\.?\s*\)|[\.\:\-\)\]\–\—]*\s*(?:ans(?:wer)?|sol(?:ution)?|cont(?:d\.?|inued))\b).*$/i,
    ""
  );
  s = s.replace(/^[#*_`~\.\:\-\–\—\s]+|[#*_`~\.\:\-\–\—\s]+$/g, "");
  // Unwrap outer matching (...) or [...]
  while (
    (s.startsWith("(") && s.endsWith(")") && !s.slice(1, -1).includes(")")) ||
    (s.startsWith("[") && s.endsWith("]") && !s.slice(1, -1).includes("]"))
  ) {
    s = s.slice(1, -1).trim();
  }
  // Strip explicit prefix again if it was inside parens like "(Q4)" or "[Ans 4]"
  s = s.replace(
    /^(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:no|num|number)\.?)\s*[\.\:\-\#\)\]\–\—\s]*/i,
    ""
  );
  // Strip unmatched trailing ")" or "]" (e.g. "1)" -> "1", but keep "1(a)" -> "1(A)")
  if (s.endsWith(")") && !s.includes("(")) {
    s = s.replace(/[\)\s]+$/g, "");
  }
  if (s.endsWith("]") && !s.includes("[")) {
    s = s.replace(/[\]\s]+$/g, "");
  }
  s = s.replace(/\s*\(\s*([a-zA-Z0-9]+)\s*\)/g, "($1)");
  return s.trim().toUpperCase();
}

const ROMAN_SUBPART_VALUES: Record<string, number> = {
  I: 1,
  II: 2,
  III: 3,
  IV: 4,
  V: 5,
  VI: 6,
  VII: 7,
  VIII: 8,
  IX: 9,
  X: 10,
  XI: 11,
  XII: 12,
};

interface ParsedQuestionSortKey {
  hasNumber: boolean;
  primaryNumber: number;
  subparts: string[];
  normalized: string;
}

function parseQuestionSortKey(raw: string | null | undefined): ParsedQuestionSortKey {
  const norm = normalizeQuestionNumber(raw);
  if (!norm) {
    return {
      hasNumber: false,
      primaryNumber: Number.MAX_SAFE_INTEGER,
      subparts: [],
      normalized: "",
    };
  }

  // Match leading digits, or word/Roman numeral when used as primary question number
  const leadingDigitsMatch = norm.match(/^(\d+)(.*)$/);
  if (leadingDigitsMatch) {
    const primaryNumber = parseInt(leadingDigitsMatch[1], 10);
    const rest = leadingDigitsMatch[2].trim();
    const subparts: string[] = [];
    if (rest.length > 0) {
      const tokenRegex = /\(([A-Z0-9]+)\)|\.(\d+|[A-Z]+)|([A-Z]+|\d+)/g;
      let m: RegExpExecArray | null;
      while ((m = tokenRegex.exec(rest)) !== null) {
        const tok = (m[1] || m[2] || m[3] || "").trim().toUpperCase();
        if (tok) subparts.push(tok);
      }
    }
    return {
      hasNumber: true,
      primaryNumber,
      subparts,
      normalized: norm,
    };
  }

  const wordOrRoman = parseWordOrDigitCount(norm);
  if (wordOrRoman !== null) {
    return {
      hasNumber: true,
      primaryNumber: wordOrRoman,
      subparts: [],
      normalized: norm,
    };
  }

  return {
    hasNumber: false,
    primaryNumber: Number.MAX_SAFE_INTEGER,
    subparts: [norm],
    normalized: norm,
  };
}

function compareSubpartTokens(a: string, b: string): number {
  if (a === b) return 0;
  const aIsNum = /^\d+$/.test(a);
  const bIsNum = /^\d+$/.test(b);
  if (aIsNum && bIsNum) {
    return parseInt(a, 10) - parseInt(b, 10);
  }
  const aRoman = ROMAN_SUBPART_VALUES[a];
  const bRoman = ROMAN_SUBPART_VALUES[b];
  if (aRoman !== undefined && bRoman !== undefined && (a.length > 1 || b.length > 1)) {
    return aRoman - bRoman;
  }
  return a.localeCompare(b);
}

/**
 * Compares two question number labels in natural numerical sequence
 * (e.g., "1" < "2" < "2(a)" < "2(b)" < "2(i)" < "2(ii)" < "3" < "10").
 */
export function compareQuestionNumbers(
  a: string | null | undefined,
  b: string | null | undefined
): number {
  const keyA = parseQuestionSortKey(a);
  const keyB = parseQuestionSortKey(b);

  if (keyA.hasNumber !== keyB.hasNumber) {
    return keyA.hasNumber ? -1 : 1;
  }
  if (keyA.primaryNumber !== keyB.primaryNumber) {
    return keyA.primaryNumber - keyB.primaryNumber;
  }

  const minLen = Math.min(keyA.subparts.length, keyB.subparts.length);
  for (let i = 0; i < minLen; i++) {
    const cmp = compareSubpartTokens(keyA.subparts[i], keyB.subparts[i]);
    if (cmp !== 0) return cmp;
  }

  if (keyA.subparts.length !== keyB.subparts.length) {
    return keyA.subparts.length - keyB.subparts.length;
  }

  return keyA.normalized.localeCompare(keyB.normalized);
}

/**
 * Deterministically sorts question objects in natural numerical order while preserving
 * each question's complete attached properties and stable relative order for ties/unnumbered items.
 */
export function sortQuestionsNumerically<T extends { question_number?: string | null }>(
  questions: T[]
): T[] {
  return questions
    .map((item, index) => ({ item, index }))
    .sort((a, b) => {
      const cmp = compareQuestionNumbers(a.item.question_number, b.item.question_number);
      return cmp !== 0 ? cmp : a.index - b.index;
    })
    .map((entry) => entry.item);
}


/**
 * Resolves optional question groups, internal OR choices, and equal-mark allocation rules
 * from question paper instructions and extracted question metadata.
 */
export function resolveEqualMarksAndSelectionRules(
  questions: RawExtractedQuestion[],
  pageExtractions: RawPageExtractionResult[],
  officialClassTotal: number
): {
  questions: RawExtractedQuestion[];
  inferredEqualMarks: boolean;
  ambiguousMarksWarning: string | null;
} {
  const allInstructions: string[] = [];
  let explicitEqualMarksPerQuestion: number | null = null;
  let examRequiredAttempts: number | null = null;
  let examTotalAvailable: number | null = null;

  for (const pe of pageExtractions) {
    if (Array.isArray(pe.page_instructions)) {
      for (const inst of pe.page_instructions) {
        if (inst && typeof inst === "string" && inst.trim().length > 0) {
          allInstructions.push(inst.trim());
        }
      }
    }
    if (typeof pe.equal_marks_per_question === "number" && pe.equal_marks_per_question > 0) {
      explicitEqualMarksPerQuestion = pe.equal_marks_per_question;
    }
    if (typeof pe.required_attempts_in_exam === "number" && pe.required_attempts_in_exam > 0) {
      examRequiredAttempts = pe.required_attempts_in_exam;
    }
    if (typeof pe.total_available_questions_in_exam === "number" && pe.total_available_questions_in_exam > 0) {
      examTotalAvailable = pe.total_available_questions_in_exam;
    }
  }

  const combinedInstructions = allInstructions.join(" \n ");
  const numTokPattern = "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)";

  // 1. Parse instruction text for "Attempt any K out of N", "Attempt any K questions", "Attempt any three", etc.
  if (!examRequiredAttempts) {
    const attemptOutOfRegex = new RegExp(
      `(?:attempt|answer|solve|do|choose)\\s+any\\s+(${numTokPattern})\\s+(?:questions?\\s+)?(?:out\\s+of|from|of)\\s+(${numTokPattern})`,
      "i"
    );
    const attemptOutOfMatch = combinedInstructions.match(attemptOutOfRegex);
    if (attemptOutOfMatch) {
      examRequiredAttempts = parseWordOrDigitCount(attemptOutOfMatch[1]);
      examTotalAvailable = parseWordOrDigitCount(attemptOutOfMatch[2]);
    } else {
      const attemptAnyRegex = new RegExp(
        `(?:attempt|answer|solve|do|choose)\\s+any\\s+(${numTokPattern})(?:\\s+questions?|\\s+from\\s+the\\s+following|\\b)`,
        "i"
      );
      const attemptAnyMatch = combinedInstructions.match(attemptAnyRegex);
      if (attemptAnyMatch && !/section\s+[a-z0-9]/i.test(combinedInstructions)) {
        examRequiredAttempts = parseWordOrDigitCount(attemptAnyMatch[1]);
        examTotalAvailable = questions.length;
      }
    }
  }

  // Parse section-level optional rules from instructions: e.g. "Attempt any 4 questions from Section A"
  const sectionRules = new Map<string, { required: number; total?: number }>();
  const secRegex1 = new RegExp(
    `(?:attempt|answer|solve|do|choose)\\s+any\\s+(${numTokPattern})\\s+(?:questions?\\s+)?(?:(?:out\\s+of|of)\\s+(${numTokPattern})\\s+)?(?:from|in)\\s+section\\s+([a-z0-9]+)`,
    "gi"
  );
  let secMatch: RegExpExecArray | null;
  while ((secMatch = secRegex1.exec(combinedInstructions)) !== null) {
    const reqCount = parseWordOrDigitCount(secMatch[1]);
    const totCount = parseWordOrDigitCount(secMatch[2]) ?? undefined;
    const secName = `SECTION_${secMatch[3].toUpperCase()}`;
    if (reqCount && reqCount > 0) {
      sectionRules.set(secName, { required: reqCount, total: totCount });
    }
  }
  const secRegex2 = new RegExp(
    `section\\s+([a-z0-9]+)\\s*[:\\-–]\\s*(?:attempt|answer|solve|do|choose)\\s+any\\s+(${numTokPattern})(?:\\s*(?:out\\s+of|of)\\s*(${numTokPattern}))?`,
    "gi"
  );
  while ((secMatch = secRegex2.exec(combinedInstructions)) !== null) {
    const secName = `SECTION_${secMatch[1].toUpperCase()}`;
    const reqCount = parseWordOrDigitCount(secMatch[2]);
    const totCount = parseWordOrDigitCount(secMatch[3]) ?? undefined;
    if (reqCount && reqCount > 0) {
      sectionRules.set(secName, { required: reqCount, total: totCount });
    }
  }

  // 2. Detect internal OR choices between questions
  //    a) Explicit `is_or_choice` or `or_partner_question_number`
  //    b) Instruction or question_text mentioning "Q2(a) OR Q2(b)" or leading/trailing "OR"
  for (let i = 0; i < questions.length; i++) {
    const q = questions[i];
    const existingRule = getSelectionRuleForQuestion(q);
    if (existingRule) {
      q.selection_group = existingRule.selectionGroup;
      q.group_required_attempts = existingRule.requiredAttempts;
      q.group_total_questions = existingRule.totalInGroup;
      q.is_or_choice = existingRule.isOrChoice;
      continue;
    }

    if (q.or_partner_question_number && q.question_number) {
      const normA = normalizeQuestionNumber(q.question_number);
      const normB = normalizeQuestionNumber(q.or_partner_question_number);
      const pairKey = [normA, normB].sort().join("_");
      const partner = questions.find(
        (other) => other !== q && normalizeQuestionNumber(other.question_number) === normB
      );
      if (partner) {
        const grpName = `OR_${pairKey}`;
        q.selection_group = grpName;
        q.group_required_attempts = 1;
        q.group_total_questions = 2;
        q.is_or_choice = true;
        partner.selection_group = grpName;
        partner.group_required_attempts = 1;
        partner.group_total_questions = 2;
        partner.is_or_choice = true;
      }
    }
  }

  // Also check adjacent questions with same base question number where one has `is_or_choice` or "OR" in text
  for (let i = 0; i < questions.length - 1; i++) {
    const q1 = questions[i];
    const q2 = questions[i + 1];
    if (q1.selection_group || q2.selection_group) continue;
    const n1 = normalizeQuestionNumber(q1.question_number);
    const n2 = normalizeQuestionNumber(q2.question_number);
    const base1 = n1.match(/^(\d+)/)?.[1];
    const base2 = n2.match(/^(\d+)/)?.[1];
    const hasOrCue =
      Boolean(q1.is_or_choice) ||
      Boolean(q2.is_or_choice) ||
      /\bOR\s*$/i.test(q1.question_text || "") ||
      /^\s*OR\b/i.test(q2.question_text || "");
    if (hasOrCue && base1 && base1 === base2 && n1 !== n2) {
      const grpName = `OR_Q${base1}`;
      q1.selection_group = grpName;
      q1.group_required_attempts = 1;
      q1.group_total_questions = 2;
      q1.is_or_choice = true;
      q2.selection_group = grpName;
      q2.group_required_attempts = 1;
      q2.group_total_questions = 2;
      q2.is_or_choice = true;
    }
  }

  // 3. Apply Section-level or Exam-level optional rules to questions not already in a group
  for (const q of questions) {
    if (q.selection_group) continue;
    if (q.section) {
      const normSec = `SECTION_${String(q.section).trim().replace(/^section\s+/i, "").toUpperCase()}`;
      const secRule = sectionRules.get(normSec);
      if (secRule) {
        const secMembers = questions.filter(
          (m) => m.section && `SECTION_${String(m.section).trim().replace(/^section\s+/i, "").toUpperCase()}` === normSec
        );
        const totalInSec = secRule.total || secMembers.length;
        if (secRule.required < totalInSec) {
          q.selection_group = normSec;
          q.group_required_attempts = secRule.required;
          q.group_total_questions = totalInSec;
          q.is_or_choice = false;
        }
      }
    }
  }

  // Check instruction text for explicit per-question equal marks before finalizing exam-level optional group
  if (!explicitEqualMarksPerQuestion) {
    const eachMarksMatch =
      combinedInstructions.match(/each\s+(?:question\s+)?(?:carries|carrying|is\s+worth|of)\s+(\d+(?:\.\d+)?)\s*marks?/i) ||
      combinedInstructions.match(/(\d+(?:\.\d+)?)\s*marks?\s+each/i) ||
      combinedInstructions.match(/\b(\d+)\s*[x×]\s*(\d+(?:\.\d+)?)\s*=\s*(\d+(?:\.\d+)?)/i);
    if (eachMarksMatch) {
      if (eachMarksMatch.length >= 4 && eachMarksMatch[2] && eachMarksMatch[3]) {
        const countPart = parseInt(eachMarksMatch[1], 10);
        const marksPart = parseFloat(eachMarksMatch[2]);
        if (marksPart > 0) {
          explicitEqualMarksPerQuestion = marksPart;
          if (!examRequiredAttempts && countPart > 0 && countPart < questions.length) {
            examRequiredAttempts = countPart;
            examTotalAvailable = questions.length;
          }
        }
      } else {
        const parsed = parseFloat(eachMarksMatch[1]);
        if (!isNaN(parsed) && parsed > 0) {
          explicitEqualMarksPerQuestion = parsed;
        }
      }
    }
  }

  if (
    examRequiredAttempts !== null &&
    examRequiredAttempts > 0 &&
    examRequiredAttempts < questions.length
  ) {
    const ungrouped = questions.filter((q) => !q.selection_group);
    if (ungrouped.length === questions.length) {
      const totalAvail = examTotalAvailable && examTotalAvailable >= questions.length
        ? examTotalAvailable
        : questions.length;
      for (const q of questions) {
        q.selection_group = "EXAM_OPTIONAL";
        q.group_required_attempts = examRequiredAttempts;
        q.group_total_questions = totalAvail;
        q.is_or_choice = false;
      }
    }
  }

  // Synchronize group_total_questions with actual member count if needed and persist rule tag in marking_criteria
  const groupCounts = new Map<string, number>();
  for (const q of questions) {
    if (q.selection_group) {
      const g = String(q.selection_group).trim().toUpperCase();
      q.selection_group = g;
      groupCounts.set(g, (groupCounts.get(g) || 0) + 1);
    }
  }

  for (const q of questions) {
    if (q.selection_group && typeof q.group_required_attempts === "number" && q.group_required_attempts > 0) {
      const actualCount = groupCounts.get(q.selection_group) || q.group_total_questions || 0;
      const totalInGroup = Math.max(actualCount, q.group_total_questions || actualCount);
      q.group_total_questions = totalInGroup;
      if (q.group_required_attempts < totalInGroup) {
        const ruleMeta: SelectionRuleMeta = {
          selectionGroup: q.selection_group,
          requiredAttempts: q.group_required_attempts,
          totalInGroup,
          isOrChoice: Boolean(q.is_or_choice) || q.selection_group.startsWith("OR_"),
        };
        q.marking_criteria = attachSelectionRuleToCriteria(q.marking_criteria, ruleMeta);
      }
    }
  }

  // 4. Resolve Equal-Mark Allocation Rules
  const hasEqualWeightInstruction =
    explicitEqualMarksPerQuestion !== null ||
    /equal\s+marks/i.test(combinedInstructions) ||
    /marks\s+are\s+equally\s+distributed/i.test(combinedInstructions);

  const questionsWithPrintedMarks = questions.filter(
    (q) => typeof q.maximum_marks === "number" && !isNaN(q.maximum_marks) && q.maximum_marks > 0
  );

  let inferredEqualMarks = false;
  let ambiguousMarksWarning: string | null = null;

  if (questions.length > 0 && questionsWithPrintedMarks.length === 0) {
    // No individual question has printed marks; check if equal marks can be unambiguously inferred
    let inferredPerQuestion: number | null = null;

    // Effective number of required questions across the exam
    let effectiveRequiredQuestionCount = 0;
    const processedGroups = new Set<string>();
    let groupAmbiguous = false;

    for (const q of questions) {
      const rule = getSelectionRuleForQuestion(q);
      if (rule) {
        if (!processedGroups.has(rule.selectionGroup)) {
          processedGroups.add(rule.selectionGroup);
          effectiveRequiredQuestionCount += rule.requiredAttempts;
        }
      } else {
        effectiveRequiredQuestionCount += 1;
      }
    }

    if (explicitEqualMarksPerQuestion !== null && explicitEqualMarksPerQuestion > 0) {
      if (
        officialClassTotal > 0 &&
        effectiveRequiredQuestionCount > 0 &&
        Math.round(explicitEqualMarksPerQuestion * effectiveRequiredQuestionCount * 100) !== Math.round(officialClassTotal * 100)
      ) {
        ambiguousMarksWarning = `Question paper instruction specifies ${explicitEqualMarksPerQuestion} marks per question (${effectiveRequiredQuestionCount} required = ${explicitEqualMarksPerQuestion * effectiveRequiredQuestionCount}), which conflicts with class total marks (${officialClassTotal}).`;
        groupAmbiguous = true;
      } else {
        inferredPerQuestion = explicitEqualMarksPerQuestion;
      }
    } else if (hasEqualWeightInstruction && officialClassTotal > 0 && effectiveRequiredQuestionCount > 0) {
      const candidate = officialClassTotal / effectiveRequiredQuestionCount;
      // Accept clean integers or standard half/quarter marks
      if (Math.round(candidate * 4) === candidate * 4 && candidate > 0) {
        inferredPerQuestion = candidate;
      } else {
        ambiguousMarksWarning = `Question paper states equal marks, but class total (${officialClassTotal}) does not divide cleanly across ${effectiveRequiredQuestionCount} required questions.`;
        groupAmbiguous = true;
      }
    }

    if (inferredPerQuestion !== null && !groupAmbiguous) {
      inferredEqualMarks = true;
      for (const q of questions) {
        q.maximum_marks = inferredPerQuestion;
        // Clear missing-marks review reason if that was the only issue
        if (
          q.review_reason &&
          /maximum marks not specified/i.test(q.review_reason)
        ) {
          q.review_reason = null;
          q.review_required = false;
        }
      }
    }
  } else if (
    questionsWithPrintedMarks.length > 0 &&
    questionsWithPrintedMarks.length < questions.length
  ) {
    // Some questions have printed marks and others are missing marks — never infer blindly
    ambiguousMarksWarning = "Some questions on the question paper have printed marks while others are missing marks.";
  }

  return {
    questions,
    inferredEqualMarks,
    ambiguousMarksWarning,
  };
}

/**
 * Calculates the valid exam total marks taking into account optional question groups
 * ("Attempt any K out of N") and internal OR choices ("Q2(a) OR Q2(b)").
 */
export function calculateEffectiveExamTotalMarks(questions: RawExtractedQuestion[]): {
  effectiveTotalMarks: number;
  rawSumMarks: number;
  allMarksPresent: boolean;
  choiceGroupDiscrepancyReason: string | null;
} {
  let rawSumMarks = 0;
  let allMarksPresent = questions.length > 0;
  let effectiveTotalMarks = 0;
  let choiceGroupDiscrepancyReason: string | null = null;

  const groupedQuestions = new Map<string, { rule: SelectionRuleMeta; members: RawExtractedQuestion[] }>();
  const compulsoryQuestions: RawExtractedQuestion[] = [];

  for (const q of questions) {
    const m = q.maximum_marks;
    if (typeof m === "number" && !isNaN(m) && isFinite(m) && m > 0) {
      rawSumMarks += m;
    } else {
      allMarksPresent = false;
    }

    const rule = getSelectionRuleForQuestion(q);
    if (rule && rule.requiredAttempts < rule.totalInGroup) {
      const existing = groupedQuestions.get(rule.selectionGroup);
      if (existing) {
        existing.members.push(q);
      } else {
        groupedQuestions.set(rule.selectionGroup, { rule, members: [q] });
      }
    } else {
      compulsoryQuestions.push(q);
    }
  }

  for (const q of compulsoryQuestions) {
    if (typeof q.maximum_marks === "number" && q.maximum_marks > 0) {
      effectiveTotalMarks += q.maximum_marks;
    }
  }

  for (const [groupName, { rule, members }] of groupedQuestions.entries()) {
    const validMarks = members
      .map((m) => (typeof m.maximum_marks === "number" && m.maximum_marks > 0 ? m.maximum_marks : null));

    if (validMarks.some((m) => m === null)) {
      allMarksPresent = false;
      continue;
    }

    const numericMarks = validMarks as number[];
    if (numericMarks.length < rule.requiredAttempts) {
      allMarksPresent = false;
      choiceGroupDiscrepancyReason = `Optional group '${groupName}' requires ${rule.requiredAttempts} attempts, but only ${numericMarks.length} questions were extracted.`;
      continue;
    }

    const firstMark = numericMarks[0];
    const allEqualInGroup = numericMarks.every((m) => Math.round(m * 100) === Math.round(firstMark * 100));
    if (!allEqualInGroup) {
      allMarksPresent = false;
      choiceGroupDiscrepancyReason = `Optional choice group '${groupName}' has unequal maximum marks across options (${numericMarks.join(", ")}), making exam maximum marks ambiguous.`;
    }

    const sortedDesc = [...numericMarks].sort((a, b) => b - a);
    const groupMax = sortedDesc.slice(0, rule.requiredAttempts).reduce((acc, v) => acc + v, 0);
    effectiveTotalMarks += groupMax;
  }

  return {
    effectiveTotalMarks: Math.round(effectiveTotalMarks * 100) / 100,
    rawSumMarks: Math.round(rawSumMarks * 100) / 100,
    allMarksPresent,
    choiceGroupDiscrepancyReason,
  };
}

/**
 * Generates and persists missing provisional expected answers and marking criteria ONCE per class
 * so every student is evaluated against the exact same rubric.
 */
async function ensureSharedClassRubrics(
  adminClient: any,
  geminiApiKey: string,
  classId: string,
  teacherId: string,
  questions: RawExtractedQuestion[],
  persistUpdatesToDb: boolean,
  retryOptions: GeminiRetryOptions = {}
): Promise<RawExtractedQuestion[]> {
  const questionsNeedingRubric = questions.filter((q) => {
    const hasQNum = Boolean(q.question_number && String(q.question_number).trim().length > 0);
    const hasQText = Boolean(q.question_text && String(q.question_text).trim().length > 0);
    const hasMaxMarks = typeof q.maximum_marks === "number" && !isNaN(q.maximum_marks) && q.maximum_marks > 0;
    const strippedCrit = stripSelectionRuleTag(q.marking_criteria);
    const missingExp = !q.expected_answer || q.expected_answer.trim().length === 0;
    const missingCrit = !strippedCrit || strippedCrit.trim().length === 0;
    return hasQNum && hasQText && hasMaxMarks && (missingExp || missingCrit);
  });

  if (questionsNeedingRubric.length === 0) {
    return questions;
  }

  console.log(
    `[process-and-grade-paper] Generating shared class-level rubric for ${questionsNeedingRubric.length} question(s) in class ${classId}...`
  );

  const rubricPrompt = `You are a senior academic examiner creating a standardized, deterministic grading rubric for an exam question paper.
For each question below, generate:
1. "expected_answer": A clear, accurate reference answer prefixed with "[Provisional Reference Answer]: ".
2. "marking_criteria": Explicit, objective point-by-point marking criteria prefixed with "[Provisional Criteria]: " where the point allocations sum EXACTLY to the question's maximum_marks.

STRICT RULES:
- Base the reference answer and marking criteria strictly on the question_text and maximum_marks.
- Make the criteria specific and deterministic so any two students writing equivalent answers will receive the exact same marks.
- If a question is genuinely incomprehensible or impossible to establish criteria for, return null for expected_answer and marking_criteria.

QUESTIONS:
${JSON.stringify(
    questionsNeedingRubric.map((q) => ({
      question_number: q.question_number,
      question_text: q.question_text,
      maximum_marks: q.maximum_marks,
    })),
    null,
    2
  )}

Return strict JSON matching this schema:
{
  "rubrics": [
    {
      "question_number": string,
      "expected_answer": string | null,
      "marking_criteria": string | null
    }
  ]
}`;

  const rubricPayload = JSON.stringify({
    contents: [{ parts: [{ text: rubricPrompt }] }],
    generationConfig: {
      responseMimeType: "application/json",
      temperature: 0.0,
      maxOutputTokens: 4096,
    },
  });

  try {
    const resp = await callGeminiWithRetry(geminiApiKey, rubricPayload, retryOptions);
    const { text: jsonText } = extractGeminiResponseText(resp);
    const parsedResult = extractAndParseJson<{
      rubrics?: { question_number?: string; expected_answer?: string | null; marking_criteria?: string | null }[];
    }>(jsonText);
    const rubrics = parsedResult.ok ? (parsedResult.value.rubrics ?? []) : [];

    const dbUpdatePromises: Promise<any>[] = [];

    for (const q of questions) {
      const normQ = normalizeQuestionNumber(q.question_number);
      if (!normQ) continue;
      const found = rubrics.find((r) => normalizeQuestionNumber(r.question_number) === normQ);
      if (!found) continue;

      const rule = getSelectionRuleForQuestion(q);
      const strippedExistingCrit = stripSelectionRuleTag(q.marking_criteria);
      let updated = false;

      if ((!q.expected_answer || q.expected_answer.trim().length === 0) && found.expected_answer) {
        const exp = found.expected_answer.trim();
        q.expected_answer = exp.startsWith("[Provisional") ? exp : `[Provisional Reference Answer]: ${exp}`;
        updated = true;
      }

      if ((!strippedExistingCrit || strippedExistingCrit.trim().length === 0) && found.marking_criteria) {
        const crit = found.marking_criteria.trim();
        const formattedCrit = crit.startsWith("[Provisional") ? crit : `[Provisional Criteria]: ${crit}`;
        q.marking_criteria = attachSelectionRuleToCriteria(formattedCrit, rule);
        updated = true;
      }

      if (updated && persistUpdatesToDb && adminClient) {
        let updateQuery = adminClient
          .from("class_question_paper_questions")
          .update({
            expected_answer: q.expected_answer,
            marking_criteria: q.marking_criteria,
          })
          .eq("class_id", classId)
          .eq("teacher_id", teacherId);

        if (typeof q.sort_order === "number") {
          updateQuery = updateQuery.eq("sort_order", q.sort_order);
        } else if (q.question_number) {
          updateQuery = updateQuery.eq("question_number", q.question_number);
        }
        dbUpdatePromises.push(updateQuery);
      }
    }

    if (dbUpdatePromises.length > 0) {
      await Promise.all(dbUpdatePromises);
    }
  } catch (err) {
    console.warn("[process-and-grade-paper] Non-fatal warning during shared rubric generation:", err);
  }

  return questions;
}

/**
 * Verifies whether `candidate` exists verbatim in `pageText` (allowing only whitespace normalization)
 * and returns the exact character slice from `pageText` so student wording is never altered.
 */
export function extractVerifiedSubstringFromPage(
  candidate: string,
  pageText: string
): { found: boolean; exactSlice: string } {
  if (!candidate || !pageText) return { found: false, exactSlice: "" };

  // Strip any page banner labels the AI might have echoed
  const cleaned = candidate
    .replace(/===\s*STUDENT ANSWER PAGE\s*\d+\s*===/gi, "")
    .replace(/^\s*\[Page\s*\d+(?:\s*\(Continued\))?\]\s*/i, "")
    .trim();

  if (!cleaned || cleaned === "[No answer detected]" || cleaned === "[Optional question — not attempted]") {
    return { found: false, exactSlice: "" };
  }

  // 1. Direct exact substring check
  const directIdx = pageText.indexOf(cleaned);
  if (directIdx !== -1) {
    return { found: true, exactSlice: pageText.slice(directIdx, directIdx + cleaned.length).trim() };
  }

  // 2. Whitespace-normalized character-offset mapping back to original `pageText`
  const normPageChars: string[] = [];
  const normToOrigIndex: number[] = [];
  let inWhitespace = false;

  for (let i = 0; i < pageText.length; i++) {
    const ch = pageText[i];
    if (/\s/.test(ch)) {
      if (!inWhitespace && normPageChars.length > 0) {
        normPageChars.push(" ");
        normToOrigIndex.push(i);
      }
      inWhitespace = true;
    } else {
      normPageChars.push(ch);
      normToOrigIndex.push(i);
      inWhitespace = false;
    }
  }
  if (normPageChars.length > 0 && normPageChars[normPageChars.length - 1] === " ") {
    normPageChars.pop();
    normToOrigIndex.pop();
  }

  const normPageStr = normPageChars.join("");
  const normCandidate = cleaned.replace(/\s+/g, " ").trim();

  if (normCandidate.length === 0) {
    return { found: false, exactSlice: "" };
  }

  let matchStart = normPageStr.indexOf(normCandidate);
  if (matchStart === -1) {
    matchStart = normPageStr.toLowerCase().indexOf(normCandidate.toLowerCase());
  }

  if (matchStart !== -1) {
    const matchEnd = matchStart + normCandidate.length - 1;
    const origStart = normToOrigIndex[matchStart];
    const origEnd = normToOrigIndex[matchEnd] + 1;
    return {
      found: true,
      exactSlice: pageText.slice(origStart, origEnd).trim(),
    };
  }

  return { found: false, exactSlice: "" };
}

export interface DeterministicQuestionBlock {
  pageNumber: number;
  verbatimText: string;
  isContinuation: boolean;
}

export interface MatchedQuestionHeader {
  matchedQuestionNumber: string | null;
  isExplicitContinuation: boolean;
  hasExplicitPrefix: boolean;
  remainingLineText: string;
  rawHeaderPrefix: string;
  isUnknownOrUnclearQuestionHeader?: boolean;
  isUnnumberedQuestionOrAnswerSeparator?: boolean;
  isParentGroupPrefixOnly?: boolean;
  parentQuestionPrefix?: string;
}

function resolveKnownQuestionMatch(
  rawCandidate: string,
  knownQuestionNumbers: string[]
): string | null {
  const normCandidate = normalizeQuestionNumber(rawCandidate);
  if (!normCandidate) return null;

  for (const kq of knownQuestionNumbers) {
    if (normalizeQuestionNumber(kq) === normCandidate) {
      return kq;
    }
  }

  // Compact subquestion e.g. "2A" -> "2(A)"
  const compactSubMatch = normCandidate.match(/^(\d+)([A-Z])$/);
  if (compactSubMatch) {
    const withParens = `${compactSubMatch[1]}(${compactSubMatch[2]})`;
    for (const kq of knownQuestionNumbers) {
      if (normalizeQuestionNumber(kq) === withParens) {
        return kq;
      }
    }
  }

  // Parenthesized subquestion e.g. "2(A)" -> "2A"
  const parenSubMatch = normCandidate.match(/^(\d+)\(([A-Z0-9]+)\)$/);
  if (parenSubMatch) {
    const withoutParens = `${parenSubMatch[1]}${parenSubMatch[2]}`;
    for (const kq of knownQuestionNumbers) {
      if (normalizeQuestionNumber(kq) === withoutParens) {
        return kq;
      }
    }
  }

  // Roman numeral or English number word fallback (e.g. "IV" -> "4", "THREE" -> "3")
  const romanVal = ROMAN_NUMERAL_MAP[normCandidate.toUpperCase()];
  if (romanVal !== undefined) {
    const arabicStr = String(romanVal);
    for (const kq of knownQuestionNumbers) {
      if (normalizeQuestionNumber(kq) === arabicStr) {
        return kq;
      }
    }
  }

  const wordVal = WORD_NUMBER_MAP[normCandidate.toLowerCase()];
  if (wordVal !== undefined) {
    const arabicStr = String(wordVal);
    for (const kq of knownQuestionNumbers) {
      if (normalizeQuestionNumber(kq) === arabicStr) {
        return kq;
      }
    }
  }

  return null;
}

function isPureMathOrUnitContinuation(remainingText: string, fullTrimmedLine: string): boolean {
  const rem = remainingText.trim();
  if (!rem) return false;
  // Starts with math operators or comparison symbols (e.g. "+ 5 = 15", "= 10", "/ 2")
  if (/^[+\-*/=^%<>≤≥≈±×÷]/.test(rem)) return true;
  // Line looks like a parenthesized math expression "(1) + (2) = 3"
  if (/^\(\s*\d+\s*\)\s*[+\-*/=^<>]/.test(fullTrimmedLine)) return true;
  // Bare number followed by hyphen/minus and another number or equation (e.g. "2 - 1 = 1", "2 - 3 hours", "3 - x = 0")
  if (/^\d+(?:\.\d+)?\s*[\-\–\—]+\s*(?:\d|[a-zA-Z]\s*[=+\-*/^<>])/.test(fullTrimmedLine)) return true;
  // Remaining text starts with a number or single variable immediately followed by an arithmetic/equality operator (e.g. "1 = 1", "x = 5")
  if (/^(?:\d+(?:\.\d+)?|[a-zA-Z])\s*[=+\-*/^×÷<>≤≥≈±]/.test(rem)) return true;
  return false;
}

function cleanRemainingAfterHeader(text: string): string {
  // Strip any leftover closing Markdown bold/italic markers immediately following a header (e.g. "**Q4:** text" or "**Q4**: text")
  return text.replace(/^(?:\*\*|\*|__|_)(\s*[\.\)\:\-\]\–\—]+\s*|\s+|$)/, "").trim();
}

/**
 * Matches a structural question header at the start of a line against known question numbers,
 * and separates the structural header prefix from the remaining verbatim student answer on that line.
 */
export function matchLineQuestionHeader(
  line: string,
  knownQuestionNumbers: string[],
  contextParentQuestionPrefix?: string | null
): MatchedQuestionHeader | null {
  const rawTrimmed = line.trim();
  if (!rawTrimmed) return null;

  // Strip leading bullet symbols or Markdown heading/bold/italic prefixes (e.g. "### Q4:", "**Q4:**", "* Q4:")
  const trimmed = rawTrimmed
    .replace(/^(?:[\•\‣\⁃]\s+|#{1,6}\s+|\*{1,3}(?=[A-Za-z0-9\(\[])|_{1,3}(?=[A-Za-z0-9\(\[])|\*\s+)/, "")
    .trim();
  if (!trimmed) return null;

  // 0A. Unwrap attempt serial number prefix when followed by an explicit question header!
  // e.g. "1. Q3: ...", "2) Ans 1: ...", "(3) Question 4: ...", "1 - Q4 Ans: ...", "1. 3 Ans: ..."
  // This prevents "Attempt any 3" serial numbers (1, 2, 3) from overriding the student's actual chosen question numbers (Q3, Q1, Q4).
  const serialThenExplicitMatch = trimmed.match(
    /^((?:\(\s*\d+\s*\)|\[\s*\d+\s*\]|\d+\s*[\.\)\:\-\–\—]+)\s+)((?:(?:\*{1,2}|_{1,2})?(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question\b|ques\b|que\b|q\.?\s*n\.?|q(?=[\s\.\:\-\#\d\(\[]))|(?:(?:\*{1,2}|_{1,2})?(?:ans(?:wer)?|sol(?:ution)?)(?=[\s\.\:\-\#\d\(\[]))|(?:\(\s*|\[\s*)(?:question\b|ques\b|que\b|q(?=[\s\.\:\-\#\d\(\[])|ans(?:wer)?(?=[\s\.\:\-\#\d\(\[])|sol(?:ution)?(?=[\s\.\:\-\#\d\(\[]))|\(?\d+(?:\s*\([a-zA-Z0-9]{1,4}\))?\)?\s*[\.\)\:\-\–\—]*\s+(?:ans(?:wer)?|sol(?:ution)?)\b).*)$/i
  );
  if (serialThenExplicitMatch) {
    const serialPrefix = serialThenExplicitMatch[1];
    const innerText = serialThenExplicitMatch[2];
    const innerMatch = matchLineQuestionHeader(innerText, knownQuestionNumbers, contextParentQuestionPrefix);
    if (
      innerMatch &&
      innerMatch.hasExplicitPrefix &&
      (innerMatch.matchedQuestionNumber !== null || innerMatch.isUnknownOrUnclearQuestionHeader)
    ) {
      return {
        ...innerMatch,
        rawHeaderPrefix: `${serialPrefix}${innerMatch.rawHeaderPrefix}`.trim(),
      };
    }
  }

  // 0B. Unwrap parenthesized or bracketed explicit question header at start of line:
  // e.g. "(Q4) The answer...", "[Q4]: The answer...", "(Ans 4) The answer...", "(Question 4): ..."
  const parenExplicitMatch = trimmed.match(
    /^(?:\(\s*|\[\s*)((?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q|(?:ans(?:wer)?|sol(?:ution)?))\s*[\.\:\-\#]*\s*[a-zA-Z0-9\(\)\.\s]+?)(?:\s*\)|\s*\])(?:\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|\s*[\.\)\:\-\]\–\—]*\s*(?:ans(?:wer)?|sol(?:ution)?)\b\.?)?(?:\s*[\.\)\:\-\]\–\—]+\s*|\s+|$)(.*)$/i
  );
  if (parenExplicitMatch) {
    const innerHeader = parenExplicitMatch[1].trim();
    const afterParenRemaining = cleanRemainingAfterHeader(parenExplicitMatch[2] ?? "");
    const syntheticLine = afterParenRemaining ? `${innerHeader}: ${afterParenRemaining}` : `${innerHeader}:`;
    const innerMatch = matchLineQuestionHeader(syntheticLine, knownQuestionNumbers, contextParentQuestionPrefix);
    if (innerMatch && (innerMatch.matchedQuestionNumber !== null || innerMatch.isUnknownOrUnclearQuestionHeader)) {
      const prefixLen = trimmed.length - afterParenRemaining.length;
      return {
        ...innerMatch,
        hasExplicitPrefix: true,
        remainingLineText: afterParenRemaining,
        rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
      };
    }
  }

  // 1. Check for unclear/illegible explicit question header (e.g., "Q[unclear]: ...", "Q?: ...", "Question ?: ...")
  const unclearHeaderRegex =
    /^(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\s*\.?\s*(?:no|num|number)\.?)?)\s*[\.\:\-\#\–\—]*\s*(?:\[\s*unclear\s*\]|\?+)(?:\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|\s*[\.\)\:\-\]\–\—]*\s*(?:ans(?:wer)?|sol(?:ution)?)\b\.?)?(?:\s*[\.\)\:\-\]\–\—]+\s*|\s+|$)(.*)$/i;
  const unclearMatch = trimmed.match(unclearHeaderRegex);
  if (unclearMatch) {
    const remaining = cleanRemainingAfterHeader(unclearMatch[1] ?? "");
    const prefixLen = trimmed.length - (unclearMatch[1] ?? "").length;
    return {
      matchedQuestionNumber: null,
      isExplicitContinuation: false,
      hasExplicitPrefix: true,
      remainingLineText: remaining,
      rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
      isUnknownOrUnclearQuestionHeader: true,
    };
  }

  // 2. Explicit keyword-prefixed header:
  // Supports "Q1", "Q1:", "Q1.", "Q.1", "Q. : 1", "Q-1", "Q#1", "Q 1", "Q1 Ans:", "Q1:Ans:", "Q1 Answer:", "Q1 Ans",
  // "Question 1", "Ques 1:", "Que 1:", "Q.No. 1:", "Ans 1", "Ans. 1", "Ans.: 1", "Ans :- 1", "Answer 1", "Answer No. 1",
  // "Ans to Q1:", "Ans of Q1:", "Sol 1:", "Solution 1:", "No. 1:", "Q1(a)", "Q1(b)", "Q1 (contd.):", Roman/word numbers, etc.
  const explicitHeaderRegex =
    /^((?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:no|num|number)\.)\s*(?:\*{1,2}|_{1,2})?\s*[\.\:\-\#\–\—]*\s*)(?:\(\s*(\d+(?:(?:\s*\((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4}\)){1,2}|[a-zA-Z])?(?:\.\d+)?)\s*\)(?:\s*\(\s*((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4})\s*\))?|(\d+(?:(?:\s*\((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4}\)){1,2}|[a-zA-Z](?=$|[\s\.\)\:\-\]\–\—\*\_])|\.\d+)?|(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[ivxIVX]{1,4})(?=$|[\s\.\)\:\-\]\–\—\*\_])))(\s*(?:\*{1,2}|_{1,2})?\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(?\s*(cont(?:d\.?|inued))\s*\)?(?:\s*[\.\)\:\-\]\–\—]+)?)?(?:\s*\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|(?:\s*[\.\)\:\-\]\–\—]+\s*|\s+)(?:ans(?:wer)?|sol(?:ution)?)\b(?=\s*(?:\*{1,2}|_{1,2})?\s*[\.\)\:\-\]\–\—]+|\s*$)|\s+(?:ans|sol)\b\.?)?(?:\s*(?:\*{1,2}|_{1,2})?\s*[\.\)\:\-\]\–\—]+\s*|\s+(?:\*{1,2}|_{1,2})?\s*|$)(.*)$/i;

  const expMatch = trimmed.match(explicitHeaderRegex);
  if (expMatch) {
    const prefixPart = expMatch[1] ?? "";
    const rawLabel = expMatch[2] && expMatch[3]
      ? `${expMatch[2]}(${expMatch[3]})`
      : (expMatch[2] || expMatch[4] || "");
    const punctAfterNum = expMatch[5] ?? "";
    const contToken = expMatch[6] ?? "";
    let remaining = cleanRemainingAfterHeader(expMatch[7] ?? "");

    // Guard only against instruction sentences like "Answer 5 questions from Section A" or "Answer 2 out of 4"
    // while allowing "Answer 5" (standalone line) and "Answer 5 Osmosis is..."
    const isInstructionSentence =
      /^(?:answer|question)\b/i.test(prefixPart.trim()) &&
      !/\b(?:q|ques|que|no|num|number|ans|sol)\b/i.test(prefixPart.replace(/^(?:answer|question)\b/i, "")) &&
      !/:|\.|\-|\#/.test(prefixPart) &&
      !punctAfterNum &&
      !expMatch[2] &&
      remaining.length > 0 &&
      /^(?:questions?\b|any\b|all\b|out\s+of\b|of\s+the\b|from\s+section\b|carries\b|marks?\b)/i.test(remaining);

    // Also guard against prose like "No. 1 producer of..." or "Ans: One of the..." / "Ans: 2 types of..."
    // where a word-number, Roman numeral, or bare number after "Ans:" has no delimiter after the token itself
    const isWordNumberWithoutDelimiter =
      /^(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[ivxIVX]{1,4})$/i.test(rawLabel) &&
      !punctAfterNum &&
      remaining.length > 0;

    const isColonAnsWithUndelimitedNumberInSentence =
      /^(?:ans(?:wer)?|sol(?:ution)?)\s*[\:\-\–\—]+$/i.test(prefixPart.trim()) &&
      !punctAfterNum &&
      !expMatch[2] &&
      remaining.length > 0;

    if (
      !isInstructionSentence &&
      !isWordNumberWithoutDelimiter &&
      !isColonAnsWithUndelimitedNumberInSentence &&
      rawLabel
    ) {
      let effectiveLabel = rawLabel;
      // Check if remaining starts with a subquestion token like "(a)" when "1(a)" is in knownQuestionNumbers
      const leadingSubMatch = remaining.match(
        /^\(\s*([a-zA-Z0-9]+)\s*\)(?:\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|(?:\s*[\.\)\:\-\]\–\—]+\s*|\s+)(?:ans(?:wer)?|sol(?:ution)?)\b\.?)?(?:\s*[\.\)\:\-\]\–\—]+\s*|\s+|$)(.*)$/i
      );
      if (leadingSubMatch) {
        const subCandidate = `${rawLabel}(${leadingSubMatch[1]})`;
        if (resolveKnownQuestionMatch(subCandidate, knownQuestionNumbers)) {
          effectiveLabel = subCandidate;
          remaining = cleanRemainingAfterHeader(leadingSubMatch[2] ?? "");
        }
      }

      const prefixLen = trimmed.length - remaining.length;
      const rawHeaderPrefix = trimmed.slice(0, prefixLen).trim();
      const isCont = Boolean(contToken) || /\bcont(?:d|inued)\b/i.test(rawHeaderPrefix);
      const matchedKnown = resolveKnownQuestionMatch(effectiveLabel, knownQuestionNumbers);
      const normMatched = matchedKnown ? normalizeQuestionNumber(matchedKnown) : normalizeQuestionNumber(effectiveLabel);
      const baseDigits = normMatched.match(/^(\d+)/)?.[1];

      if (matchedKnown) {
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: true,
          remainingLineText: remaining,
          rawHeaderPrefix,
          parentQuestionPrefix: baseDigits,
        };
      }

      // Check if student wrote "Q1:" on its own line (or before subparts) when "1(a)", "1(b)" are in knownQuestionNumbers
      if (baseDigits) {
        const hasSubquestionsInPaper = knownQuestionNumbers.some((kq) =>
          normalizeQuestionNumber(kq).startsWith(`${baseDigits}(`)
        );
        if (hasSubquestionsInPaper && !remaining) {
          return {
            matchedQuestionNumber: null,
            isExplicitContinuation: isCont,
            hasExplicitPrefix: true,
            remainingLineText: "",
            rawHeaderPrefix: trimmed,
            isParentGroupPrefixOnly: true,
            parentQuestionPrefix: baseDigits,
          };
        }

        // Or if student wrote "Q1(a):" when the question paper only has parent question "1"
        if (!hasSubquestionsInPaper) {
          const parentMatched = resolveKnownQuestionMatch(baseDigits, knownQuestionNumbers);
          if (parentMatched) {
            return {
              matchedQuestionNumber: parentMatched,
              isExplicitContinuation: isCont,
              hasExplicitPrefix: true,
              remainingLineText: remaining,
              rawHeaderPrefix,
              parentQuestionPrefix: baseDigits,
            };
          }
        }
      }

      // If it has an unmistakable "Q"/"Question"/"Ques"/"Que"/"Ans"/"Answer"/"Sol" prefix or explicit delimiter (e.g. "Q9: ...", "Ans 9 ..."),
      // treat it as an explicit header for an unknown question number so it closes the previous question's block!
      const isStrongQuestionHeaderSyntax =
        /\b(?:question|ques|que|q|ans(?:wer)?|sol(?:ution)?)\b/i.test(prefixPart) ||
        Boolean(punctAfterNum) ||
        /[\.\)\:\-\]\–\—]/.test(rawHeaderPrefix);
      if (isStrongQuestionHeaderSyntax) {
        return {
          matchedQuestionNumber: null,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: true,
          remainingLineText: remaining,
          rawHeaderPrefix,
          isUnknownOrUnclearQuestionHeader: true,
        };
      }
    }
  }

  // 2B. Unnumbered explicit "Ans:" / "Answer:" / "Q:" / "Question:" / "Sol:" marker at start of line
  // (e.g., when a student writes "Ans: ..." without repeating the question number, or when the margin question number on Page 2 was cropped)
  const unnumberedSeparatorRegex =
    /^((?:question|ques|que|q|ans(?:wer)?|sol(?:ution)?)(?:\s*(?:\*{1,2}|_{1,2}))?\s*[\:\-\–\—]+(?:\s*(?:\*{1,2}|_{1,2}))?)\s*(.*)$/i;
  const unnumMatch = trimmed.match(unnumberedSeparatorRegex);
  if (unnumMatch) {
    const rawHeaderPrefix = unnumMatch[1].trim();
    const remaining = cleanRemainingAfterHeader(unnumMatch[2] ?? "");
    return {
      matchedQuestionNumber: null,
      isExplicitContinuation: false,
      hasExplicitPrefix: true,
      remainingLineText: remaining,
      rawHeaderPrefix,
      isUnnumberedQuestionOrAnswerSeparator: true,
    };
  }

  // 3. Number + explicit trailing "Ans" / "Answer" / "Sol" keyword
  // (e.g. "1 Ans", "1 Answer", "1 Ans: ...", "1. Ans: ...", "1.Ans: ...", "1)Ans: ...", "1:Ans: ...", "1(a) Answer: ...")
  const numThenAnsRegex =
    /^(?:\(\s*(\d+(?:(?:\s*\((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4}\)){1,2}|[a-zA-Z])?(?:\.\d+)?)\s*\)|(\d+(?:(?:\s*\((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4}\)){1,2}|[a-zA-Z](?=$|[\s\.\)\:\-\]\–\—])|\.\d+)?))(\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(?\s*(cont(?:d\.?|inued))\s*\)?(?:\s*[\.\)\:\-\]\–\—]+)?)?(?:\s+|\s*(?=[aA][nN][sS]\b|[sS][oO][lL]\b))(\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|(?:ans(?:wer)?|sol(?:ution)?)\b\.?)(?:(\s*(?:\*{1,2}|_{1,2})?\s*[\.\)\:\-\]\–\—]+)\s*|\s+|$)(.*)$/i;
  const numAnsMatch = trimmed.match(numThenAnsRegex);
  if (numAnsMatch) {
    const rawLabel = numAnsMatch[1] || numAnsMatch[2] || "";
    const punctAfterNum = (numAnsMatch[3] ?? "").trim();
    const isCont = Boolean(numAnsMatch[4]);
    const kwToken = (numAnsMatch[5] ?? "").trim();
    const punctAfterKw = (numAnsMatch[6] ?? "").trim();
    const remaining = cleanRemainingAfterHeader(numAnsMatch[7] ?? "");
    const prefixLen = trimmed.length - remaining.length;
    const rawHeaderPrefix = trimmed.slice(0, prefixLen).trim();
    const hasHeaderPunct = Boolean(punctAfterNum) || Boolean(punctAfterKw) || /[\.\)\:\-\]\–\—]/.test(rawHeaderPrefix);
    const looksLikeProseAnswerCount =
      !hasHeaderPunct &&
      remaining.length > 0 &&
      /^(?:is\b|was\b|were\b|are\b|only\b)/i.test(remaining);
    const looksLikeSentenceStartingWithFullWordAfterPunctuatedNum =
      Boolean(punctAfterNum) &&
      /^(?:answer|solution)$/i.test(kwToken) &&
      !punctAfterKw &&
      remaining.length > 0;

    if (!looksLikeProseAnswerCount && !looksLikeSentenceStartingWithFullWordAfterPunctuatedNum && rawLabel) {
      const matchedKnown = resolveKnownQuestionMatch(rawLabel, knownQuestionNumbers);
      const baseDigits = rawLabel.match(/^(\d+)/)?.[1];
      if (matchedKnown) {
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: true,
          remainingLineText: remaining,
          rawHeaderPrefix,
          parentQuestionPrefix: baseDigits,
        };
      }

      if (baseDigits) {
        const hasSubquestionsInPaper = knownQuestionNumbers.some((kq) =>
          normalizeQuestionNumber(kq).startsWith(`${baseDigits}(`)
        );
        if (!hasSubquestionsInPaper) {
          const parentMatched = resolveKnownQuestionMatch(baseDigits, knownQuestionNumbers);
          if (parentMatched) {
            return {
              matchedQuestionNumber: parentMatched,
              isExplicitContinuation: isCont,
              hasExplicitPrefix: true,
              remainingLineText: remaining,
              rawHeaderPrefix,
              parentQuestionPrefix: baseDigits,
            };
          }
        }
      }

      return {
        matchedQuestionNumber: null,
        isExplicitContinuation: isCont,
        hasExplicitPrefix: true,
        remainingLineText: remaining,
        rawHeaderPrefix,
        isUnknownOrUnclearQuestionHeader: true,
      };
    }
  }

  // 4. Bare Number / Bare Subquestion at start of line:
  // 4A: Parenthesized or bracketed at start of line: "(1) ...", "[1] ...", "(1(a)) ...", "(1a) ..."
  const bareParenRegex =
    /^(?:\(\s*(\d+(?:\s*\([a-zA-Z0-9]+\)|[a-zA-Z])?(?:\.\d+)?)\s*\)(?:\s*\(\s*([a-zA-Z0-9]+)\s*\))?|\[\s*(\d+(?:\s*\([a-zA-Z0-9]+\)|[a-zA-Z])?(?:\.\d+)?)\s*\])(?:\s*[\.\:\-\)\]\–\—]+)?(?:\s*\(?\s*(cont(?:d\.?|inued))\s*\)?(?:\s*[\.\:\-\–\—]+)?)?(?:\s+|$)(.*)$/i;
  const bareParenMatch = trimmed.match(bareParenRegex);
  if (bareParenMatch) {
    const rawLabel = bareParenMatch[1] && bareParenMatch[2]
      ? `${bareParenMatch[1]}(${bareParenMatch[2]})`
      : (bareParenMatch[1] || bareParenMatch[3] || "");
    const isCont = Boolean(bareParenMatch[4]);
    const remaining = cleanRemainingAfterHeader(bareParenMatch[5] ?? "");
    if (!isPureMathOrUnitContinuation(remaining, trimmed)) {
      const matchedKnown = resolveKnownQuestionMatch(rawLabel, knownQuestionNumbers);
      if (matchedKnown) {
        const baseDigits = rawLabel.match(/^(\d+)/)?.[1];
        const hasSubquestionParens = Boolean(bareParenMatch[2]) || /\([a-zA-Z0-9]+\)/.test(rawLabel);
        const prefixLen = trimmed.length - remaining.length;
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: hasSubquestionParens || isCont,
          remainingLineText: remaining,
          rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
          parentQuestionPrefix: baseDigits,
        };
      }
    }
  }

  // 4B: Number with explicit parenthesized subquestion at start of line: "1(a) ...", "1(a). ...", "1 (a): ..."
  const bareSubqRegex =
    /^(\d+(?:\s*\((?!\s*(?:cont|ans|sol))[a-zA-Z0-9]{1,4}\)){1,2})(?:\s*[\.\)\:\-\]\–\—]+)?(?:\s*\(?\s*(cont(?:d\.?|inued))\s*\)?(?:\s*[\.\)\:\-\]\–\—]+)?)?(?:\s+|$)(.*)$/i;
  const bareSubqMatch = trimmed.match(bareSubqRegex);
  if (bareSubqMatch) {
    const rawLabel = bareSubqMatch[1];
    const isCont = Boolean(bareSubqMatch[2]);
    const remaining = cleanRemainingAfterHeader(bareSubqMatch[3] ?? "");
    if (!isPureMathOrUnitContinuation(remaining, trimmed)) {
      const matchedKnown = resolveKnownQuestionMatch(rawLabel, knownQuestionNumbers);
      if (matchedKnown) {
        const baseDigits = rawLabel.match(/^(\d+)/)?.[1];
        const prefixLen = trimmed.length - remaining.length;
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: true,
          remainingLineText: remaining,
          rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
          parentQuestionPrefix: baseDigits,
        };
      }
    }
  }

  // 4C: Bare number with mandatory punctuation delimiter:
  // Supports "1. ...", "1) ...", "1: ...", "1.) ...", "1). ...", "1:- ...", "1 :- ...", "1 - ...", "1)Text", "1.Text", "1a) ...", "2.1. ..."
  // Note: "." and ":" must NOT be followed by a digit so decimals like "1.5" or ratios like "1:2" never match!
  const bareDelimitedRegex =
    /^(\d+(?:[a-zA-Z]|\.\d+)?)(?:\s*(?:[\.\)\:]+[\-\–\—]*)(?=\s|$|[A-Za-z\"\'\[])|\s+([\-\–\—]+)(?=\s+[A-Za-z0-9]|$)|\s+\(\s*(cont(?:d\.?|inued))\s*\)\s*[\.\)\:\-\–\—]*)(?:\s*\(?\s*(cont(?:d\.?|inued))\s*\)?(?:\s*[\.\)\:\-\–\—]+)?)?\s*(.*)$/i;
  const bareDelimMatch = trimmed.match(bareDelimitedRegex);
  if (bareDelimMatch) {
    const rawLabel = bareDelimMatch[1];
    const isCont = Boolean(bareDelimMatch[3] || bareDelimMatch[4]);
    const remaining = cleanRemainingAfterHeader(bareDelimMatch[5] ?? "");
    if (!isPureMathOrUnitContinuation(remaining, trimmed)) {
      const matchedKnown = resolveKnownQuestionMatch(rawLabel, knownQuestionNumbers);
      if (matchedKnown) {
        const baseDigits = rawLabel.match(/^(\d+)/)?.[1];
        const prefixLen = trimmed.length - remaining.length;
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: isCont,
          hasExplicitPrefix: isCont,
          remainingLineText: remaining,
          rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
          parentQuestionPrefix: baseDigits,
        };
      }
    }
  }

  // 4D: Standalone bare question number on its own line (e.g. "4" or "4(a)" with nothing else on the line)
  if (/^\d+(?:\s*\([a-zA-Z0-9]{1,4}\)|[a-zA-Z])?$/.test(trimmed)) {
    const matchedKnown = resolveKnownQuestionMatch(trimmed, knownQuestionNumbers);
    if (matchedKnown) {
      const baseDigits = trimmed.match(/^(\d+)/)?.[1];
      return {
        matchedQuestionNumber: matchedKnown,
        isExplicitContinuation: false,
        hasExplicitPrefix: /\([a-zA-Z0-9]+\)/.test(trimmed),
        remainingLineText: "",
        rawHeaderPrefix: trimmed,
        parentQuestionPrefix: baseDigits,
      };
    }
  }

  // 5. Contextual subquestion label ("(a) ...", "(b) ...", "a) ...", "b. ...") when a parent question prefix is active
  if (contextParentQuestionPrefix) {
    const contextSubqRegex =
      /^(?:\(\s*([a-zA-Z]|[ivxIVX]{1,4})\s*\)|([a-zA-Z]|[ivxIVX]{1,4})[\.\)])(?:\s*[\.\:\-\–\—]+)?(?:\s*\(\s*(?:ans(?:wer)?|sol(?:ution)?)\.?\s*\)|\s+(?:ans(?:wer)?|sol(?:ution)?)\b\.?)?(?:\s*[\.\:\-\–\—]+\s*|\s+|$)(.*)$/i;
    const ctxMatch = trimmed.match(contextSubqRegex);
    if (ctxMatch) {
      const subToken = ctxMatch[1] || ctxMatch[2];
      const remaining = cleanRemainingAfterHeader(ctxMatch[3] ?? "");
      const candidateSubq = `${contextParentQuestionPrefix}(${subToken})`;
      const matchedKnown = resolveKnownQuestionMatch(candidateSubq, knownQuestionNumbers);
      if (matchedKnown && !isPureMathOrUnitContinuation(remaining, trimmed)) {
        const prefixLen = trimmed.length - remaining.length;
        return {
          matchedQuestionNumber: matchedKnown,
          isExplicitContinuation: false,
          hasExplicitPrefix: true,
          remainingLineText: remaining,
          rawHeaderPrefix: trimmed.slice(0, prefixLen).trim(),
          parentQuestionPrefix: contextParentQuestionPrefix,
        };
      }
    }
  }

  return null;
}

/**
 * Removes only structural question headers/labels (e.g. "Q1:", "Q1 Ans:", "Question 1 -", "1.")
 * for `questionNumber` from the beginning of an extracted answer block while preserving
 * verbatim student wording, mathematical expressions, numbers, and internal numbered lists.
 */
export function stripLeadingQuestionHeader(
  text: string,
  questionNumber: string,
  knownQuestionNumbers: string[] = []
): string {
  if (!text) return "";
  const normTarget = normalizeQuestionNumber(questionNumber);
  if (!normTarget) return text.trim();

  const effectiveKnown = Array.from(new Set([questionNumber, ...knownQuestionNumbers]));
  const baseDigits = normTarget.match(/^(\d+)/)?.[1] ?? null;
  const lines = text.split(/\r?\n/);

  // Check if the block is a bare numbered list ("1. ... \n 2. ..."), allowing item 1 to wrap across lines
  const nonEmptyLines = lines.map((l) => l.trim()).filter((l) => l.length > 0);
  const looksLikeBareNumberedList =
    nonEmptyLines.length >= 2 &&
    /^1[\.\)]\s+/.test(nonEmptyLines[0]) &&
    nonEmptyLines.slice(1, 7).some((l) => /^2[\.\)]\s+/.test(l));

  const cleanedLines: string[] = [];
  let strippedFirstLineHeader = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) {
      if (cleanedLines.length > 0) {
        cleanedLines.push("");
      }
      continue;
    }

    if (!strippedFirstLineHeader) {
      if (/^(?:\*{1,2}|_{1,2})?(?:section|part)\s*[\-\–\—\:\.]*\s*(?:[a-zA-Z0-9]{1,4}|\([a-zA-Z0-9]{1,4}\))(?:\s*[\:\.\-\–\—])?(?:\*{1,2}|_{1,2})?$/i.test(trimmed)) {
        continue;
      }
      const hm = matchLineQuestionHeader(trimmed, effectiveKnown, baseDigits);
      if (hm) {
        if (hm.isParentGroupPrefixOnly) {
          // Standalone parent header line like "Q1:" before "(a) ..."; keep strippedFirstLineHeader=false so "(a)" is also stripped on the next line
          continue;
        }
        if (hm.isUnnumberedQuestionOrAnswerSeparator) {
          strippedFirstLineHeader = true;
          if (hm.remainingLineText.length > 0) {
            cleanedLines.push(hm.remainingLineText);
          }
          continue;
        }
        strippedFirstLineHeader = true;
        if (
          hm.matchedQuestionNumber &&
          normalizeQuestionNumber(hm.matchedQuestionNumber) === normTarget
        ) {
          // Do not strip bare "1." if this is the start of a "1. ... \n 2. ..." internal numbered list
          if (!hm.hasExplicitPrefix && looksLikeBareNumberedList) {
            cleanedLines.push(line);
          } else if (hm.remainingLineText.length > 0) {
            cleanedLines.push(hm.remainingLineText);
          }
          continue;
        }
      }
      strippedFirstLineHeader = true;
      cleanedLines.push(line);
    } else {
      // On subsequent lines within the same question's answer, strip repeated explicit headers or continuation headers
      // for the same question (e.g. "Q1 (contd.): ...", "Q1:", "Ans 1:")
      const hm = matchLineQuestionHeader(trimmed, effectiveKnown, baseDigits);
      if (
        hm &&
        hm.matchedQuestionNumber &&
        normalizeQuestionNumber(hm.matchedQuestionNumber) === normTarget &&
        hm.hasExplicitPrefix
      ) {
        if (hm.remainingLineText.length > 0) {
          cleanedLines.push(hm.remainingLineText);
        }
        continue;
      }
      cleanedLines.push(line);
    }
  }

  return cleanedLines.join("\n").trim();
}

/**
 * Splits a single line if an unmistakable explicit question header (e.g. "Q5:", "Ans 5:", "Answer 5:", "5 Ans:")
 * appears mid-line after sentence-ending punctuation or whitespace separation, preventing the previous answer
 * from absorbing the next question's answer when two answers share a line.
 */
function splitLineWithInlineQuestionHeaders(
  line: string,
  knownQuestionNumbers: string[],
  contextParentQuestionPrefix?: string | null
): string[] {
  if (!line || line.trim().length === 0) return [line];

  const inlineRegex =
    /(?:[\.\!\?\;]\s+|\s{2,}|\s+(?=(?:\*{1,2}|_{1,2})?(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)\s*[\.\:\-\#]*\s*\(?\d+\)?\s*[\.\)\:\-\]\–\—]|\b(?:ans(?:wer)?|sol(?:ution)?)\s*[\.\:\-\#]*\s*\(?\d+\)?\s*[\.\)\:\-\]\–\—]|\(?\d+\)?\s*[\.\)\:\-\]\–\—]*\s+(?:ans(?:wer)?|sol(?:ution)?)\s*[\.\)\:\-\]\–\—]))(?=(?:\*{1,2}|_{1,2})?(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\s*\.?\s*(?:no|num|number)\.?)?)\s*[\.\:\-\#]*\s*\(?\d+|\(?\d+(?:\s*\([a-zA-Z0-9]{1,4}\))?\)?(?:\s*[\.\)\:\-\]\–\—]+)?\s+(?:ans(?:wer)?|sol(?:ution)?)\b)/gi;

  const splitIndices: number[] = [];
  let match: RegExpExecArray | null;
  while ((match = inlineRegex.exec(line)) !== null) {
    const candidateStart = match.index + match[0].length;
    if (candidateStart <= 0 || candidateStart >= line.length) continue;
    const leftPart = line.slice(0, candidateStart).trim();
    const rightPart = line.slice(candidateStart).trim();
    if (!leftPart || !rightPart) continue;
    // Never split an attempt serial prefix like "1. Q3: ..." or "(2) Q1: ..." into "1." and "Q3: ..."
    if (/^(?:\(\s*\d+[a-zA-Z]?\s*\)|\[\s*\d+[a-zA-Z]?\s*\]|\d+[a-zA-Z]?\s*[\.\)\:\-\–\—]*)$/.test(leftPart)) {
      continue;
    }
    const leftHeaderMatch = matchLineQuestionHeader(leftPart, knownQuestionNumbers, contextParentQuestionPrefix);
    if (leftHeaderMatch && leftHeaderMatch.remainingLineText.trim().length === 0) {
      continue;
    }
    // Do not split if leftPart ends with "to", "for", "of", "and", "or", "in" (e.g. "refer to Q4" or "similar to Question 2")
    if (/\b(?:to|for|of|and|or|in|see|from|with|than|as|like|between)\s*$/i.test(leftPart)) {
      continue;
    }

    const hm = matchLineQuestionHeader(rightPart, knownQuestionNumbers, contextParentQuestionPrefix);
    if (
      hm &&
      hm.hasExplicitPrefix &&
      (hm.matchedQuestionNumber !== null || hm.isUnknownOrUnclearQuestionHeader) &&
      hm.remainingLineText.length > 0
    ) {
      splitIndices.push(candidateStart);
    }
  }

  if (splitIndices.length === 0) return [line];

  const segments: string[] = [];
  let lastIdx = 0;
  for (const idx of splitIndices) {
    const seg = line.slice(lastIdx, idx).trim();
    if (seg.length > 0) segments.push(seg);
    lastIdx = idx;
  }
  const tail = line.slice(lastIdx).trim();
  if (tail.length > 0) segments.push(tail);
  return segments.length > 0 ? segments : [line];
}

function isCopiedQuestionPromptText(preAnsText: string): boolean {
  const preAnsWords = preAnsText.split(/\s+/).filter((w) => w.length > 0);
  const isSingleSentenceWithoutAnswerPunct =
    preAnsWords.length >= 2 &&
    preAnsWords.length <= 12 &&
    !/[\.\!\?]\s+\S/.test(preAnsText) &&
    !/[:;=\-\–\—]/.test(preAnsText);
  if (!isSingleSentenceWithoutAnswerPunct) return false;

  const hasStudentAnswerIndicators =
    /\b\w+ing\b/i.test(preAnsText) ||
    /\b(?:by|using|first|second|next|then|finally|before|after|while|if|unless|because|since|as|therefore|thus|hence|that|each|every|into|from|through|across|during|to\s+\w+|is\s+(?:the|a|an|defined|called|known|equal|proportional|conserved|constant)|are\s+(?:the|defined|called|known|equal|proportional)|means|refers|states|shows)\b/i.test(
      preAnsText
    );
  const isInterrogativeCopiedPrompt =
    /\?\s*$/.test(preAnsText) &&
    /^(?:what|why|how|when|where|which|who|whom|whose|define|explain|state|describe|list|write|differentiate|compare|distinguish|give|name|find|calculate|solve|prove|derive|draw|discuss|mention|enumerate)\b/i.test(
      preAnsText
    );
  const isImperativeCopiedPrompt =
    !/\?\s*$/.test(preAnsText) &&
    !hasStudentAnswerIndicators &&
    ((/^(?:explain|describe|discuss|state)\s+(?:why|how|what|when|where|which|whether|briefly)\b/i.test(
      preAnsText
    ) &&
      preAnsWords.length <= 10) ||
      (/^(?:define|explain|state|describe|list|write|differentiate|compare|distinguish|give|name|find|calculate|solve|prove|derive|draw|discuss|mention|enumerate)\b/i.test(
        preAnsText
      ) &&
        preAnsWords.length <= 6 &&
        !/\b(?:is|are|was|were|be|been|has|have|had|do|does|did|can|could|will|would|shall|should|may|might|must)\b/i.test(
          preAnsText
        )));

  return isInterrogativeCopiedPrompt || isImperativeCopiedPrompt;
}

/**
 * Deterministically scans the ordered page OCR transcripts to identify question answer blocks
 * and cross-page continuations (including when a continuation page does not repeat the question number,
 * or when answers are written out of question-number order such as Q3 -> Q1 -> Q4).
 * Strips only the structural question header prefix from each block so saved student_answer text
 * contains only the student's actual verbatim answer words.
 */
export function extractQuestionBlocksFromTranscript(
  transcribedPages: { pageNumber: number; text: string }[],
  knownQuestionNumbers: string[]
): {
  blocksByQuestion: Map<string, DeterministicQuestionBlock[]>;
  ambiguousUnassignedBlocks: { pageNumber: number; text: string }[];
  ambiguousQuestions: Set<string>;
  hasAmbiguousHeaders: boolean;
} {
  const sortedPages = [...transcribedPages].sort((a, b) => a.pageNumber - b.pageNumber);
  const blocksByQuestion = new Map<string, DeterministicQuestionBlock[]>();
  const ambiguousUnassignedBlocks: { pageNumber: number; text: string }[] = [];
  const ambiguousQuestions = new Set<string>();
  let hasAmbiguousHeaders = false;

  for (const kq of knownQuestionNumbers) {
    blocksByQuestion.set(normalizeQuestionNumber(kq), []);
  }

  // Pre-pass: identify which questions have an explicit keyword-prefixed header (e.g. "Q1:", "Question 3:", "Ans 4:")
  // anywhere in the transcript. This allows distinguishing internal numbered list items ("1. ...", "2. ...")
  // inside an answer from top-level question headers.
  const explicitlyHeaderedQuestions = new Set<string>();
  let prePassParentPrefix: string | null = null;
  for (const page of sortedPages) {
    const rawPageText = (page.text || "").trim();
    if (!rawPageText || rawPageText === "[No answer detected]") continue;
    for (const rawLine of rawPageText.split(/\r?\n/)) {
      const subLines = splitLineWithInlineQuestionHeaders(rawLine, knownQuestionNumbers, prePassParentPrefix);
      for (const line of subLines) {
        const hm = matchLineQuestionHeader(line, knownQuestionNumbers, prePassParentPrefix);
        if (hm) {
          if (hm.parentQuestionPrefix) {
            prePassParentPrefix = hm.parentQuestionPrefix;
          }
          if (hm.hasExplicitPrefix && hm.matchedQuestionNumber) {
            explicitlyHeaderedQuestions.add(normalizeQuestionNumber(hm.matchedQuestionNumber));
          }
        }
      }
    }
  }

  let activeQuestionNorm: string | null = null;
  let activeQuestionHadExplicitPrefix = false;
  let contextParentQuestionPrefix: string | null = null;
  let internalListCounter = 0;
  let linesSinceLastListItem = 0;
  let lastNonEmptyLineText = "";

  for (const page of sortedPages) {
    const rawPageText = (page.text || "").trim();
    if (!rawPageText || rawPageText === "[No answer detected]") {
      continue;
    }

    const rawLines = rawPageText.split(/\r?\n/);
    const lines: string[] = [];
    for (const rl of rawLines) {
      lines.push(...splitLineWithInlineQuestionHeaders(rl, knownQuestionNumbers, contextParentQuestionPrefix));
    }

    let currentSegmentLines: string[] = [];
    let currentSegmentQuestionNorm: string | null = activeQuestionNorm;
    let currentSegmentIsContinuation = activeQuestionNorm !== null;
    // Reset internalListCounter at the start of a new page unless we are inside an explicitly headered question
    // that ended the previous page inside an active numbered list (so "1., 2." on Page 1 can continue with "3." on Page 2).
    if (!activeQuestionHadExplicitPrefix || linesSinceLastListItem > 6) {
      internalListCounter = 0;
      linesSinceLastListItem = 0;
    }

    const flushSegment = () => {
      const text = currentSegmentLines.join("\n").trim();
      if (!text) {
        currentSegmentLines = [];
        return;
      }
      if (currentSegmentQuestionNorm) {
        // Verify the extracted segment against `page.text` to guarantee 100% verbatim fidelity
        const verifiedSlice = extractVerifiedSubstringFromPage(text, page.text);
        const finalVerbatim = verifiedSlice.found ? verifiedSlice.exactSlice : text;
        const list = blocksByQuestion.get(currentSegmentQuestionNorm) ?? [];
        list.push({
          pageNumber: page.pageNumber,
          verbatimText: finalVerbatim,
          isContinuation: currentSegmentIsContinuation,
        });
        blocksByQuestion.set(currentSegmentQuestionNorm, list);
      } else {
        ambiguousUnassignedBlocks.push({
          pageNumber: page.pageNumber,
          text,
        });
      }
      currentSegmentLines = [];
    };

    for (let idx = 0; idx < lines.length; idx++) {
      const line = lines[idx];
      const trimmedLine = line.trim();
      const headerMatch = matchLineQuestionHeader(
        line,
        knownQuestionNumbers,
        contextParentQuestionPrefix
      );

      if (headerMatch) {
        if (headerMatch.isParentGroupPrefixOnly) {
          // Standalone parent header e.g. "Q1:" before "(a) ..."
          flushSegment();
          contextParentQuestionPrefix = headerMatch.parentQuestionPrefix ?? null;
          activeQuestionNorm = null;
          currentSegmentQuestionNorm = null;
          internalListCounter = 0;
          linesSinceLastListItem = 0;
          continue;
        }

        const flushAmbiguousBoundary = (remainingText: string, rawTrimmed: string) => {
          if (activeQuestionNorm) {
            ambiguousQuestions.add(activeQuestionNorm);
          }
          flushSegment();
          hasAmbiguousHeaders = true;
          activeQuestionNorm = null;
          currentSegmentQuestionNorm = null;
          currentSegmentIsContinuation = false;
          internalListCounter = 0;
          linesSinceLastListItem = 0;
          if (remainingText.length > 0) {
            currentSegmentLines.push(remainingText);
          }
          lastNonEmptyLineText = rawTrimmed;
        };

        if (headerMatch.isUnnumberedQuestionOrAnswerSeparator) {
          // Line starts with an unnumbered "Ans:", "Answer:", "Q:", or "Question:"
          const activeExistingBlocks = activeQuestionNorm
            ? (blocksByQuestion.get(activeQuestionNorm) ?? [])
            : [];
          const nonEmptyCurrentLines = currentSegmentLines
            .map((l) => l.trim())
            .filter((l) => l.length > 0);
          const activeAlreadyHasBodyText =
            nonEmptyCurrentLines.length > 0 || activeExistingBlocks.length > 0;
          const isAnswerStyleSeparator = /^(?:ans(?:wer)?|sol(?:ution)?)\b/i.test(
            headerMatch.rawHeaderPrefix.trim()
          );
          const preAnsText = nonEmptyCurrentLines.join(" ").trim();
          const hasAnswerTextAfterSeparator =
            headerMatch.remainingLineText.trim().length > 0 ||
            lines.slice(idx + 1).some((l) => l.trim().length > 0);
          const preAnsLooksLikeCopiedQuestionPrompt =
            isAnswerStyleSeparator &&
            hasAnswerTextAfterSeparator &&
            activeExistingBlocks.length === 0 &&
            nonEmptyCurrentLines.length > 0 &&
            nonEmptyCurrentLines.length <= 2 &&
            isCopiedQuestionPromptText(preAnsText);

          if (activeQuestionNorm && (!activeAlreadyHasBodyText || preAnsLooksLikeCopiedQuestionPrompt)) {
            if (preAnsLooksLikeCopiedQuestionPrompt) {
              currentSegmentLines = [];
            }
            if (headerMatch.remainingLineText.length > 0) {
              currentSegmentLines.push(headerMatch.remainingLineText);
              lastNonEmptyLineText = headerMatch.remainingLineText;
            }
            continue;
          }

          flushAmbiguousBoundary(headerMatch.remainingLineText, trimmedLine);
          continue;
        }

        if (headerMatch.isUnknownOrUnclearQuestionHeader) {
          flushAmbiguousBoundary(headerMatch.remainingLineText, trimmedLine);
          continue;
        }

        const targetNorm = normalizeQuestionNumber(headerMatch.matchedQuestionNumber!);
        const isBareIntegerHeader =
          !headerMatch.hasExplicitPrefix && /^\d+$/.test(targetNorm);
        const isStandaloneBareIntegerLine =
          isBareIntegerHeader && headerMatch.remainingLineText.length === 0;

        if (isStandaloneBareIntegerLine && activeQuestionNorm !== null) {
          // A standalone number on its own line (e.g. "4") when a question is active:
          // treat as a question header only if the next non-empty line is prose and targetNorm wasn't already explicitly headered
          let nextNonEmptyLine = "";
          for (let j = idx + 1; j < lines.length; j++) {
            if (lines[j].trim().length > 0) {
              nextNonEmptyLine = lines[j].trim();
              break;
            }
          }
          if (!nextNonEmptyLine || isPureMathOrUnitContinuation(nextNonEmptyLine, nextNonEmptyLine) || explicitlyHeaderedQuestions.has(targetNorm)) {
            currentSegmentLines.push(line);
            lastNonEmptyLineText = trimmedLine;
            continue;
          }
        } else if (isBareIntegerHeader && activeQuestionNorm !== null) {
          const numVal = parseInt(targetNorm, 10);
          // Look ahead across up to 6 non-empty wrapped lines to see if a sequential numbered list ("1." -> "2.") continues
          let nextStartsWithNextListInt = false;
          if (!isNaN(numVal)) {
            const nextListPattern = new RegExp(`^\\(?${numVal + 1}[\\)\\.\\:]\\s+`);
            let nonEmptyLookahead = 0;
            for (let j = idx + 1; j < lines.length && nonEmptyLookahead < 6; j++) {
              const candidateTrimmed = lines[j].trim();
              if (!candidateTrimmed) continue;
              nonEmptyLookahead++;
              if (nextListPattern.test(candidateTrimmed)) {
                nextStartsWithNextListInt = true;
                break;
              }
              const candidateHm = matchLineQuestionHeader(
                candidateTrimmed,
                knownQuestionNumbers,
                contextParentQuestionPrefix
              );
              if (candidateHm && candidateHm.hasExplicitPrefix) {
                break;
              }
            }
          }
          const prevEndedWithColon = /:\s*$/.test(lastNonEmptyLineText);
          const activeHasBodyLinesOnCurrentPage = currentSegmentLines.some((l) => l.trim().length > 0);

          // Determine if this bare number is an internal list item inside `activeQuestionNorm`:
          // Note: If `targetNorm` does NOT have an explicit header elsewhere in the paper, do NOT swallow `1.` on a new page
          // unless `activeQuestionNorm` is already `"1"`, or `1.` is preceded by a colon (`:`), or `"1"` already has an explicit header!
          const isClearlyInternalListItem =
            (numVal === 1 &&
              (prevEndedWithColon ||
                (nextStartsWithNextListInt &&
                  (activeHasBodyLinesOnCurrentPage ||
                    activeQuestionNorm === "1" ||
                    explicitlyHeaderedQuestions.has("1"))))) ||
            (internalListCounter > 0 && numVal === internalListCounter + 1) ||
            (activeQuestionHadExplicitPrefix && explicitlyHeaderedQuestions.has(targetNorm));

          if (isClearlyInternalListItem) {
            internalListCounter = numVal;
            linesSinceLastListItem = 0;
            currentSegmentLines.push(line);
            lastNonEmptyLineText = trimmedLine;
            continue;
          }

          if (activeQuestionHadExplicitPrefix && explicitlyHeaderedQuestions.size > 0 && prevEndedWithColon) {
            // Single-item numbered point right after a colon inside an explicit Q-header block:
            // keep inside current question and flag as ambiguous for teacher review
            ambiguousQuestions.add(activeQuestionNorm);
            ambiguousQuestions.add(targetNorm);
            hasAmbiguousHeaders = true;
            currentSegmentLines.push(line);
            lastNonEmptyLineText = trimmedLine;
            continue;
          }
        }

        // Genuine question boundary: flush previous question segment and start the new question block
        flushSegment();
        const existingBlocks = blocksByQuestion.get(targetNorm) ?? [];
        currentSegmentQuestionNorm = targetNorm;
        currentSegmentIsContinuation = existingBlocks.length > 0 || headerMatch.isExplicitContinuation;
        activeQuestionNorm = targetNorm;
        activeQuestionHadExplicitPrefix = headerMatch.hasExplicitPrefix;
        if (headerMatch.parentQuestionPrefix) {
          contextParentQuestionPrefix = headerMatch.parentQuestionPrefix;
        }
        internalListCounter = 0;
        linesSinceLastListItem = 0;

        // Push ONLY the remaining answer text after the structural question header!
        if (headerMatch.remainingLineText.length > 0) {
          currentSegmentLines.push(headerMatch.remainingLineText);
          lastNonEmptyLineText = headerMatch.remainingLineText;
        } else {
          lastNonEmptyLineText = "";
        }
      } else {
        if (
          trimmedLine.length > 0 &&
          /^(?:\*{1,2}|_{1,2})?(?:section|part)\s*[\-\–\—\:\.]*\s*(?:[a-zA-Z0-9]{1,4}|\([a-zA-Z0-9]{1,4}\))(?:\s*[\:\.\-\–\—])?(?:\*{1,2}|_{1,2})?$/i.test(
            trimmedLine
          )
        ) {
          flushSegment();
          activeQuestionNorm = null;
          currentSegmentQuestionNorm = null;
          currentSegmentIsContinuation = false;
          internalListCounter = 0;
          linesSinceLastListItem = 0;
          continue;
        }
        if (trimmedLine.length > 0) {
          if (internalListCounter > 0) {
            linesSinceLastListItem++;
            if (linesSinceLastListItem > 6) {
              internalListCounter = 0;
              linesSinceLastListItem = 0;
            }
          }
          lastNonEmptyLineText = trimmedLine;
        } else {
          // Blank line resets internalListCounter so a bare-numbered question after a blank line is not swallowed as a list item
          internalListCounter = 0;
          linesSinceLastListItem = 0;
        }
        currentSegmentLines.push(line);
      }
    }

    flushSegment();
  }

  return {
    blocksByQuestion,
    ambiguousUnassignedBlocks,
    ambiguousQuestions,
    hasAmbiguousHeaders,
  };
}

/**
 * Formats verified page segments into a single student_answer string without inventing any connecting words
 * and without adding any artificial question or page labels.
 */
export function formatVerifiedSegments(
  segments: { pageNumber: number; verbatimText: string }[]
): string {
  const nonEmpty = segments
    .map((s) => ({ pageNumber: s.pageNumber, verbatimText: s.verbatimText.trim() }))
    .filter((s) => s.verbatimText.length > 0);

  if (nonEmpty.length === 0) return "";

  // Merge adjacent segments on the same page
  const mergedByPage: { pageNumber: number; verbatimText: string }[] = [];
  for (const seg of nonEmpty) {
    const last = mergedByPage[mergedByPage.length - 1];
    if (last && last.pageNumber === seg.pageNumber) {
      if (!last.verbatimText.includes(seg.verbatimText)) {
        last.verbatimText = `${last.verbatimText}\n${seg.verbatimText}`;
      }
    } else {
      mergedByPage.push({ ...seg });
    }
  }

  return mergedByPage.map((s) => s.verbatimText).join("\n\n");
}

/**
 * Sanitizes an AI-returned candidate segment for `questionNumber`:
 * - Truncates/splits if the AI accidentally included a question header belonging to a DIFFERENT question
 *   (preventing cross-question merging when students answer in random order e.g. Q3 -> Q1 -> Q4).
 * - Strips any leading structural question header for `questionNumber` (e.g. "Q1:", "Q1 Ans:", "1.").
 */
function sanitizeAiCandidateSegmentForQuestion(
  rawSegmentText: string,
  questionNumber: string,
  knownQuestionNumbers: string[]
): { cleanedText: string; truncatedForeignQuestion: boolean } {
  const normTarget = normalizeQuestionNumber(questionNumber);
  const effectiveKnown = Array.from(new Set([questionNumber, ...knownQuestionNumbers]));
  const baseDigits = normTarget.match(/^(\d+)/)?.[1] ?? null;

  const rawLines = rawSegmentText.split(/\r?\n/);
  const lines: string[] = [];
  for (const rl of rawLines) {
    lines.push(...splitLineWithInlineQuestionHeaders(rl, effectiveKnown, baseDigits));
  }

  const nonEmptyLines = lines.map((l) => l.trim()).filter((l) => l.length > 0);
  const looksLikeBareNumberedList =
    nonEmptyLines.length >= 2 &&
    /^1[\.\)]\s+/.test(nonEmptyLines[0]) &&
    /^2[\.\)]\s+/.test(nonEmptyLines[1]);

  const keptLines: string[] = [];
  let truncatedForeignQuestion = false;
  let insideForeignLeadingBlock = false;
  let sawTargetHeader = false;
  let internalListCounter = 0;
  let linesSinceLastListItem = 0;
  let lastLineText = "";

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) {
      if (!insideForeignLeadingBlock && keptLines.length > 0) {
        keptLines.push("");
      }
      continue;
    }

    const hm = matchLineQuestionHeader(trimmed, effectiveKnown, baseDigits);
    if (hm) {
      if (hm.isUnnumberedQuestionOrAnswerSeparator) {
        if (keptLines.some((l) => l.trim().length > 0)) {
          truncatedForeignQuestion = true;
          break;
        }
        if (hm.remainingLineText.length > 0) {
          keptLines.push(hm.remainingLineText);
          lastLineText = hm.remainingLineText;
        }
        continue;
      }

      if (hm.isUnknownOrUnclearQuestionHeader) {
        truncatedForeignQuestion = true;
        if (keptLines.length > 0) {
          break;
        } else {
          insideForeignLeadingBlock = true;
          continue;
        }
      }

      if (hm.matchedQuestionNumber) {
        const matchedNorm = normalizeQuestionNumber(hm.matchedQuestionNumber);
        const isBareInt = !hm.hasExplicitPrefix && /^\d+$/.test(matchedNorm);

        // Check if a bare integer inside keptLines is actually an internal numbered list item ("1. ... 2. ...")
        if (isBareInt) {
          const numVal = parseInt(matchedNorm, 10);
          let nextIsSeqInt = false;
          if (!isNaN(numVal)) {
            let nonEmptySeen = 0;
            const nextListPattern = new RegExp(`^\\(?${numVal + 1}[\\)\\.\\:]\\s+`);
            for (let j = i + 1; j < lines.length && nonEmptySeen < 6; j++) {
              const candLine = lines[j].trim();
              if (candLine.length > 0) {
                nonEmptySeen++;
                if (nextListPattern.test(candLine)) {
                  nextIsSeqInt = true;
                  break;
                }
              }
            }
          }
          const prevEndedWithColon = /:\s*$/.test(lastLineText);

          if (
            looksLikeBareNumberedList ||
            (numVal === 1 && (nextIsSeqInt || prevEndedWithColon)) ||
            (internalListCounter > 0 &&
              linesSinceLastListItem <= 6 &&
              numVal === internalListCounter + 1)
          ) {
            internalListCounter = numVal;
            linesSinceLastListItem = 0;
            if (!insideForeignLeadingBlock) {
              keptLines.push(line);
              lastLineText = trimmed;
            }
            continue;
          }
        }

        if (matchedNorm === normTarget) {
          if (
            !sawTargetHeader &&
            hm.hasExplicitPrefix &&
            !hm.isExplicitContinuation &&
            keptLines.some((l) => l.trim().length > 0)
          ) {
            // Unheadered lines before this question's explicit opening header belonged to a preceding question
            keptLines.length = 0;
            truncatedForeignQuestion = true;
          }
          sawTargetHeader = true;
          insideForeignLeadingBlock = false;
          internalListCounter = 0;
          linesSinceLastListItem = 0;
          if (hm.remainingLineText.length > 0) {
            keptLines.push(hm.remainingLineText);
            lastLineText = hm.remainingLineText;
          }
          continue;
        } else {
          // Foreign question header encountered!
          truncatedForeignQuestion = true;
          if (keptLines.length > 0) {
            // Stop immediately before the foreign question header so answers never merge
            break;
          } else {
            insideForeignLeadingBlock = true;
            continue;
          }
        }
      }
    }

    if (!insideForeignLeadingBlock) {
      if (internalListCounter > 0) {
        linesSinceLastListItem++;
        if (linesSinceLastListItem > 6) {
          internalListCounter = 0;
        }
      }
      keptLines.push(line);
      lastLineText = trimmed;
    }
  }

  const joined = keptLines.join("\n").trim();
  const stripped = stripLeadingQuestionHeader(joined, questionNumber, knownQuestionNumbers);
  return { cleanedText: stripped, truncatedForeignQuestion };
}

/**
 * Validates that the mapped student answer for `questionNumber` genuinely exists verbatim in `transcribedPages`,
 * enforces strict per-question boundaries so random-order answers never merge, strips structural question headers,
 * assembles multi-page continuations without inventing words, and flags uncertain/fabricated mappings for review.
 */
export function verifyAndAssembleVerbatimAnswer(
  questionNumber: string,
  aiMatch: RawGradingResult | undefined,
  transcribedPages: { pageNumber: number; text: string }[],
  knownQuestionNumbers: string[],
  precomputedTranscriptScan?: ReturnType<typeof extractQuestionBlocksFromTranscript>
): {
  studentAnswer: string | null;
  sourcePages: number[];
  isAttempted: boolean;
  reviewRequired: boolean;
  reviewReason: string | null;
} {
  const normQ = normalizeQuestionNumber(questionNumber);
  const scan =
    precomputedTranscriptScan ??
    extractQuestionBlocksFromTranscript(transcribedPages, knownQuestionNumbers);
  const detBlocks = scan.blocksByQuestion.get(normQ) ?? [];

  const rawAiAns = aiMatch?.student_answer?.trim() ?? "";
  const aiSaysUnattempted =
    aiMatch?.is_attempted === false ||
    (!rawAiAns && (!aiMatch?.source_segments || aiMatch.source_segments.length === 0)) ||
    rawAiAns === "[No answer detected]" ||
    rawAiAns === "[Optional question — not attempted]";

  let reviewRequired =
    Boolean(aiMatch?.mapping_ambiguous) || scan.ambiguousQuestions.has(normQ);
  let reviewReason: string | null = aiMatch?.mapping_ambiguous
    ? (aiMatch.mapping_review_reason?.trim() ||
        "Ambiguous answer boundary or cross-page continuation detected.")
    : scan.ambiguousQuestions.has(normQ)
    ? "Ambiguous question header or answer boundary detected in student transcript; flagged for teacher review."
    : null;

  // Helper to check if a text slice overlaps with another question's deterministic block
  const overlapsOtherQuestionDetBlock = (candidateSlice: string, pageNum?: number): boolean => {
    if (!candidateSlice) return false;
    for (const [otherNormQ, otherBlocks] of scan.blocksByQuestion.entries()) {
      if (otherNormQ === normQ) continue;
      for (const ob of otherBlocks) {
        if (pageNum !== undefined && ob.pageNumber !== pageNum) continue;
        if (
          extractVerifiedSubstringFromPage(candidateSlice, ob.verbatimText).found ||
          extractVerifiedSubstringFromPage(ob.verbatimText, candidateSlice).found
        ) {
          return true;
        }
      }
    }
    return false;
  };

  // Case 1: AI says unattempted, check if deterministic scanner found an explicit block for this question
  if (aiSaysUnattempted) {
    if (detBlocks.length > 0) {
      const assembled = formatVerifiedSegments(detBlocks);
      const pages = Array.from(new Set(detBlocks.map((b) => b.pageNumber)));
      return {
        studentAnswer: assembled,
        sourcePages: pages,
        isAttempted: true,
        reviewRequired: true,
        reviewReason:
          "Answer text for this question was detected in the OCR transcript but was missed during AI matching; flagged for teacher review.",
      };
    }
    return {
      studentAnswer: null,
      sourcePages: [],
      isAttempted: false,
      reviewRequired,
      reviewReason: reviewRequired ? reviewReason : null,
    };
  }

  // Case 2: Collect and sanitize candidate segments from AI (`source_segments` or `student_answer`)
  const rawCandidateSegments: { pageNumber?: number; text: string }[] = [];
  if (Array.isArray(aiMatch?.source_segments) && aiMatch!.source_segments.length > 0) {
    for (const seg of aiMatch!.source_segments) {
      if (seg?.verbatim_text && seg.verbatim_text.trim().length > 0) {
        rawCandidateSegments.push({
          pageNumber: typeof seg.page_number === "number" ? seg.page_number : undefined,
          text: seg.verbatim_text.trim(),
        });
      }
    }
  } else if (rawAiAns) {
    const pageSplitRegex = /\[Page\s*(\d+)(?:\s*\(Continued\))?\]\s*/gi;
    let match: RegExpExecArray | null;
    const markers: { pageNumber: number; startIdx: number; contentStart: number }[] = [];
    while ((match = pageSplitRegex.exec(rawAiAns)) !== null) {
      markers.push({
        pageNumber: parseInt(match[1], 10),
        startIdx: match.index,
        contentStart: match.index + match[0].length,
      });
    }
    if (markers.length > 0) {
      for (let i = 0; i < markers.length; i++) {
        const endIdx = i + 1 < markers.length ? markers[i + 1].startIdx : rawAiAns.length;
        const chunk = rawAiAns.slice(markers[i].contentStart, endIdx).trim();
        if (chunk) {
          rawCandidateSegments.push({ pageNumber: markers[i].pageNumber, text: chunk });
        }
      }
    } else {
      rawCandidateSegments.push({ text: rawAiAns });
    }
  }

  const candidateSegments: { pageNumber?: number; text: string }[] = [];
  for (const rawSeg of rawCandidateSegments) {
    const { cleanedText } = sanitizeAiCandidateSegmentForQuestion(
      rawSeg.text,
      questionNumber,
      knownQuestionNumbers
    );
    if (cleanedText.length > 0) {
      candidateSegments.push({ pageNumber: rawSeg.pageNumber, text: cleanedText });
    }
  }

  // If sanitizing foreign question headers left no text for this question:
  if (candidateSegments.length === 0) {
    if (detBlocks.length > 0) {
      const assembled = formatVerifiedSegments(detBlocks);
      const pages = Array.from(new Set(detBlocks.map((b) => b.pageNumber)));
      return {
        studentAnswer: assembled,
        sourcePages: pages,
        isAttempted: true,
        reviewRequired,
        reviewReason,
      };
    }
    return {
      studentAnswer: null,
      sourcePages: [],
      isAttempted: false,
      reviewRequired: false,
      reviewReason: null,
    };
  }

  // Verify each sanitized candidate segment against `transcribedPages`
  const verifiedAiSegments: { pageNumber: number; verbatimText: string }[] = [];
  let allAiSegmentsVerified = candidateSegments.length > 0;

  for (const cand of candidateSegments) {
    let matchedOnPage = false;

    if (cand.pageNumber !== undefined) {
      const targetPage = transcribedPages.find((p) => p.pageNumber === cand.pageNumber);
      if (targetPage) {
        const check = extractVerifiedSubstringFromPage(cand.text, targetPage.text);
        if (check.found) {
          const cleanSlice = stripLeadingQuestionHeader(
            check.exactSlice,
            questionNumber,
            knownQuestionNumbers
          );
          if (cleanSlice) {
            verifiedAiSegments.push({ pageNumber: targetPage.pageNumber, verbatimText: cleanSlice });
            matchedOnPage = true;
          }
        }
      }
    }

    if (!matchedOnPage) {
      for (const page of transcribedPages) {
        const check = extractVerifiedSubstringFromPage(cand.text, page.text);
        if (check.found) {
          const cleanSlice = stripLeadingQuestionHeader(
            check.exactSlice,
            questionNumber,
            knownQuestionNumbers
          );
          if (cleanSlice) {
            verifiedAiSegments.push({ pageNumber: page.pageNumber, verbatimText: cleanSlice });
            matchedOnPage = true;
            break;
          }
        }
      }
    }

    // Multi-page span without explicit markers
    if (!matchedOnPage && transcribedPages.length > 1) {
      const lines = cand.text
        .split(/\r?\n/)
        .map((l) => l.trim())
        .filter((l) => l.length > 0);
      if (lines.length >= 2) {
        const multiPageMatches: { pageNumber: number; verbatimText: string }[] = [];
        let allLinesVerified = true;
        for (const line of lines) {
          let lineFound = false;
          for (const page of transcribedPages) {
            const check = extractVerifiedSubstringFromPage(line, page.text);
            if (check.found) {
              const cleanLine = stripLeadingQuestionHeader(
                check.exactSlice,
                questionNumber,
                knownQuestionNumbers
              );
              if (cleanLine) {
                multiPageMatches.push({ pageNumber: page.pageNumber, verbatimText: cleanLine });
              }
              lineFound = true;
              break;
            }
          }
          if (!lineFound) {
            allLinesVerified = false;
            break;
          }
        }
        if (allLinesVerified && multiPageMatches.length > 0) {
          verifiedAiSegments.push(...multiPageMatches);
          matchedOnPage = true;
        }
      }
    }

    if (!matchedOnPage) {
      allAiSegmentsVerified = false;
    }
  }

  // Case 3: Deterministic blocks exist for this question (`detBlocks.length > 0`)
  // `detBlocks` enforces strict question header boundaries, strips structural question headers,
  // and preserves cross-page continuations in page order without ever merging foreign question blocks.
  if (detBlocks.length > 0) {
    if (allAiSegmentsVerified && verifiedAiSegments.length > 0) {
      const aiCombined = formatVerifiedSegments(verifiedAiSegments);
      for (const blk of detBlocks) {
        const matchAiInBlk = extractVerifiedSubstringFromPage(aiCombined, blk.verbatimText);
        if (matchAiInBlk.found) {
          const exactAi = matchAiInBlk.exactSlice;
          const posAi = blk.verbatimText.indexOf(exactAi);
          if (posAi !== -1) {
            const trailingUnmapped = blk.verbatimText.slice(posAi + exactAi.length).trim();
            if (
              trailingUnmapped.length >= 10 &&
              !extractVerifiedSubstringFromPage(trailingUnmapped, aiCombined).found
            ) {
              reviewRequired = true;
              reviewReason =
                reviewReason ||
                "OCR block for this question contains additional unheadered text beyond the AI-matched answer segment; preserved verbatim and flagged for teacher review.";
              break;
            }
          }
        }
      }
    }
    const assembled = formatVerifiedSegments(detBlocks);
    const pages = Array.from(new Set(detBlocks.map((b) => b.pageNumber)));
    return {
      studentAnswer: assembled,
      sourcePages: pages,
      isAttempted: true,
      reviewRequired,
      reviewReason,
    };
  }

  // Case 4: `detBlocks.length === 0`, check if AI assigned text that lies inside another question's deterministic block
  if (detBlocks.length === 0 && verifiedAiSegments.length > 0) {
    const nonOverlappingAiSegments = verifiedAiSegments.filter(
      (seg) => !overlapsOtherQuestionDetBlock(seg.verbatimText, seg.pageNumber)
    );
    if (nonOverlappingAiSegments.length === 0) {
      return {
        studentAnswer: null,
        sourcePages: [],
        isAttempted: false,
        reviewRequired: true,
        reviewReason:
          reviewReason ||
          "Potential answer for this question appears merged inside another question's OCR block without a clear boundary; flagged for teacher review.",
      };
    }
    verifiedAiSegments.length = 0;
    verifiedAiSegments.push(...nonOverlappingAiSegments);
  }

  // Case 5: All remaining AI segments were verified verbatim against the page OCR transcripts
  if (allAiSegmentsVerified && verifiedAiSegments.length > 0) {
    if (detBlocks.length === 0 && scan.ambiguousUnassignedBlocks.length > 0 && knownQuestionNumbers.length > 1) {
      reviewRequired = true;
      reviewReason =
        reviewReason ||
        "Student answer lacked explicit question numbering; answer boundary mapped provisionally and flagged for teacher review.";
    } else if (scan.hasAmbiguousHeaders) {
      reviewRequired = true;
      reviewReason =
        reviewReason ||
        "Unclear or ambiguous question header detected on answer sheet; flagged for teacher review.";
    }

    return {
      studentAnswer: formatVerifiedSegments(verifiedAiSegments),
      sourcePages: Array.from(new Set(verifiedAiSegments.map((s) => s.pageNumber))),
      isAttempted: true,
      reviewRequired,
      reviewReason,
    };
  }

  // Case 6: AI text contained fabricated/modified words AND no deterministic question header matched.
  // Extract only verified fragments that do NOT belong to another question's deterministic block,
  // and flag for teacher review.
  const fallbackVerifiedSegments: { pageNumber: number; verbatimText: string }[] = [];
  const sanitizedRaw = sanitizeAiCandidateSegmentForQuestion(
    rawAiAns,
    questionNumber,
    knownQuestionNumbers
  ).cleanedText;
  const candidateLines = sanitizedRaw
    .split(/(?:\r?\n|(?<=[\.\?\!])\s+)/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

  for (const fragment of candidateLines) {
    for (const page of transcribedPages) {
      const check = extractVerifiedSubstringFromPage(fragment, page.text);
      if (check.found && !overlapsOtherQuestionDetBlock(check.exactSlice, page.pageNumber)) {
        const cleanFrag = stripLeadingQuestionHeader(
          check.exactSlice,
          questionNumber,
          knownQuestionNumbers
        );
        if (cleanFrag) {
          fallbackVerifiedSegments.push({ pageNumber: page.pageNumber, verbatimText: cleanFrag });
        }
        break;
      }
    }
  }

  if (fallbackVerifiedSegments.length > 0) {
    return {
      studentAnswer: formatVerifiedSegments(fallbackVerifiedSegments),
      sourcePages: Array.from(new Set(fallbackVerifiedSegments.map((s) => s.pageNumber))),
      isAttempted: true,
      reviewRequired: true,
      reviewReason:
        reviewReason ||
        "AI answer mapping included text not found verbatim in the OCR transcript; only verified OCR segments were kept and flagged for teacher review.",
    };
  }

  // If other questions have deterministic blocks and this question has no unassigned text left, treat as unattempted
  if (scan.ambiguousUnassignedBlocks.length === 0) {
    const anyDetBlocksExist = Array.from(scan.blocksByQuestion.values()).some((b) => b.length > 0);
    if (anyDetBlocksExist || knownQuestionNumbers.length > 1) {
      return {
        studentAnswer: null,
        sourcePages: [],
        isAttempted: false,
        reviewRequired: true,
        reviewReason:
          reviewReason ||
          "AI marked question as attempted, but no matching verbatim text could be verified in the OCR transcript; flagged for teacher review.",
      };
    }
  }

  // Ultimate fallback if unassigned text exists on the paper (or single-question paper): preserve unassigned/page OCR source text and flag for teacher review
  const unassignedFallback =
    scan.ambiguousUnassignedBlocks.length > 0
      ? formatVerifiedSegments(
          scan.ambiguousUnassignedBlocks.map((b) => ({
            pageNumber: b.pageNumber,
            verbatimText: stripLeadingQuestionHeader(b.text, questionNumber, knownQuestionNumbers),
          }))
        )
      : formatVerifiedSegments(
          transcribedPages
            .filter((p) => p.text && p.text.trim() !== "[No answer detected]")
            .map((p) => ({
              pageNumber: p.pageNumber,
              verbatimText: stripLeadingQuestionHeader(p.text, questionNumber, knownQuestionNumbers),
            }))
        );

  return {
    studentAnswer: unassignedFallback || "[No answer detected]",
    sourcePages:
      scan.ambiguousUnassignedBlocks.length > 0
        ? Array.from(new Set(scan.ambiguousUnassignedBlocks.map((b) => b.pageNumber)))
        : transcribedPages.map((p) => p.pageNumber),
    isAttempted: Boolean(unassignedFallback),
    reviewRequired: true,
    reviewReason:
      reviewReason ||
      "Student answer could not be verified verbatim against a specific question segment in the OCR transcript; flagged for teacher review.",
  };
}

export interface AlignedQuestionEvaluation {
  question_number: string | null;
  question_text: string | null;
  maximum_marks: number;
  student_answer: string | null;
  expected_answer: string | null;
  marking_criteria: string | null;
  expected_answer_source: "question_paper" | "ai_generated" | "missing";
  marking_criteria_source: "question_paper" | "ai_generated" | "missing";
  awarded_marks: number;
  feedback: string;
  review_required: boolean;
  review_reason: string | null;
  source_pages?: number[];
}

/**
 * Refines `transcriptScan` when a deterministic block on a multi-page or multi-answer sheet
 * absorbed a subsequent question's unheadered answer, and AI grading cleanly identified
 * disjoint verbatim substrings for both questions inside that deterministic block.
 */
export function refineTranscriptScanWithDisjointAiSplits(
  scan: ReturnType<typeof extractQuestionBlocksFromTranscript>,
  rawEvaluations: RawGradingResult[],
  knownQuestionNumbers: string[]
): void {
  const getCandidateTexts = (ev: RawGradingResult | undefined, qNum: string): string[] => {
    if (!ev || ev.is_attempted === false) return [];
    const rawCandidates: string[] = [];

    if (Array.isArray(ev.source_segments) && ev.source_segments.length > 0) {
      const segParts: string[] = [];
      for (const seg of ev.source_segments) {
        if (seg?.verbatim_text && seg.verbatim_text.trim().length > 0) {
          segParts.push(seg.verbatim_text.trim());
        }
      }
      if (segParts.length > 0) {
        rawCandidates.push(segParts.join("\n"));
        if (segParts.length > 1) {
          for (const sp of segParts) {
            rawCandidates.push(sp);
          }
        }
      }
    }

    if (ev.student_answer && ev.student_answer.trim().length > 0) {
      const raw = ev.student_answer.trim();
      if (raw !== "[No answer detected]" && raw !== "[Optional question — not attempted]") {
        rawCandidates.push(raw.replace(/\[Page\s*\d+(?:\s*\(Continued\))?\]\s*/gi, "\n").trim());
      }
    }

    const cleanedSet = new Set<string>();
    for (const rc of rawCandidates) {
      const cleaned = sanitizeAiCandidateSegmentForQuestion(
        rc,
        qNum,
        knownQuestionNumbers
      ).cleanedText;
      if (cleaned && cleaned.length >= 4) {
        cleanedSet.add(cleaned);
      }
    }
    return Array.from(cleanedSet);
  };

  const isTextCoveredByBlocks = (
    text: string,
    blocks: DeterministicQuestionBlock[]
  ): boolean => {
    if (!text || blocks.length === 0) return false;
    const combinedBlocks = formatVerifiedSegments(blocks);
    return extractVerifiedSubstringFromPage(text, combinedBlocks).found;
  };

  const appendBlockInPageOrder = (
    normQ: string,
    newBlock: DeterministicQuestionBlock
  ) => {
    const existing = scan.blocksByQuestion.get(normQ) ?? [];
    existing.push({
      ...newBlock,
      isContinuation: existing.length > 0 || newBlock.isContinuation,
    });
    existing.sort((a, b) => a.pageNumber - b.pageNumber);
    scan.blocksByQuestion.set(normQ, existing);
  };

  const maxPasses = Math.max(knownQuestionNumbers.length, 2);
  for (let pass = 0; pass < maxPasses; pass++) {
    let anySplitInPass = false;

    for (const kqB of knownQuestionNumbers) {
      const normB = normalizeQuestionNumber(kqB);
      if (!normB) continue;

      const evalB = rawEvaluations.find(
        (e) => e.question_number && normalizeQuestionNumber(e.question_number) === normB
      );
      const candidatesB = getCandidateTexts(evalB, kqB);
      if (candidatesB.length === 0) continue;

      const blocksB = scan.blocksByQuestion.get(normB) ?? [];
      const unmatchedCandidatesB = candidatesB.filter((c) => !isTextCoveredByBlocks(c, blocksB));
      if (unmatchedCandidatesB.length === 0) continue;

      let splitForB = false;

      for (const kqA of knownQuestionNumbers) {
        const normA = normalizeQuestionNumber(kqA);
        if (!normA || normA === normB) continue;
        const blocksA = scan.blocksByQuestion.get(normA) ?? [];
        if (blocksA.length === 0) continue;

        const evalA = rawEvaluations.find(
          (e) => e.question_number && normalizeQuestionNumber(e.question_number) === normA
        );
        const candidatesA = getCandidateTexts(evalA, kqA);

        for (let bIdx = blocksA.length - 1; bIdx >= 0; bIdx--) {
          const blk = blocksA[bIdx];

          // Way 1: A candidate for Question B matches a suffix (or continuation page) of `blk.verbatimText`
          for (const candB of unmatchedCandidatesB) {
            const matchB = extractVerifiedSubstringFromPage(candB, blk.verbatimText);
            if (!matchB.found) continue;

            const exactB = matchB.exactSlice;
            const posB = blk.verbatimText.indexOf(exactB);
            if (posB === -1) continue;

            const rawBeforeB = blk.verbatimText.slice(0, posB).trim();
            const beforeB = rawBeforeB
              .replace(
                /(?:^|\n)\s*(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[\.\:\-\s]*(?:(?:to|for|of)\s+)?)?(?:question|ques|que|q\.?\s*n\.?|q)(?:\s*\.?\s*(?:no|num|number)\.?)?\s*[\.\:\-\#]*\s*[a-zA-Z0-9\(\)]+\s*[\.\)\:\-\]]*|(?:ans(?:wer)?|sol(?:ution)?)\s*(?:[a-zA-Z0-9\(\)]+\s*)?[\.\)\:\-\]]+|\(\s*\d+\s*\)\s*[\.\)\:\-]*|\d+\s*[\.\)\:\-]+)\s*$/i,
                ""
              )
              .trim();
            const afterB = blk.verbatimText.slice(posB + exactB.length).trim();

            if (beforeB.length > 0 && afterB.length === 0) {
              // Ensure `candB` is specifically the second answer `exactB` and did not include `beforeB`
              const candBIncludesBeforeB = extractVerifiedSubstringFromPage(beforeB, candB).found;
              if (candBIncludesBeforeB) continue;

              // Allow split both when `candidatesA` is disjoint (`beforeB` only) AND when `candidatesA`
              // echoed the combined `pre_segmented_student_answer` (`beforeB + exactB`)
              const aCoversBeforeB =
                candidatesA.length === 0 ||
                blocksA.length > 1 ||
                candidatesA.some(
                  (cA) =>
                    extractVerifiedSubstringFromPage(cA, beforeB).found ||
                    extractVerifiedSubstringFromPage(beforeB, cA).found
                );

              if (aCoversBeforeB) {
                blk.verbatimText =
                  stripLeadingQuestionHeader(beforeB, kqA, knownQuestionNumbers) || beforeB;
                const cleanExactB =
                  stripLeadingQuestionHeader(exactB, kqB, knownQuestionNumbers) || exactB;
                appendBlockInPageOrder(normB, {
                  pageNumber: blk.pageNumber,
                  verbatimText: cleanExactB,
                  isContinuation: false,
                });
                scan.ambiguousQuestions.add(normA);
                scan.ambiguousQuestions.add(normB);
                scan.hasAmbiguousHeaders = true;
                splitForB = true;
                anySplitInPass = true;
                break;
              }
            } else if (
              beforeB.length === 0 &&
              afterB.length === 0 &&
              blocksA.length > 1 &&
              bIdx > 0
            ) {
              // Entire unheadered continuation block on a later page belongs to Question B
              const candBIncludesFirstBlockA = extractVerifiedSubstringFromPage(
                blocksA[0].verbatimText,
                candB
              ).found;
              if (!candBIncludesFirstBlockA) {
                blocksA.splice(bIdx, 1);
                const cleanExactB =
                  stripLeadingQuestionHeader(exactB, kqB, knownQuestionNumbers) || exactB;
                appendBlockInPageOrder(normB, {
                  pageNumber: blk.pageNumber,
                  verbatimText: cleanExactB,
                  isContinuation: false,
                });
                scan.ambiguousQuestions.add(normA);
                scan.ambiguousQuestions.add(normB);
                scan.hasAmbiguousHeaders = true;
                splitForB = true;
                anySplitInPass = true;
                break;
              }
            }
          }

          if (splitForB) break;

          // Way 2: Question A's candidate matches a proper prefix of `blk.verbatimText`, while Question B's
          // candidate returned the combined block (`exactA + afterA`)
          for (const candA of candidatesA) {
            const matchA = extractVerifiedSubstringFromPage(candA, blk.verbatimText);
            if (!matchA.found) continue;

            const exactA = matchA.exactSlice;
            const posA = blk.verbatimText.indexOf(exactA);
            if (posA !== 0) continue;

            const afterA = blk.verbatimText.slice(exactA.length).trim();
            if (afterA.length < 4) continue;
            if (extractVerifiedSubstringFromPage(afterA, candA).found) continue;

            const bCoversAfterA = unmatchedCandidatesB.some(
              (candB) =>
                extractVerifiedSubstringFromPage(afterA, candB).found ||
                extractVerifiedSubstringFromPage(candB, afterA).found
            );

            if (bCoversAfterA) {
              blk.verbatimText =
                stripLeadingQuestionHeader(exactA, kqA, knownQuestionNumbers) || exactA;
              const cleanAfterA =
                stripLeadingQuestionHeader(afterA, kqB, knownQuestionNumbers) || afterA;
              appendBlockInPageOrder(normB, {
                pageNumber: blk.pageNumber,
                verbatimText: cleanAfterA,
                isContinuation: false,
              });
              scan.ambiguousQuestions.add(normA);
              scan.ambiguousQuestions.add(normB);
              scan.hasAmbiguousHeaders = true;
              splitForB = true;
              anySplitInPass = true;
              break;
            }
          }

          if (splitForB) break;
        }

        if (splitForB) break;
      }
    }

    if (!anySplitInPass) break;
  }

  // Post-split check: if any attempted question B still has no separated deterministic block and its
  // AI-mapped text overlaps another question A's deterministic block (e.g., AI returned the identical
  // combined block for both A and B with no verifiable split point), flag both A and B as ambiguous
  // so neither is silently accepted without teacher review.
  for (const kqB of knownQuestionNumbers) {
    const normB = normalizeQuestionNumber(kqB);
    if (!normB) continue;
    const blocksB = scan.blocksByQuestion.get(normB) ?? [];
    if (blocksB.length > 0) continue;

    const evalB = rawEvaluations.find(
      (e) => e.question_number && normalizeQuestionNumber(e.question_number) === normB
    );
    const candidatesB = getCandidateTexts(evalB, kqB);
    if (candidatesB.length === 0) continue;

    for (const kqA of knownQuestionNumbers) {
      const normA = normalizeQuestionNumber(kqA);
      if (!normA || normA === normB) continue;
      const blocksA = scan.blocksByQuestion.get(normA) ?? [];
      if (blocksA.length === 0) continue;

      const overlapsA = candidatesB.some((candB) =>
        blocksA.some(
          (blk) =>
            extractVerifiedSubstringFromPage(candB, blk.verbatimText).found ||
            extractVerifiedSubstringFromPage(blk.verbatimText, candB).found
        )
      );
      if (overlapsA) {
        scan.ambiguousQuestions.add(normA);
        scan.ambiguousQuestions.add(normB);
        scan.hasAmbiguousHeaders = true;
      }
    }
  }
}

/**
 * Aligns graded results with extracted questions, verifies verbatim student answers,
 * enforces mark clamping [0, maximum_marks], and applies optional-question / internal OR-choice rules.
 */
export function evaluateOptionalGroupsAndAlignResults(
  rawQuestions: RawExtractedQuestion[],
  rawEvaluations: RawGradingResult[],
  transcribedPages: { pageNumber: number; text: string }[],
  precomputedTranscriptScan?: ReturnType<typeof extractQuestionBlocksFromTranscript>
): {
  evaluationQuestionsToInsert: AlignedQuestionEvaluation[];
  totalMarksObtained: number;
  computedEffectiveTotalMarks: number;
  allQuestionMarksReliable: boolean;
  anyQuestionReviewRequired: boolean;
  choiceGroupReviewReason: string | null;
} {
  const orderedRawQuestions = sortQuestionsNumerically(rawQuestions);

  const knownQuestionNumbers = orderedRawQuestions
    .map((q) => (q.question_number ? String(q.question_number).trim() : ""))
    .filter((s) => s.length > 0);

  const transcriptScan =
    precomputedTranscriptScan ??
    extractQuestionBlocksFromTranscript(transcribedPages, knownQuestionNumbers);

  // If a deterministic block absorbed an unheadered subsequent question's answer (e.g., final two answers on Page 2)
  // and AI returned disjoint verbatim segments for both questions, split them cleanly and flag for review.
  refineTranscriptScanWithDisjointAiSplits(transcriptScan, rawEvaluations, knownQuestionNumbers);

  let allQuestionMarksReliable = true;
  let choiceGroupReviewReason: string | null = null;

  interface IntermediateQuestionEval {
    rawQuestion: RawExtractedQuestion;
    rule: SelectionRuleMeta | null;
    isStructuralDefect: boolean;
    isAttempted: boolean;
    rawAwarded: number;
    result: AlignedQuestionEvaluation;
  }

  const intermediates: IntermediateQuestionEval[] = orderedRawQuestions.map((q) => {
    const rawQNum = q.question_number ? String(q.question_number).trim() : null;
    const rawQText = q.question_text ? String(q.question_text).trim() : null;
    const normQNum = rawQNum ? normalizeQuestionNumber(rawQNum) : null;
    const rule = getSelectionRuleForQuestion(q);

    const matchingResults = normQNum
      ? rawEvaluations.filter(
          (e) => e.question_number && normalizeQuestionNumber(e.question_number) === normQNum
        )
      : [];

    const maxMarks =
      typeof q.maximum_marks === "number" && !isNaN(q.maximum_marks) && isFinite(q.maximum_marks) && q.maximum_marks > 0
        ? q.maximum_marks
        : 0;

    let isReview = false;
    let reviewReason = "";

    if (q.review_required && q.review_reason && !isMissingCriteriaReason(q.review_reason)) {
      isReview = true;
      reviewReason = q.review_reason;
    }

    const strippedQpCriteria = stripSelectionRuleTag(q.marking_criteria);
    const qpExpIsProvisional = Boolean(q.expected_answer && /^\[Provisional/i.test(q.expected_answer.trim()));
    const qpCritIsProvisional = Boolean(strippedQpCriteria && /^\[Provisional/i.test(strippedQpCriteria.trim()));

    let expectedAns: string | null = q.expected_answer || null;
    let markingCrit: string | null = q.marking_criteria || null;
    let expectedAnswerSource: "question_paper" | "ai_generated" | "missing" = q.expected_answer
      ? (qpExpIsProvisional ? "ai_generated" : "question_paper")
      : "missing";
    let markingCriteriaSource: "question_paper" | "ai_generated" | "missing" = strippedQpCriteria
      ? (qpCritIsProvisional ? "ai_generated" : "question_paper")
      : "missing";

    if (!rawQNum) {
      allQuestionMarksReliable = false;
      const reason = reviewReason
        ? `${reviewReason} | Question number could not be determined from question paper.`
        : "Question number could not be determined from question paper.";
      return {
        rawQuestion: q,
        rule,
        isStructuralDefect: true,
        isAttempted: false,
        rawAwarded: 0,
        result: {
          question_number: null,
          question_text: rawQText,
          maximum_marks: maxMarks,
          student_answer: null,
          expected_answer: expectedAns,
          marking_criteria: markingCrit,
          expected_answer_source: expectedAnswerSource,
          marking_criteria_source: markingCriteriaSource,
          awarded_marks: 0,
          feedback: "Question number is unreadable or unknown on question paper. Marks cannot be awarded.",
          review_required: true,
          review_reason: reason,
        },
      };
    }

    if (!rawQText) {
      allQuestionMarksReliable = false;
      const reason = reviewReason
        ? `${reviewReason} | Question text is unreadable or missing in question paper.`
        : "Question text is unreadable or missing in question paper.";
      return {
        rawQuestion: q,
        rule,
        isStructuralDefect: true,
        isAttempted: false,
        rawAwarded: 0,
        result: {
          question_number: rawQNum,
          question_text: null,
          maximum_marks: maxMarks,
          student_answer: null,
          expected_answer: expectedAns,
          marking_criteria: markingCrit,
          expected_answer_source: expectedAnswerSource,
          marking_criteria_source: markingCriteriaSource,
          awarded_marks: 0,
          feedback: "Question text is unreadable or missing on question paper. Marks cannot be awarded.",
          review_required: true,
          review_reason: reason,
        },
      };
    }

    if (maxMarks === 0) {
      allQuestionMarksReliable = false;
      const reason = reviewReason
        ? `${reviewReason} | Maximum marks not specified in question paper.`
        : "Maximum marks not specified in question paper. Marks cannot be awarded without a defined maximum.";
      return {
        rawQuestion: q,
        rule,
        isStructuralDefect: true,
        isAttempted: false,
        rawAwarded: 0,
        result: {
          question_number: rawQNum,
          question_text: rawQText,
          maximum_marks: 0,
          student_answer: null,
          expected_answer: expectedAns,
          marking_criteria: markingCrit,
          expected_answer_source: expectedAnswerSource,
          marking_criteria_source: markingCriteriaSource,
          awarded_marks: 0,
          feedback: "Maximum marks not specified on question paper. Marks cannot be awarded without a defined maximum.",
          review_required: true,
          review_reason: reason,
        },
      };
    }

    if (matchingResults.length === 0) {
      const verified = verifyAndAssembleVerbatimAnswer(
        rawQNum,
        undefined,
        transcribedPages,
        knownQuestionNumbers,
        transcriptScan
      );
      if (!verified.isAttempted) {
        const unattemptedReview = isReview || verified.reviewRequired;
        const unattemptedReason = [reviewReason, verified.reviewReason].filter(Boolean).join(" | ");
        return {
          rawQuestion: q,
          rule,
          isStructuralDefect: false,
          isAttempted: false,
          rawAwarded: 0,
          result: {
            question_number: rawQNum,
            question_text: rawQText,
            maximum_marks: maxMarks,
            student_answer: "[No answer detected]",
            expected_answer: expectedAns,
            marking_criteria: attachSelectionRuleToCriteria(strippedQpCriteria, rule),
            expected_answer_source: expectedAnswerSource,
            marking_criteria_source: markingCriteriaSource,
            awarded_marks: 0,
            feedback: "No answer written for this question.",
            review_required: unattemptedReview,
            review_reason: unattemptedReview ? (unattemptedReason || "Flagged for teacher review.") : null,
            source_pages: verified.sourcePages,
          },
        };
      }
      allQuestionMarksReliable = false;
      const reason = reviewReason
        ? `${reviewReason} | AI did not return a grading result for this question.`
        : "AI did not return a grading result for this question.";
      return {
        rawQuestion: q,
        rule,
        isStructuralDefect: true,
        isAttempted: verified.isAttempted,
        rawAwarded: 0,
        result: {
          question_number: rawQNum,
          question_text: rawQText,
          maximum_marks: maxMarks,
          student_answer: verified.studentAnswer,
          expected_answer: expectedAns,
          marking_criteria: attachSelectionRuleToCriteria(strippedQpCriteria, rule),
          expected_answer_source: expectedAnswerSource,
          marking_criteria_source: markingCriteriaSource,
          awarded_marks: 0,
          feedback: "AI did not return an evaluation for this question.",
          review_required: true,
          review_reason: reason,
          source_pages: verified.sourcePages,
        },
      };
    }

    if (matchingResults.length > 1) {
      allQuestionMarksReliable = false;
      const verified = verifyAndAssembleVerbatimAnswer(
        rawQNum,
        matchingResults[0],
        transcribedPages,
        knownQuestionNumbers,
        transcriptScan
      );
      return {
        rawQuestion: q,
        rule,
        isStructuralDefect: true,
        isAttempted: verified.isAttempted,
        rawAwarded: 0,
        result: {
          question_number: rawQNum,
          question_text: rawQText,
          maximum_marks: maxMarks,
          student_answer: verified.studentAnswer,
          expected_answer: expectedAns,
          marking_criteria: markingCrit,
          expected_answer_source: expectedAnswerSource,
          marking_criteria_source: markingCriteriaSource,
          awarded_marks: 0,
          feedback: "AI returned multiple conflicting grading results for this question.",
          review_required: true,
          review_reason: `Multiple contradictory grading results returned by AI for question ${rawQNum}.`,
          source_pages: verified.sourcePages,
        },
      };
    }

    const match = matchingResults[0];

    // Verify student answer verbatim against page OCR transcripts & assemble cross-page continuations
    const verified = verifyAndAssembleVerbatimAnswer(
      rawQNum,
      match,
      transcribedPages,
      knownQuestionNumbers,
      transcriptScan
    );

    if (verified.reviewRequired && verified.reviewReason) {
      isReview = true;
      reviewReason = reviewReason ? `${reviewReason} | ${verified.reviewReason}` : verified.reviewReason;
    }

    // Resolve expected answer (shared class cache takes precedence over per-student AI generation)
    if (q.expected_answer && q.expected_answer.trim().length > 0) {
      expectedAns = q.expected_answer.trim();
      expectedAnswerSource = qpExpIsProvisional ? "ai_generated" : "question_paper";
    } else if (match.expected_answer && match.expected_answer.trim().length > 0) {
      expectedAns = match.expected_answer.trim();
      expectedAnswerSource = match.expected_answer_source === "question_paper" ? "question_paper" : "ai_generated";
    } else {
      expectedAns = null;
      expectedAnswerSource = "missing";
    }

    // Resolve marking criteria (preserving any [Selection Rule: ...] tag)
    if (strippedQpCriteria && strippedQpCriteria.trim().length > 0) {
      markingCrit = attachSelectionRuleToCriteria(strippedQpCriteria.trim(), rule);
      markingCriteriaSource = qpCritIsProvisional ? "ai_generated" : "question_paper";
    } else if (match.marking_criteria && stripSelectionRuleTag(match.marking_criteria)?.trim()) {
      const matchCrit = stripSelectionRuleTag(match.marking_criteria)!.trim();
      markingCrit = attachSelectionRuleToCriteria(matchCrit, rule);
      markingCriteriaSource = match.marking_criteria_source === "question_paper" ? "question_paper" : "ai_generated";
    } else {
      markingCrit = attachSelectionRuleToCriteria(null, rule);
      markingCriteriaSource = "missing";
    }

    const strippedFinalCrit = stripSelectionRuleTag(markingCrit);
    const hasReliableCriteria = Boolean(
      (expectedAns && expectedAns.trim().length > 0) ||
      (strippedFinalCrit && strippedFinalCrit.trim().length > 0)
    );

    let awarded = 0;
    let feedback = match.feedback?.trim() || "";

    if (!hasReliableCriteria) {
      awarded = 0;
      isReview = true;
      reviewReason = reviewReason
        ? `${reviewReason} | Expected answer or marking criteria could not be reliably established or generated.`
        : "Expected answer or marking criteria could not be reliably established or generated. Marks cannot be awarded without reliable criteria.";
      feedback = feedback
        ? `${feedback} (0 marks awarded: Marking criteria unavailable.)`
        : "0 marks awarded: Expected answer or marking criteria unavailable.";
      allQuestionMarksReliable = false;
    } else if (verified.isAttempted) {
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
        reviewReason = reviewReason
          ? `${reviewReason} | AI awarded marks (${rawAwarded}) exceeded maximum marks (${maxMarks}); clamped.`
          : `AI awarded marks (${rawAwarded}) exceeded maximum marks (${maxMarks}); clamped.`;
      } else {
        awarded = Math.round(rawAwarded * 100) / 100;
      }
    } else {
      awarded = 0;
    }

    // Check match review reasons (ignoring false missing-criteria-only flags and unattempted-optional flags)
    if (match.review_required) {
      const matchReason = match.review_reason?.trim() || "";
      const isCriteriaOnly = isMissingCriteriaReason(matchReason);
      const isNoAnswerOnly = /no answer detected/i.test(matchReason) && !verified.isAttempted;
      if (!isCriteriaOnly && !isNoAnswerOnly) {
        isReview = true;
        if (matchReason) {
          reviewReason = reviewReason ? `${reviewReason} | ${matchReason}` : matchReason;
        }
      }
    }

    if (verified.isAttempted && verified.studentAnswer && verified.studentAnswer.includes("[unclear]")) {
      isReview = true;
      reviewReason = reviewReason
        ? `${reviewReason} | Student handwriting contains [unclear] segments or is ambiguous.`
        : "Student handwriting contains [unclear] segments or is ambiguous.";
    }

    return {
      rawQuestion: q,
      rule,
      isStructuralDefect: false,
      isAttempted: verified.isAttempted,
      rawAwarded: awarded,
      result: {
        question_number: rawQNum,
        question_text: rawQText,
        maximum_marks: maxMarks,
        student_answer: verified.isAttempted ? verified.studentAnswer : "[No answer detected]",
        expected_answer: expectedAns,
        marking_criteria: markingCrit,
        expected_answer_source: expectedAnswerSource,
        marking_criteria_source: markingCriteriaSource,
        awarded_marks: awarded,
        feedback: feedback || (verified.isAttempted ? "Evaluated." : "No answer written for this question."),
        review_required: isReview,
        review_reason: isReview ? (reviewReason || "Flagged for teacher review.") : null,
        source_pages: verified.sourcePages,
      },
    };
  });

  // Group non-defective questions by optional selection group vs. compulsory
  const groups = new Map<string, { rule: SelectionRuleMeta; items: IntermediateQuestionEval[] }>();
  for (const item of intermediates) {
    if (item.rule && item.rule.requiredAttempts < item.rule.totalInGroup) {
      const existing = groups.get(item.rule.selectionGroup);
      if (existing) {
        existing.items.push(item);
      } else {
        groups.set(item.rule.selectionGroup, { rule: item.rule, items: [item] });
      }
    } else if (!item.isStructuralDefect && !item.isAttempted) {
      // Compulsory question not attempted by student -> flag for review
      item.result.student_answer = "[No answer detected]";
      item.result.awarded_marks = 0;
      item.result.review_required = true;
      item.result.review_reason = item.result.review_reason
        ? `${item.result.review_reason} | No answer detected in student paper.`
        : "No answer detected in student paper.";
      item.result.feedback = "No answer written for this compulsory question.";
    }
  }

  // Evaluate each optional / OR-choice group
  for (const [groupName, { rule, items }] of groups.entries()) {
    const attemptedItems = items.filter((i) => !i.isStructuralDefect && i.isAttempted);
    const unattemptedItems = items.filter((i) => !i.isStructuralDefect && !i.isAttempted);
    const required = rule.requiredAttempts;
    const groupDisplay = rule.isOrChoice
      ? `internal OR choice (${items.map((i) => i.result.question_number).join(" OR ")})`
      : `optional group '${groupName}' (attempt any ${required} of ${items.length})`;

    if (attemptedItems.length === required) {
      // Student attempted the exact required count: skipped optional questions are NOT penalized or flagged
      // unless a genuine boundary ambiguity was already flagged on that question.
      for (const unattempted of unattemptedItems) {
        unattempted.result.student_answer = "[Optional question — not attempted]";
        unattempted.result.awarded_marks = 0;
        if (!unattempted.result.review_required) {
          unattempted.result.review_reason = null;
        }
        unattempted.result.feedback = rule.isOrChoice
          ? "Alternative OR question was attempted by student (not counted)."
          : `Optional question not attempted by student (${required} of ${items.length} required attempts satisfied).`;
      }
    } else if (attemptedItems.length < required) {
      // Student attempted fewer questions than required
      const missingCount = required - attemptedItems.length;
      const underAttemptReason = `Student attempted ${attemptedItems.length} of ${required} required question(s) in ${groupDisplay}.`;
      choiceGroupReviewReason = choiceGroupReviewReason
        ? `${choiceGroupReviewReason} | ${underAttemptReason}`
        : underAttemptReason;

      unattemptedItems.forEach((unattempted, idx) => {
        if (idx < missingCount) {
          unattempted.result.student_answer = "[No answer detected]";
          unattempted.result.awarded_marks = 0;
          unattempted.result.review_required = true;
          unattempted.result.review_reason = unattempted.result.review_reason
            ? `${unattempted.result.review_reason} | ${underAttemptReason}`
            : underAttemptReason;
          unattempted.result.feedback = `No answer written. ${underAttemptReason}`;
        } else {
          unattempted.result.student_answer = "[Optional question — not attempted]";
          unattempted.result.awarded_marks = 0;
          if (!unattempted.result.review_required) {
            unattempted.result.review_reason = null;
          }
          unattempted.result.feedback = "Optional question not attempted by student.";
        }
      });
    } else {
      // Student attempted MORE questions than allowed (`attemptedItems.length > required`):
      // Do NOT silently select the best `required` or discard/zero-out answers.
      // Preserve all detected answers and their graded scores, and flag the extra attempts for teacher review.
      const overAttemptReason = `Student attempted ${attemptedItems.length} questions in ${groupDisplay} where only ${required} ${required === 1 ? "is" : "are"} allowed; all detected answers are preserved and flagged for teacher review.`;
      choiceGroupReviewReason = choiceGroupReviewReason
        ? `${choiceGroupReviewReason} | ${overAttemptReason}`
        : overAttemptReason;

      attemptedItems.forEach((att, idx) => {
        att.result.review_required = true;
        att.result.review_reason = att.result.review_reason
          ? `${att.result.review_reason} | ${overAttemptReason}`
          : overAttemptReason;
        if (idx >= required) {
          att.result.feedback = `[Extra Attempt (${idx + 1} of ${attemptedItems.length} attempted, ${required} allowed) — Preserved for teacher review] ${att.result.feedback}`;
        }
      });

      for (const unattempted of unattemptedItems) {
        unattempted.result.student_answer = "[Optional question — not attempted]";
        unattempted.result.awarded_marks = 0;
        if (!unattempted.result.review_required) {
          unattempted.result.review_reason = null;
        }
        unattempted.result.feedback = "Optional question not attempted by student.";
      }
    }
  }

  const effectiveCalc = calculateEffectiveExamTotalMarks(rawQuestions);
  if (!effectiveCalc.allMarksPresent) {
    allQuestionMarksReliable = false;
  }
  if (effectiveCalc.choiceGroupDiscrepancyReason) {
    choiceGroupReviewReason = choiceGroupReviewReason
      ? `${choiceGroupReviewReason} | ${effectiveCalc.choiceGroupDiscrepancyReason}`
      : effectiveCalc.choiceGroupDiscrepancyReason;
  }

  const groupedItemsSet = new Set<IntermediateQuestionEval>();
  let totalMarksObtained = 0;
  let anyQuestionReviewRequired = false;

  for (const [, { rule, items }] of groups.entries()) {
    const memberMaxMarks = items
      .map((it) =>
        typeof it.result.maximum_marks === "number" && it.result.maximum_marks > 0
          ? it.result.maximum_marks
          : 0
      )
      .sort((a, b) => b - a);
    const groupPermittedMax = memberMaxMarks
      .slice(0, rule.requiredAttempts)
      .reduce((acc, v) => acc + v, 0);

    for (const it of items) {
      groupedItemsSet.add(it);
    }

    const countedGroupAwarded = items
      .filter((it) => !it.isStructuralDefect && it.isAttempted)
      .map((it) => {
        const maxM =
          typeof it.result.maximum_marks === "number" && it.result.maximum_marks > 0
            ? it.result.maximum_marks
            : 0;
        const aw =
          typeof it.result.awarded_marks === "number" && it.result.awarded_marks > 0
            ? it.result.awarded_marks
            : 0;
        return maxM > 0 ? Math.min(aw, maxM) : 0;
      })
      .sort((a, b) => b - a)
      .slice(0, rule.requiredAttempts)
      .reduce((acc, v) => acc + v, 0);

    totalMarksObtained +=
      groupPermittedMax > 0
        ? Math.min(countedGroupAwarded, groupPermittedMax)
        : countedGroupAwarded;
  }

  const evaluationQuestionsToInsert = intermediates.map((item) => {
    if (!groupedItemsSet.has(item)) {
      totalMarksObtained += item.result.awarded_marks;
    }
    if (item.result.review_required) {
      anyQuestionReviewRequired = true;
    }
    return item.result;
  });

  if (effectiveCalc.effectiveTotalMarks > 0) {
    totalMarksObtained = Math.min(totalMarksObtained, effectiveCalc.effectiveTotalMarks);
  }

  return {
    evaluationQuestionsToInsert,
    totalMarksObtained: Math.round(Math.max(0, totalMarksObtained) * 100) / 100,
    computedEffectiveTotalMarks: effectiveCalc.effectiveTotalMarks,
    allQuestionMarksReliable,
    anyQuestionReviewRequired,
    choiceGroupReviewReason,
  };
}

/**
 * Computes the final provisional evaluation summary, ensuring that provisional total marks
 * and percentage never exceed the permitted maximum and that any review condition or marks
 * mismatch disables automatic percentage approval and places the paper in `needs_review`.
 */
export function finalizeProvisionalEvaluationSummary(params: {
  aligned: ReturnType<typeof evaluateOptionalGroupsAndAlignResults>;
  officialClassTotal: number;
  extractedTotalMarks?: number;
  isQpUnclear?: boolean;
  qpReviewReason?: string | null;
  qpSequenceWarning?: string | null;
  unexpectedAiQuestionsWarning?: string | null;
}): {
  finalEvaluationStatus: "completed" | "needs_review";
  finalTotalMarksObtained: number;
  finalTotalMarks: number;
  finalExtractedMarks: number;
  marksMatchClassTotal: boolean;
  percentageAvailable: boolean;
  calculatedPercentage: number | null;
  overallReviewRequired: boolean;
  evaluationReviewReason: string | null;
} {
  const {
    aligned,
    officialClassTotal,
    extractedTotalMarks = 0,
    isQpUnclear = false,
    qpReviewReason = null,
    qpSequenceWarning = null,
    unexpectedAiQuestionsWarning = null,
  } = params;

  const qpHasGenuineIssue = isQpUnclear && !isMissingCriteriaReason(qpReviewReason);
  let evaluationReviewReason: string | null =
    qpSequenceWarning ||
    unexpectedAiQuestionsWarning ||
    aligned.choiceGroupReviewReason ||
    (qpHasGenuineIssue ? (qpReviewReason || "Question paper was unclear or incomplete.") : null);

  const finalExtractedMarks =
    aligned.computedEffectiveTotalMarks > 0
      ? aligned.computedEffectiveTotalMarks
      : extractedTotalMarks > 0
      ? extractedTotalMarks
      : 0;

  const marksMatchClassTotal =
    aligned.allQuestionMarksReliable &&
    officialClassTotal > 0 &&
    Math.round(finalExtractedMarks * 100) === Math.round(officialClassTotal * 100);

  const permittedMax =
    officialClassTotal > 0
      ? finalExtractedMarks > 0
        ? Math.min(officialClassTotal, finalExtractedMarks)
        : officialClassTotal
      : finalExtractedMarks;

  const finalTotalMarksObtained =
    permittedMax > 0
      ? Math.round(Math.min(Math.max(0, aligned.totalMarksObtained), permittedMax) * 100) / 100
      : Math.round(Math.max(0, aligned.totalMarksObtained) * 100) / 100;

  let percentageAvailable = false;
  let calculatedPercentage: number | null = null;
  let overallReviewRequired =
    aligned.anyQuestionReviewRequired || qpHasGenuineIssue || Boolean(aligned.choiceGroupReviewReason);

  if (marksMatchClassTotal) {
    percentageAvailable = true;
    calculatedPercentage = Math.min(
      100,
      Math.max(0, Math.round((finalTotalMarksObtained / officialClassTotal) * 10000) / 100)
    );
  } else {
    percentageAvailable = false;
    calculatedPercentage = null;
    overallReviewRequired = true;

    const mismatchMsg = !aligned.allQuestionMarksReliable
      ? "Some question maximum marks or marking criteria could not be reliably determined."
      : `Extracted question marks (${finalExtractedMarks}) do not reliably match the configured class total marks (${officialClassTotal}).`;

    if (!evaluationReviewReason || !evaluationReviewReason.includes(mismatchMsg)) {
      evaluationReviewReason = evaluationReviewReason
        ? `${evaluationReviewReason} | ${mismatchMsg}`
        : mismatchMsg;
    }
  }

  const finalEvaluationStatus = overallReviewRequired ? "needs_review" : "completed";

  return {
    finalEvaluationStatus,
    finalTotalMarksObtained,
    finalTotalMarks: officialClassTotal,
    finalExtractedMarks,
    marksMatchClassTotal,
    percentageAvailable,
    calculatedPercentage,
    overallReviewRequired,
    evaluationReviewReason,
  };
}

export interface ResolvedClassQuestionCache {
  rawQuestions: RawExtractedQuestion[];
  isQpUnclear: boolean;
  qpReviewReason: string | null;
  qpSequenceWarning: string | null;
  extractedTotalMarks: number;
}

export interface SinglePaperEvaluationOutcome {
  paperId: string;
  httpStatus: number;
  body: Record<string, any>;
}

function mapCachedQuestionRow(q: any): RawExtractedQuestion {
  return {
    page_number:
      q.page_number !== null && q.page_number !== undefined ? Number(q.page_number) : null,
    question_number: q.question_number ?? null,
    question_text: q.question_text ?? null,
    maximum_marks:
      q.maximum_marks !== null && q.maximum_marks !== undefined ? Number(q.maximum_marks) : null,
    expected_answer: q.expected_answer ?? null,
    marking_criteria: q.marking_criteria ?? null,
    review_required: Boolean(q.review_required),
    review_reason: q.review_reason ?? null,
    sort_order:
      q.sort_order !== null && q.sort_order !== undefined ? Number(q.sort_order) : undefined,
  };
}


/**
 * Evaluates a single student paper with strict isolation of state, OCR transcripts,
 * question cloning, and error handling so concurrent or sequential evaluations in a batch
 * never leak data across students.
 */
export async function evaluateSingleStudentPaper(params: {
  adminClient: any;
  teacherId: string;
  paperId: string;
  geminiApiKey: string;
  retryOptions?: GeminiRetryOptions;
  sharedClassCache?: Map<string, Promise<ResolvedClassQuestionCache>>;
}): Promise<SinglePaperEvaluationOutcome> {
  const {
    adminClient,
    teacherId,
    geminiApiKey,
    retryOptions = {},
    sharedClassCache,
  } = params;
  const paperId = String(params.paperId ?? "").trim();

  if (!paperId) {
    return {
      paperId: "",
      httpStatus: 400,
      body: {
        status: "failed",
        error: "Validation error: 'paper_id' (or 'paperId') string is required.",
      },
    };
  }

  let studentOcrCompleted = false;

  try {
    // 1. Ownership & Relations Verification (Scoped strictly to paperId + teacherId)
    const { data: paper, error: paperError } = await adminClient
      .from("student_papers")
      .select("id, student_id, class_id, teacher_id, total_pages, ocr_status, combined_ocr_text")
      .eq("id", paperId)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (paperError || !paper) {
      return {
        paperId,
        httpStatus: 404,
        body: {
          status: "failed",
          paperId,
          error: "Student paper not found or access denied.",
        },
      };
    }

    // Execute independent metadata & cache lookups in parallel to minimize round-trip latency
    const [
      { data: existingEval },
      { data: classRoom, error: classError },
      { data: multiQpPages, error: multiQpError },
      { data: studentPages, error: studentPagesError },
      { data: initialClassExtraction },
    ] = await Promise.all([
      adminClient
        .from("student_paper_evaluations")
        .select("id, status")
        .eq("paper_id", paperId)
        .maybeSingle(),
      adminClient
        .from("classes")
        .select("id, subject_name, total_marks, question_paper_path")
        .eq("id", paper.class_id)
        .eq("teacher_id", teacherId)
        .maybeSingle(),
      adminClient
        .from("class_question_paper_pages")
        .select("page_number, storage_path")
        .eq("class_id", paper.class_id)
        .eq("teacher_id", teacherId)
        .order("page_number", { ascending: true }),
      adminClient
        .from("student_paper_pages")
        .select("id, page_number, storage_path, ocr_text, ocr_status")
        .eq("paper_id", paperId)
        .eq("teacher_id", teacherId)
        .order("page_number", { ascending: true }),
      adminClient
        .from("class_question_paper_extractions")
        .select(
          "id, status, total_pages, total_questions, extracted_total_marks, marks_match_class_total, unclear_question_paper, review_required, review_reason, updated_at"
        )
        .eq("class_id", paper.class_id)
        .eq("teacher_id", teacherId)
        .maybeSingle(),
    ]);

    // Safety: Protect Approved Final Results from Overwrite (HTTP 409)
    if (existingEval && existingEval.status === "approved") {
      return {
        paperId,
        httpStatus: 409,
        body: {
          status: "failed",
          paperId,
          error:
            "This student paper has already been finalized and approved by the teacher. Approved results cannot be overwritten.",
        },
      };
    }

    if (classError || !classRoom) {
      return {
        paperId,
        httpStatus: 404,
        body: {
          status: "failed",
          paperId,
          error: "Class associated with paper not found.",
        },
      };
    }

    const officialClassTotal = Number(classRoom.total_marks) || 0;

    // Validate Question Paper Pages Configuration (Multi-Page table first, fallback to classes.question_paper_path)
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
      return {
        paperId,
        httpStatus: 400,
        body: {
          status: "failed",
          paperId,
          error:
            "No question paper found for this class. Please upload a question paper in Class settings before running evaluation.",
        },
      };
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

    // Validate Student Paper Pages & Continuous Sequence 1..N = total_pages
    if (studentPagesError || !studentPages || studentPages.length === 0) {
      return {
        paperId,
        httpStatus: 400,
        body: {
          status: "failed",
          paperId,
          error: "This student paper has no scanned pages to process.",
        },
      };
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
      return {
        paperId,
        httpStatus: 400,
        body: { status: "failed", paperId, error: errorMsg },
      };
    }

    // =========================================================================
    // 5 & 6. CONCURRENT QUESTION PAPER RESOLUTION & STUDENT PAGE OCR
    //        Step 5 (Class QP Cache Load / Extraction) and Step 6 (Student OCR)
    //        operate on independent tables/buckets and run in parallel.
    // =========================================================================
    const loadOrExtractClassQuestions = async (): Promise<ResolvedClassQuestionCache> => {
      let classExtraction = initialClassExtraction;
      let resolvedQuestions: RawExtractedQuestion[] = [];
      let localQpUnclear = isQpUnclear;
      let localQpSequenceWarning = qpSequenceWarning;
      let localQpReviewReason: string | null = null;
      let localExtractedTotalMarks = 0;

      // Concurrency Rule: If another request is currently processing this class's question paper, wait for it
      if (classExtraction && classExtraction.status === "processing") {
        const isStale =
          classExtraction.updated_at &&
          Date.now() - new Date(classExtraction.updated_at).getTime() > 180000; // 3 minutes timeout
        if (!isStale) {
          console.log(
            `[process-and-grade-paper] Class ${paper.class_id} question extraction is currently processing by another request. Waiting...`
          );
          const maxWaitMs = 60000;
          const pollInterval = 2500;
          const startWait = Date.now();

          while (Date.now() - startWait < maxWaitMs) {
            await sleep(pollInterval);
            const { data: pollExt } = await adminClient
              .from("class_question_paper_extractions")
              .select(
                "id, status, total_pages, total_questions, extracted_total_marks, marks_match_class_total, unclear_question_paper, review_required, review_reason, updated_at"
              )
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
      if (
        classExtraction &&
        (classExtraction.status === "completed" || classExtraction.status === "needs_review")
      ) {
        console.log(
          `[process-and-grade-paper] Reusing cached class questions from public.class_question_paper_questions for class ${paper.class_id}...`
        );
        const { data: cachedQRows, error: qFetchErr } = await adminClient
          .from("class_question_paper_questions")
          .select(
            "page_number, question_number, question_text, maximum_marks, expected_answer, marking_criteria, review_required, review_reason, sort_order"
          )
          .eq("class_id", paper.class_id)
          .eq("teacher_id", teacherId)
          .order("sort_order", { ascending: true });

        if (!qFetchErr && cachedQRows && cachedQRows.length > 0) {
          resolvedQuestions = sortQuestionsNumerically(cachedQRows.map(mapCachedQuestionRow));

          // Backfill and persist any missing provisional expected answers / marking criteria once for the class
          resolvedQuestions = await ensureSharedClassRubrics(
            adminClient,
            geminiApiKey,
            paper.class_id,
            teacherId,
            resolvedQuestions,
            true,
            retryOptions
          );

          const isQpCriteriaOnly = isMissingCriteriaReason(classExtraction.review_reason);
          localQpUnclear =
            localQpUnclear ||
            Boolean(classExtraction.unclear_question_paper) ||
            (Boolean(classExtraction.review_required) && !isQpCriteriaOnly);
          localQpReviewReason = isQpCriteriaOnly ? null : classExtraction.review_reason || null;
          const effectiveFromCache = calculateEffectiveExamTotalMarks(resolvedQuestions);
          localExtractedTotalMarks = effectiveFromCache.allMarksPresent
            ? effectiveFromCache.effectiveTotalMarks
            : Number(classExtraction.extracted_total_marks) || 0;
        } else {
          console.warn(
            "[process-and-grade-paper] Class extraction status was completed but no questions were found in table. Re-extracting..."
          );
          classExtraction = null;
        }
      }

      // If no questions are cached, perform extraction once and persist to class cache
      if (resolvedQuestions.length === 0) {
        console.log(
          `[process-and-grade-paper] No valid cache found for class ${paper.class_id}. Performing one-time question paper extraction...`
        );

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

            if (
              existingAfterConflict &&
              (existingAfterConflict.status === "completed" ||
                existingAfterConflict.status === "needs_review")
            ) {
              const { data: qAfter } = await adminClient
                .from("class_question_paper_questions")
                .select(
                  "page_number, question_number, question_text, maximum_marks, expected_answer, marking_criteria, review_required, review_reason, sort_order"
                )
                .eq("class_id", paper.class_id)
                .eq("teacher_id", teacherId)
                .order("sort_order", { ascending: true });

              if (qAfter && qAfter.length > 0) {
                resolvedQuestions = sortQuestionsNumerically(qAfter.map(mapCachedQuestionRow));
              }
            }

            if (resolvedQuestions.length === 0) {
              throw new Error(
                `Could not initiate question paper extraction for class: ${
                  newExtErr?.message || "Concurrent extraction lock error"
                }`
              );
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

        if (resolvedQuestions.length === 0) {
          try {
            // Extract question paper pages with bounded concurrency while preserving exact page order
            const perPageOutcomes = await runWithConcurrency(
              qpPagePaths,
              MAX_CONCURRENT_OCR,
              async (qp) => {
                console.log(
                  `[process-and-grade-paper] Downloading and extracting question paper page ${qp.pageNumber}...`
                );

                const { data: qpBlob, error: qpDownloadError } = await adminClient.storage
                  .from("question-papers")
                  .download(qp.storagePath);

                if (qpDownloadError || !qpBlob) {
                  throw new Error(
                    `Failed to download question paper page ${qp.pageNumber} (${qp.storagePath}): ${qpDownloadError?.message}`
                  );
                }

                const qpArrayBuffer = await qpBlob.arrayBuffer();
                const qpBase64 = toBase64(new Uint8Array(qpArrayBuffer));
                const qpMime = qpBlob.type || "image/jpeg";

                const pageExtractionPrompt = `You are a meticulous exam question paper digitizer.
Analyze this single page of an exam question paper (Page ${qp.pageNumber}) and extract all exam instructions, marking rules, optional question rules, and questions/subquestions present on this page.

STRICT EXTRACTION RULES:
1. Extract ONLY questions and instructions visibly present on this page image. DO NOT invent or assume any questions.
2. Extract all general or section-level instructions visible on this page into "page_instructions" (e.g., "Attempt any 7 out of 10 questions", "All questions carry equal marks", "4 questions each carrying 20 marks", "Section A: Attempt any 4").
   - If the instructions state that every question carries the same specific mark (e.g. "each question carries 20 marks"), set "equal_marks_per_question" to that number; otherwise null.
   - If the instructions state an overall exam attempt limit (e.g. "Attempt any 7 out of 10"), set "required_attempts_in_exam" (e.g. 7) and "total_available_questions_in_exam" (e.g. 10); otherwise null.
3. Extract the exact question numbering (e.g., "1", "2(a)", "2(b)", "3.1").
   - If a question has NO visible question number, return null for question_number. DO NOT invent "1", "[Unknown]", or any replacement number.
4. Extract the complete question text visible on this page.
   - If question text is unreadable, cut off, or missing, return null for question_text. DO NOT invent question text.
5. Extract maximum marks:
   - If marks are visibly printed next to the question, set "maximum_marks" to that number.
   - If marks are not printed next to the individual question, leave "maximum_marks" as null (equal-mark instructions will be resolved from page_instructions if applicable).
6. Optional questions & internal OR choices:
   - If a question belongs to a section (e.g., "Section A"), set "section" to "A".
   - If a question is part of an optional selection group (e.g., "Attempt any 7 of 10" or "Section A: Attempt any 4 of 6"), set "selection_group" (e.g., "EXAM_OPTIONAL" or "SECTION_A"), "group_required_attempts" (e.g. 7 or 4), and "group_total_questions" (e.g. 10 or 6).
   - If two questions/subquestions are an internal OR choice (e.g., "Q2(a) ... OR Q2(b) ..."), extract both as separate entries in "questions", set "is_or_choice": true on both, set "or_partner_question_number" to the partner's question_number, set "selection_group": "OR_Q2", "group_required_attempts": 1, and "group_total_questions": 2.
7. For expected answers and marking criteria:
   - If the question paper provides an answer key, formula, options, rubric, or specific criteria, extract it.
   - If the question paper does not provide an expected answer or marking criteria, leave expected_answer and marking_criteria as null.
8. If a question is blurry, cut off, or illegible, set review_required to true and explain in review_reason.
9. If the overall page is unreadable or cut off, set unclear_page to true.

Return strict JSON matching this schema:
{
  "unclear_page": boolean,
  "page_instructions": string[],
  "equal_marks_per_question": number | null,
  "required_attempts_in_exam": number | null,
  "total_available_questions_in_exam": number | null,
  "questions": [
    {
      "question_number": string | null,
      "question_text": string | null,
      "maximum_marks": number | null,
      "section": string | null,
      "selection_group": string | null,
      "group_required_attempts": number | null,
      "group_total_questions": number | null,
      "is_or_choice": boolean,
      "or_partner_question_number": string | null,
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
                    temperature: 0.0,
                    maxOutputTokens: 8192,
                  },
                });

                const qpResponse = await callGeminiWithRetry(
                  geminiApiKey,
                  qpPagePayload,
                  retryOptions
                );
                const { text: qpJsonText } = extractGeminiResponseText(qpResponse);
                const parsedQpResult = extractAndParseJson<RawPageExtractionResult>(qpJsonText);
                if (!parsedQpResult.ok || !parsedQpResult.value || typeof parsedQpResult.value !== "object") {
                  throw new Error(
                    `Gemini returned invalid JSON for question paper page ${qp.pageNumber}.`
                  );
                }

                return { pageNumber: qp.pageNumber, parsedPageQp: parsedQpResult.value };
              }
            );

            perPageOutcomes.sort((a, b) => a.pageNumber - b.pageNumber);

            const allExtractedQuestions: RawExtractedQuestion[] = [];
            const pageExtractionResults: RawPageExtractionResult[] = [];

            for (const outcome of perPageOutcomes) {
              pageExtractionResults.push(outcome.parsedPageQp);
              if (Boolean(outcome.parsedPageQp.unclear_page)) {
                localQpUnclear = true;
              }
              const pageQuestions: RawExtractedQuestion[] = outcome.parsedPageQp.questions ?? [];
              for (const q of pageQuestions) {
                q.page_number = outcome.pageNumber;
                allExtractedQuestions.push(q);
              }
            }

            if (allExtractedQuestions.length === 0) {
              throw new Error(
                "No questions could be extracted from the question paper. Please verify the uploaded image."
              );
            }

            // Resolve optional groups, internal OR choices, and equal-mark instructions across all extracted pages
            const { ambiguousMarksWarning } = resolveEqualMarksAndSelectionRules(
              allExtractedQuestions,
              pageExtractionResults,
              officialClassTotal
            );

            if (ambiguousMarksWarning) {
              localQpUnclear = true;
              localQpSequenceWarning = localQpSequenceWarning
                ? `${localQpSequenceWarning} | ${ambiguousMarksWarning}`
                : ambiguousMarksWarning;
            }

            // Programmatic Validation of Extracted Questions: Check Duplicates, Unknown Numbers, Empty fields, Marks
            const seenQNumbers = new Set<string>();
            const duplicateQNumbers = new Set<string>();

            for (const q of allExtractedQuestions) {
              const rawNum = q.question_number ? String(q.question_number).trim() : "";
              if (!rawNum) {
                q.question_number = null;
                q.review_required = true;
                q.review_reason =
                  q.review_reason || "Question number could not be determined from question paper.";
                localQpUnclear = true;
              } else {
                q.question_number = rawNum;
                const norm = normalizeQuestionNumber(rawNum);
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
                q.review_reason =
                  q.review_reason || "Question text is empty or missing in question paper.";
                localQpUnclear = true;
              } else {
                q.question_text = rawText;
              }

              const rawMax = q.maximum_marks;
              if (
                rawMax === null ||
                rawMax === undefined ||
                typeof rawMax !== "number" ||
                isNaN(rawMax) ||
                !isFinite(rawMax) ||
                rawMax <= 0
              ) {
                q.maximum_marks = null;
                q.review_required = true;
                q.review_reason =
                  q.review_reason || "Maximum marks not specified or invalid in question paper.";
                localQpUnclear = true;
              }

              const strippedCrit = stripSelectionRuleTag(q.marking_criteria);
              const rule = getSelectionRuleForQuestion(q);
              const hasCriteria = Boolean(
                (q.expected_answer && q.expected_answer.trim().length > 0) ||
                  (strippedCrit && strippedCrit.trim().length > 0)
              );
              if (!hasCriteria) {
                q.expected_answer = null;
                q.marking_criteria = attachSelectionRuleToCriteria(null, rule);
              } else {
                q.marking_criteria = attachSelectionRuleToCriteria(strippedCrit, rule);
              }
            }

            if (duplicateQNumbers.size > 0) {
              localQpUnclear = true;
              for (const q of allExtractedQuestions) {
                if (q.question_number) {
                  const norm = normalizeQuestionNumber(q.question_number);
                  if (duplicateQNumbers.has(norm)) {
                    q.review_required = true;
                    q.review_reason = `Duplicate question number '${q.question_number}' detected in question paper.`;
                  }
                }
              }
            }

            // Generate shared provisional expected answers and marking criteria ONCE for the class before inserting into cache
            await ensureSharedClassRubrics(
              adminClient,
              geminiApiKey,
              paper.class_id,
              teacherId,
              allExtractedQuestions,
              false,
              retryOptions
            );

            // Compute effective exam total marks (accounting for optional question groups and OR choices)
            const effectiveMarksCalc = calculateEffectiveExamTotalMarks(allExtractedQuestions);
            if (effectiveMarksCalc.choiceGroupDiscrepancyReason) {
              localQpUnclear = true;
              localQpSequenceWarning = localQpSequenceWarning
                ? `${localQpSequenceWarning} | ${effectiveMarksCalc.choiceGroupDiscrepancyReason}`
                : effectiveMarksCalc.choiceGroupDiscrepancyReason;
            }

            const allMarksPresent = effectiveMarksCalc.allMarksPresent;
            localExtractedTotalMarks = allMarksPresent ? effectiveMarksCalc.effectiveTotalMarks : 0;
            const marksMatch =
              allMarksPresent &&
              officialClassTotal > 0 &&
              Math.round(localExtractedTotalMarks * 100) === Math.round(officialClassTotal * 100);

            localQpReviewReason =
              localQpSequenceWarning ||
              (localQpUnclear ? "Question paper was unclear or incomplete." : null);

            // Delete any existing questions for this class (in case of re-try)
            await adminClient
              .from("class_question_paper_questions")
              .delete()
              .eq("class_id", paper.class_id);

            const sortedExtractedQuestions = sortQuestionsNumerically(allExtractedQuestions);

            // Save extracted questions (including shared rubrics and selection rule tags) into public.class_question_paper_questions
            const questionsToInsert = sortedExtractedQuestions.map((q, idx) => ({
              class_id: paper.class_id,
              extraction_id: extractionRecordId,
              teacher_id: teacherId,
              page_number:
                q.page_number !== undefined && q.page_number !== null
                  ? Number(q.page_number)
                  : null,
              question_number: q.question_number || null,
              question_text: q.question_text || null,
              maximum_marks:
                q.maximum_marks !== null && q.maximum_marks !== undefined ? q.maximum_marks : null,
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
              throw new Error(
                `Failed to save extracted questions to class_question_paper_questions: ${insQuestionsErr.message}`
              );
            }

            // Mark class extraction header completed or needs_review
            const extractionFinalStatus = localQpUnclear ? "needs_review" : "completed";
            const { error: updExtErr } = await adminClient
              .from("class_question_paper_extractions")
              .update({
                status: extractionFinalStatus,
                total_questions: allExtractedQuestions.length,
                extracted_total_marks: allMarksPresent ? localExtractedTotalMarks : null,
                marks_match_class_total: marksMatch,
                unclear_question_paper: localQpUnclear,
                review_required: localQpUnclear,
                review_reason: localQpReviewReason,
                extracted_at: new Date().toISOString(),
                updated_at: new Date().toISOString(),
              })
              .eq("id", extractionRecordId);

            if (updExtErr) {
              throw new Error(
                `Failed to update class_question_paper_extractions: ${updExtErr.message}`
              );
            }

            resolvedQuestions = sortedExtractedQuestions;
          } catch (extErr: unknown) {
            const extErrMsg = sanitizeErrorMessage(
              extErr instanceof Error ? extErr.message : String(extErr)
            );
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

      return {
        rawQuestions: resolvedQuestions.map((q) => ({ ...q })),
        isQpUnclear: localQpUnclear,
        qpReviewReason: localQpReviewReason,
        qpSequenceWarning: localQpSequenceWarning,
        extractedTotalMarks: localExtractedTotalMarks,
      };
    };

    const resolveClassQuestionsTask = async (): Promise<ResolvedClassQuestionCache> => {
      const cacheKey = `${teacherId}:${paper.class_id}`;
      if (sharedClassCache) {
        let cachedPromise = sharedClassCache.get(cacheKey);
        if (!cachedPromise) {
          cachedPromise = loadOrExtractClassQuestions();
          sharedClassCache.set(cacheKey, cachedPromise);
        }
        try {
          const cached = await cachedPromise;
          // Return a fresh deep clone of rawQuestions so no student evaluation mutates shared question objects
          return {
            ...cached,
            rawQuestions: cached.rawQuestions.map((q) => ({ ...q })),
          };
        } catch (err) {
          sharedClassCache.delete(cacheKey);
          throw err;
        }
      }
      const resolved = await loadOrExtractClassQuestions();
      return {
        ...resolved,
        rawQuestions: resolved.rawQuestions.map((q) => ({ ...q })),
      };
    };

    const transcribeStudentPagesTask = async (): Promise<{
      transcribedPages: { pageNumber: number; text: string }[];
      combinedStudentAnswers: string;
    }> => {
      const needsAnyPageOcr = studentPages.some(
        (p: any) => !p.ocr_text || p.ocr_text.trim().length === 0 || p.ocr_status !== "completed"
      );

      if (needsAnyPageOcr) {
        // 4. Mark Paper-Level OCR as Processing only when fresh OCR is needed
        await adminClient
          .from("student_papers")
          .update({ ocr_status: "processing", ocr_error: null })
          .eq("id", paperId);
      }

      // 6. Bounded-Concurrency Student Page OCR (Max 3 Concurrent Pages + Preserve Order + Reuse Completed OCR)
      console.log(
        `[process-and-grade-paper] Processing ${studentPages.length} student answer page(s) for paper ${paperId} with bounded concurrency (limit=${MAX_CONCURRENT_OCR})...`
      );

      const ocrSystemInstruction = `You are a high-precision optical character recognition (OCR) engine specialized in transcribing handwritten student examination answer sheets.
Your task is to transcribe what the student actually wrote verbatim.

STRICT OCR RULES:
1. Transcribe the handwriting exactly as written.
2. Preserve original spelling, capitalization, punctuation, line breaks, question numbers, and student mistakes.
3. Preserve numbers, mathematical equations, and scientific notation as accurately as possible.
4. If a word or phrase is crossed out, omit it unless legible and clearly intended as the final answer.
5. If handwriting is illegible, smudged, cut off, or unclear, transcribe it as "[unclear]". DO NOT GUESS words.
6. If the page contains no handwriting or answer at all, output exactly: "[No answer detected]".
7. DO NOT return an empty response.
8. DO NOT solve, answer, correct, paraphrase, complete, or grade any questions.
9. Output raw transcribed text only.`;

      const pages = await runWithConcurrency(
        studentPages,
        MAX_CONCURRENT_OCR,
        async (page: any): Promise<{ pageNumber: number; text: string }> => {
          const pageNum = Number(page.page_number);
          if (
            page.ocr_text &&
            page.ocr_text.trim().length > 0 &&
            page.ocr_status === "completed"
          ) {
            console.log(
              `[process-and-grade-paper] Reusing existing OCR for student paper ${paperId} page ${pageNum}`
            );
            return { pageNumber: pageNum, text: page.ocr_text.trim() };
          }

          console.log(
            `[process-and-grade-paper] Downloading & transcribing student paper ${paperId} page ${pageNum}...`
          );
          const { data: pageBlob, error: pageDownloadErr } = await adminClient.storage
            .from("student-papers")
            .download(page.storage_path);

          if (pageDownloadErr || !pageBlob) {
            throw new Error(
              `Failed to download student page ${pageNum} (${page.storage_path}): ${pageDownloadErr?.message}`
            );
          }

          const pageBytes = await pageBlob.arrayBuffer();
          const pageBase64 = toBase64(new Uint8Array(pageBytes));
          const pageMime = pageBlob.type || "image/jpeg";

          const pagePayloadString = JSON.stringify({
            systemInstruction: { parts: [{ text: ocrSystemInstruction }] },
            contents: [
              {
                parts: [
                  { inlineData: { mimeType: pageMime, data: pageBase64 } },
                  {
                    text: `Transcribe all handwritten student answers from Page ${pageNum} verbatim according to instructions:`,
                  },
                ],
              },
            ],
            generationConfig: {
              temperature: 0.0,
              maxOutputTokens: 4096,
            },
          });

          const pageOcrResp = await callGeminiWithRetry(
            geminiApiKey,
            pagePayloadString,
            retryOptions
          );
          const { text: transcribed } = extractGeminiResponseText(pageOcrResp);

          // Reject Empty OCR Response
          if (!transcribed || transcribed.length === 0) {
            const ocrEmptyError = `OCR engine returned empty or unreadable text for page ${pageNum}. Cannot proceed with evaluation.`;
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
              `Failed to save OCR result to student_paper_pages for page ${pageNum}: ${pageUpdateErr.message}`
            );
          }

          return { pageNumber: pageNum, text: transcribed };
        }
      );

      pages.sort((a, b) => a.pageNumber - b.pageNumber);
      const combined = pages
        .map((p) => `=== STUDENT ANSWER PAGE ${p.pageNumber} ===\n${p.text}`)
        .join("\n\n");

      // Only write paper-level OCR status if fresh OCR ran or paper-level combined_ocr_text was not yet marked completed
      if (
        needsAnyPageOcr ||
        paper.ocr_status !== "completed" ||
        paper.combined_ocr_text !== combined
      ) {
        await adminClient
          .from("student_papers")
          .update({
            ocr_status: "completed",
            combined_ocr_text: combined,
            ocr_completed_at: new Date().toISOString(),
            ocr_error: null,
          })
          .eq("id", paperId);
      }

      studentOcrCompleted = true;
      return { transcribedPages: pages, combinedStudentAnswers: combined };
    };

    const [resolvedClassCache, { transcribedPages, combinedStudentAnswers }] = await Promise.all([
      resolveClassQuestionsTask(),
      transcribeStudentPagesTask(),
    ]);

    const rawQuestions = resolvedClassCache.rawQuestions;
    isQpUnclear = resolvedClassCache.isQpUnclear;
    const qpReviewReason = resolvedClassCache.qpReviewReason;
    qpSequenceWarning = resolvedClassCache.qpSequenceWarning;
    const extractedTotalMarks = resolvedClassCache.extractedTotalMarks;

    // Pre-segment student answers deterministically before calling Gemini so the grader receives
    // clean per-question boundaries and Step 8 can reuse the scan directly.
    const knownQuestionNumbers = rawQuestions
      .map((q) => (q.question_number ? String(q.question_number).trim() : ""))
      .filter((s) => s.length > 0);
    const precomputedTranscriptScan = extractQuestionBlocksFromTranscript(
      transcribedPages,
      knownQuestionNumbers
    );

    // 7. Student Answer Identification & Deterministic AI Grading (Text-Only, Temperature 0.0)
    console.log(
      `[process-and-grade-paper] Running AI verbatim answer mapping and criterion-based grading for paper ${paperId}...`
    );

    const gradingSystemPrompt = `You are an objective, deterministic academic examination grader.
You will receive:
1. EXAM QUESTIONS & LOCKED RUBRIC: The list of extracted questions, their question numbers, question text, maximum marks, selection rules (optional/OR choices), the shared expected_answer and marking_criteria, and (where deterministically bounded by question headers) the pre_segmented_student_answer and pre_segmented_source_pages.
2. STUDENT'S TRANSCRIBED ANSWER PAPER: Verbatim OCR transcription of the student's handwritten answers across all exam pages (Page 1..N).

CRITICAL SEPARATION OF RESPONSIBILITIES:

STEP 1 — VERBATIM ANSWER IDENTIFICATION, RANDOM-ORDER BOUNDARIES & CROSS-PAGE CONTINUATION MAPPING:
- If "pre_segmented_student_answer" is provided for a question, use it as that question's header-stripped answer across pages, EXCEPT if you detect that "pre_segmented_student_answer" accidentally includes an unheadered answer to a subsequent question (for example, when the final two answers on Page 2 had no visible question header between them). In that case, split them at the exact verbatim boundary so each question receives ONLY its own verbatim answer segment, and set "mapping_ambiguous": true on both questions.
- Whenever a question has "pre_segmented_student_answer": null, ALWAYS inspect the full OCR transcript across all pages (including the trailing paragraphs of other questions' pre_segmented_student_answer blocks on multi-page sheets) to check whether the student wrote an unheadered answer to that question. If found, extract its exact verbatim segment for that question, exclude that segment from the preceding question's answer, and set "mapping_ambiguous": true on both questions.
- RANDOM / OUT-OF-ORDER ANSWERS, OPTIONAL QUESTION IDENTITY & STRICT QUESTION BOUNDARIES:
  * Students may answer questions in any order (for example: Q3, then Q1, then Q4, while leaving Q2, Q5, Q6 unanswered).
  * NEVER assign student answers sequentially to 1, 2, 3 by default! Always preserve the actual question identity (e.g., if the student answered Q3, Q1, and Q4 on an "Attempt any 3 of 6" paper, map their answers to question_number "3", "1", and "4", and set is_attempted: false on "2", "5", and "6").
  * If a student writes an attempt serial number before a question label (e.g., "1. Q3: ...", "2. Q1: ...", "3. Q4: ..."), the question identity is Q3, Q1, Q4 — NOT Q1, Q2, Q3.
  * Every new question header (e.g., "Q1", "Q4", "Question 2", "Ans 3", "Answer 5", "5 Ans", "1.", "2(a)") is a strict boundary that immediately ends the previous question's answer.
  * NEVER combine or merge answers from two different questions (e.g., never attach Q5's answer to Q4 or Q4's answer to Q1), even when intermediate questions are skipped or when an answer continues across pages.
- DO NOT INCLUDE QUESTION LABELS IN THE EXTRACTED ANSWER:
  * Do NOT prepend or include structural question headers/labels such as "Q1:", "Q1.", "Q1 Ans:", "Ans 1:", "Answer 1:", "1 Ans:", "Question 1:", "1.", or "1)" in "student_answer" or "source_segments[].verbatim_text".
  * Copy ONLY the student's actual verbatim answer body after the structural question header.
  * Preserve legitimate numbers, mathematical expressions, equations, dates, units, and internal numbered points that are part of the student's answer content.
- NEVER paraphrase, summarize, grammar-correct, spell-correct, complete unfinished sentences, or add extra words to the student's answer!
- Every character in "source_segments[].verbatim_text" and "student_answer" MUST be copied character-for-character from the provided OCR transcript.
- CROSS-PAGE CONTINUATIONS:
  * An answer may start at the bottom of Page N and continue at the top of Page N+1 (or a later page) WITHOUT repeating the question number on the continuation page, until a new question header appears.
  * An answer may also be continued later using an explicit continuation header (e.g. "Q1 contd").
  * When an answer spans multiple pages, include each page's exact verbatim excerpt in "source_segments" in logical reading order (e.g., [{"page_number": 1, "verbatim_text": "..."}, {"page_number": 2, "verbatim_text": "..."}]) and set "continuation_detected": true.
  * Do NOT invent connecting words or artificial page/question labels between pages.
  * If it is genuinely ambiguous whether an unnumbered continuation belongs to a question, still include the verbatim source segment, set "mapping_ambiguous": true, and explain in "mapping_review_reason".
- UNATTEMPTED QUESTIONS:
  * If the student did not write any answer for a question, set "is_attempted": false, "source_segments": [], and "student_answer": "[No answer detected]". Never copy another question's answer into an unattempted question.

STEP 2 — CONSISTENT CRITERION-BY-CRITERION GRADING:
- Grade the student's verbatim answer strictly against the provided "expected_answer" and "marking_criteria" for that question.
- Do NOT invent a different rubric per student. Use the provided marking_criteria breakdown so equivalent student answers always receive identical marks.
- Accept academically valid equivalent phrasing and synonyms that satisfy each criterion point.
- Award full marks when all criteria points are met, proportional partial marks when some criteria points are met, and 0 marks if unattempted or completely incorrect.
- NEVER exceed maximum_marks. NEVER award negative marks.
- Provide clear, constructive "feedback" citing which marking criteria points were met or missed.
- Set "review_required": true ONLY if there is an actual defect requiring human attention: unclear handwriting ("[unclear]"), ambiguous cross-page mapping ("mapping_ambiguous": true), missing question metadata, or unattempted compulsory question.

Return strict JSON matching this schema:
{
  "evaluations": [
    {
      "question_number": string | null,
      "is_attempted": boolean,
      "source_segments": [
        {
          "page_number": number,
          "verbatim_text": string
        }
      ],
      "student_answer": string | null,
      "continuation_detected": boolean,
      "mapping_ambiguous": boolean,
      "mapping_review_reason": string | null,
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

    const sanitizedQuestionsForGrading = rawQuestions.map((q) => {
      const rule = getSelectionRuleForQuestion(q);
      const normQ = q.question_number ? normalizeQuestionNumber(q.question_number) : "";
      const detBlocks = normQ ? (precomputedTranscriptScan.blocksByQuestion.get(normQ) ?? []) : [];
      return {
        question_number: q.question_number ?? null,
        question_text: q.question_text ?? null,
        maximum_marks: q.maximum_marks ?? null,
        selection_rule: rule
          ? {
              group: rule.selectionGroup,
              required_attempts: rule.requiredAttempts,
              total_in_group: rule.totalInGroup,
              is_or_choice: rule.isOrChoice,
            }
          : null,
        expected_answer: q.expected_answer ?? null,
        marking_criteria: stripSelectionRuleTag(q.marking_criteria) ?? null,
        pre_segmented_student_answer:
          detBlocks.length > 0 ? formatVerifiedSegments(detBlocks) : null,
        pre_segmented_source_pages:
          detBlocks.length > 0 ? Array.from(new Set(detBlocks.map((b) => b.pageNumber))) : [],
      };
    });

    const gradingUserPrompt = `EXAM QUESTIONS & LOCKED RUBRIC:
${JSON.stringify(sanitizedQuestionsForGrading, null, 2)}

STUDENT'S TRANSCRIBED ANSWER PAPER:
${combinedStudentAnswers}`;

    const gradingPayloadString = JSON.stringify({
      systemInstruction: { parts: [{ text: gradingSystemPrompt }] },
      contents: [{ parts: [{ text: gradingUserPrompt }] }],
      generationConfig: {
        responseMimeType: "application/json",
        temperature: 0.0,
        maxOutputTokens: 8192,
      },
    });

    const parsedGrading = await callGeminiGradingWithRetry(
      geminiApiKey,
      gradingPayloadString,
      knownQuestionNumbers,
      retryOptions
    );
    const rawEvaluations = parsedGrading.evaluations ?? [];

    // Check for Unexpected Grading Question Numbers Returned by AI
    const extractedNumSet = new Set(
      rawQuestions
        .filter((q) => q.question_number !== null && q.question_number !== undefined)
        .map((q) => normalizeQuestionNumber(q.question_number))
    );

    let unexpectedAiQuestionsWarning: string | null = null;
    for (const g of rawEvaluations) {
      if (g.question_number) {
        const gNum = normalizeQuestionNumber(g.question_number);
        if (gNum && !extractedNumSet.has(gNum)) {
          unexpectedAiQuestionsWarning = `AI returned a grading result for question number '${g.question_number}' which was not found in the extracted question paper.`;
          isQpUnclear = true;
          console.warn(`[process-and-grade-paper] ${unexpectedAiQuestionsWarning}`);
        }
      }
    }

    // 8. Verbatim Verification, Cross-Page Assembly, Optional/OR Choice Scoring, and Mark Clamping
    const alignedEvaluation = evaluateOptionalGroupsAndAlignResults(
      rawQuestions,
      rawEvaluations,
      transcribedPages,
      precomputedTranscriptScan
    );
    const { evaluationQuestionsToInsert } = alignedEvaluation;

    // 9. Handle Class Total Marks & Percentage Reliability Safely
    const {
      finalEvaluationStatus,
      finalTotalMarksObtained,
      finalExtractedMarks,
      percentageAvailable,
      calculatedPercentage,
      overallReviewRequired,
      evaluationReviewReason,
    } = finalizeProvisionalEvaluationSummary({
      aligned: alignedEvaluation,
      officialClassTotal,
      extractedTotalMarks,
      isQpUnclear,
      qpReviewReason,
      qpSequenceWarning,
      unexpectedAiQuestionsWarning,
    });

    // 10. Atomic Database Persistence via PostgreSQL Transactional RPC
    const { data: savedEvaluation, error: rpcErr } = await adminClient.rpc(
      "save_provisional_paper_evaluation",
      {
        p_paper_id: paper.id,
        p_student_id: paper.student_id,
        p_class_id: paper.class_id,
        p_teacher_id: teacherId,
        p_status: finalEvaluationStatus,
        p_total_marks_obtained: finalTotalMarksObtained,
        p_total_marks: officialClassTotal,
        p_percentage: calculatedPercentage,
        p_review_required: overallReviewRequired,
        p_review_reason: evaluationReviewReason || null,
        p_questions: evaluationQuestionsToInsert,
      }
    );

    if (rpcErr || !savedEvaluation) {
      throw new Error(
        `Atomic evaluation persistence failed: ${rpcErr?.message || "Unknown database error"}`
      );
    }

    // 11. Return Structured Provisional Result (HTTP 200) in deterministic numerical question order
    const questionsWithSources = sortQuestionsNumerically(
      (savedEvaluation.questions || []).map((savedQ: any) => {
        const match = evaluationQuestionsToInsert.find(
          (eq) =>
            eq.question_number &&
            savedQ.question_number &&
            normalizeQuestionNumber(eq.question_number) ===
              normalizeQuestionNumber(savedQ.question_number)
        );
        return {
          ...savedQ,
          expected_answer_source:
            match?.expected_answer_source || (savedQ.expected_answer ? "ai_generated" : "missing"),
          marking_criteria_source:
            match?.marking_criteria_source ||
            (savedQ.marking_criteria ? "ai_generated" : "missing"),
          source_pages: match?.source_pages || [],
        };
      })
    );

    return {
      paperId,
      httpStatus: 200,
      body: {
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
      },
    };
  } catch (err: unknown) {
    const errorMessage = sanitizeErrorMessage(
      err instanceof Error ? err.message : String(err)
    );
    console.error(`[process-and-grade-paper] Pipeline error for paper ${paperId}:`, errorMessage);

    if (paperId && adminClient) {
      if (!studentOcrCompleted) {
        try {
          await adminClient
            .from("student_papers")
            .update({ ocr_status: "failed", ocr_error: errorMessage })
            .eq("id", paperId);
        } catch (dbErr) {
          console.warn(
            "[process-and-grade-paper] Failed to update student_papers error status:",
            dbErr
          );
        }
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
          .eq("paper_id", paperId);
      } catch (dbErr) {
        console.warn(
          "[process-and-grade-paper] Failed to update student_paper_evaluations error status:",
          dbErr
        );
      }
    }

    let responseStatus = 500;
    let clientErrorMessage =
      errorMessage || "An unexpected error occurred during paper evaluation.";

    if (err instanceof TemporaryGeminiError) {
      if (err.status === 503) {
        responseStatus = 503;
        clientErrorMessage =
          "The AI grading service (Gemini) is temporarily experiencing high demand and is unavailable (HTTP 503). Retries were exhausted. Please wait a moment and try again.";
      } else if (err.status === 429) {
        responseStatus = 429;
        clientErrorMessage =
          "The AI grading service rate limit was exceeded (HTTP 429). Please wait a moment and try again.";
      } else if (err.status === 504) {
        responseStatus = 504;
        clientErrorMessage =
          "The AI grading service (Gemini) request timed out (HTTP 504). Retries were exhausted. Please wait a moment and try again.";
      }
    } else if (err instanceof PermanentGeminiError) {
      responseStatus = err.status;
    }

    return {
      paperId,
      httpStatus: responseStatus,
      body: {
        status: "failed",
        paperId,
        error: clientErrorMessage,
      },
    };
  }
}

/**
 * Evaluates multiple student papers in a batch with bounded concurrency,
 * shared class question-paper cache reuse, and strict per-student isolation.
 * A failure on one student paper never corrupts or aborts another student's evaluation.
 */
export async function evaluateStudentPapersBatch(params: {
  adminClient: any;
  teacherId: string;
  paperIds: string[];
  geminiApiKey: string;
  retryOptions?: GeminiRetryOptions;
  concurrency?: number;
}): Promise<{
  results: SinglePaperEvaluationOutcome[];
  succeededCount: number;
  failedCount: number;
}> {
  const {
    adminClient,
    teacherId,
    paperIds,
    geminiApiKey,
    retryOptions = {},
    concurrency = 2,
  } = params;
  const sharedClassCache = new Map<string, Promise<ResolvedClassQuestionCache>>();

  const results = await runWithConcurrency(
    paperIds,
    Math.max(1, Math.min(concurrency, 3)),
    async (rawPaperId: string) =>
      evaluateSingleStudentPaper({
        adminClient,
        teacherId,
        paperId: rawPaperId,
        geminiApiKey,
        retryOptions,
        sharedClassCache,
      })
  );

  const succeededCount = results.filter((r) => r.httpStatus === 200).length;
  const failedCount = results.length - succeededCount;

  return {
    results,
    succeededCount,
    failedCount,
  };
}

if (typeof Deno !== "undefined" && typeof Deno.serve === "function") {
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

    try {
      // 1. Authenticate Teacher Token
      const authHeader = req.headers.get("Authorization");
      if (!authHeader || !authHeader.startsWith("Bearer ")) {
        return new Response(
          JSON.stringify({
            status: "failed",
            error: "Unauthorized: Missing Authorization header.",
          }),
          { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const supabaseUrl = Deno.env.get("SUPABASE_URL");
      const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
      const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
      const geminiApiKey = Deno.env.get("GEMINI_API_KEY");

      if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceRoleKey) {
        return new Response(
          JSON.stringify({
            status: "failed",
            error: "Server configuration error: Missing Supabase credentials.",
          }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      if (!geminiApiKey) {
        return new Response(
          JSON.stringify({
            status: "failed",
            error: "Server configuration error: Missing GEMINI_API_KEY secret.",
          }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const authClient = createClient(supabaseUrl, supabaseAnonKey, {
        global: { headers: { Authorization: authHeader } },
      });
      const {
        data: { user },
        error: authError,
      } = await authClient.auth.getUser();

      if (authError || !user) {
        return new Response(
          JSON.stringify({
            status: "failed",
            error: "Unauthorized: Invalid or expired teacher token.",
          }),
          { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const teacherId = user.id;
      const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

      // 2. Parse & Validate Request Body (Supports single paper_id or batch paper_ids)
      const body = await req.json().catch(() => null);
      const rawBatchPaperIds = body?.paper_ids ?? body?.paperIds;
      if (Array.isArray(rawBatchPaperIds)) {
        const cleanedPaperIds = rawBatchPaperIds
          .map((id: unknown) => (typeof id === "string" ? id.trim() : ""))
          .filter((id: string) => id.length > 0);
        if (cleanedPaperIds.length === 0) {
          return new Response(
            JSON.stringify({
              status: "failed",
              error: "Validation error: 'paper_ids' must contain at least one valid paper ID.",
            }),
            { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        const batchOutcome = await evaluateStudentPapersBatch({
          adminClient,
          teacherId,
          paperIds: cleanedPaperIds,
          geminiApiKey,
        });

        const overallStatus =
          batchOutcome.failedCount === 0
            ? "completed"
            : batchOutcome.succeededCount > 0
            ? "partial"
            : "failed";

        return new Response(
          JSON.stringify({
            status: overallStatus,
            succeededCount: batchOutcome.succeededCount,
            failedCount: batchOutcome.failedCount,
            results: batchOutcome.results.map((r) => ({
              paperId: r.paperId,
              httpStatus: r.httpStatus,
              ...r.body,
            })),
          }),
          {
            status: batchOutcome.succeededCount > 0 ? 200 : batchOutcome.results[0]?.httpStatus ?? 500,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          }
        );
      }

      const rawPaperId = body?.paper_id ?? body?.paperId;
      if (!body || typeof body !== "object" || !rawPaperId || typeof rawPaperId !== "string") {
        return new Response(
          JSON.stringify({
            status: "failed",
            error: "Validation error: 'paper_id' (or 'paperId') string is required.",
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const outcome = await evaluateSingleStudentPaper({
        adminClient,
        teacherId,
        paperId: rawPaperId.trim(),
        geminiApiKey,
      });

      return new Response(JSON.stringify(outcome.body), {
        status: outcome.httpStatus,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    } catch (err: unknown) {
      const errorMessage = sanitizeErrorMessage(
        err instanceof Error ? err.message : String(err)
      );
      return new Response(
        JSON.stringify({
          status: "failed",
          error: errorMessage || "An unexpected error occurred during paper evaluation.",
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }
  });
}
