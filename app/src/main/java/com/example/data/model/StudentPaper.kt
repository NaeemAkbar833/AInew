package com.example.data.model

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudentPaper(
  @SerialName("id")
  val id: String? = null,
  @SerialName("student_id")
  val studentId: String,
  @SerialName("class_id")
  val classId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("total_pages")
  val totalPages: Int,
  @SerialName("status")
  val status: String = "scanned",
  @SerialName("created_at")
  val createdAt: String? = null,
  val pages: List<StudentPaperPage> = emptyList(),
)

@Serializable
data class StudentPaperPage(
  @SerialName("id")
  val id: String? = null,
  @SerialName("paper_id")
  val paperId: String,
  @SerialName("student_id")
  val studentId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("page_number")
  val pageNumber: Int,
  @SerialName("storage_path")
  val storagePath: String,
  @SerialName("created_at")
  val createdAt: String? = null,
)

@Serializable
data class CreatePaperRequest(
  @SerialName("student_id")
  val studentId: String,
  @SerialName("class_id")
  val classId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("total_pages")
  val totalPages: Int,
  @SerialName("status")
  val status: String = "scanned",
)

@Serializable
data class CreatePaperPageRequest(
  @SerialName("paper_id")
  val paperId: String,
  @SerialName("student_id")
  val studentId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("page_number")
  val pageNumber: Int,
  @SerialName("storage_path")
  val storagePath: String,
)

data class LocalScannedPage(
  val id: String,
  val uri: Uri,
  val pageNumber: Int,
)
