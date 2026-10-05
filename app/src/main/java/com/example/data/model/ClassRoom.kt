package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClassTiming(val displayName: String) {
  @SerialName("Morning")
  MORNING("Morning"),

  @SerialName("Evening")
  EVENING("Evening");

  companion object {
    fun fromDisplayName(name: String): ClassTiming {
      return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: MORNING
    }
  }
}

@Serializable
data class ClassRoom(
  @SerialName("id")
  val id: String? = null,
  @SerialName("exam_id")
  val examId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("session")
  val session: String,
  @SerialName("semester")
  val semester: String,
  @SerialName("timing")
  val timing: String,
  @SerialName("subject_name")
  val subjectName: String,
  @SerialName("total_marks")
  val totalMarks: Int,
  @SerialName("question_paper_path")
  val questionPaperPath: String? = null,
  @SerialName("created_at")
  val createdAt: String? = null,
)

@Serializable
data class CreateClassRequest(
  @SerialName("exam_id")
  val examId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("session")
  val session: String,
  @SerialName("semester")
  val semester: String,
  @SerialName("timing")
  val timing: String,
  @SerialName("subject_name")
  val subjectName: String,
  @SerialName("total_marks")
  val totalMarks: Int,
  @SerialName("question_paper_path")
  val questionPaperPath: String? = null,
)
