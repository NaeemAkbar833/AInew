package com.example.ui.paper

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.QuestionDbRecord
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.evaluationJsonParser
import com.example.data.model.parseAiEvaluationResponse
import com.example.data.remote.SupabaseClientProvider
import com.example.data.repository.ClassRepository
import com.example.data.repository.OcrRepository
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
  val isApproving: Boolean = false,
  val approvalSuccess: Boolean = false,
  val approvalError: String? = null,
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

  private var evaluationJob: Job? = null

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

  private suspend fun loadExistingEvaluationForPaper(paperId: String): AiEvaluationResponse? =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) return@withContext null
        val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
        if (session?.accessToken.isNullOrBlank()) return@withContext null

        val evalRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
          .select {
            filter {
              eq("paper_id", paperId)
            }
          }
          .decodeList<EvaluationDbRecord>()

        val evalRecord = evalRecords.firstOrNull() ?: return@withContext null

        val questionRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluation_questions"]
          .select {
            filter {
              eq("evaluation_id", evalRecord.id)
            }
          }
          .decodeList<QuestionDbRecord>()

        val evaluationDetail = EvaluationDetail(
          id = evalRecord.id,
          paperId = evalRecord.paperId,
          studentId = evalRecord.studentId,
          classId = evalRecord.classId,
          teacherId = evalRecord.teacherId,
          status = evalRecord.status,
          totalMarksObtained = evalRecord.totalMarksObtained,
          totalMarks = evalRecord.totalMarks,
          extractedQuestionTotalMarks = questionRecords.sumOf { it.maximumMarks },
          percentage = evalRecord.percentage,
          percentageAvailable = evalRecord.percentage != null,
          reviewRequired = evalRecord.reviewRequired,
          reviewReason = evalRecord.reviewReason,
          createdAt = evalRecord.createdAt,
          updatedAt = evalRecord.updatedAt,
          questions = questionRecords.map { q ->
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

        AiEvaluationResponse(
          status = evalRecord.status,
          percentageAvailable = evaluationDetail.percentageAvailable,
          evaluation = evaluationDetail,
        )
      } catch (e: Exception) {
        Log.e("StudentDetailViewModel", "Error loading existing evaluation for paper $paperId", e)
        null
      }
    }

  fun runAiEvaluation(paperId: String) {
    if (paperId.isBlank()) return
    if (_uiState.value.isEvaluatingPaperId == paperId) return

    evaluationJob?.cancel()
    evaluationJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isEvaluatingPaperId = paperId,
          evaluatedPaperId = paperId,
          evaluationResultJson = null,
          evaluationError = null,
        )
      }

      // 1. Check if an evaluation already exists in database for this paper
      val existingEval = loadExistingEvaluationForPaper(paperId)
      if (existingEval != null && existingEval.evaluation != null) {
        val evalStatus = existingEval.evaluation.status
        if (evalStatus == "approved" || evalStatus == "completed" || evalStatus == "needs_review") {
          Log.i("StudentDetailViewModel", "Found existing evaluation for paper $paperId with status $evalStatus. Skipping AI grading pipeline.")
          val msg = if (evalStatus == "approved") {
            "Loaded existing approved evaluation."
          } else {
            "Loaded existing evaluation."
          }
          _uiState.update {
            it.copy(
              isEvaluatingPaperId = null,
              evaluationResult = existingEval,
              evaluationResultJson = null,
              evaluationError = null,
              feedbackMessage = msg,
            )
          }
          return@launch
        }
      }

      // 2. Otherwise, run AI grading pipeline
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
          val parsedResponse = parseAiEvaluationResponse(jsonText, fallbackPaperId = paperId)
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

          // Refresh papers list to show updated OCR status/results on the cards
          _uiState.value.student?.id?.let { sid ->
            loadStudentAndPapers(sid)
          }
        },
        onFailure = { error ->
          val errMessage = error.localizedMessage ?: error.message.orEmpty()
          val isConflict409 = errMessage.contains("409", ignoreCase = true) ||
            errMessage.contains("already finalized", ignoreCase = true) ||
            errMessage.contains("already approved", ignoreCase = true)

          if (isConflict409) {
            val fallbackEval = loadExistingEvaluationForPaper(paperId)
            if (fallbackEval != null && fallbackEval.evaluation != null) {
              _uiState.update {
                it.copy(
                  isEvaluatingPaperId = null,
                  evaluationResult = fallbackEval,
                  evaluationResultJson = null,
                  evaluationError = null,
                  feedbackMessage = "This paper has already been approved. Loading the saved result...",
                )
              }
              return@launch
            }
          }

          _uiState.update {
            it.copy(
              isEvaluatingPaperId = null,
              evaluationError = error.localizedMessage ?: "Unknown network error during evaluation.",
              evaluationResult = null,
              evaluationResultJson = null,
            )
          }

          // Refresh papers list to show updated OCR status/results on the cards
          _uiState.value.student?.id?.let { sid ->
            loadStudentAndPapers(sid)
          }
        }
      )
    }
  }

  fun stopAiEvaluation(paperId: String) {
    if (_uiState.value.isEvaluatingPaperId == paperId) {
      evaluationJob?.cancel()
      evaluationJob = null
      _uiState.update { it.copy(isEvaluatingPaperId = null) }
      // Refresh to get latest paper status from DB (it might still be 'processing' on server, but UI should be idle)
      _uiState.value.student?.id?.let { loadStudentAndPapers(it) }
    }
  }

  fun approveEvaluation(evaluationId: String, paperId: String) {
    if (evaluationId.isBlank() || paperId.isBlank()) return

    val currentEval = _uiState.value.evaluationResult?.evaluation
    if (currentEval?.status == "approved") {
      _uiState.update {
        it.copy(feedbackMessage = "This result has already been approved.")
      }
      return
    }

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isApproving = true,
          approvalError = null,
        )
      }

      val result = withContext(Dispatchers.IO) {
        try {
          if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.failure<Unit>(
              IllegalStateException("Supabase is not configured yet.")
            )
          }

          val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
          val accessToken = session?.accessToken
          if (accessToken.isNullOrBlank()) {
            return@withContext Result.failure<Unit>(
              IllegalStateException("Teacher is not authenticated. Please log in again.")
            )
          }

          val params = buildJsonObject {
            put("p_evaluation_id", evaluationId)
            put("p_paper_id", paperId)
          }

          SupabaseClientProvider.client.postgrest.rpc(
            function = "approve_paper_evaluation",
            parameters = params,
          )

          Result.success(Unit)
        } catch (e: Exception) {
          Log.e("StudentDetailViewModel", "Error approving evaluation", e)
          Result.failure(e)
        }
      }

      result.fold(
        onSuccess = {
          _uiState.update { state ->
            val updatedDetail = state.evaluationResult?.evaluation?.copy(
              status = "approved",
              reviewRequired = false,
            )
            val updatedResponse = state.evaluationResult?.copy(
              status = "approved",
              evaluation = updatedDetail,
            )
            state.copy(
              isApproving = false,
              approvalSuccess = true,
              approvalError = null,
              evaluationResult = updatedResponse,
              feedbackMessage = "Result approved and saved successfully.",
            )
          }
        },
        onFailure = { error ->
          val rawMessage = error.localizedMessage ?: error.message.orEmpty()
          val isAlreadyApproved = rawMessage.contains("already approved", ignoreCase = true) ||
            rawMessage.contains("cannot be modified or re-approved", ignoreCase = true)

          if (isAlreadyApproved) {
            _uiState.update { state ->
              val updatedDetail = state.evaluationResult?.evaluation?.copy(
                status = "approved",
                reviewRequired = false,
              )
              val updatedResponse = state.evaluationResult?.copy(
                status = "approved",
                evaluation = updatedDetail,
              )
              state.copy(
                isApproving = false,
                approvalSuccess = true,
                approvalError = null,
                evaluationResult = updatedResponse,
                feedbackMessage = "This result has already been approved.",
              )
            }
          } else {
            val userFriendlyError = when {
              rawMessage.contains("Unauthorized", ignoreCase = true) ->
                "You are not authorized to approve this evaluation."
              rawMessage.contains("not ready for approval", ignoreCase = true) ->
                "Evaluation is not ready for approval."
              rawMessage.contains("Evaluation not found", ignoreCase = true) ->
                "Evaluation record was not found."
              rawMessage.isNotBlank() ->
                "Approval failed: ${rawMessage.substringBefore("\n")}"
              else ->
                "Approval failed. Please try again."
            }

            _uiState.update { state ->
              state.copy(
                isApproving = false,
                approvalSuccess = false,
                approvalError = userFriendlyError,
              )
            }
          }
        }
      )
    }
  }

  fun clearApprovalError() {
    _uiState.update { it.copy(approvalError = null) }
  }

  fun dismissEvaluationDialog() {
    _uiState.update {
      it.copy(
        evaluationResult = null,
        evaluationResultJson = null,
        evaluationError = null,
        evaluatedPaperId = null,
        isApproving = false,
        approvalSuccess = false,
        approvalError = null,
      )
    }
  }
}
