package com.example.ui.paper

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.evaluationJsonParser
import com.example.data.remote.SupabaseClientProvider
import com.example.data.repository.ClassRepository
import com.example.data.repository.OcrRepository
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class StudentDetailUiState(
  val student: Student? = null,
  val classRoom: ClassRoom? = null,
  val papers: List<StudentPaper> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val isDeletingPaper: Boolean = false,
  val deletePaperError: String? = null,
  val feedbackMessage: String? = null,
  val signedUrls: Map<String, String> = emptyMap(),
  val ocrTextByPageId: Map<String, String> = emptyMap(),
  val ocrLoadingPageIds: Set<String> = emptySet(),
  val ocrErrorByPageId: Map<String, String> = emptyMap(),
  val isEvaluatingPaperId: String? = null,
  val evaluatedPaperId: String? = null,
  val evaluationResult: AiEvaluationResponse? = null,
  val evaluationResultJson: String? = null,
  val evaluationError: String? = null,
)

class StudentDetailViewModel(
  private val studentRepository: StudentRepository,
  private val classRepository: ClassRepository,
  private val paperRepository: PaperRepository,
  private val ocrRepository: OcrRepository? = null,
) : ViewModel() {

  private val evaluationHttpClient = OkHttpClient.Builder()
    .connectTimeout(180, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .build()

  private val _uiState = MutableStateFlow(StudentDetailUiState())
  val uiState: StateFlow<StudentDetailUiState> = _uiState.asStateFlow()

  fun loadStudentAndPapers(studentId: String) {
    if (studentId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }

      // 1. Fetch student info
      val studentResult = studentRepository.getStudentById(studentId)
      val student = studentResult.getOrNull()

      // 2. Fetch class info
      val classRoom = if (student != null) {
        classRepository.getClassById(student.classId).getOrNull()
      } else null

      // 3. Fetch papers
      val papersResult = paperRepository.getPapersByStudentId(studentId)
      papersResult.fold(
        onSuccess = { papersList ->
          _uiState.update {
            it.copy(
              student = student ?: it.student,
              classRoom = classRoom ?: it.classRoom,
              papers = papersList,
              isLoading = false,
              errorMessage = null,
            )
          }

          // Preload first page signed URLs for thumbnails
          papersList.forEach { paper ->
            paper.pages.firstOrNull()?.let { firstPage ->
              loadSignedUrlForPage(firstPage.storagePath)
            }
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              student = student ?: it.student,
              classRoom = classRoom ?: it.classRoom,
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to load papers.",
            )
          }
        }
      )
    }
  }

  fun loadSignedUrlForPage(storagePath: String) {
    if (_uiState.value.signedUrls.containsKey(storagePath)) return
    viewModelScope.launch {
      val result = paperRepository.getSignedPageUrl(storagePath)
      result.onSuccess { url ->
        _uiState.update {
          it.copy(signedUrls = it.signedUrls + (storagePath to url))
        }
      }
    }
  }

  fun deletePaper(paper: StudentPaper, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _uiState.update { it.copy(isDeletingPaper = true, deletePaperError = null) }
      val result = paperRepository.deletePaper(paper)
      result.fold(
        onSuccess = {
          _uiState.update {
            it.copy(
              papers = it.papers.filter { p -> p.id != paper.id },
              isDeletingPaper = false,
              deletePaperError = null,
              feedbackMessage = "Paper submission deleted.",
            )
          }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isDeletingPaper = false,
              deletePaperError = error.localizedMessage ?: "Failed to delete paper.",
            )
          }
        }
      )
    }
  }

  fun dismissFeedbackMessage() {
    _uiState.update { it.copy(feedbackMessage = null) }
  }

  fun dismissDeletePaperError() {
    _uiState.update { it.copy(deletePaperError = null) }
  }

  fun runOcrForPage(paperId: String, pageId: String) {
    if (pageId.isBlank() || paperId.isBlank()) return

    if (ocrRepository == null) {
      _uiState.update {
        it.copy(
          ocrErrorByPageId = it.ocrErrorByPageId + (pageId to "OCR service is not initialized.")
        )
      }
      return
    }

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          ocrLoadingPageIds = it.ocrLoadingPageIds + pageId,
          ocrErrorByPageId = it.ocrErrorByPageId - pageId,
        )
      }

      val result = ocrRepository.processPageOcr(paperId = paperId, pageId = pageId)
      result.fold(
        onSuccess = { text ->
          _uiState.update {
            it.copy(
              ocrLoadingPageIds = it.ocrLoadingPageIds - pageId,
              ocrTextByPageId = it.ocrTextByPageId + (pageId to text),
              feedbackMessage = "Handwriting transcribed successfully.",
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              ocrLoadingPageIds = it.ocrLoadingPageIds - pageId,
              ocrErrorByPageId = it.ocrErrorByPageId + (pageId to (error.localizedMessage ?: "OCR processing failed.")),
            )
          }
        }
      )
    }
  }

  fun clearOcrError(pageId: String) {
    _uiState.update { it.copy(ocrErrorByPageId = it.ocrErrorByPageId - pageId) }
  }

  fun runAiEvaluation(paperId: String) {
    if (paperId.isBlank()) return

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isEvaluatingPaperId = paperId,
          evaluatedPaperId = paperId,
          evaluationResultJson = null,
          evaluationError = null,
        )
      }

      val result = withContext(Dispatchers.IO) {
        try {
          if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.failure<String>(
              IllegalStateException("Supabase is not configured yet.")
            )
          }

          val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
          val accessToken = session?.accessToken
          if (accessToken.isNullOrBlank()) {
            return@withContext Result.failure<String>(
              IllegalStateException("Teacher is not authenticated. Please log in again.")
            )
          }

          val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
          val endpoint = "$baseUrl/functions/v1/process-and-grade-paper"
          val jsonBody = """{"paperId":"$paperId"}"""
          val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

          val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()

          val response = evaluationHttpClient.newCall(request).execute()
          val responseBody = response.body?.string().orEmpty()

          if (!response.isSuccessful) {
            val errorMsg = "HTTP ${response.code}:\n$responseBody"
            Log.e("StudentDetailViewModel", "Evaluation failed with $errorMsg")
            Result.failure(Exception(errorMsg))
          } else {
            Result.success(responseBody)
          }
        } catch (e: Exception) {
          Log.e("StudentDetailViewModel", "Error calling process-and-grade-paper", e)
          Result.failure(e)
        }
      }

      result.fold(
        onSuccess = { jsonText ->
          val parsedResponse = try {
            evaluationJsonParser.decodeFromString<AiEvaluationResponse>(jsonText)
          } catch (e: Exception) {
            Log.e("StudentDetailViewModel", "Error parsing evaluation response JSON", e)
            null
          }

          val isFailedStatus = parsedResponse?.status == "failed"
          val errorText = if (isFailedStatus) {
            parsedResponse?.error ?: "AI evaluation failed."
          } else null

          _uiState.update {
            it.copy(
              isEvaluatingPaperId = null,
              evaluationResult = if (!isFailedStatus) parsedResponse else null,
              evaluationResultJson = jsonText,
              evaluationError = errorText,
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isEvaluatingPaperId = null,
              evaluationError = error.localizedMessage ?: "Unknown network error during evaluation.",
              evaluationResult = null,
              evaluationResultJson = null,
            )
          }
        }
      )
    }
  }

  fun dismissEvaluationDialog() {
    _uiState.update {
      it.copy(
        evaluationResult = null,
        evaluationResultJson = null,
        evaluationError = null,
        evaluatedPaperId = null,
      )
    }
  }
}
