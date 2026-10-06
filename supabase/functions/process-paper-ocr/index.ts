import { createClient } from "https://esm.sh/@supabase/supabase-js@2.48.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function toBase64(uint8Array: Uint8Array): string {
  let binary = "";
  const chunkSize = 8192;
  for (let i = 0; i < uint8Array.length; i += chunkSize) {
    const chunk = uint8Array.subarray(i, Math.min(i + chunkSize, uint8Array.length));
    binary += String.fromCharCode.apply(null, chunk as unknown as number[]);
  }
  return btoa(binary);
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

Deno.serve(async (req: Request) => {
  // 1. Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ status: "failed", error: "Method not allowed. Only POST is supported." }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  let requestPageId: string | null = null;

  try {
    // 2. Read and Validate Authentication Header
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "Unauthorized: Missing or invalid Authorization header.",
        }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "Server configuration error: Supabase environment variables are missing.",
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Verify Authenticated Teacher Token
    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: authError } = await authClient.auth.getUser();

    if (authError || !user) {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "Unauthorized: Invalid or expired session token.",
        }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const teacherId = user.id;

    // 4. Parse Request Payload
    const body = await req.json().catch(() => null);
    if (!body || typeof body !== "object") {
      return new Response(
        JSON.stringify({
          status: "failed",
          error: "Malformed request: Request body must be a valid JSON object.",
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { paperId, pageId } = body;
    requestPageId = typeof pageId === "string" ? pageId : null;

    if (!paperId || typeof paperId !== "string" || !pageId || typeof pageId !== "string") {
      return new Response(
        JSON.stringify({
          pageId: requestPageId,
          status: "failed",
          error: "Validation error: Both paperId and pageId are required string parameters.",
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Strict Teacher Ownership Verification
    const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    // Verify paper exists and belongs to this authenticated teacher
    const { data: paper, error: paperError } = await adminClient
      .from("student_papers")
      .select("id, teacher_id")
      .eq("id", paperId)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (paperError || !paper) {
      return new Response(
        JSON.stringify({
          pageId,
          status: "failed",
          error: "Paper not found or access denied.",
        }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Verify page exists, belongs to this paper, and belongs to this teacher
    const { data: page, error: pageError } = await adminClient
      .from("student_paper_pages")
      .select("id, paper_id, teacher_id, page_number, storage_path")
      .eq("id", pageId)
      .eq("paper_id", paperId)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (pageError || !page) {
      return new Response(
        JSON.stringify({
          pageId,
          status: "failed",
          error: "Page not found or does not belong to the specified paper.",
        }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. Secure Server-Side Access to Private Storage
    const { data: fileBlob, error: downloadError } = await adminClient
      .storage
      .from("student-papers")
      .download(page.storage_path);

    if (downloadError || !fileBlob) {
      console.error("Storage download error for path:", page.storage_path, downloadError);
      return new Response(
        JSON.stringify({
          pageId,
          status: "failed",
          error: "Failed to retrieve page image from storage.",
        }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const arrayBuffer = await fileBlob.arrayBuffer();
    const base64Image = toBase64(new Uint8Array(arrayBuffer));
    const mimeType = fileBlob.type || "image/jpeg";

    // 7. Check Gemini Server Secret
    const geminiApiKey = Deno.env.get("GEMINI_API_KEY");
    if (!geminiApiKey) {
      return new Response(
        JSON.stringify({
          pageId,
          status: "failed",
          error: "OCR service configuration error: GEMINI_API_KEY is not configured.",
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 8. Build Gemini Transcription-Only Request
    const systemInstruction = `You are a high-precision optical character recognition (OCR) engine specialized in transcribing handwritten student examination answer sheets.
Your task is to transcribe the handwritten English text from the provided image verbatim.

STRICT TRANSCRIPTION RULES:
1. Transcribe exactly what is written on the page without altering words, sentence order, or layout.
2. Preserve original spelling, capitalization, punctuation, and grammatical mistakes made by the student.
3. Preserve numbers, mathematical expressions, equations, and meaningful scientific symbols as accurately as possible.
4. If a word or phrase is crossed out, omit it unless legible and clearly intended as the answer.
5. If handwriting is illegible, smudged, or cut off, transcribe it as "[unclear]". Do NOT guess or invent words.
6. DO NOT answer questions or solve problems found in the text.
7. DO NOT evaluate the answers, assign marks, or provide feedback.
8. DO NOT complete incomplete sentences or expand abbreviations.
9. Output ONLY the raw transcribed text. Do NOT include markdown wrappers like \`\`\`text, conversational remarks, or metadata headers.`;

    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${geminiApiKey}`;

    const geminiPayload = {
      systemInstruction: {
        parts: [{ text: systemInstruction }],
      },
      contents: [
        {
          parts: [
            {
              inlineData: {
                mimeType: mimeType,
                data: base64Image,
              },
            },
            {
              text: "Transcribe all handwritten text from this page verbatim according to the system instructions:",
            },
          ],
        },
      ],
      generationConfig: {
        temperature: 0.0,
        topP: 1.0,
        maxOutputTokens: 4096,
      },
    };

    // 9. Execute with Exponential Backoff Retry (Max 3 attempts)
    const maxAttempts = 3;
    const retryDelays = [2000, 5000]; // 2s after attempt 1, 5s after attempt 2

    let extractedText = "";
    let lastError = "OCR processing failed.";
    let lastStatus = 502;
    let succeeded = false;

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      console.log(`[process-paper-ocr] Gemini API attempt ${attempt} of ${maxAttempts} for page ${pageId}`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 16000); // 16 second per-attempt timeout

      try {
        const geminiResponse = await fetch(geminiUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(geminiPayload),
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (geminiResponse.ok) {
          const geminiData = await geminiResponse.json();
          extractedText = geminiData.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? "";
          succeeded = true;
          console.log(`[process-paper-ocr] Gemini OCR succeeded on attempt ${attempt}`);
          break;
        }

        const statusCode = geminiResponse.status;
        lastStatus = statusCode;
        const errorText = await geminiResponse.text().catch(() => "");
        console.warn(`[process-paper-ocr] Attempt ${attempt} failed with HTTP ${statusCode}: ${errorText.slice(0, 300)}`);

        // Check if error is temporary (503 High Demand, 429 Rate Limit, or transient 5xx)
        const isTemporary = statusCode === 503 || statusCode === 429 || statusCode === 500 || statusCode === 502 || statusCode === 504;

        let parsedMessage: string | null = null;
        try {
          const parsed = JSON.parse(errorText);
          parsedMessage = parsed.error?.message || null;
        } catch {
          // ignore parsing error
        }

        if (statusCode === 503) {
          lastError = parsedMessage || "The OCR model is currently experiencing high demand. Please try again in a moment.";
        } else if (statusCode === 429) {
          lastError = parsedMessage || "OCR rate limit reached. Please wait a moment before trying again.";
        } else if (statusCode === 404) {
          lastError = `OCR model not found (HTTP 404): ${parsedMessage || "Invalid model name."}`;
        } else if (statusCode === 400) {
          lastError = `Bad Request to OCR provider: ${parsedMessage || "Invalid request payload."}`;
        } else if (statusCode === 401 || statusCode === 403) {
          lastError = "OCR provider authentication failed. Check GEMINI_API_KEY secret.";
        } else {
          lastError = parsedMessage || `OCR provider returned HTTP ${statusCode}.`;
        }

        // Permanent client errors (400, 401, 403, 404): DO NOT RETRY
        if (!isTemporary) {
          console.error(`[process-paper-ocr] Permanent error (HTTP ${statusCode}). Aborting retries.`);
          break;
        }

        // If temporary and retries remain, back off and wait
        if (attempt < maxAttempts) {
          const delay = retryDelays[attempt - 1] ?? 5000;
          console.log(`[process-paper-ocr] High demand or rate limit encountered. Retrying in ${delay}ms...`);
          await sleep(delay);
        }
      } catch (fetchErr: unknown) {
        clearTimeout(timeoutId);
        const isTimeout = fetchErr instanceof Error && fetchErr.name === "AbortError";
        console.warn(`[process-paper-ocr] Attempt ${attempt} fetch error:`, isTimeout ? "Request timed out" : fetchErr);

        lastStatus = isTimeout ? 504 : 502;
        lastError = isTimeout
          ? "OCR request timed out while communicating with Gemini."
          : "Network connectivity error while contacting OCR service.";

        if (attempt < maxAttempts) {
          const delay = retryDelays[attempt - 1] ?? 5000;
          console.log(`[process-paper-ocr] Retrying network failure in ${delay}ms...`);
          await sleep(delay);
        }
      }
    }

    // 10. Database Update and Response
    if (succeeded) {
      // Save successful transcription to database
      try {
        await adminClient
          .from("student_paper_pages")
          .update({
            ocr_text: extractedText,
            ocr_status: "completed",
            ocr_error: null,
            ocr_processed_at: new Date().toISOString(),
          })
          .eq("id", pageId);
      } catch (dbErr: unknown) {
        console.warn("[process-paper-ocr] Warning: Database update for ocr_text failed:", dbErr);
      }

      return new Response(
        JSON.stringify({
          pageId,
          status: "completed",
          ocrText: extractedText,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    } else {
      // Record failure in database
      try {
        await adminClient
          .from("student_paper_pages")
          .update({
            ocr_status: "failed",
            ocr_error: lastError,
            ocr_processed_at: new Date().toISOString(),
          })
          .eq("id", pageId);
      } catch (dbErr: unknown) {
        console.warn("[process-paper-ocr] Warning: Database update for ocr_status=failed:", dbErr);
      }

      return new Response(
        JSON.stringify({
          pageId,
          status: "failed",
          error: lastError,
        }),
        {
          status: lastStatus >= 400 && lastStatus < 600 ? lastStatus : 502,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }
  } catch (err: unknown) {
    console.error("Unhandled error in process-paper-ocr:", err);
    return new Response(
      JSON.stringify({
        pageId: requestPageId,
        status: "failed",
        error: "An unexpected error occurred while processing page OCR.",
      }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
