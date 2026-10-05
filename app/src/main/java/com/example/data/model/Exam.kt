package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExamType(val displayName: String) {
  @SerialName("Mid Term")
  MID_TERM("Mid Term"),

  @SerialName("Final")
  FINAL("Final"),

  @SerialName("Supply")
  SUPPLY("Supply");

  companion object {
    fun fromDisplayName(name: String): ExamType {
      return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: MID_TERM
    }
  }
}

@Serializable
data class Exam(
  @SerialName("id")
  val id: String? = null,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("name")
  val name: String,
  @SerialName("type")
  val type: String,
  @SerialName("date")
  val date: String,
  @SerialName("created_at")
  val createdAt: String? = null,
)

@Serializable
data class CreateExamRequest(
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("name")
  val name: String,
  @SerialName("type")
  val type: String,
  @SerialName("date")
  val date: String,
)
