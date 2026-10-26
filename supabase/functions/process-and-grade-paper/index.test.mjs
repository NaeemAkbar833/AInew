import { register } from "node:module";
import assert from "node:assert/strict";
import { test } from "node:test";

// Stub out Deno remote HTTPS imports so Node can directly import and execute `./index.ts`
const loaderCode = `
export async function resolve(specifier, context, nextResolve) {
  if (specifier.startsWith("https://")) {
    return {
      shortCircuit: true,
      url: "data:text/javascript,export function serve() {} export function createClient() { return {}; }",
    };
  }
  return nextResolve(specifier, context);
}
`;
register(`data:text/javascript,${encodeURIComponent(loaderCode)}`);

const {
  TemporaryGeminiError,
  PermanentGeminiError,
  sanitizeErrorMessage,
  extractGeminiResponseText,
  extractAndParseJson,
  validateGradingResponseSchema,
  callGeminiWithRetry,
  callGeminiGradingWithRetry,
  normalizeQuestionNumber,
  compareQuestionNumbers,
  sortQuestionsNumerically,
  matchLineQuestionHeader,
  stripLeadingQuestionHeader,
  extractQuestionBlocksFromTranscript,
  verifyAndAssembleVerbatimAnswer,
  refineTranscriptScanWithDisjointAiSplits,
  resolveEqualMarksAndSelectionRules,
  calculateEffectiveExamTotalMarks,
  evaluateOptionalGroupsAndAlignResults,
  finalizeProvisionalEvaluationSummary,
  evaluateSingleStudentPaper,
  evaluateStudentPapersBatch,
} = await import("./index.ts");

function makeExamPaper(questionCount, marksPerQuestion, generalInstructions = [], printedTotalMarks = null) {
  const questions = Array.from({ length: questionCount }, (_, idx) => {
    const n = idx + 1;
    return {
      question_number: `${n}`,
      question_text: `Question ${n} prompt`,
      maximum_marks: marksPerQuestion,
      expected_answer: `Expected answer for Q${n}`,
      marking_criteria: `Award up to ${marksPerQuestion} marks for Q${n}`,
    };
  });
  const pageExtractions = [
    {
      printed_total_marks: printedTotalMarks,
      page_instructions: generalInstructions,
      questions,
    },
  ];
  return { questions, pageExtractions };
}

test("1. Combined answers that Gemini returns as one block are separated when exact OCR boundary exists", () => {
  const { questions, pageExtractions } = makeExamPaper(
    5,
    12,
    ["Attempt any 3 questions."],
    36
  );
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 36);
  const known = normalizedMeta.questions.map((q) => q.question_number);

  // Page 2 has Q2 followed by an unheadered answer for Q5 (deterministic scan initially merges both under Q2)
  const pages = [
    {
      pageNumber: 1,
      text: "Q4: First answer on page one explaining Boyle's gas law.",
    },
    {
      pageNumber: 2,
      text: [
        "Q2: Second answer on page two explaining Newton's second law of motion.",
        "Final answer on page two explaining Snell's law of refraction across media.",
      ].join("\n"),
    },
  ];

  // Subcase 1A: Gemini echoed the combined pre_segmented_student_answer (Q2 + Q5) for Q2, while returning Q5's part for Q5
  const alignedEchoedInA = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "4",
        is_attempted: true,
        student_answer: "First answer on page one explaining Boyle's gas law.",
        awarded_marks: 10,
        feedback: "Good Q4",
        review_required: false,
      },
      {
        question_number: "2",
        is_attempted: true,
        student_answer: [
          "Second answer on page two explaining Newton's second law of motion.",
          "Final answer on page two explaining Snell's law of refraction across media.",
        ].join("\n"),
        awarded_marks: 11,
        feedback: "Good Q2",
        review_required: false,
      },
      {
        question_number: "5",
        is_attempted: true,
        student_answer: "Final answer on page two explaining Snell's law of refraction across media.",
        awarded_marks: 9,
        feedback: "Good Q5",
        review_required: false,
      },
    ],
    pages
  );

  const mapA = new Map(alignedEchoedInA.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(
    mapA.get("2")?.student_answer,
    "Second answer on page two explaining Newton's second law of motion."
  );
  assert.equal(
    mapA.get("5")?.student_answer,
    "Final answer on page two explaining Snell's law of refraction across media."
  );
  assert.equal(mapA.get("2")?.review_required, true);
  assert.equal(mapA.get("5")?.review_required, true);

  // Subcase 1B: Gemini returned only Q2's part for Q2, but returned the combined block (Q2 + Q5) for Q5
  const alignedEchoedInB = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "4",
        is_attempted: true,
        student_answer: "First answer on page one explaining Boyle's gas law.",
        awarded_marks: 10,
        feedback: "Good Q4",
        review_required: false,
      },
      {
        question_number: "2",
        is_attempted: true,
        student_answer: "Second answer on page two explaining Newton's second law of motion.",
        awarded_marks: 11,
        feedback: "Good Q2",
        review_required: false,
      },
      {
        question_number: "5",
        is_attempted: true,
        student_answer: [
          "Second answer on page two explaining Newton's second law of motion.",
          "Final answer on page two explaining Snell's law of refraction across media.",
        ].join("\n"),
        awarded_marks: 9,
        feedback: "Good Q5",
        review_required: false,
      },
    ],
    pages
  );

  const mapB = new Map(alignedEchoedInB.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(
    mapB.get("2")?.student_answer,
    "Second answer on page two explaining Newton's second law of motion."
  );
  assert.equal(
    mapB.get("5")?.student_answer,
    "Final answer on page two explaining Snell's law of refraction across media."
  );
  assert.equal(mapB.get("2")?.review_required, true);
  assert.equal(mapB.get("5")?.review_required, true);
});

test("2. Correct separation when exact OCR boundaries are available across multiple formats and multi-answer blocks", () => {
  const known = ["1", "2", "3", "4", "5", "6"];

  // Case A: Explicit formatted headers and bare numbered headers on Page 2
  const pagesFormatted = [
    {
      pageNumber: 1,
      text: "Q3: First answer on page one about thermodynamics.",
    },
    {
      pageNumber: 2,
      text: [
        "**Q1:** Second answer at top of page two about kinematics.",
        "(Q4) Final answer at bottom of page two about electromagnetism.",
      ].join("\n"),
    },
  ];
  const scanFormatted = extractQuestionBlocksFromTranscript(pagesFormatted, known);
  assert.equal(
    scanFormatted.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "Second answer at top of page two about kinematics."
  );
  assert.equal(
    scanFormatted.blocksByQuestion.get("4")?.[0]?.verbatimText,
    "Final answer at bottom of page two about electromagnetism."
  );

  // Case B: Three answers on Page 2 where the last two are unheadered and split iteratively from the end backwards
  const pagesThreeMerged = [
    {
      pageNumber: 1,
      text: "Q2: Page one answer about atomic structure.",
    },
    {
      pageNumber: 2,
      text: [
        "Q6: First answer on page two about covalent bonding.",
        "Second answer on page two about ionic lattice energy.",
        "Third answer on page two about metallic delocalized electrons.",
      ].join("\n"),
    },
  ];
  const scanThree = extractQuestionBlocksFromTranscript(pagesThreeMerged, known);
  refineTranscriptScanWithDisjointAiSplits(
    scanThree,
    [
      {
        question_number: "2",
        source_segments: [{ page_number: 1, verbatim_text: "Page one answer about atomic structure." }],
      },
      {
        question_number: "6",
        source_segments: [{ page_number: 2, verbatim_text: "First answer on page two about covalent bonding." }],
      },
      {
        question_number: "1",
        source_segments: [{ page_number: 2, verbatim_text: "Second answer on page two about ionic lattice energy." }],
      },
      {
        question_number: "5",
        source_segments: [{ page_number: 2, verbatim_text: "Third answer on page two about metallic delocalized electrons." }],
      },
    ],
    known
  );

  assert.equal(
    scanThree.blocksByQuestion.get("6")?.[0]?.verbatimText,
    "First answer on page two about covalent bonding."
  );
  assert.equal(
    scanThree.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "Second answer on page two about ionic lattice energy."
  );
  assert.equal(
    scanThree.blocksByQuestion.get("5")?.[0]?.verbatimText,
    "Third answer on page two about metallic delocalized electrons."
  );
});

test("3. Uncertain boundaries trigger teacher review while preserving verbatim OCR evidence", () => {
  const { questions, pageExtractions } = makeExamPaper(
    4,
    10,
    ["Attempt any 2 questions."],
    20
  );
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 20);

  const pages = [
    {
      pageNumber: 1,
      text: "Q1: First paragraph about cellular respiration in mitochondria.\nSecond unheadered paragraph about Calvin cycle in stroma.",
    },
  ];

  // Case A: Gemini returns the exact same combined block for BOTH Q1 and Q3 with no verifiable split point
  const alignedIdenticalOverlap = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "1",
        is_attempted: true,
        student_answer:
          "First paragraph about cellular respiration in mitochondria.\nSecond unheadered paragraph about Calvin cycle in stroma.",
        awarded_marks: 8,
        feedback: "Good Q1",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer:
          "First paragraph about cellular respiration in mitochondria.\nSecond unheadered paragraph about Calvin cycle in stroma.",
        awarded_marks: 7,
        feedback: "Good Q3",
        review_required: false,
      },
    ],
    pages
  );

  const summaryA = finalizeProvisionalEvaluationSummary({
    aligned: alignedIdenticalOverlap,
    officialClassTotal: 20,
  });
  const byNumA = new Map(alignedIdenticalOverlap.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));

  // Verbatim OCR text is preserved once under Q1 without duplication into Q3, and BOTH Q1 and Q3 are flagged for review
  assert.equal(
    byNumA.get("1")?.student_answer,
    "First paragraph about cellular respiration in mitochondria.\nSecond unheadered paragraph about Calvin cycle in stroma."
  );
  assert.equal(byNumA.get("1")?.review_required, true);
  assert.equal(byNumA.get("3")?.review_required, true);
  assert.equal(summaryA.overallReviewRequired, true);
  assert.equal(summaryA.finalEvaluationStatus, "needs_review");
  assert.equal(summaryA.percentageAvailable, true);
  assert.equal(summaryA.calculatedPercentage, 40);

  // Case B: Gemini matches only the first paragraph for Q1 and leaves all other questions unattempted,
  // leaving an unmapped trailing paragraph inside Q1's deterministic OCR block
  const alignedUnmappedTail = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "1",
        is_attempted: true,
        student_answer: "First paragraph about cellular respiration in mitochondria.",
        awarded_marks: 8,
        feedback: "Good Q1",
        review_required: false,
      },
    ],
    pages
  );

  const byNumB = new Map(alignedUnmappedTail.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  // Preserves the full verbatim OCR block (never discards student writing) and flags Q1 for teacher review
  assert.equal(
    byNumB.get("1")?.student_answer,
    "First paragraph about cellular respiration in mitochondria.\nSecond unheadered paragraph about Calvin cycle in stroma."
  );
  assert.equal(byNumB.get("1")?.review_required, true);
});

test("4. Different page counts (3-page paper) and answers continuing across page boundaries including numbered lists", () => {
  const known = ["1", "2", "3", "4", "5", "6"];
  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q4: Four key properties of electromagnetic waves are:",
        "1. They are transverse waves in which electric and magnetic fields",
        "   oscillate perpendicular to the direction of wave propagation.",
        "2. They travel through vacuum at a constant speed of light",
        "   approximately equal to 3 x 10^8 meters per second.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "3. They do not require any material medium for propagation",
        "   and can travel across interstellar space.",
        "4. They carry energy and momentum which can be transferred",
        "   to matter upon incidence.",
        "Q2: Faraday's law states that induced EMF is proportional to",
      ].join("\n"),
    },
    {
      pageNumber: 3,
      text: [
        "the rate of change of magnetic flux linked with the circuit.",
        "Ans 6: Lenz's law states that the direction of induced current opposes the cause that produces it.",
      ].join("\n"),
    },
  ];

  const scan = extractQuestionBlocksFromTranscript(pages, known);
  assert.equal(scan.blocksByQuestion.get("4")?.length, 2);
  assert.equal(scan.blocksByQuestion.get("2")?.length, 2);
  assert.equal(scan.blocksByQuestion.get("6")?.length, 1);
  assert.equal(scan.blocksByQuestion.get("3")?.length ?? 0, 0);

  const verifiedQ4 = verifyAndAssembleVerbatimAnswer(
    "4",
    {
      question_number: "4",
      is_attempted: true,
      student_answer: pages[0].text + "\n" + pages[1].text.split("\n").slice(0, 4).join("\n"),
    },
    pages,
    known,
    scan
  );
  assert.equal(verifiedQ4.isAttempted, true);
  assert.equal(verifiedQ4.reviewRequired, false);
  assert.deepEqual(verifiedQ4.sourcePages, [1, 2]);

  const verifiedQ2 = verifyAndAssembleVerbatimAnswer(
    "2",
    {
      question_number: "2",
      is_attempted: true,
      student_answer:
        "Faraday's law states that induced EMF is proportional to\nthe rate of change of magnetic flux linked with the circuit.",
    },
    pages,
    known,
    scan
  );
  assert.equal(verifiedQ2.isAttempted, true);
  assert.equal(verifiedQ2.reviewRequired, false);
  assert.deepEqual(verifiedQ2.sourcePages, [2, 3]);
  assert.equal(
    verifiedQ2.studentAnswer,
    "Faraday's law states that induced EMF is proportional to\n\nthe rate of change of magnetic flux linked with the circuit."
  );
});

test("5. Random question orders with different question numbers and subquestions preserve true identity", () => {
  const known = ["1", "2(a)", "2(b)", "3", "5", "8"];
  const pages = [
    {
      pageNumber: 1,
      text: [
        "1. Q8: Answer written first for question eight.",
        "2) Ans 2(b): Answer written second for subquestion two-b.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "(3) Question 5: Answer written third for question five.",
        "Q2(a): Answer written fourth for subquestion two-a.",
      ].join("\n"),
    },
  ];

  const scan = extractQuestionBlocksFromTranscript(pages, known);
  assert.equal(scan.blocksByQuestion.get("8")?.[0]?.verbatimText, "Answer written first for question eight.");
  assert.equal(scan.blocksByQuestion.get("2(B)")?.[0]?.verbatimText, "Answer written second for subquestion two-b.");
  assert.equal(scan.blocksByQuestion.get("5")?.[0]?.verbatimText, "Answer written third for question five.");
  assert.equal(scan.blocksByQuestion.get("2(A)")?.[0]?.verbatimText, "Answer written fourth for subquestion two-a.");
  assert.equal(scan.blocksByQuestion.get("1")?.length ?? 0, 0);
  assert.equal(scan.blocksByQuestion.get("3")?.length ?? 0, 0);
});

test("6. Optional-question rules with different question counts and marks grade and calculate percentage accurately", () => {
  // 8 questions, 15 marks each, "Attempt any 4 questions" -> effective max = 60
  const { questions, pageExtractions } = makeExamPaper(
    8,
    15,
    ["Attempt any 4 out of 8 questions. Each question carries 15 marks."],
    60
  );
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 60);
  const effectiveCalc = calculateEffectiveExamTotalMarks(normalizedMeta.questions);
  assert.equal(effectiveCalc.effectiveTotalMarks, 60);
  assert.equal(effectiveCalc.allMarksPresent, true);

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q7: Student answer for question seven.",
        "Q2: Student answer for question two.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "Q8: Student answer for question eight.",
        "Q4: Student answer for question four.",
      ].join("\n"),
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "7", is_attempted: true, student_answer: "Student answer for question seven.", awarded_marks: 14, feedback: "Good" },
      { question_number: "2", is_attempted: true, student_answer: "Student answer for question two.", awarded_marks: 12, feedback: "Good" },
      { question_number: "8", is_attempted: true, student_answer: "Student answer for question eight.", awarded_marks: 15, feedback: "Great" },
      { question_number: "4", is_attempted: true, student_answer: "Student answer for question four.", awarded_marks: 13, feedback: "Good" },
    ],
    pages
  );

  const summary = finalizeProvisionalEvaluationSummary({
    aligned,
    officialClassTotal: 60,
  });

  assert.equal(summary.finalEvaluationStatus, "completed");
  assert.equal(summary.finalTotalMarksObtained, 54);
  assert.equal(summary.finalTotalMarks, 60);
  assert.equal(summary.finalExtractedMarks, 60);
  assert.equal(summary.marksMatchClassTotal, true);
  assert.equal(summary.percentageAvailable, true);
  assert.equal(summary.calculatedPercentage, 90);
  assert.equal(summary.overallReviewRequired, false);
});

test("7. Fewer attempts than required preserves actual marks, keeps max at permitted total, and requires teacher review", () => {
  const { questions, pageExtractions } = makeExamPaper(6, 10, ["Attempt any 3 questions."], 30);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q3: Student answer for question three.",
        "Q1: Student answer for question one.",
      ].join("\n"),
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "3", awarded_marks: 9, feedback: "Good Q3", review_required: false },
      { question_number: "1", awarded_marks: 8, feedback: "Good Q1", review_required: false },
    ],
    pages
  );

  const summary = finalizeProvisionalEvaluationSummary({
    aligned,
    officialClassTotal: 30,
  });

  assert.equal(aligned.totalMarksObtained, 17);
  assert.equal(aligned.computedEffectiveTotalMarks, 30);
  assert.equal(summary.finalTotalMarksObtained, 17);
  assert.equal(summary.finalTotalMarks, 30);
  assert.equal(summary.finalEvaluationStatus, "needs_review");
  assert.equal(summary.overallReviewRequired, true);
  assert.equal(summary.percentageAvailable, true);
  assert.equal(summary.calculatedPercentage, 56.67);

  const byNum = new Map(aligned.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(byNum.get("3")?.awarded_marks, 9);
  assert.equal(byNum.get("1")?.awarded_marks, 8);
});

test("8. More attempts than permitted preserves all attempted answers and individual marks without exceeding maximum total or percentage", () => {
  // Format A: 6 questions worth 10 marks each, attempt any 3 (max 30), student attempts 4 (9 + 6 + 10 + 8 = 33 raw)
  const { questions, pageExtractions } = makeExamPaper(6, 10, ["Attempt any 3 questions."], 30);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q3: Student answer for question three.",
        "Q1: Student answer for question one.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "Q4: Student answer for question four.",
        "Q6: Student answer for question six.",
      ].join("\n"),
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "3", awarded_marks: 9, feedback: "Good Q3", review_required: false },
      { question_number: "1", awarded_marks: 6, feedback: "Okay Q1", review_required: false },
      { question_number: "4", awarded_marks: 10, feedback: "Great Q4", review_required: false },
      { question_number: "6", awarded_marks: 8, feedback: "Good Q6", review_required: false },
    ],
    pages
  );

  const summary = finalizeProvisionalEvaluationSummary({
    aligned,
    officialClassTotal: 30,
  });

  // Provisional total counts only the highest 3 of 4 attempted marks (10 + 9 + 8 = 27/30, 90%)
  assert.equal(aligned.computedEffectiveTotalMarks, 30);
  assert.equal(aligned.totalMarksObtained, 27);
  assert.equal(summary.finalTotalMarksObtained, 27);
  assert.equal(summary.finalTotalMarks, 30);
  assert.equal(summary.finalEvaluationStatus, "needs_review");
  assert.equal(summary.overallReviewRequired, true);
  assert.equal(summary.percentageAvailable, true);
  assert.equal(summary.calculatedPercentage, 90);
  assert.match(summary.evaluationReviewReason ?? "", /attempted 4 questions/i);

  // All 4 individual answers and their provisional marks (9, 6, 10, 8) are preserved for teacher review
  const byNum = new Map(aligned.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(byNum.get("3")?.student_answer, "Student answer for question three.");
  assert.equal(byNum.get("3")?.awarded_marks, 9);
  assert.equal(byNum.get("3")?.review_required, true);

  assert.equal(byNum.get("1")?.student_answer, "Student answer for question one.");
  assert.equal(byNum.get("1")?.awarded_marks, 6);
  assert.equal(byNum.get("1")?.review_required, true);

  assert.equal(byNum.get("4")?.student_answer, "Student answer for question four.");
  assert.equal(byNum.get("4")?.awarded_marks, 10);
  assert.equal(byNum.get("4")?.review_required, true);

  assert.equal(byNum.get("6")?.student_answer, "Student answer for question six.");
  assert.equal(byNum.get("6")?.awarded_marks, 8);
  assert.equal(byNum.get("6")?.review_required, true);

  // Format B: 5 questions worth 15 marks each, attempt any 2 (max 30), student attempts 3 (14, 13, 12 -> highest 2 = 27)
  const { questions: q5, pageExtractions: pe5 } = makeExamPaper(5, 15, ["Attempt any 2 questions."], 30);
  const norm5 = resolveEqualMarksAndSelectionRules(q5, pe5, 30);
  const aligned5 = evaluateOptionalGroupsAndAlignResults(
    norm5.questions,
    [
      { question_number: "2", awarded_marks: 14, feedback: "Good" },
      { question_number: "4", awarded_marks: 13, feedback: "Good" },
      { question_number: "5", awarded_marks: 12, feedback: "Good" },
    ],
    [{ pageNumber: 1, text: "Q2: Ans two.\nQ4: Ans four.\nQ5: Ans five." }]
  );
  const summary5 = finalizeProvisionalEvaluationSummary({ aligned: aligned5, officialClassTotal: 30 });
  assert.equal(aligned5.totalMarksObtained, 27);
  assert.equal(summary5.finalTotalMarksObtained, 27);
  assert.equal(summary5.calculatedPercentage, 90);
  assert.equal(summary5.finalEvaluationStatus, "needs_review");
});

test("9. Compulsory questions where class total does not match paper marks are NOT guessed as optional and require teacher review", () => {
  const { questions, pageExtractions } = makeExamPaper(6, 10, ["Answer all questions."], null);
  const officialClassTotal = 30;
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, officialClassTotal);

  for (const q of normalizedMeta.questions) {
    assert.equal(q.selection_group ?? null, null);
    assert.equal(q.group_required_attempts ?? null, null);
  }

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q1: Answer one.",
        "Q2: Answer two.",
        "Q3: Answer three.",
      ].join("\n"),
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "1", awarded_marks: 8, feedback: "Good", review_required: false },
      { question_number: "2", awarded_marks: 7, feedback: "Good", review_required: false },
      { question_number: "3", awarded_marks: 9, feedback: "Good", review_required: false },
    ],
    pages
  );

  const summary = finalizeProvisionalEvaluationSummary({
    aligned,
    officialClassTotal,
  });

  assert.equal(aligned.computedEffectiveTotalMarks, 60);
  assert.equal(summary.finalExtractedMarks, 60);
  assert.equal(summary.marksMatchClassTotal, false);
  assert.equal(summary.percentageAvailable, false);
  assert.equal(summary.calculatedPercentage, null);
  assert.equal(summary.overallReviewRequired, true);
  assert.equal(summary.finalEvaluationStatus, "needs_review");
  assert.match(summary.evaluationReviewReason ?? "", /do not reliably match/i);
});

test("10. Verbatim OCR preservation and question identity through alignment and database payload construction", () => {
  const { questions, pageExtractions } = makeExamPaper(6, 10, ["Attempt any 3 questions."], 30);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);
  const known = normalizedMeta.questions.map((q) => q.question_number);

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q3: The mitochondria is the powerhouse of the eukaryotic cell and generates ATP",
        "through oxidative phosphorylation across the inner mitochondrial membrane.",
        "Q1: Verbatim student response for Q1.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: "Q4: Verbatim student response for Q4.",
    },
  ];

  const scan = extractQuestionBlocksFromTranscript(pages, known);

  // Simulate AI attempting to paraphrase Q3 while grading Q3, Q1, Q4
  const rawEvaluations = [
    {
      question_number: "3",
      student_answer:
        "Q3: The mitochondrion is the powerhouse of the eukaryotic cell and generates ATP energy.",
      awarded_marks: 9,
      feedback: "Feedback Q3",
      review_required: false,
    },
    {
      question_number: "1",
      student_answer: "Verbatim student response for Q1.",
      awarded_marks: 7,
      feedback: "Feedback Q1",
      review_required: false,
    },
    {
      question_number: "4",
      student_answer: "Verbatim student response for Q4.",
      awarded_marks: 10,
      feedback: "Feedback Q4",
      review_required: false,
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    rawEvaluations,
    pages,
    scan
  );

  const summary = finalizeProvisionalEvaluationSummary({
    aligned,
    officialClassTotal: 30,
  });

  const savedByQuestion = new Map(
    aligned.evaluationQuestionsToInsert.map((row) => [row.question_number, row])
  );

  // Verbatim OCR text is preserved without AI hallucinated words ("mitochondrion" / "ATP energy") or "Q3:" prefix
  assert.equal(
    savedByQuestion.get("3")?.student_answer,
    "The mitochondria is the powerhouse of the eukaryotic cell and generates ATP\nthrough oxidative phosphorylation across the inner mitochondrial membrane."
  );
  assert.equal(savedByQuestion.get("3")?.student_answer?.includes("mitochondrion"), false);
  assert.equal(savedByQuestion.get("3")?.awarded_marks, 9);

  assert.equal(savedByQuestion.get("1")?.student_answer, "Verbatim student response for Q1.");
  assert.equal(savedByQuestion.get("1")?.awarded_marks, 7);

  assert.equal(savedByQuestion.get("4")?.student_answer, "Verbatim student response for Q4.");
  assert.equal(savedByQuestion.get("4")?.awarded_marks, 10);

  assert.equal(savedByQuestion.get("2")?.student_answer, "[Optional question — not attempted]");
  assert.equal(savedByQuestion.get("2")?.awarded_marks, 0);

  assert.equal(summary.finalTotalMarksObtained, 26);
  assert.equal(summary.finalTotalMarks, 30);
  assert.equal(summary.percentageAvailable, true);
  assert.equal(summary.calculatedPercentage, 86.67);
  assert.equal(summary.finalEvaluationStatus, "completed");
});

test("11. Copied question prompts followed by Ans: and answers starting with word/digit/Roman tokens after Ans: preserve exact student answers", () => {
  const known = ["1", "2", "3", "4", "5", "6"];
  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q3: What is Ohm's law?",
        "Ans: V = IR is the mathematical equation for Ohm's law.",
        "Q1: Define primary memory.",
        "Ans: 2 types of primary memory are RAM and ROM.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "Q4",
        "Ans: One of the main advantages of optical fiber is high bandwidth.",
      ].join("\n"),
    },
  ];

  const scan = extractQuestionBlocksFromTranscript(pages, known);
  assert.equal(scan.hasAmbiguousHeaders, false);
  assert.equal(scan.ambiguousUnassignedBlocks.length, 0);
  assert.equal(
    scan.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "V = IR is the mathematical equation for Ohm's law."
  );
  assert.equal(
    scan.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "2 types of primary memory are RAM and ROM."
  );
  assert.equal(
    scan.blocksByQuestion.get("4")?.[0]?.verbatimText,
    "One of the main advantages of optical fiber is high bandwidth."
  );
});

test("12. Verbatim student answers: AI-invented words are rejected, only verbatim OCR substrings are accepted, and unclear or missing answers trigger review", () => {
  const { questions, pageExtractions } = makeExamPaper(4, 10, ["Answer all questions."], 40);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 40);
  const known = normalizedMeta.questions.map((q) => q.question_number);

  const pages = [
    {
      pageNumber: 1,
      text: [
        "Q1: Osmosis is the movement of water molecules across a semi-permeable membrane.",
        "Q2: The force between two point charges is [unclear] proportional to product of charges.",
        "Ans: Unnumbered stray sentence about metabolic pathways in cytoplasm.",
      ].join("\n"),
    },
  ];

  // Subcase A: AI rewrites Q1 with extra words absent from OCR ("from high water potential to low water potential")
  // Deterministic OCR block for Q1 must replace the AI rewrite so no invented words survive.
  const scan = extractQuestionBlocksFromTranscript(pages, known);
  const verifiedQ1 = verifyAndAssembleVerbatimAnswer(
    "1",
    {
      question_number: "1",
      is_attempted: true,
      student_answer:
        "Osmosis is the movement of water molecules across a semi-permeable membrane from high water potential to low water potential.",
      awarded_marks: 9,
      feedback: "Good",
    },
    pages,
    known,
    scan
  );
  assert.equal(
    verifiedQ1.studentAnswer,
    "Osmosis is the movement of water molecules across a semi-permeable membrane."
  );
  assert.equal(verifiedQ1.studentAnswer?.includes("water potential"), false);
  assert.equal(pages[0].text.includes(verifiedQ1.studentAnswer), true);

  // Subcase B: Question 3 has NO deterministic block; AI returns one sentence that exists verbatim in OCR
  // plus a second AI-invented sentence absent from the OCR transcript.
  const verifiedQ3PartialFabrication = verifyAndAssembleVerbatimAnswer(
    "3",
    {
      question_number: "3",
      is_attempted: true,
      student_answer:
        "Unnumbered stray sentence about metabolic pathways in cytoplasm. This process generates NADH and ATP in glycolysis.",
      awarded_marks: 6,
      feedback: "Partial",
    },
    pages,
    known,
    scan
  );
  assert.equal(
    verifiedQ3PartialFabrication.studentAnswer,
    "Unnumbered stray sentence about metabolic pathways in cytoplasm."
  );
  assert.equal(verifiedQ3PartialFabrication.studentAnswer?.includes("glycolysis"), false);
  assert.equal(pages[0].text.includes(verifiedQ3PartialFabrication.studentAnswer), true);
  assert.equal(verifiedQ3PartialFabrication.reviewRequired, true);

  // Subcase C: Question 4 has NO deterministic block and AI completely invents an answer absent from OCR.
  const verifiedQ4PureFabrication = verifyAndAssembleVerbatimAnswer(
    "4",
    {
      question_number: "4",
      is_attempted: true,
      student_answer: "Completely fabricated sentence that the student never wrote on any page.",
      awarded_marks: 8,
      feedback: "Invented",
    },
    pages,
    known,
    scan
  );
  // None of the AI's invented words may appear in studentAnswer, and reviewRequired must be true
  assert.equal(
    verifiedQ4PureFabrication.studentAnswer?.includes("Completely fabricated sentence") ?? false,
    false
  );
  assert.equal(verifiedQ4PureFabrication.reviewRequired, true);

  // Subcase D: Full alignment check — Q2 has "[unclear]" in OCR and Q4 is missing/unattempted
  const aligned = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "1",
        is_attempted: true,
        student_answer:
          "Osmosis is the movement of water molecules across a semi-permeable membrane from high water potential to low water potential.",
        awarded_marks: 9,
        feedback: "Good Q1",
        review_required: false,
      },
      {
        question_number: "2",
        is_attempted: true,
        student_answer: "The force between two point charges is [unclear] proportional to product of charges.",
        awarded_marks: 7,
        feedback: "Unclear word in Q2",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer:
          "Unnumbered stray sentence about metabolic pathways in cytoplasm. Invented extra sentence by AI.",
        awarded_marks: 5,
        feedback: "Q3",
        review_required: false,
      },
      // Q4 is missing from AI evaluations (unattempted compulsory question)
    ],
    pages
  );

  const byNum = new Map(aligned.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));

  // Q1 has only verbatim OCR text
  assert.equal(
    byNum.get("1")?.student_answer,
    "Osmosis is the movement of water molecules across a semi-permeable membrane."
  );

  // Q2 preserves "[unclear]" from OCR and triggers review_required
  assert.equal(
    byNum.get("2")?.student_answer,
    "The force between two point charges is [unclear] proportional to product of charges."
  );
  assert.equal(byNum.get("2")?.review_required, true);
  assert.match(byNum.get("2")?.review_reason ?? "", /\[unclear\]/i);

  // Q3 strips AI-invented sentence, keeps only verbatim OCR substring, and triggers review_required
  assert.equal(
    byNum.get("3")?.student_answer,
    "Unnumbered stray sentence about metabolic pathways in cytoplasm."
  );
  assert.equal(byNum.get("3")?.review_required, true);

  // Q4 (missing compulsory answer) has "[No answer detected]", 0 marks, and review_required = true
  assert.equal(byNum.get("4")?.student_answer, "[No answer detected]");
  assert.equal(byNum.get("4")?.awarded_marks, 0);
  assert.equal(byNum.get("4")?.review_required, true);
});

test("13. Copied question prompts followed by Ans: vs. genuine answers beginning with What, Define, or Explain", () => {
  const known = ["1", "2", "3", "4", "5", "6"];

  // Part A: Copied question prompts followed by Ans: / Answer: / Sol:
  const copiedPromptPages = [
    {
      pageNumber: 1,
      text: [
        "Q1: What is photosynthesis?",
        "Ans: Photosynthesis is the synthesis of glucose from carbon dioxide and water using sunlight.",
        "Q2: Define Boyle's law of gases.",
        "Answer: For a fixed mass of ideal gas at constant temperature, pressure is inversely proportional to volume.",
        "Q3: Explain why metals are good conductors of electricity.",
        "Sol: Metals contain free delocalized electrons that move rapidly under an applied electric field.",
      ].join("\n"),
    },
  ];

  const scanCopied = extractQuestionBlocksFromTranscript(copiedPromptPages, known);
  assert.equal(scanCopied.ambiguousUnassignedBlocks.length, 0);
  assert.equal(
    scanCopied.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "Photosynthesis is the synthesis of glucose from carbon dioxide and water using sunlight."
  );
  assert.equal(
    scanCopied.blocksByQuestion.get("2")?.[0]?.verbatimText,
    "For a fixed mass of ideal gas at constant temperature, pressure is inversely proportional to volume."
  );
  assert.equal(
    scanCopied.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "Metals contain free delocalized electrons that move rapidly under an applied electric field."
  );

  // Part B: Genuine student answers beginning with "What", "Define", or "Explain" (both inline after Q-header and after Ans:)
  const genuineAnswerPages = [
    {
      pageNumber: 1,
      text: [
        "Q1: What remains constant in an isolated system is total mechanical energy and momentum.",
        "Q2: Define the system boundary first before applying the conservation of energy equation.",
        "Q3: Explain the phenomenon by resolving the velocity vector into horizontal and vertical components.",
        "Q4",
        "Ans: What determines the pitch of a sound wave is the frequency of vibration of the source.",
        "Q5",
        "Ans: Define a base case and a recursive relation to prove termination of the algorithm.",
        "Q6",
        "Ans: Explain each force acting on the free body before writing the equilibrium equations.",
      ].join("\n"),
    },
  ];

  const scanGenuine = extractQuestionBlocksFromTranscript(genuineAnswerPages, known);
  assert.equal(scanGenuine.ambiguousUnassignedBlocks.length, 0);
  assert.equal(
    scanGenuine.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "What remains constant in an isolated system is total mechanical energy and momentum."
  );
  assert.equal(
    scanGenuine.blocksByQuestion.get("2")?.[0]?.verbatimText,
    "Define the system boundary first before applying the conservation of energy equation."
  );
  assert.equal(
    scanGenuine.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "Explain the phenomenon by resolving the velocity vector into horizontal and vertical components."
  );
  assert.equal(
    scanGenuine.blocksByQuestion.get("4")?.[0]?.verbatimText,
    "What determines the pitch of a sound wave is the frequency of vibration of the source."
  );
  assert.equal(
    scanGenuine.blocksByQuestion.get("5")?.[0]?.verbatimText,
    "Define a base case and a recursive relation to prove termination of the algorithm."
  );
  assert.equal(
    scanGenuine.blocksByQuestion.get("6")?.[0]?.verbatimText,
    "Explain each force acting on the free body before writing the equilibrium equations."
  );
});

test("14. Multiple pages: cross-page continuations, two answers on different pages where the second has no header, and uncertain boundaries triggering review", () => {
  const { questions, pageExtractions } = makeExamPaper(5, 10, ["Attempt any 3 questions."], 30);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);
  const known = normalizedMeta.questions.map((q) => q.question_number);

  // Subcase A: Answer for Q2 continues from Page 1 onto Page 2, followed by Q4 on Page 2
  const pagesContinuation = [
    {
      pageNumber: 1,
      text: "Q2: First part of answer for question two started at the bottom of page one",
    },
    {
      pageNumber: 2,
      text: [
        "and continued seamlessly at the top of page two with full derivation.",
        "Q4: Complete answer for question four on page two.",
        "Q5: Complete answer for question five on page two.",
      ].join("\n"),
    },
  ];

  const alignedCont = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "2",
        is_attempted: true,
        student_answer:
          "First part of answer for question two started at the bottom of page one\nand continued seamlessly at the top of page two with full derivation.",
        awarded_marks: 9,
        feedback: "Good continuation",
        review_required: false,
      },
      {
        question_number: "4",
        is_attempted: true,
        student_answer: "Complete answer for question four on page two.",
        awarded_marks: 8,
        feedback: "Good Q4",
        review_required: false,
      },
      {
        question_number: "5",
        is_attempted: true,
        student_answer: "Complete answer for question five on page two.",
        awarded_marks: 10,
        feedback: "Good Q5",
        review_required: false,
      },
    ],
    pagesContinuation
  );
  const mapCont = new Map(alignedCont.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(
    mapCont.get("2")?.student_answer,
    "First part of answer for question two started at the bottom of page one\n\nand continued seamlessly at the top of page two with full derivation."
  );
  assert.deepEqual(mapCont.get("2")?.source_pages, [1, 2]);
  assert.equal(mapCont.get("2")?.review_required, false);

  // Subcase B: Two answers on different pages (Page 1 has Q1, Page 2 has Q3's answer WITHOUT any question header)
  // When AI identifies Page 1 as Q1 and Page 2 as Q3, refineTranscriptScanWithDisjointAiSplits separates them
  // and flags both for teacher review because the boundary lacked an explicit header.
  const pagesDifferentNoHeader = [
    {
      pageNumber: 1,
      text: [
        "Q2: Answer for question two on page one.",
        "Q1: Answer for question one at the end of page one.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: "Unheadered answer on page two that actually answers question three about Kirchhoff's voltage law.",
    },
  ];

  const alignedDiffPagesSplit = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "2",
        is_attempted: true,
        student_answer: "Answer for question two on page one.",
        awarded_marks: 8,
        feedback: "Good Q2",
        review_required: false,
      },
      {
        question_number: "1",
        is_attempted: true,
        student_answer: "Answer for question one at the end of page one.",
        awarded_marks: 9,
        feedback: "Good Q1",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer:
          "Unheadered answer on page two that actually answers question three about Kirchhoff's voltage law.",
        awarded_marks: 7,
        feedback: "Good Q3",
        review_required: false,
      },
    ],
    pagesDifferentNoHeader
  );

  const mapDiffSplit = new Map(
    alignedDiffPagesSplit.evaluationQuestionsToInsert.map((q) => [q.question_number, q])
  );
  assert.equal(mapDiffSplit.get("1")?.student_answer, "Answer for question one at the end of page one.");
  assert.deepEqual(mapDiffSplit.get("1")?.source_pages, [1]);
  assert.equal(
    mapDiffSplit.get("3")?.student_answer,
    "Unheadered answer on page two that actually answers question three about Kirchhoff's voltage law."
  );
  assert.deepEqual(mapDiffSplit.get("3")?.source_pages, [2]);
  assert.equal(mapDiffSplit.get("1")?.review_required, true);
  assert.equal(mapDiffSplit.get("3")?.review_required, true);

  // Subcase C: Uncertain cross-page boundary — AI claims Q3's answer is the combined Page 1 + Page 2 text
  // AND also claims Q1's answer is the combined Page 1 + Page 2 text (no disjoint boundary can be verified).
  // System must NOT silently assign the combined block to Q3; it must flag both Q1 and Q3 for review.
  const combinedP1P2 =
    "Answer for question one at the end of page one.\nUnheadered answer on page two that actually answers question three about Kirchhoff's voltage law.";
  const alignedUncertainCrossPage = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "2",
        is_attempted: true,
        student_answer: "Answer for question two on page one.",
        awarded_marks: 8,
        feedback: "Good Q2",
        review_required: false,
      },
      {
        question_number: "1",
        is_attempted: true,
        student_answer: combinedP1P2,
        awarded_marks: 9,
        feedback: "Q1",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer: combinedP1P2,
        awarded_marks: 7,
        feedback: "Q3",
        review_required: false,
      },
    ],
    pagesDifferentNoHeader
  );
  const summaryUncertain = finalizeProvisionalEvaluationSummary({
    aligned: alignedUncertainCrossPage,
    officialClassTotal: 30,
  });
  const mapUncertain = new Map(
    alignedUncertainCrossPage.evaluationQuestionsToInsert.map((q) => [q.question_number, q])
  );
  // Q3 must NOT silently receive Q1's text; Q3 is treated as unverified/unattempted and flagged for review
  assert.equal(mapUncertain.get("3")?.student_answer, "[No answer detected]");
  assert.equal(mapUncertain.get("3")?.awarded_marks, 0);
  assert.equal(mapUncertain.get("3")?.review_required, true);
  assert.equal(summaryUncertain.overallReviewRequired, true);
  assert.equal(summaryUncertain.finalEvaluationStatus, "needs_review");
});

test("15. Optional questions: 'Attempt any 3 out of 6' (10 marks each) with exactly 3 attempts and with 5 attempts", () => {
  const { questions, pageExtractions } = makeExamPaper(
    6,
    10,
    ["Attempt any 3 out of 6 questions. All questions carry equal marks."],
    30
  );
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);
  const effectiveCalc = calculateEffectiveExamTotalMarks(normalizedMeta.questions);
  assert.equal(effectiveCalc.effectiveTotalMarks, 30);

  // Case A: Exactly 3 attempted questions (Q6, Q2, Q5)
  const pagesExact3 = [
    {
      pageNumber: 1,
      text: [
        "Q6: Student answer for question six.",
        "Q2: Student answer for question two.",
        "Q5: Student answer for question five.",
      ].join("\n"),
    },
  ];
  const alignedExact3 = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "6", is_attempted: true, student_answer: "Student answer for question six.", awarded_marks: 8, feedback: "Good Q6", review_required: false },
      { question_number: "2", is_attempted: true, student_answer: "Student answer for question two.", awarded_marks: 9, feedback: "Good Q2", review_required: false },
      { question_number: "5", is_attempted: true, student_answer: "Student answer for question five.", awarded_marks: 7, feedback: "Good Q5", review_required: false },
    ],
    pagesExact3
  );
  const summaryExact3 = finalizeProvisionalEvaluationSummary({
    aligned: alignedExact3,
    officialClassTotal: 30,
  });

  assert.equal(summaryExact3.finalTotalMarksObtained, 24);
  assert.equal(summaryExact3.finalTotalMarks, 30);
  assert.equal(summaryExact3.calculatedPercentage, 80);
  assert.equal(summaryExact3.overallReviewRequired, false);
  assert.equal(summaryExact3.finalEvaluationStatus, "completed");

  const mapExact3 = new Map(alignedExact3.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(mapExact3.get("6")?.awarded_marks, 8);
  assert.equal(mapExact3.get("2")?.awarded_marks, 9);
  assert.equal(mapExact3.get("5")?.awarded_marks, 7);
  for (const unattemptedQ of ["1", "3", "4"]) {
    assert.equal(mapExact3.get(unattemptedQ)?.student_answer, "[Optional question — not attempted]");
    assert.equal(mapExact3.get(unattemptedQ)?.awarded_marks, 0);
    assert.equal(mapExact3.get(unattemptedQ)?.review_required, false);
  }

  // Case B: Student attempts 5 out of 6 questions (Q1, Q2, Q4, Q5, Q6 with marks 9 + 8 + 7 + 10 + 6 = 40 raw)
  const pagesOver5 = [
    {
      pageNumber: 1,
      text: [
        "Q1: Student answer for Q1.",
        "Q2: Student answer for Q2.",
        "Q4: Student answer for Q4.",
      ].join("\n"),
    },
    {
      pageNumber: 2,
      text: [
        "Q5: Student answer for Q5.",
        "Q6: Student answer for Q6.",
      ].join("\n"),
    },
  ];
  const alignedOver5 = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "1", is_attempted: true, student_answer: "Student answer for Q1.", awarded_marks: 9, feedback: "Good Q1", review_required: false },
      { question_number: "2", is_attempted: true, student_answer: "Student answer for Q2.", awarded_marks: 8, feedback: "Good Q2", review_required: false },
      { question_number: "4", is_attempted: true, student_answer: "Student answer for Q4.", awarded_marks: 7, feedback: "Good Q4", review_required: false },
      { question_number: "5", is_attempted: true, student_answer: "Student answer for Q5.", awarded_marks: 10, feedback: "Great Q5", review_required: false },
      { question_number: "6", is_attempted: true, student_answer: "Student answer for Q6.", awarded_marks: 6, feedback: "Fair Q6", review_required: false },
    ],
    pagesOver5
  );
  const summaryOver5 = finalizeProvisionalEvaluationSummary({
    aligned: alignedOver5,
    officialClassTotal: 30,
  });

  // Provisional total counts only the highest 3 of 5 attempted marks (10 + 9 + 8 = 27/30), status is needs_review
  assert.equal(alignedOver5.totalMarksObtained, 27);
  assert.equal(summaryOver5.finalTotalMarksObtained, 27);
  assert.equal(summaryOver5.finalTotalMarks, 30);
  assert.equal(summaryOver5.calculatedPercentage, 90);
  assert.equal(summaryOver5.overallReviewRequired, true);
  assert.equal(summaryOver5.finalEvaluationStatus, "needs_review");

  // All 5 attempted answers and their individual marks remain available for teacher review; none is discarded
  const mapOver5 = new Map(alignedOver5.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  const expectedIndividual = [
    ["1", "Student answer for Q1.", 9],
    ["2", "Student answer for Q2.", 8],
    ["4", "Student answer for Q4.", 7],
    ["5", "Student answer for Q5.", 10],
    ["6", "Student answer for Q6.", 6],
  ];
  for (const [qNum, ansText, marks] of expectedIndividual) {
    assert.equal(mapOver5.get(qNum)?.student_answer, ansText);
    assert.equal(mapOver5.get(qNum)?.awarded_marks, marks);
    assert.equal(mapOver5.get(qNum)?.review_required, true);
  }
  assert.equal(mapOver5.get("3")?.student_answer, "[Optional question — not attempted]");
  assert.equal(mapOver5.get("3")?.awarded_marks, 0);
});

test("16. Repeatability: running the same deterministic input twice produces identical parsing, alignment, and summary outputs", () => {
  const runPipelineOnce = () => {
    const { questions, pageExtractions } = makeExamPaper(
      6,
      10,
      ["Attempt any 3 out of 6 questions."],
      30
    );
    const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);
    const known = normalizedMeta.questions.map((q) => q.question_number);

    const pages = [
      {
        pageNumber: 1,
        text: [
          "Q4: What is the first law of thermodynamics?",
          "Ans: Energy can neither be created nor destroyed, only transformed from one form",
        ].join("\n"),
      },
      {
        pageNumber: 2,
        text: [
          "into another inside an isolated system.",
          "Q1: Define electric flux.",
          "Ans: 2 factors determine electric flux: electric field strength and surface area.",
          "Unheadered final paragraph on page two answering question six about Gauss's law.",
        ].join("\n"),
      },
    ];

    const rawEvaluations = [
      {
        question_number: "4",
        is_attempted: true,
        student_answer:
          "Energy can neither be created nor destroyed, only transformed from one form\ninto another inside an isolated system.",
        awarded_marks: 9,
        feedback: "Good Q4",
        review_required: false,
      },
      {
        question_number: "1",
        is_attempted: true,
        student_answer: "2 factors determine electric flux: electric field strength and surface area.",
        awarded_marks: 8,
        feedback: "Good Q1",
        review_required: false,
      },
      {
        question_number: "6",
        is_attempted: true,
        student_answer:
          "Unheadered final paragraph on page two answering question six about Gauss's law.",
        awarded_marks: 10,
        feedback: "Good Q6",
        review_required: false,
      },
    ];

    const scan = extractQuestionBlocksFromTranscript(pages, known);
    refineTranscriptScanWithDisjointAiSplits(scan, rawEvaluations, known);
    const aligned = evaluateOptionalGroupsAndAlignResults(
      normalizedMeta.questions,
      rawEvaluations,
      pages,
      scan
    );
    const summary = finalizeProvisionalEvaluationSummary({
      aligned,
      officialClassTotal: 30,
    });

    return {
      normalizedMeta,
      blocksByQuestionEntries: Array.from(scan.blocksByQuestion.entries()),
      ambiguousUnassignedBlocks: scan.ambiguousUnassignedBlocks,
      ambiguousQuestions: Array.from(scan.ambiguousQuestions),
      hasAmbiguousHeaders: scan.hasAmbiguousHeaders,
      aligned,
      summary,
    };
  };

  const run1 = runPipelineOnce();
  const run2 = runPipelineOnce();

  assert.deepEqual(run1, run2);
});

test("17. Genuine short answers starting with What, Define, or Explain followed by unnumbered Ans:/Answer:/Sol: are preserved and flagged for review, while confident copied prompts are stripped", () => {
  const { questions, pageExtractions } = makeExamPaper(6, 10, ["Attempt any 3 questions."], 30);
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);
  const known = normalizedMeta.questions.map((q) => q.question_number);

  // Part A: Genuine short student answers starting with "What", "Define", and "Explain"
  // where the immediately following line starts with an unnumbered "Ans:", "Answer:", or "Sol:" separator.
  const pagesWithFollowingUnnumberedAns = [
    {
      pageNumber: 1,
      text: [
        "Q1: What remains constant in an isolated system is total mechanical energy.",
        "Ans: Linear momentum is also conserved when net external force is zero.",
        "Q2: Define the system boundary first before applying the conservation of energy equation.",
        "Answer: Internal energy of an ideal gas depends only on temperature.",
        "Q3: Explain the phenomenon by resolving the velocity vector into horizontal and vertical components.",
        "Sol: Horizontal velocity remains constant throughout projectile motion.",
      ].join("\n"),
    },
  ];

  const scanGenuineBeforeAns = extractQuestionBlocksFromTranscript(
    pagesWithFollowingUnnumberedAns,
    known
  );

  // Genuine short answers for Q1, Q2, and Q3 must NOT be deleted!
  assert.equal(
    scanGenuineBeforeAns.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "What remains constant in an isolated system is total mechanical energy."
  );
  assert.equal(
    scanGenuineBeforeAns.blocksByQuestion.get("2")?.[0]?.verbatimText,
    "Define the system boundary first before applying the conservation of energy equation."
  );
  assert.equal(
    scanGenuineBeforeAns.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "Explain the phenomenon by resolving the velocity vector into horizontal and vertical components."
  );

  // The unnumbered Ans:/Answer:/Sol: lines are also preserved in ambiguousUnassignedBlocks (nothing lost)
  assert.equal(scanGenuineBeforeAns.ambiguousUnassignedBlocks.length, 3);
  assert.equal(
    scanGenuineBeforeAns.ambiguousUnassignedBlocks[0]?.text,
    "Linear momentum is also conserved when net external force is zero."
  );
  assert.equal(
    scanGenuineBeforeAns.ambiguousUnassignedBlocks[1]?.text,
    "Internal energy of an ideal gas depends only on temperature."
  );
  assert.equal(
    scanGenuineBeforeAns.ambiguousUnassignedBlocks[2]?.text,
    "Horizontal velocity remains constant throughout projectile motion."
  );

  // And all three questions + transcript are flagged as ambiguous for teacher review
  assert.equal(scanGenuineBeforeAns.hasAmbiguousHeaders, true);
  assert.equal(scanGenuineBeforeAns.ambiguousQuestions.has("1"), true);
  assert.equal(scanGenuineBeforeAns.ambiguousQuestions.has("2"), true);
  assert.equal(scanGenuineBeforeAns.ambiguousQuestions.has("3"), true);

  // Verify end-to-end alignment preserves the student answers and sets review_required = true
  const alignedGenuine = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      {
        question_number: "1",
        is_attempted: true,
        student_answer: "What remains constant in an isolated system is total mechanical energy.",
        awarded_marks: 8,
        feedback: "Good Q1",
        review_required: false,
      },
      {
        question_number: "2",
        is_attempted: true,
        student_answer:
          "Define the system boundary first before applying the conservation of energy equation.",
        awarded_marks: 7,
        feedback: "Good Q2",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer:
          "Explain the phenomenon by resolving the velocity vector into horizontal and vertical components.",
        awarded_marks: 9,
        feedback: "Good Q3",
        review_required: false,
      },
    ],
    pagesWithFollowingUnnumberedAns,
    scanGenuineBeforeAns
  );

  const summaryGenuine = finalizeProvisionalEvaluationSummary({
    aligned: alignedGenuine,
    officialClassTotal: 30,
  });
  const byNumGenuine = new Map(
    alignedGenuine.evaluationQuestionsToInsert.map((q) => [q.question_number, q])
  );
  assert.equal(
    byNumGenuine.get("1")?.student_answer,
    "What remains constant in an isolated system is total mechanical energy."
  );
  assert.equal(byNumGenuine.get("1")?.review_required, true);
  assert.equal(
    byNumGenuine.get("2")?.student_answer,
    "Define the system boundary first before applying the conservation of energy equation."
  );
  assert.equal(byNumGenuine.get("2")?.review_required, true);
  assert.equal(
    byNumGenuine.get("3")?.student_answer,
    "Explain the phenomenon by resolving the velocity vector into horizontal and vertical components."
  );
  assert.equal(byNumGenuine.get("3")?.review_required, true);
  assert.equal(summaryGenuine.overallReviewRequired, true);
  assert.equal(summaryGenuine.finalEvaluationStatus, "needs_review");

  // Part B: Genuine copied question prompts followed by Ans: / Answer: / Sol: on the next line
  const pagesWithCopiedPrompts = [
    {
      pageNumber: 1,
      text: [
        "Q1: What is the SI unit of electric charge?",
        "Ans: The SI unit of electric charge is the coulomb (C).",
        "Q2: Define specific latent heat.",
        "Answer: It is the heat required to change the state of unit mass without temperature change.",
        "Q3: Explain why the sky appears blue.",
        "Sol: Shorter blue wavelengths of sunlight are scattered more strongly by atmospheric molecules.",
      ].join("\n"),
    },
  ];

  const scanCopiedPrompts = extractQuestionBlocksFromTranscript(pagesWithCopiedPrompts, known);
  assert.equal(scanCopiedPrompts.hasAmbiguousHeaders, false);
  assert.equal(scanCopiedPrompts.ambiguousUnassignedBlocks.length, 0);
  assert.equal(
    scanCopiedPrompts.blocksByQuestion.get("1")?.[0]?.verbatimText,
    "The SI unit of electric charge is the coulomb (C)."
  );
  assert.equal(
    scanCopiedPrompts.blocksByQuestion.get("2")?.[0]?.verbatimText,
    "It is the heat required to change the state of unit mass without temperature change."
  );
  assert.equal(
    scanCopiedPrompts.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "Shorter blue wavelengths of sunlight are scattered more strongly by atmospheric molecules."
  );
});

test("18. Gemini JSON parsing & schema validation: valid JSON, Markdown code fences, leading/trailing text, unescaped newline repair, and rejection of empty or truncated JSON", () => {
  // 1. Valid Gemini JSON output
  const validPayload = JSON.stringify({
    evaluations: [
      {
        question_number: "1",
        is_attempted: true,
        source_segments: [{ page_number: 1, verbatim_text: "Force equals mass times acceleration." }],
        student_answer: "Force equals mass times acceleration.",
        continuation_detected: false,
        mapping_ambiguous: false,
        mapping_review_reason: null,
        awarded_marks: 5,
        feedback: "Complete and accurate.",
        review_required: false,
        review_reason: null,
      },
    ],
  });
  const parsedValid = extractAndParseJson(validPayload);
  assert.equal(parsedValid.ok, true);
  const schemaValid = validateGradingResponseSchema(parsedValid.value, ["1"]);
  assert.equal(schemaValid.valid, true);
  assert.equal(schemaValid.evaluations.length, 1);
  assert.equal(schemaValid.evaluations[0].awarded_marks, 5);

  // 2. Gemini JSON wrapped in Markdown code fences
  const fencedJson = "```json\n" + validPayload + "\n```";
  const parsedFenced = extractAndParseJson(fencedJson);
  assert.equal(parsedFenced.ok, true);
  assert.equal(validateGradingResponseSchema(parsedFenced.value, ["1"]).valid, true);

  // 3. Gemini JSON with leading and trailing commentary text
  const surroundedJson =
    "Here is the objective grading output for the student paper:\n" +
    validPayload +
    "\nNote: All criteria were verified against the rubric.";
  const parsedSurrounded = extractAndParseJson(surroundedJson);
  assert.equal(parsedSurrounded.ok, true);
  assert.equal(validateGradingResponseSchema(parsedSurrounded.value, ["1"]).valid, true);

  // 4. Repair of unescaped literal newlines/tabs inside JSON string literals and trailing commas
  const unescapedControlJson =
    '{"evaluations":[{"question_number":"1","is_attempted":true,"student_answer":"Line one\nLine two\twith tab","awarded_marks":"4","feedback":"Good work",},]}';
  const parsedRepaired = extractAndParseJson(unescapedControlJson);
  assert.equal(parsedRepaired.ok, true);
  const schemaRepaired = validateGradingResponseSchema(parsedRepaired.value, ["1"]);
  assert.equal(schemaRepaired.valid, true);
  assert.equal(schemaRepaired.evaluations[0].awarded_marks, 4);
  assert.equal(schemaRepaired.evaluations[0].student_answer, "Line one\nLine two\twith tab");

  // 5. Empty Gemini output must be rejected (never turned into {} or fake evaluation)
  const parsedEmpty = extractAndParseJson("   ");
  assert.equal(parsedEmpty.ok, false);
  assert.equal(parsedEmpty.isTruncated, false);

  const parsedEmptyObj = extractAndParseJson("{}");
  assert.equal(parsedEmptyObj.ok, true);
  const schemaEmptyObj = validateGradingResponseSchema(parsedEmptyObj.value, ["1"]);
  assert.equal(schemaEmptyObj.valid, false);

  const parsedEmptyEvaluations = extractAndParseJson('{"evaluations":[]}');
  assert.equal(parsedEmptyEvaluations.ok, true);
  const schemaEmptyEvaluations = validateGradingResponseSchema(parsedEmptyEvaluations.value, ["1", "2"]);
  assert.equal(schemaEmptyEvaluations.valid, false);

  // 6. Truncated Gemini JSON output must be rejected with isTruncated = true
  const truncatedJson =
    '{"evaluations":[{"question_number":"1","is_attempted":true,"student_answer":"Incomplete answer text cut off at token limit';
  const parsedTruncated = extractAndParseJson(truncatedJson);
  assert.equal(parsedTruncated.ok, false);
  assert.equal(parsedTruncated.isTruncated, true);

  // 7. Attempted question missing numeric awarded_marks must fail schema validation
  const missingMarksJson = extractAndParseJson(
    '{"evaluations":[{"question_number":"1","is_attempted":true,"student_answer":"Some answer","awarded_marks":null}]}'
  );
  assert.equal(missingMarksJson.ok, true);
  const schemaMissingMarks = validateGradingResponseSchema(missingMarksJson.value, ["1"]);
  assert.equal(schemaMissingMarks.valid, false);

  // 8. Multi-part candidate text extraction
  const multiPartResp = {
    candidates: [
      {
        finishReason: "STOP",
        content: {
          parts: [{ text: '{"evaluations":[' }, { text: '{"question_number":"1","is_attempted":false,"awarded_marks":0}]}' }],
        },
      },
    ],
  };
  const extractedMulti = extractGeminiResponseText(multiPartResp);
  assert.equal(
    extractedMulti.text,
    '{"evaluations":[{"question_number":"1","is_attempted":false,"awarded_marks":0}]}'
  );
});

test("19. Transient Gemini failure followed by recovery vs. permanent invalid JSON and HTTP 4xx failure handling, plus secret redaction", async () => {
  // Part A: Transient HTTP 503 failure on attempt 1 + truncated JSON on attempt 2 -> valid JSON recovery on attempt 3
  let gradingCallCount = 0;
  const mockFetchRecovery = async () => {
    gradingCallCount++;
    if (gradingCallCount === 1) {
      return new Response("Service Unavailable", { status: 503 });
    }
    if (gradingCallCount === 2) {
      return new Response(
        JSON.stringify({
          candidates: [
            {
              finishReason: "MAX_TOKENS",
              content: {
                parts: [{ text: '{"evaluations":[{"question_number":"1","is_attempted":true' }],
              },
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }
    return new Response(
      JSON.stringify({
        candidates: [
          {
            finishReason: "STOP",
            content: {
              parts: [
                {
                  text: '```json\n{"evaluations":[{"question_number":"1","is_attempted":true,"student_answer":"Ohm law states V = IR.","awarded_marks":5,"feedback":"Full marks","review_required":false}]}\n```',
                },
              ],
            },
          },
        ],
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  };

  const recoveredResult = await callGeminiGradingWithRetry(
    "test-api-key",
    { contents: [] },
    ["1"],
    { fetchImpl: mockFetchRecovery, retryDelays: [0, 0], maxAttempts: 3 }
  );
  assert.equal(gradingCallCount, 3);
  assert.equal(recoveredResult.evaluations.length, 1);
  assert.equal(recoveredResult.evaluations[0].question_number, "1");
  assert.equal(recoveredResult.evaluations[0].awarded_marks, 5);

  // Part B: Permanent invalid JSON across all bounded attempts fails cleanly after maxAttempts
  let invalidJsonAttempts = 0;
  const mockFetchAlwaysInvalidJson = async () => {
    invalidJsonAttempts++;
    return new Response(
      JSON.stringify({
        candidates: [
          {
            finishReason: "STOP",
            content: { parts: [{ text: "This is not JSON at all and cannot be parsed." }] },
          },
        ],
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  };

  await assert.rejects(
    () =>
      callGeminiGradingWithRetry("test-api-key", { contents: [] }, ["1"], {
        fetchImpl: mockFetchAlwaysInvalidJson,
        retryDelays: [0, 0],
        maxAttempts: 3,
      }),
    (err) => {
      assert.match(err.message, /Gemini returned invalid JSON for student answer grading/i);
      return true;
    }
  );
  assert.equal(invalidJsonAttempts, 3);

  // Part C: Permanent HTTP 400 error is NEVER retried (fails on attempt 1)
  let permanentHttpAttempts = 0;
  const mockFetchPermanent400 = async () => {
    permanentHttpAttempts++;
    return new Response("Bad Request: key=AIzaSySecretKey12345678901234567890", { status: 400 });
  };

  await assert.rejects(
    () =>
      callGeminiGradingWithRetry("AIzaSySecretKey12345678901234567890", { contents: [] }, ["1"], {
        fetchImpl: mockFetchPermanent400,
        retryDelays: [0, 0],
        maxAttempts: 3,
      }),
    (err) => {
      assert.equal(err instanceof PermanentGeminiError, true);
      assert.equal(err.message.includes("AIzaSySecretKey12345678901234567890"), false);
      assert.equal(err.message.includes("[REDACTED_API_KEY]"), true);
      return true;
    }
  );
  assert.equal(permanentHttpAttempts, 1);

  // Part D: Verify sanitizeErrorMessage scrubs API keys, Bearer tokens, JWTs, and Supabase secrets
  const rawDirtyError =
    "Request failed for https://example.com?key=AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz123456 with Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c and sb_secret_9876543210abcdef";
  const sanitized = sanitizeErrorMessage(rawDirtyError);
  assert.equal(sanitized.includes("AIzaSy"), false);
  assert.equal(sanitized.includes("eyJhbGci"), false);
  assert.equal(sanitized.includes("sb_secret_9876543210abcdef"), false);
  assert.equal(sanitized.includes("[REDACTED"), true);
});

test("20. Question headers vs. numbers, formulas, dates, and standalone Part/Section banners inside answer text", () => {
  const known = ["1", "2", "3"];
  const pages = [
    {
      pageNumber: 1,
      text: [
        "Part A",
        "Q1: In 1947 the treaty was signed on 15 August 1947.",
        "2 - 1 = 1 is the fundamental identity.",
        "3 - x = 0 gives x = 3.",
        "Section B",
        "Question 2: The acceleration is 10 m/s^2 for 2 - 3 hours.",
        "Ans 3: The final value is 25 J.",
      ].join("\n"),
    },
  ];

  const scan = extractQuestionBlocksFromTranscript(pages, known);
  assert.equal(scan.hasAmbiguousHeaders, false);
  assert.equal(scan.ambiguousUnassignedBlocks.length, 0);

  // Q1 must keep its dates and formulas ("2 - 1 = 1", "3 - x = 0 gives x = 3.") and must NOT include "Section B"
  assert.equal(
    scan.blocksByQuestion.get("1")?.[0]?.verbatimText,
    [
      "In 1947 the treaty was signed on 15 August 1947.",
      "2 - 1 = 1 is the fundamental identity.",
      "3 - x = 0 gives x = 3.",
    ].join("\n")
  );
  // Q2 must NOT be broken by "2 - 1 = 1" or "2 - 3 hours"
  assert.equal(
    scan.blocksByQuestion.get("2")?.[0]?.verbatimText,
    "The acceleration is 10 m/s^2 for 2 - 3 hours."
  );
  // Q3 must NOT be broken by "3 - x = 0"
  assert.equal(
    scan.blocksByQuestion.get("3")?.[0]?.verbatimText,
    "The final value is 25 J."
  );
});

test("21. Multi-student batch evaluation: strict per-student isolation, no cross-student leakage, cache reuse, and isolated failure handling", async () => {
  // Build a deterministic in-memory mock Supabase adminClient with 1 class (2 questions, 20 marks total)
  // and 2 student papers (paper-student-1 and paper-student-2) with distinct completed OCR transcripts,
  // plus a 3rd paper (paper-approved) that is already approved and a 4th paper (paper-fail) whose grading fails.
  const teacherId = "teacher-uuid-1";
  const classId = "class-uuid-1";

  const studentPapersTable = new Map([
    [
      "paper-student-1",
      {
        id: "paper-student-1",
        student_id: "student-1",
        class_id: classId,
        teacher_id: teacherId,
        total_pages: 1,
        ocr_status: "completed",
        combined_ocr_text:
          "=== STUDENT ANSWER PAGE 1 ===\nQ1: Student One answer for Q1.\nQ2: Student One answer for Q2.",
      },
    ],
    [
      "paper-student-2",
      {
        id: "paper-student-2",
        student_id: "student-2",
        class_id: classId,
        teacher_id: teacherId,
        total_pages: 1,
        ocr_status: "completed",
        combined_ocr_text:
          "=== STUDENT ANSWER PAGE 1 ===\nQ2: Student Two out-of-order answer for Q2.\nQ1: Student Two out-of-order answer for Q1.",
      },
    ],
    [
      "paper-approved",
      {
        id: "paper-approved",
        student_id: "student-3",
        class_id: classId,
        teacher_id: teacherId,
        total_pages: 1,
        ocr_status: "completed",
        combined_ocr_text: "=== STUDENT ANSWER PAGE 1 ===\nQ1: Approved answer.",
      },
    ],
  ]);

  const studentPagesByPaper = new Map([
    [
      "paper-student-1",
      [
        {
          id: "page-s1-1",
          paper_id: "paper-student-1",
          page_number: 1,
          storage_path: "student-1/page1.jpg",
          ocr_text: "Q1: Student One answer for Q1.\nQ2: Student One answer for Q2.",
          ocr_status: "completed",
        },
      ],
    ],
    [
      "paper-student-2",
      [
        {
          id: "page-s2-1",
          paper_id: "paper-student-2",
          page_number: 1,
          storage_path: "student-2/page1.jpg",
          ocr_text: "Q2: Student Two out-of-order answer for Q2.\nQ1: Student Two out-of-order answer for Q1.",
          ocr_status: "completed",
        },
      ],
    ],
    [
      "paper-approved",
      [
        {
          id: "page-s3-1",
          paper_id: "paper-approved",
          page_number: 1,
          storage_path: "student-3/page1.jpg",
          ocr_text: "Q1: Approved answer.",
          ocr_status: "completed",
        },
      ],
    ],
  ]);

  const cachedClassQuestions = [
    {
      page_number: 1,
      question_number: "1",
      question_text: "State Newton's First Law.",
      maximum_marks: 10,
      expected_answer: "A body remains at rest or uniform motion unless acted upon by a net external force.",
      marking_criteria: "10 marks for complete statement.",
      review_required: false,
      review_reason: null,
      sort_order: 1,
    },
    {
      page_number: 1,
      question_number: "2",
      question_text: "Define kinetic energy.",
      maximum_marks: 10,
      expected_answer: "Energy possessed by a body due to its motion.",
      marking_criteria: "10 marks for accurate definition.",
      review_required: false,
      review_reason: null,
      sort_order: 2,
    },
  ];

  let storageDownloadCalls = 0;
  let studentPapersUpdateCalls = 0;
  const savedRpcPayloads = new Map();

  const mockAdminClient = {
    storage: {
      from() {
        return {
          async download() {
            storageDownloadCalls++;
            throw new Error("Storage download should not be called when cache and OCR are completed.");
          },
        };
      },
    },
    from(table) {
      const filters = {};
      let isUpdate = false;
      const builder = {
        select() {
          return builder;
        },
        update() {
          isUpdate = true;
          if (table === "student_papers") {
            studentPapersUpdateCalls++;
          }
          return builder;
        },
        eq(col, val) {
          filters[col] = val;
          return builder;
        },
        order() {
          return Promise.resolve(builder._resolveList());
        },
        maybeSingle() {
          return Promise.resolve(builder._resolveSingle());
        },
        then(resolve, reject) {
          return Promise.resolve(builder._resolveSingle()).then(resolve, reject);
        },
        _resolveSingle() {
          if (isUpdate) return { data: null, error: null };
          if (table === "student_papers") {
            const p = studentPapersTable.get(filters.id);
            return { data: p && p.teacher_id === filters.teacher_id ? p : null, error: null };
          }
          if (table === "student_paper_evaluations") {
            if (filters.paper_id === "paper-approved") {
              return { data: { id: "eval-approved", status: "approved" }, error: null };
            }
            return { data: null, error: null };
          }
          if (table === "classes") {
            return {
              data: {
                id: classId,
                subject_name: "Physics",
                total_marks: 20,
                question_paper_path: "class1/qp.jpg",
              },
              error: null,
            };
          }
          if (table === "class_question_paper_extractions") {
            return {
              data: {
                id: "ext-1",
                status: "completed",
                total_pages: 1,
                total_questions: 2,
                extracted_total_marks: 20,
                marks_match_class_total: true,
                unclear_question_paper: false,
                review_required: false,
                review_reason: null,
                updated_at: new Date().toISOString(),
              },
              error: null,
            };
          }
          return { data: null, error: null };
        },
        _resolveList() {
          if (table === "class_question_paper_pages") {
            return { data: [{ page_number: 1, storage_path: "class1/qp.jpg" }], error: null };
          }
          if (table === "student_paper_pages") {
            return { data: studentPagesByPaper.get(filters.paper_id) ?? [], error: null };
          }
          if (table === "class_question_paper_questions") {
            return { data: cachedClassQuestions, error: null };
          }
          return { data: [], error: null };
        },
      };
      return builder;
    },
    async rpc(fnName, args) {
      assert.equal(fnName, "save_provisional_paper_evaluation");
      savedRpcPayloads.set(args.p_paper_id, args);
      return {
        data: {
          id: `eval-${args.p_paper_id}`,
          paper_id: args.p_paper_id,
          student_id: args.p_student_id,
          class_id: args.p_class_id,
          teacher_id: args.p_teacher_id,
          status: args.p_status,
          total_marks_obtained: args.p_total_marks_obtained,
          total_marks: args.p_total_marks,
          percentage: args.p_percentage,
          review_required: args.p_review_required,
          review_reason: args.p_review_reason,
          created_at: "2026-10-25T00:00:00Z",
          updated_at: "2026-10-25T00:00:00Z",
          questions: args.p_questions,
        },
        error: null,
      };
    },
  };

  let geminiCallCount = 0;
  const mockFetchBatch = async (_url, init) => {
    geminiCallCount++;
    const bodyText = String(init?.body ?? "");
    if (bodyText.includes("Student One answer for Q1.")) {
      return new Response(
        JSON.stringify({
          candidates: [
            {
              finishReason: "STOP",
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      evaluations: [
                        {
                          question_number: "1",
                          is_attempted: true,
                          student_answer: "Student One answer for Q1.",
                          awarded_marks: 9,
                          feedback: "Strong Q1 from Student 1",
                          review_required: false,
                        },
                        {
                          question_number: "2",
                          is_attempted: true,
                          student_answer: "Student One answer for Q2.",
                          awarded_marks: 8,
                          feedback: "Strong Q2 from Student 1",
                          review_required: false,
                        },
                      ],
                    }),
                  },
                ],
              },
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    if (bodyText.includes("Student Two out-of-order answer for Q2.")) {
      return new Response(
        JSON.stringify({
          candidates: [
            {
              finishReason: "STOP",
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      evaluations: [
                        {
                          question_number: "1",
                          is_attempted: true,
                          student_answer: "Student Two out-of-order answer for Q1.",
                          awarded_marks: 6,
                          feedback: "Partial Q1 from Student 2",
                          review_required: false,
                        },
                        {
                          question_number: "2",
                          is_attempted: true,
                          student_answer: "Student Two out-of-order answer for Q2.",
                          awarded_marks: 7,
                          feedback: "Partial Q2 from Student 2",
                          review_required: false,
                        },
                      ],
                    }),
                  },
                ],
              },
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    throw new Error("Unexpected Gemini call in batch test.");
  };

  const batchResult = await evaluateStudentPapersBatch({
    adminClient: mockAdminClient,
    teacherId,
    paperIds: ["paper-student-1", "paper-student-2", "paper-approved"],
    geminiApiKey: "test-gemini-key",
    retryOptions: { fetchImpl: mockFetchBatch, retryDelays: [0, 0], maxAttempts: 2 },
    concurrency: 2,
  });

  // 2 papers succeeded (HTTP 200), 1 paper was protected as already approved (HTTP 409)
  assert.equal(batchResult.succeededCount, 2);
  assert.equal(batchResult.failedCount, 1);

  // Verify zero storage downloads and zero redundant student_papers OCR updates when cache is already completed
  assert.equal(storageDownloadCalls, 0);
  assert.equal(studentPapersUpdateCalls, 0);
  // Only 2 Gemini calls total (1 grading call for Student 1, 1 grading call for Student 2; 0 for QP extraction, 0 for OCR, 0 for approved paper)
  assert.equal(geminiCallCount, 2);

  // Verify Student 1 and Student 2 results are strictly isolated with zero cross-student data leakage
  const s1Payload = savedRpcPayloads.get("paper-student-1");
  const s2Payload = savedRpcPayloads.get("paper-student-2");
  assert.ok(s1Payload);
  assert.ok(s2Payload);

  assert.equal(s1Payload.p_student_id, "student-1");
  assert.equal(s1Payload.p_total_marks_obtained, 17);
  assert.equal(s1Payload.p_percentage, 85);
  assert.equal(s1Payload.p_questions[0].student_answer, "Student One answer for Q1.");
  assert.equal(s1Payload.p_questions[1].student_answer, "Student One answer for Q2.");

  assert.equal(s2Payload.p_student_id, "student-2");
  assert.equal(s2Payload.p_total_marks_obtained, 13);
  assert.equal(s2Payload.p_percentage, 65);
  assert.equal(s2Payload.p_questions[0].student_answer, "Student Two out-of-order answer for Q1.");
  assert.equal(s2Payload.p_questions[1].student_answer, "Student Two out-of-order answer for Q2.");

  // Verify approved paper returned HTTP 409 and was never overwritten
  const approvedOutcome = batchResult.results.find((r) => r.paperId === "paper-approved");
  assert.equal(approvedOutcome?.httpStatus, 409);
  assert.equal(savedRpcPayloads.has("paper-approved"), false);

  // Verify that if one student paper fails grading in a batch, the other student paper still succeeds
  studentPapersTable.set("paper-student-fail", {
    id: "paper-student-fail",
    student_id: "student-4",
    class_id: classId,
    teacher_id: teacherId,
    total_pages: 1,
    ocr_status: "completed",
    combined_ocr_text: "=== STUDENT ANSWER PAGE 1 ===\nQ1: Student Four answer.",
  });
  studentPagesByPaper.set("paper-student-fail", [
    {
      id: "page-s4-1",
      paper_id: "paper-student-fail",
      page_number: 1,
      storage_path: "student-4/page1.jpg",
      ocr_text: "Q1: Student Four answer.",
      ocr_status: "completed",
    },
  ]);

  const mockFetchPartialFailure = async (_url, init) => {
    const bodyText = String(init?.body ?? "");
    if (bodyText.includes("Student Four answer.")) {
      return new Response(
        JSON.stringify({
          candidates: [{ finishReason: "STOP", content: { parts: [{ text: "INVALID_NON_JSON_OUTPUT" }] } }],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }
    return mockFetchBatch(_url, init);
  };

  const partialBatch = await evaluateStudentPapersBatch({
    adminClient: mockAdminClient,
    teacherId,
    paperIds: ["paper-student-1", "paper-student-fail"],
    geminiApiKey: "test-gemini-key",
    retryOptions: { fetchImpl: mockFetchPartialFailure, retryDelays: [0, 0], maxAttempts: 2 },
    concurrency: 2,
  });

  assert.equal(partialBatch.succeededCount, 1);
  assert.equal(partialBatch.failedCount, 1);
  const s1AfterPartial = partialBatch.results.find((r) => r.paperId === "paper-student-1");
  const s4AfterPartial = partialBatch.results.find((r) => r.paperId === "paper-student-fail");
  assert.equal(s1AfterPartial?.httpStatus, 200);
  assert.equal(s4AfterPartial?.httpStatus, 500);
  assert.equal(s4AfterPartial?.body.status, "failed");
  assert.match(s4AfterPartial?.body.error ?? "", /Gemini returned invalid JSON for student answer grading/i);
});

test("22. Numerical question ordering: Q1..Q10, subquestions Q2(a)/Q2(b)/Q2(i)/Q2(ii), out-of-order student answers, and scrambled DB rows", async () => {
  // 1. Direct comparator & sorter verification: double-digit numbers (Q10 after Q2) and subquestions
  const scrambledQuestions = [
    { question_number: "10", id: "q10", text: "Question 10" },
    { question_number: "2(b)", id: "q2b", text: "Question 2b" },
    { question_number: "1", id: "q1", text: "Question 1" },
    { question_number: "2(a)(ii)", id: "q2aii", text: "Question 2a-ii" },
    { question_number: "2(a)(i)", id: "q2ai", text: "Question 2a-i" },
    { question_number: "2", id: "q2", text: "Question 2" },
    { question_number: "3", id: "q3", text: "Question 3" },
    { question_number: "2(a)", id: "q2a", text: "Question 2a" },
  ];

  const sorted = sortQuestionsNumerically(scrambledQuestions);
  assert.deepEqual(
    sorted.map((q) => q.question_number),
    ["1", "2", "2(a)", "2(a)(i)", "2(a)(ii)", "2(b)", "3", "10"]
  );
  // Verify every object kept its own attached fields after sorting
  assert.equal(sorted[0].id, "q1");
  assert.equal(sorted[2].id, "q2a");
  assert.equal(sorted[5].id, "q2b");
  assert.equal(sorted[7].id, "q10");
  assert.ok(compareQuestionNumbers("2", "10") < 0);
  assert.ok(compareQuestionNumbers("Q2", "Q10") < 0);
  assert.ok(compareQuestionNumbers("2(a)", "2(b)") < 0);
  assert.ok(compareQuestionNumbers("2(i)", "2(ii)") < 0);
  assert.ok(compareQuestionNumbers("2(iv)", "2(v)") < 0);

  // 2. Out-of-order student answers (Q10, then Q1, then Q3, then Q2(b), then Q2(a))
  //    AND scrambled rawQuestions input -> evaluationQuestionsToInsert must be strictly ordered Q1, Q2(a), Q2(b), Q3, Q10
  const rawQuestionsScrambled = [
    {
      question_number: "10",
      question_text: "Explain nuclear fission.",
      maximum_marks: 10,
      expected_answer: "Splitting of heavy nucleus into lighter nuclei.",
      marking_criteria: "10 marks for fission definition.",
    },
    {
      question_number: "3",
      question_text: "State Hooke's law.",
      maximum_marks: 5,
      expected_answer: "Force is proportional to extension within elastic limit.",
      marking_criteria: "5 marks for Hooke's law.",
    },
    {
      question_number: "2(b)",
      question_text: "Define power.",
      maximum_marks: 5,
      expected_answer: "Rate of doing work.",
      marking_criteria: "5 marks for power definition.",
    },
    {
      question_number: "1",
      question_text: "Define velocity.",
      maximum_marks: 5,
      expected_answer: "Rate of change of displacement.",
      marking_criteria: "5 marks for velocity definition.",
    },
    {
      question_number: "2(a)",
      question_text: "Define work.",
      maximum_marks: 5,
      expected_answer: "Product of force and displacement in direction of force.",
      marking_criteria: "5 marks for work definition.",
    },
  ];

  const studentPages = [
    {
      pageNumber: 1,
      text: [
        "Q10: Nuclear fission is the splitting of a heavy nucleus into two lighter nuclei.",
        "Q1: Velocity is the rate of change of displacement with time.",
        "Q3: Within the elastic limit, stress is directly proportional to strain.",
        "Q2(b): Power is defined as the rate at which work is done.",
        "Q2(a): Work is the product of force and displacement in the direction of force.",
      ].join("\n"),
    },
  ];

  const aligned = evaluateOptionalGroupsAndAlignResults(
    rawQuestionsScrambled,
    [
      {
        question_number: "10",
        is_attempted: true,
        student_answer: "Nuclear fission is the splitting of a heavy nucleus into two lighter nuclei.",
        awarded_marks: 9,
        feedback: "Feedback for Q10",
        review_required: false,
      },
      {
        question_number: "1",
        is_attempted: true,
        student_answer: "Velocity is the rate of change of displacement with time.",
        awarded_marks: 5,
        feedback: "Feedback for Q1",
        review_required: false,
      },
      {
        question_number: "3",
        is_attempted: true,
        student_answer: "Within the elastic limit, stress is directly proportional to strain.",
        awarded_marks: 4,
        feedback: "Feedback for Q3",
        review_required: false,
      },
      {
        question_number: "2(b)",
        is_attempted: true,
        student_answer: "Power is defined as the rate at which work is done.",
        awarded_marks: 5,
        feedback: "Feedback for Q2(b)",
        review_required: false,
      },
      {
        question_number: "2(a)",
        is_attempted: true,
        student_answer: "Work is the product of force and displacement in the direction of force.",
        awarded_marks: 5,
        feedback: "Feedback for Q2(a)",
        review_required: false,
      },
    ],
    studentPages
  );

  assert.deepEqual(
    aligned.evaluationQuestionsToInsert.map((q) => q.question_number),
    ["1", "2(a)", "2(b)", "3", "10"]
  );
  assert.equal(
    aligned.evaluationQuestionsToInsert[0].student_answer,
    "Velocity is the rate of change of displacement with time."
  );
  assert.equal(aligned.evaluationQuestionsToInsert[0].awarded_marks, 5);
  assert.equal(aligned.evaluationQuestionsToInsert[0].feedback, "Feedback for Q1");

  assert.equal(
    aligned.evaluationQuestionsToInsert[1].student_answer,
    "Work is the product of force and displacement in the direction of force."
  );
  assert.equal(aligned.evaluationQuestionsToInsert[1].awarded_marks, 5);

  assert.equal(
    aligned.evaluationQuestionsToInsert[2].student_answer,
    "Power is defined as the rate at which work is done."
  );
  assert.equal(aligned.evaluationQuestionsToInsert[2].awarded_marks, 5);

  assert.equal(
    aligned.evaluationQuestionsToInsert[3].student_answer,
    "Within the elastic limit, stress is directly proportional to strain."
  );
  assert.equal(aligned.evaluationQuestionsToInsert[3].awarded_marks, 4);

  assert.equal(
    aligned.evaluationQuestionsToInsert[4].student_answer,
    "Nuclear fission is the splitting of a heavy nucleus into two lighter nuclei."
  );
  assert.equal(aligned.evaluationQuestionsToInsert[4].awarded_marks, 9);
  assert.equal(aligned.evaluationQuestionsToInsert[4].maximum_marks, 10);
  assert.equal(aligned.totalMarksObtained, 28);
});

test("23. Optional-group highest-required-attempts scoring: 4 scores of 5 count as 15/30 (not 20) and 8, 6, 7, 5 count as 21/30 while preserving all attempted answers", () => {
  const { questions, pageExtractions } = makeExamPaper(
    6,
    10,
    ["Attempt any 3 out of 6 questions."],
    30
  );
  const normalizedMeta = resolveEqualMarksAndSelectionRules(questions, pageExtractions, 30);

  const pagesFourAttempted = [
    {
      pageNumber: 1,
      text: [
        "Q1: Student answer one.",
        "Q2: Student answer two.",
        "Q4: Student answer four.",
        "Q6: Student answer six.",
      ].join("\n"),
    },
  ];

  // Case 1: 4 scores of 5 (5, 5, 5, 5) in an attempt-3-of-6 group -> counted total 15/30 (not 20/30)
  const alignedEqual5s = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "1", is_attempted: true, student_answer: "Student answer one.", awarded_marks: 5, feedback: "Feedback Q1", review_required: false },
      { question_number: "2", is_attempted: true, student_answer: "Student answer two.", awarded_marks: 5, feedback: "Feedback Q2", review_required: false },
      { question_number: "4", is_attempted: true, student_answer: "Student answer four.", awarded_marks: 5, feedback: "Feedback Q4", review_required: false },
      { question_number: "6", is_attempted: true, student_answer: "Student answer six.", awarded_marks: 5, feedback: "Feedback Q6", review_required: false },
    ],
    pagesFourAttempted
  );
  const summaryEqual5s = finalizeProvisionalEvaluationSummary({
    aligned: alignedEqual5s,
    officialClassTotal: 30,
  });

  assert.equal(alignedEqual5s.totalMarksObtained, 15);
  assert.equal(summaryEqual5s.finalTotalMarksObtained, 15);
  assert.equal(summaryEqual5s.finalTotalMarks, 30);
  assert.equal(summaryEqual5s.calculatedPercentage, 50);
  assert.equal(summaryEqual5s.overallReviewRequired, true);
  assert.equal(summaryEqual5s.finalEvaluationStatus, "needs_review");

  const byNum5s = new Map(alignedEqual5s.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  for (const [qNum, ansText, fbText] of [
    ["1", "Student answer one.", "Feedback Q1"],
    ["2", "Student answer two.", "Feedback Q2"],
    ["4", "Student answer four.", "Feedback Q4"],
    ["6", "Student answer six.", "Feedback Q6"],
  ]) {
    assert.equal(byNum5s.get(qNum)?.student_answer, ansText);
    assert.equal(byNum5s.get(qNum)?.awarded_marks, 5);
    assert.equal(byNum5s.get(qNum)?.feedback?.includes(fbText), true);
    assert.equal(byNum5s.get(qNum)?.review_required, true);
  }
  for (const unattemptedQ of ["3", "5"]) {
    assert.equal(byNum5s.get(unattemptedQ)?.student_answer, "[Optional question — not attempted]");
    assert.equal(byNum5s.get(unattemptedQ)?.awarded_marks, 0);
  }

  // Case 2: Different scores 8, 6, 7, 5 across 4 attempted questions -> counted total 21/30 (8 + 7 + 6)
  const alignedMixed = evaluateOptionalGroupsAndAlignResults(
    normalizedMeta.questions,
    [
      { question_number: "1", is_attempted: true, student_answer: "Student answer one.", awarded_marks: 8, feedback: "Feedback Q1", review_required: false },
      { question_number: "2", is_attempted: true, student_answer: "Student answer two.", awarded_marks: 6, feedback: "Feedback Q2", review_required: false },
      { question_number: "4", is_attempted: true, student_answer: "Student answer four.", awarded_marks: 7, feedback: "Feedback Q4", review_required: false },
      { question_number: "6", is_attempted: true, student_answer: "Student answer six.", awarded_marks: 5, feedback: "Feedback Q6", review_required: false },
    ],
    pagesFourAttempted
  );
  const summaryMixed = finalizeProvisionalEvaluationSummary({
    aligned: alignedMixed,
    officialClassTotal: 30,
  });

  assert.equal(alignedMixed.totalMarksObtained, 21);
  assert.equal(summaryMixed.finalTotalMarksObtained, 21);
  assert.equal(summaryMixed.finalTotalMarks, 30);
  assert.equal(summaryMixed.calculatedPercentage, 70);
  assert.equal(summaryMixed.overallReviewRequired, true);
  assert.equal(summaryMixed.finalEvaluationStatus, "needs_review");

  const byNumMixed = new Map(alignedMixed.evaluationQuestionsToInsert.map((q) => [q.question_number, q]));
  assert.equal(byNumMixed.get("1")?.awarded_marks, 8);
  assert.equal(byNumMixed.get("2")?.awarded_marks, 6);
  assert.equal(byNumMixed.get("4")?.awarded_marks, 7);
  assert.equal(byNumMixed.get("6")?.awarded_marks, 5);
  assert.equal(byNumMixed.get("6")?.student_answer, "Student answer six.");
  assert.equal(byNumMixed.get("6")?.review_required, true);
});

