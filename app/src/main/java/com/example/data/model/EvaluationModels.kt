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
      val finalEvaluation = if (finalPaperId != decoded.evaluation.paperId) {
        decoded.evaluation.copy(paperId = finalPaperId)
      } else decoded.evaluation
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

    val questions = questionsJsonArray.mapNotNull { qElem ->
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

    val detail = EvaluationDetail(
      id = id,
      paperId = paperId,
      studentId = studentId,
      classId = classId,
      teacherId = teacherId,
      status = evalStatus,
      totalMarksObtained = totalMarksObtained,
      totalMarks = totalMarks,
      extractedQuestionTotalMarks = extractedQuestionTotalMarks,
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

