package com.example.data.model

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val evaluationJsonParser = Json {
  ignoreUnknownKeys = true
  isLenient = true
  coerceInputValues = true
}

@Serializable
data class AiEvaluationResponse(
  @SerialName("status")
  val status: String? = null,
  @SerialName("percentageAvailable")
  val percentageAvailable: Boolean = false,
  @SerialName("evaluation")
  val evaluation: EvaluationDetail? = null,
  @SerialName("error")
  val error: String? = null,
)

@Serializable
data class EvaluationDetail(
  @SerialName("id")
  val id: String? = null,
  @SerialName("paperId")
  val paperId: String? = null,
  @SerialName("studentId")
  val studentId: String? = null,
  @SerialName("classId")
  val classId: String? = null,
  @SerialName("teacherId")
  val teacherId: String? = null,
  @SerialName("status")
  val status: String? = null,
  @SerialName("totalMarksObtained")
  val totalMarksObtained: Double? = null,
  @SerialName("totalMarks")
  val totalMarks: Double? = null,
  @SerialName("extractedQuestionTotalMarks")
  val extractedQuestionTotalMarks: Double? = null,
  @SerialName("percentage")
  val percentage: Double? = null,
  @SerialName("percentageAvailable")
  val percentageAvailable: Boolean = false,
  @SerialName("reviewRequired")
  val reviewRequired: Boolean = false,
  @SerialName("reviewReason")
  val reviewReason: String? = null,
  @SerialName("createdAt")
  val createdAt: String? = null,
  @SerialName("updatedAt")
  val updatedAt: String? = null,
  @SerialName("questions")
  val questions: List<EvaluationQuestion> = emptyList(),
) {
  val displayTotalMarksObtained: String
    get() {
      val marks = totalMarksObtained ?: 0.0
      return if (marks % 1.0 == 0.0) marks.toInt().toString() else marks.toString()
    }

  val displayTotalMarks: String
    get() {
      val marks = totalMarks ?: 0.0
      return if (marks % 1.0 == 0.0) marks.toInt().toString() else marks.toString()
    }

  val displayExtractedTotalMarks: String
    get() {
      val marks = extractedQuestionTotalMarks ?: 0.0
      return if (marks % 1.0 == 0.0) marks.toInt().toString() else marks.toString()
    }
}

@Serializable
data class EvaluationQuestion(
  @SerialName("id")
  val id: String? = null,
  @SerialName("evaluation_id")
  val evaluationId: String? = null,
  @SerialName("question_number")
  val questionNumber: String? = null,
  @SerialName("question_text")
  val questionText: String? = null,
  @SerialName("maximum_marks")
  val maximumMarks: Double? = null,
  @SerialName("student_answer")
  val studentAnswer: String? = null,
  @SerialName("expected_answer")
  val expectedAnswer: String? = null,
  @SerialName("marking_criteria")
  val markingCriteria: String? = null,
  @SerialName("awarded_marks")
  val awardedMarks: Double? = null,
  @SerialName("feedback")
  val feedback: String? = null,
  @SerialName("review_required")
  val reviewRequired: Boolean = false,
  @SerialName("review_reason")
  val reviewReason: String? = null,
  @SerialName("expected_answer_source")
  val expectedAnswerSource: String? = null,
  @SerialName("marking_criteria_source")
  val markingCriteriaSource: String? = null,
) {
  val displayAwardedMarks: String
    get() {
      val marks = awardedMarks ?: 0.0
      return if (marks % 1.0 == 0.0) marks.toInt().toString() else marks.toString()
    }

  val displayMaxMarks: String
    get() {
      val marks = maximumMarks ?: 0.0
      return if (marks % 1.0 == 0.0) marks.toInt().toString() else marks.toString()
    }
}

/**
 * Resilient parser that extracts AiEvaluationResponse from edge function, RPC, or database JSON.
 * Seamlessly handles both wrapped {"status": ..., "evaluation": {...}} and flat evaluation JSON,
 * supporting both camelCase and snake_case keys, number/string type variations, and fallback paper IDs.
 */
fun parseAiEvaluationResponse(jsonText: String, fallbackPaperId: String? = null): AiEvaluationResponse? {
  if (jsonText.isBlank()) return null

  // 1. Try standard parser first
  try {
    val decoded = evaluationJsonParser.decodeFromString<AiEvaluationResponse>(jsonText)
    if (decoded.evaluation != null && decoded.evaluation.questions.isNotEmpty()) {
      val finalPaperId = decoded.evaluation.paperId ?: fallbackPaperId
      val sortedQuestions = sortEvaluationQuestionsNumerically(decoded.evaluation.questions)
      val finalEvaluation = decoded.evaluation.copy(
        paperId = finalPaperId,
        questions = sortedQuestions,
      )
      return decoded.copy(evaluation = finalEvaluation)
    }
  } catch (e: Exception) {
    Log.d("EvaluationModels", "Standard parse deferred to tree parser: ${e.message}")
  }

  // 2. Lenient JSON tree parser
  return try {
    val rootElement = evaluationJsonParser.parseToJsonElement(jsonText)
    if (rootElement !is JsonObject) return null

    val rootObj = rootElement.jsonObject
    val topStatus = rootObj["status"]?.jsonPrimitive?.contentOrNull
    val topPercentageAvailable = rootObj["percentageAvailable"]?.jsonPrimitive?.booleanOrNull
      ?: rootObj["percentage_available"]?.jsonPrimitive?.booleanOrNull
      ?: false
    val topError = rootObj["error"]?.jsonPrimitive?.contentOrNull

    val evalElement = rootObj["evaluation"]
    val evalObj: JsonObject = if (evalElement is JsonObject) {
      evalElement
    } else {
      rootObj
    }

    fun getString(obj: JsonObject, vararg keys: String): String? {
      for (k in keys) {
        val prim = obj[k]?.jsonPrimitive ?: continue
        prim.contentOrNull?.let { return it }
      }
      return null
    }

    fun getDouble(obj: JsonObject, vararg keys: String): Double? {
      for (k in keys) {
        val prim = obj[k]?.jsonPrimitive ?: continue
        prim.doubleOrNull?.let { return it }
        prim.intOrNull?.let { return it.toDouble() }
        prim.contentOrNull?.toDoubleOrNull()?.let { return it }
      }
      return null
    }

    fun getBoolean(obj: JsonObject, vararg keys: String): Boolean {
      for (k in keys) {
        val prim = obj[k]?.jsonPrimitive ?: continue
        prim.booleanOrNull?.let { return it }
        val s = prim.contentOrNull?.lowercase()
        if (s == "true") return true
        if (s == "false") return false
      }
      return false
    }

    val id = getString(evalObj, "id", "evaluation_id", "evaluationId")
    val paperId = getString(evalObj, "paperId", "paper_id") ?: fallbackPaperId
    val studentId = getString(evalObj, "studentId", "student_id")
    val classId = getString(evalObj, "classId", "class_id")
    val teacherId = getString(evalObj, "teacherId", "teacher_id")
    val evalStatus = getString(evalObj, "status") ?: topStatus

    val totalMarksObtained = getDouble(evalObj, "totalMarksObtained", "total_marks_obtained")
    val totalMarks = getDouble(evalObj, "totalMarks", "total_marks")
    val extractedQuestionTotalMarks = getDouble(evalObj, "extractedQuestionTotalMarks", "extracted_question_total_marks")
    val percentage = getDouble(evalObj, "percentage")
    val evalPercentageAvailable = getBoolean(evalObj, "percentageAvailable", "percentage_available") || topPercentageAvailable
    val reviewRequired = getBoolean(evalObj, "reviewRequired", "review_required")
    val reviewReason = getString(evalObj, "reviewReason", "review_reason")
    val createdAt = getString(evalObj, "createdAt", "created_at")
    val updatedAt = getString(evalObj, "updatedAt", "updated_at")

    val questionsJsonArray = evalObj["questions"]?.jsonArray
      ?: rootObj["questions"]?.jsonArray
      ?: JsonArray(emptyList())

    val parsedQuestions = questionsJsonArray.mapNotNull { qElem ->
      if (qElem !is JsonObject) return@mapNotNull null
      val qObj = qElem.jsonObject

      val qNum = getString(qObj, "question_number", "questionNumber")
        ?: qObj["question_number"]?.jsonPrimitive?.intOrNull?.toString()
        ?: qObj["questionNumber"]?.jsonPrimitive?.intOrNull?.toString()

      EvaluationQuestion(
        id = getString(qObj, "id"),
        evaluationId = getString(qObj, "evaluation_id", "evaluationId") ?: id,
        questionNumber = qNum,
        questionText = getString(qObj, "question_text", "questionText"),
        maximumMarks = getDouble(qObj, "maximum_marks", "maximumMarks"),
        studentAnswer = getString(qObj, "student_answer", "studentAnswer"),
        expectedAnswer = getString(qObj, "expected_answer", "expectedAnswer"),
        markingCriteria = getString(qObj, "marking_criteria", "markingCriteria"),
        awardedMarks = getDouble(qObj, "awarded_marks", "awardedMarks"),
        feedback = getString(qObj, "feedback"),
        reviewRequired = getBoolean(qObj, "review_required", "reviewRequired"),
        reviewReason = getString(qObj, "review_reason", "reviewReason"),
        expectedAnswerSource = getString(qObj, "expected_answer_source", "expectedAnswerSource"),
        markingCriteriaSource = getString(qObj, "marking_criteria_source", "markingCriteriaSource"),
      )
    }
    val questions = sortEvaluationQuestionsNumerically(parsedQuestions)

    val detail = EvaluationDetail(
      id = id,
      paperId = paperId,
      studentId = studentId,
      classId = classId,
      teacherId = teacherId,
      status = evalStatus,
      totalMarksObtained = totalMarksObtained,
      totalMarks = totalMarks,
      extractedQuestionTotalMarks = extractedQuestionTotalMarks
        ?: if (questions.isNotEmpty()) calculateEffectiveEvaluationQuestionTotalMarks(questions) else null,
      percentage = percentage,
      percentageAvailable = evalPercentageAvailable,
      reviewRequired = reviewRequired,
      reviewReason = reviewReason,
      createdAt = createdAt,
      updatedAt = updatedAt,
      questions = questions,
    )

    AiEvaluationResponse(
      status = topStatus ?: evalStatus,
      percentageAvailable = evalPercentageAvailable,
      evaluation = detail,
      error = topError,
    )
  } catch (e: Exception) {
    Log.e("EvaluationModels", "Error in parseAiEvaluationResponse", e)
    null
  }
}

private val ROMAN_NUMERAL_VALUES = mapOf(
  "I" to 1,
  "II" to 2,
  "III" to 3,
  "IV" to 4,
  "V" to 5,
  "VI" to 6,
  "VII" to 7,
  "VIII" to 8,
  "IX" to 9,
  "X" to 10,
  "XI" to 11,
  "XII" to 12,
)

private val WORD_NUMERAL_VALUES = mapOf(
  "ONE" to 1,
  "TWO" to 2,
  "THREE" to 3,
  "FOUR" to 4,
  "FIVE" to 5,
  "SIX" to 6,
  "SEVEN" to 7,
  "EIGHT" to 8,
  "NINE" to 9,
  "TEN" to 10,
  "ELEVEN" to 11,
  "TWELVE" to 12,
)

private data class QuestionSortKey(
  val hasNumber: Boolean,
  val primaryNumber: Int,
  val subparts: List<String>,
  val normalized: String,
)

private val SORT_MARKDOWN_WRAPPER_REGEX = Regex("^[#*_`~\\s]+|[#*_`~\\s]+$")
private val SORT_QUESTION_PREFIX_REGEX = Regex(
  "^(?:(?:(?:ans(?:wer)?|sol(?:ution)?)[.:\\-\\s]*(?:(?:to|for|of)\\s+)?)?(?:question|ques|que|q\\.?\\s*n\\.?|q)(?:\\s*\\.?\\s*(?:no|num|number)\\.?)?|(?:ans(?:wer)?|sol(?:ution)?)(?:\\s*\\.?\\s*(?:no|num|number)\\.?)?|(?:no|num|number)\\.?)\\s*[.:\\-#)\\]–—\\s]*",
  RegexOption.IGNORE_CASE
)
private val SORT_PUNCTUATION_WRAPPER_REGEX = Regex("^[#*_`~.:\\-–—\\s]+|[#*_`~.:\\-–—\\s]+$")
private val SORT_TRAILING_PAREN_REGEX = Regex("[)\\s]+$")
private val SORT_TRAILING_BRACKET_REGEX = Regex("[\\]\\s]+$")
private val SORT_SPACED_SUBPART_PAREN_REGEX = Regex("\\s*\\(\\s*([a-zA-Z0-9]+)\\s*\\)")
private val SORT_LEADING_DIGITS_REGEX = Regex("^(\\d+)(.*)$")
private val SORT_SUBPART_TOKEN_REGEX = Regex("\\(([A-Z0-9]+)\\)|\\.(\\d+|[A-Z]+)|([A-Z]+|\\d+)")

fun normalizeQuestionNumberForSort(raw: String?): String {
  if (raw.isNullOrBlank()) return ""
  var s = raw.trim()
  s = s.replace(SORT_MARKDOWN_WRAPPER_REGEX, "")
  s = s.replace(SORT_QUESTION_PREFIX_REGEX, "")
  s = s.replace(SORT_PUNCTUATION_WRAPPER_REGEX, "")
  while (
    (s.startsWith("(") && s.endsWith(")") && !s.substring(1, s.length - 1).contains(")")) ||
    (s.startsWith("[") && s.endsWith("]") && !s.substring(1, s.length - 1).contains("]"))
  ) {
    s = s.substring(1, s.length - 1).trim()
  }
  s = s.replace(SORT_QUESTION_PREFIX_REGEX, "")
  if (s.endsWith(")") && !s.contains("(")) {
    s = s.replace(SORT_TRAILING_PAREN_REGEX, "")
  }
  if (s.endsWith("]") && !s.contains("[")) {
    s = s.replace(SORT_TRAILING_BRACKET_REGEX, "")
  }
  s = s.replace(SORT_SPACED_SUBPART_PAREN_REGEX, "($1)")
  return s.trim().uppercase()
}

private fun parseQuestionSortKey(raw: String?): QuestionSortKey {
  val norm = normalizeQuestionNumberForSort(raw)
  if (norm.isEmpty()) {
    return QuestionSortKey(
      hasNumber = false,
      primaryNumber = Int.MAX_VALUE,
      subparts = emptyList(),
      normalized = "",
    )
  }

  val leadingDigits = SORT_LEADING_DIGITS_REGEX.matchEntire(norm)
  if (leadingDigits != null) {
    val primary = leadingDigits.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
    val rest = leadingDigits.groupValues[2].trim()
    val subparts = mutableListOf<String>()
    if (rest.isNotEmpty()) {
      for (m in SORT_SUBPART_TOKEN_REGEX.findAll(rest)) {
        val tok = (m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } })
          .trim()
          .uppercase()
        if (tok.isNotEmpty()) subparts.add(tok)
      }
    }
    return QuestionSortKey(
      hasNumber = true,
      primaryNumber = primary,
      subparts = subparts,
      normalized = norm,
    )
  }

  val romanOrWord = ROMAN_NUMERAL_VALUES[norm] ?: WORD_NUMERAL_VALUES[norm]
  if (romanOrWord != null) {
    return QuestionSortKey(
      hasNumber = true,
      primaryNumber = romanOrWord,
      subparts = emptyList(),
      normalized = norm,
    )
  }

  return QuestionSortKey(
    hasNumber = false,
    primaryNumber = Int.MAX_VALUE,
    subparts = listOf(norm),
    normalized = norm,
  )
}

private fun compareSubpartTokens(a: String, b: String): Int {
  if (a == b) return 0
  val aInt = a.toIntOrNull()
  val bInt = b.toIntOrNull()
  if (aInt != null && bInt != null) {
    return aInt.compareTo(bInt)
  }
  val aRoman = ROMAN_NUMERAL_VALUES[a]
  val bRoman = ROMAN_NUMERAL_VALUES[b]
  if (aRoman != null && bRoman != null && (a.length > 1 || b.length > 1)) {
    return aRoman.compareTo(bRoman)
  }
  return a.compareTo(b)
}

/**
 * Compares two question number labels in natural numerical sequence
 * (e.g., "1" < "2" < "2(a)" < "2(b)" < "2(i)" < "2(ii)" < "3" < "10").
 */
fun compareQuestionNumbers(a: String?, b: String?): Int {
  val keyA = parseQuestionSortKey(a)
  val keyB = parseQuestionSortKey(b)

  if (keyA.hasNumber != keyB.hasNumber) {
    return if (keyA.hasNumber) -1 else 1
  }
  if (keyA.primaryNumber != keyB.primaryNumber) {
    return keyA.primaryNumber.compareTo(keyB.primaryNumber)
  }

  val minLen = minOf(keyA.subparts.size, keyB.subparts.size)
  for (i in 0 until minLen) {
    val cmp = compareSubpartTokens(keyA.subparts[i], keyB.subparts[i])
    if (cmp != 0) return cmp
  }

  if (keyA.subparts.size != keyB.subparts.size) {
    return keyA.subparts.size.compareTo(keyB.subparts.size)
  }

  return keyA.normalized.compareTo(keyB.normalized)
}

/**
 * Deterministically sorts evaluation questions in natural numerical order while preserving
 * each question's complete attached properties and stable relative order for ties/unnumbered items.
 */
fun sortEvaluationQuestionsNumerically(questions: List<EvaluationQuestion>): List<EvaluationQuestion> {
  if (questions.size <= 1) return questions
  return questions.sortedWith { q1, q2 ->
    compareQuestionNumbers(q1.questionNumber, q2.questionNumber)
  }
}

private val SELECTION_RULE_TAG_REGEX = Regex(
  """^\s*\[Selection Rule:\s*group=([^\s|;\]]+)\s*[|;]\s*attempt=(\d+)/(\d+)(?:\s*[|;]\s*type=(or|optional))?\]""",
  RegexOption.IGNORE_CASE
)

private data class ParsedSelectionRule(
  val group: String,
  val requiredAttempts: Int,
  val totalInGroup: Int,
)

private fun parseSelectionRule(markingCriteria: String?): ParsedSelectionRule? {
  if (markingCriteria.isNullOrBlank()) return null
  val match = SELECTION_RULE_TAG_REGEX.find(markingCriteria) ?: return null
  val group = match.groupValues[1].trim().uppercase()
  val required = match.groupValues[2].toIntOrNull() ?: return null
  val total = match.groupValues[3].toIntOrNull() ?: return null
  if (required <= 0 || total <= 0) return null
  return ParsedSelectionRule(group = group, requiredAttempts = required, totalInGroup = total)
}

/**
 * Strips any internal `[Selection Rule: ...]` prefix tag from `markingCriteria` for UI display
 * while preserving the underlying `EvaluationQuestion.markingCriteria` field for scoring rules.
 */
fun stripSelectionRuleTagFromCriteria(text: String?): String {
  if (text.isNullOrBlank()) return ""
  return text.replace(SELECTION_RULE_TAG_REGEX, "").trim()
}

/**
 * Returns true if the question has no student answer (blank, "[No answer detected]",
 * "[Optional question — not attempted]", or "No answer text extracted.") and therefore
 * cannot be awarded positive marks.
 */
fun isQuestionUnattemptedForScoring(question: EvaluationQuestion): Boolean {
  val ans = question.studentAnswer?.trim()
  if (ans.isNullOrEmpty()) return true
  if (ans.equals("[No answer detected]", ignoreCase = true)) return true
  if (ans.startsWith("[Optional question — not attempted]", ignoreCase = true)) return true
  if (ans.equals("No answer text extracted.", ignoreCase = true)) return true
  return false
}

private fun formatMarkForMessage(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
  }
}

sealed class MarkEditValidationResult {
  data class Valid(
    val updatedQuestions: List<EvaluationQuestion>,
    val totalMarksObtained: Double,
  ) : MarkEditValidationResult()

  data class Invalid(val message: String) : MarkEditValidationResult()
}

/**
 * Calculates the counted total marks obtained for a paper:
 * - Unattempted questions ("[No answer detected]" or "[Optional question — not attempted]") contribute 0.
 * - Optional question groups contribute at most the top `requiredAttempts` attempted questions, capped at the group maximum.
 * - Never returns a negative value.
 */
fun calculateCountedEvaluationTotalMarks(
  questions: List<EvaluationQuestion>,
  totalPossibleMarks: Double? = null,
): Double {
  if (questions.isEmpty()) return 0.0
  var total = 0.0
  val grouped = LinkedHashMap<String, Pair<ParsedSelectionRule, MutableList<EvaluationQuestion>>>()

  for (q in questions) {
    val rule = parseSelectionRule(q.markingCriteria)
    if (rule != null && rule.requiredAttempts < rule.totalInGroup) {
      val entry = grouped.getOrPut(rule.group) { rule to mutableListOf() }
      entry.second.add(q)
    } else if (!isQuestionUnattemptedForScoring(q)) {
      val maxM = (q.maximumMarks ?: 0.0).coerceAtLeast(0.0)
      val awarded = (q.awardedMarks ?: 0.0).coerceAtLeast(0.0)
      total += if (maxM > 0.0) awarded.coerceAtMost(maxM) else 0.0
    }
  }

  for ((_, pair) in grouped) {
    val (rule, groupQuestions) = pair
    val groupMax = groupQuestions
      .map { (it.maximumMarks ?: 0.0).coerceAtLeast(0.0) }
      .sortedDescending()
      .take(rule.requiredAttempts)
      .sum()
    val countedAwarded = groupQuestions
      .filter { !isQuestionUnattemptedForScoring(it) }
      .map { q ->
        val maxM = (q.maximumMarks ?: 0.0).coerceAtLeast(0.0)
        val aw = (q.awardedMarks ?: 0.0).coerceAtLeast(0.0)
        if (maxM > 0.0) aw.coerceAtMost(maxM) else 0.0
      }
      .sortedDescending()
      .take(rule.requiredAttempts)
      .sum()
    total += if (groupMax > 0.0) countedAwarded.coerceAtMost(groupMax) else countedAwarded
  }

  val capped = if (totalPossibleMarks != null && totalPossibleMarks > 0.0) {
    total.coerceAtMost(totalPossibleMarks)
  } else {
    total
  }
  return Math.round(capped.coerceAtLeast(0.0) * 100.0) / 100.0
}

/**
 * Validates a teacher mark edit on `questions[index]` before applying it.
 * Rejects negative marks, marks exceeding `maximumMarks`, positive marks on unattempted questions,
 * positive marks exceeding optional-group attempt limits, and edits that make the total exceed `totalPossibleMarks`.
 */
fun validateQuestionMarkEdit(
  questions: List<EvaluationQuestion>,
  index: Int,
  newAwardedMarks: Double?,
  totalPossibleMarks: Double?,
): MarkEditValidationResult {
  if (index !in questions.indices) {
    return MarkEditValidationResult.Invalid("Invalid question index.")
  }
  if (newAwardedMarks == null || newAwardedMarks.isNaN() || newAwardedMarks.isInfinite()) {
    return MarkEditValidationResult.Invalid("Please enter a valid numeric mark.")
  }

  val targetQuestion = questions[index]
  val qLabel = targetQuestion.questionNumber?.takeIf { it.isNotBlank() } ?: "${index + 1}"

  if (newAwardedMarks < 0.0) {
    return MarkEditValidationResult.Invalid("Marks for Q$qLabel cannot be negative.")
  }

  val maxMarks = targetQuestion.maximumMarks ?: 0.0
  if (maxMarks <= 0.0 && newAwardedMarks > 0.0) {
    return MarkEditValidationResult.Invalid(
      "Cannot award marks to Q$qLabel because maximum marks are not defined."
    )
  }
  if (maxMarks > 0.0 && newAwardedMarks > maxMarks + 1e-6) {
    return MarkEditValidationResult.Invalid(
      "Marks for Q$qLabel (${formatMarkForMessage(newAwardedMarks)}) cannot exceed its maximum of ${formatMarkForMessage(maxMarks)}."
    )
  }

  if (isQuestionUnattemptedForScoring(targetQuestion) && newAwardedMarks > 0.0) {
    return MarkEditValidationResult.Invalid(
      "Cannot award marks to Q$qLabel because no student answer was detected."
    )
  }

  val roundedAwarded = Math.round(newAwardedMarks * 100.0) / 100.0
  val updatedQuestions = questions.toMutableList()
  updatedQuestions[index] = targetQuestion.copy(awardedMarks = roundedAwarded)

  val previousAwarded = (targetQuestion.awardedMarks ?: 0.0).coerceAtLeast(0.0)
  val isIncreasingMark = roundedAwarded > previousAwarded + 1e-6

  // Check optional selection group limit if applicable
  val rule = parseSelectionRule(targetQuestion.markingCriteria)
  if (rule != null && rule.requiredAttempts < rule.totalInGroup && roundedAwarded > 0.0 && isIncreasingMark) {
    val groupQuestions = updatedQuestions.filter {
      parseSelectionRule(it.markingCriteria)?.group == rule.group
    }
    val positiveCount = groupQuestions.count { (it.awardedMarks ?: 0.0) > 0.0 }
    if (positiveCount > rule.requiredAttempts) {
      return MarkEditValidationResult.Invalid(
        "Only ${rule.requiredAttempts} of ${rule.totalInGroup} questions may be counted in this optional group. Set another question to 0 before awarding marks to Q$qLabel."
      )
    }
  }

  // Check raw sum of awarded marks against paper total (totalPossibleMarks)
  val rawAwardedSum = Math.round(
    updatedQuestions.sumOf { q ->
      if (isQuestionUnattemptedForScoring(q)) 0.0 else (q.awardedMarks ?: 0.0).coerceAtLeast(0.0)
    } * 100.0
  ) / 100.0

  if (
    totalPossibleMarks != null &&
    totalPossibleMarks > 0.0 &&
    rawAwardedSum > totalPossibleMarks + 1e-6 &&
    isIncreasingMark
  ) {
    return MarkEditValidationResult.Invalid(
      "Total marks (${formatMarkForMessage(rawAwardedSum)}) cannot exceed the configured paper total of ${formatMarkForMessage(totalPossibleMarks)}."
    )
  }

  val countedTotal = calculateCountedEvaluationTotalMarks(updatedQuestions, totalPossibleMarks)
  return MarkEditValidationResult.Valid(
    updatedQuestions = updatedQuestions,
    totalMarksObtained = countedTotal,
  )
}

/**
 * Validates the entire list of evaluation questions before saving teacher corrections.
 * Returns null if all per-question marks, optional group counts, and total marks are valid,
 * or a human-readable validation error message if invalid.
 */
fun validatePaperEvaluationForSave(
  questions: List<EvaluationQuestion>,
  totalPossibleMarks: Double?,
): String? {
  if (questions.isEmpty()) return "No evaluation questions to save."

  for ((idx, q) in questions.withIndex()) {
    val qLabel = q.questionNumber?.takeIf { it.isNotBlank() } ?: "${idx + 1}"
    val awarded = q.awardedMarks ?: 0.0
    if (awarded.isNaN() || awarded.isInfinite() || awarded < 0.0) {
      return "Marks for Q$qLabel cannot be negative or invalid."
    }
    val maxMarks = q.maximumMarks ?: 0.0
    if (maxMarks <= 0.0 && awarded > 0.0) {
      return "Cannot award marks to Q$qLabel because maximum marks are not defined."
    }
    if (maxMarks > 0.0 && awarded > maxMarks + 1e-6) {
      return "Marks for Q$qLabel (${formatMarkForMessage(awarded)}) cannot exceed its maximum of ${formatMarkForMessage(maxMarks)}."
    }
    if (isQuestionUnattemptedForScoring(q) && awarded > 0.0) {
      return "Cannot award marks to Q$qLabel because no student answer was detected."
    }
  }

  // Validate optional selection groups
  val grouped = LinkedHashMap<String, Pair<ParsedSelectionRule, MutableList<EvaluationQuestion>>>()
  for (q in questions) {
    val rule = parseSelectionRule(q.markingCriteria)
    if (rule != null && rule.requiredAttempts < rule.totalInGroup) {
      grouped.getOrPut(rule.group) { rule to mutableListOf() }.second.add(q)
    }
  }
  for ((_, pair) in grouped) {
    val (rule, groupQuestions) = pair
    val positiveCount = groupQuestions.count { (it.awardedMarks ?: 0.0) > 0.0 }
    if (positiveCount > rule.requiredAttempts) {
      return "Only ${rule.requiredAttempts} of ${rule.totalInGroup} questions may receive marks in optional group '${rule.group}' (currently $positiveCount questions have marks > 0)."
    }
  }

  val rawAwardedSum = Math.round(
    questions.sumOf { (it.awardedMarks ?: 0.0).coerceAtLeast(0.0) } * 100.0
  ) / 100.0

  if (totalPossibleMarks != null && totalPossibleMarks > 0.0 && rawAwardedSum > totalPossibleMarks + 1e-6) {
    return "Total marks (${formatMarkForMessage(rawAwardedSum)}) cannot exceed the configured paper total of ${formatMarkForMessage(totalPossibleMarks)}."
  }

  return null
}

/**
 * Calculates effective exam maximum marks from evaluation questions, accounting for
 * optional selection groups (e.g. "Attempt any 7 of 10") and internal OR choices (e.g. "Q2(a) OR Q2(b)").
 */
fun calculateEffectiveEvaluationQuestionTotalMarks(questions: List<EvaluationQuestion>): Double {
  if (questions.isEmpty()) return 0.0
  var total = 0.0
  val grouped = LinkedHashMap<String, Pair<ParsedSelectionRule, MutableList<Double>>>()

  for (q in questions) {
    val maxMarks = (q.maximumMarks ?: 0.0).coerceAtLeast(0.0)
    val rule = parseSelectionRule(q.markingCriteria)
    if (rule != null && rule.requiredAttempts < rule.totalInGroup) {
      val entry = grouped.getOrPut(rule.group) { rule to mutableListOf() }
      entry.second.add(maxMarks)
    } else if (q.studentAnswer?.trim().equals("[Optional question — not attempted]", ignoreCase = true)) {
      // Unattempted optional question without explicit tag does not add to required total marks
    } else {
      total += maxMarks
    }
  }

  for ((_, pair) in grouped) {
    val (rule, marksList) = pair
    val counted = marksList.sortedDescending().take(rule.requiredAttempts).sum()
    total += counted
  }

  return Math.round(total * 100.0) / 100.0
}

fun calculateEffectiveQuestionTotalMarks(questionRecords: List<QuestionDbRecord>): Double {
  if (questionRecords.isEmpty()) return 0.0
  return calculateEffectiveEvaluationQuestionTotalMarks(
    questionRecords.map { q ->
      EvaluationQuestion(
        id = q.id,
        evaluationId = q.evaluationId,
        questionNumber = q.questionNumber,
        questionText = q.questionText,
        maximumMarks = q.maximumMarks,
        studentAnswer = q.studentAnswer,
        expectedAnswer = q.expectedAnswer,
        markingCriteria = q.markingCriteria,
        awardedMarks = q.awardedMarks,
        feedback = q.feedback,
        reviewRequired = q.reviewRequired,
        reviewReason = q.reviewReason,
      )
    }
  )
}

@Serializable
data class EvaluationDbRecord(
  @SerialName("id") val id: String,
  @SerialName("paper_id") val paperId: String,
  @SerialName("student_id") val studentId: String,
  @SerialName("class_id") val classId: String,
  @SerialName("teacher_id") val teacherId: String,
  @SerialName("status") val status: String,
  @SerialName("total_marks_obtained") val totalMarksObtained: Double? = null,
  @SerialName("total_marks") val totalMarks: Double? = null,
  @SerialName("percentage") val percentage: Double? = null,
  @SerialName("review_required") val reviewRequired: Boolean = false,
  @SerialName("review_reason") val reviewReason: String? = null,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class QuestionDbRecord(
  @SerialName("id") val id: String? = null,
  @SerialName("evaluation_id") val evaluationId: String,
  @SerialName("question_number") val questionNumber: String? = null,
  @SerialName("question_text") val questionText: String? = null,
  @SerialName("maximum_marks") val maximumMarks: Double = 0.0,
  @SerialName("student_answer") val studentAnswer: String? = null,
  @SerialName("expected_answer") val expectedAnswer: String? = null,
  @SerialName("marking_criteria") val markingCriteria: String? = null,
  @SerialName("awarded_marks") val awardedMarks: Double = 0.0,
  @SerialName("feedback") val feedback: String? = null,
  @SerialName("review_required") val reviewRequired: Boolean = false,
  @SerialName("review_reason") val reviewReason: String? = null,
)


