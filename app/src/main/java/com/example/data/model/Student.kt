package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Student(
  @SerialName("id")
  val id: String? = null,
  @SerialName("class_id")
  val classId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("name")
  val name: String,
  @SerialName("father_name")
  val fatherName: String,
  @SerialName("roll_number")
  val rollNumber: String,
  @SerialName("created_at")
  val createdAt: String? = null,
)

@Serializable
data class CreateStudentRequest(
  @SerialName("class_id")
  val classId: String,
  @SerialName("teacher_id")
  val teacherId: String,
  @SerialName("name")
  val name: String,
  @SerialName("father_name")
  val fatherName: String,
  @SerialName("roll_number")
  val rollNumber: String,
)
