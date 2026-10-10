package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtractedStudent(
    @SerialName("roll_number")
    val rollNumber: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("father_name")
    val fatherName: String = "",
    @SerialName("is_unclear")
    val isUnclear: Boolean = false,
    @SerialName("review_reason")
    val reviewReason: String? = null
)

@Serializable
data class ExtractStudentListRequest(
    @SerialName("class_id")
    val classId: String,
    @SerialName("images")
    val images: List<String>
)

@Serializable
data class ExtractStudentListResponse(
    @SerialName("students")
    val students: List<ExtractedStudent> = emptyList(),
    @SerialName("error")
    val error: String? = null
)
