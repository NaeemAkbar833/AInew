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
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) throw new Error("Missing Authorization header");

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const geminiApiKey = Deno.env.get("GEMINI_API_KEY");

    if (!geminiApiKey) throw new Error("GEMINI_API_KEY is not configured");

    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: authError } = await authClient.auth.getUser();
    if (authError || !user) throw new Error("Unauthorized");
    const teacherId = user.id;

    const body = await req.json();
    const { images, class_id: classId } = body;
    if (!images || !Array.isArray(images)) throw new Error("images must be an array of base64 strings");
    if (!classId || typeof classId !== "string") throw new Error("classId is required");

    // 4. Request Size and Count Safeguards
    if (images.length > 5) throw new Error("Maximum 5 pages allowed per import.");
    
    // Check total request size (approximate)
    const totalBytes = images.reduce((acc, img) => acc + img.length, 0) * 0.75;
    if (totalBytes > 8 * 1024 * 1024) { // 8MB limit
      throw new Error("The selected images are too large. Please try again with fewer pages or lower resolution.");
    }

    const adminClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 5. Verify Class Ownership
    const { data: classData, error: classError } = await adminClient
      .from("classes")
      .select("id, teacher_id")
      .eq("id", classId)
      .eq("teacher_id", teacherId)
      .maybeSingle();

    if (classError || !classData) {
      console.error(`Security Violation: User ${teacherId} attempted to access class ${classId}`);
      return new Response(JSON.stringify({ error: "Unauthorized: Class not found or access denied." }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 403,
      });
    }

    const students: any[] = [];
    const validImages = images.filter(img => !!img);

    if (validImages.length > 0) {
      const mimeType = "image/jpeg";
      const systemInstruction = `You are a specialized AI for extracting student lists from photos of registers or printed sheets.
Your task is to extract Roll Number, Student Name, and Father Name for each student listed in the images.

STRICT RULES:
1. Return ONLY a JSON object with a "students" key containing an array of objects.
2. Each student object must have: "roll_number", "name", "father_name", "is_unclear" (boolean), "review_reason" (string or null).
3. Do NOT invent information. If a field is missing or unreadable, leave it as an empty string and set "is_unclear": true.
4. "is_unclear" should be true if any field for that student is illegible or missing.
5. Provide a brief "review_reason" if "is_unclear" is true (e.g., "Handwriting smudged", "Roll number cut off").
6. Combine entries from all provided images into one clean list.
7. Output NO other text, just valid JSON.`;

      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${geminiApiKey}`;
      
      const imageParts = validImages.map(base64Image => ({
        inlineData: { mimeType, data: base64Image }
      }));

      const geminiPayload = {
        systemInstruction: { parts: [{ text: systemInstruction }] },
        contents: [
          {
            parts: [
              ...imageParts,
              { text: "Extract the student list from these images into JSON format:" }
            ]
          }
        ],
        generationConfig: {
          responseMimeType: "application/json",
          temperature: 0.1
        }
      };

      const resp = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(geminiPayload)
      });

      if (resp.ok) {
        const data = await resp.json();
        const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "{}";
        try {
          const jsonMatch = text.match(/```json\s*([\s\S]*?)\s*```/) || text.match(/```\s*([\s\S]*?)\s*```/);
          const cleanedText = jsonMatch ? jsonMatch[1] : text;
          const parsed = JSON.parse(cleanedText);
          if (parsed.students && Array.isArray(parsed.students)) {
            students.push(...parsed.students);
          }
        } catch (e) {
          console.error("Failed to parse Gemini JSON:", text, e);
        }
      } else {
        const errorText = await resp.text();
        console.error("Gemini API error:", errorText);
        throw new Error(`AI service failed: ${resp.status}`);
      }
    }

    return new Response(JSON.stringify({ students }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });

  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 400,
    });
  }
});
