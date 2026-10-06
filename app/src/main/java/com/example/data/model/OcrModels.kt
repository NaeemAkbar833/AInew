package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessOcrRequest(
  @SerialName("paperId")
  val paperId: String,
  @SerialName("pageId")
  val pageId: String,
)

@Serializable
data class ProcessOcrResponse(
  @SerialName("pageId")
  val pageId: String? = null,
  @SerialName("status")
  val status: String,
  @SerialName("ocrText")
  val ocrText: String? = null,
  @SerialName("error")
  val error: String? = null,
)
