package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
