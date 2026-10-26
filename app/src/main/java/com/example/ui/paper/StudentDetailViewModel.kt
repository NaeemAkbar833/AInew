package com.example.ui.paper

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.QuestionDbRecord
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.data.model.EvaluationStatus
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.MarkEditValidationResult
import com.example.data.model.calculateCountedEvaluationTotalMarks
import com.example.data.model.calculateEffectiveEvaluationQuestionTotalMarks
import com.example.data.model.calculateEffectiveQuestionTotalMarks
import com.example.data.model.evaluationJsonParser
import com.example.data.model.parseAiEvaluationResponse
import com.example.data.model.sortEvaluationQuestionsNumerically
import com.example.data.model.validatePaperEvaluationForSave
import com.example.data.model.validateQuestionMarkEdit
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
  val isSavingCorrections: Boolean = false,
  val saveCorrectionsSuccess: Boolean = false,
  val saveCorrectionsError: String? = null,
  val editedQuestions: List<EvaluationQuestion>? = null,
  val pendingReviewNavigationPaperId: String? = null,
)

class StudentDetailViewModel(
  private val studentRepository: StudentRepository,
  private val classRepository: ClassRepository,
  private val paperRepository: PaperRepository,
  private val ocrRepository: OcrRepository? = null,
  private val approveEvaluationAction: (suspend (evaluationId: String, paperId: String) -> Result<Unit>)? = null,
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
      val paperId = paper.id
      if (!paperId.isNullOrBlank() && _uiState.value.isEvaluatingPaperId == paperId) {
        evaluationJob?.cancel()
        evaluationJob = null
      }
      _uiState.update { it.copy(isDeletingPaper = true, deletePaperError = null) }
      val result = paperRepository.deletePaper(paper)
      result.fold(
        onSuccess = {
          val deletedPaths = paper.pages.map { it.storagePath }.toSet()
          val deletedPageIds = paper.pages.mapNotNull { it.id }.toSet()
          _uiState.update { state ->
            val shouldClearEvaluation = !paper.id.isNullOrBlank() && state.evaluatedPaperId == paper.id
            val shouldClearEvaluating = !paper.id.isNullOrBlank() && state.isEvaluatingPaperId == paper.id
            state.copy(
              papers = state.papers.filter { p -> p.id != paper.id },
              signedUrls = state.signedUrls - deletedPaths,
              ocrLoadingPageIds = state.ocrLoadingPageIds - deletedPageIds,
              ocrTextByPageId = state.ocrTextByPageId - deletedPageIds,
              ocrErrorByPageId = state.ocrErrorByPageId - deletedPageIds,
              isEvaluatingPaperId = if (shouldClearEvaluating) null else state.isEvaluatingPaperId,
              evaluatedPaperId = if (shouldClearEvaluation) null else state.evaluatedPaperId,
              evaluationResult = if (shouldClearEvaluation) null else state.evaluationResult,
              evaluationResultJson = if (shouldClearEvaluation) null else state.evaluationResultJson,
              evaluationError = if (shouldClearEvaluation) null else state.evaluationError,
              editedQuestions = if (shouldClearEvaluation) null else state.editedQuestions,
              pendingReviewNavigationPaperId = if (state.pendingReviewNavigationPaperId == paper.id) null else state.pendingReviewNavigationPaperId,
              isApproving = if (shouldClearEvaluation) false else state.isApproving,
              approvalSuccess = if (shouldClearEvaluation) false else state.approvalSuccess,
              approvalError = if (shouldClearEvaluation) null else state.approvalError,
              isSavingCorrections = if (shouldClearEvaluation) false else state.isSavingCorrections,
              saveCorrectionsSuccess = if (shouldClearEvaluation) false else state.saveCorrectionsSuccess,
              saveCorrectionsError = if (shouldClearEvaluation) null else state.saveCorrectionsError,
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

  fun deletePaperPage(page: com.example.data.model.StudentPaperPage, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      if (page.paperId.isNotBlank() && _uiState.value.isEvaluatingPaperId == page.paperId) {
        evaluationJob?.cancel()
        evaluationJob = null
      }
      _uiState.update { it.copy(isDeletingPaper = true, deletePaperError = null) }
      val result = paperRepository.deletePaperPage(page)
      result.fold(
        onSuccess = {
          _uiState.update { state ->
            val updatedPapers = state.papers.mapNotNull { p ->
              if (p.id != page.paperId) {
                p
              } else {
                val remaining = p.pages
                  .filterNot {
                    (!page.id.isNullOrBlank() && it.id == page.id) ||
                      (page.id.isNullOrBlank() && it.pageNumber == page.pageNumber)
                  }
                  .sortedBy { it.pageNumber }
                  .mapIndexed { index, remainingPage ->
                    remainingPage.copy(pageNumber = index + 1)
                  }
                if (remaining.isEmpty()) {
                  null
                } else {
                  p.copy(
                    totalPages = remaining.size,
                    status = "scanned",
                    ocrStatus = "pending",
                    combinedOcrText = null,
                    ocrError = null,
                    evaluation = null,
                    pages = remaining,
                  )
                }
              }
            }
            val shouldClearEvaluation = state.evaluatedPaperId == page.paperId
            val shouldClearEvaluating = state.isEvaluatingPaperId == page.paperId
            state.copy(
              papers = updatedPapers,
              signedUrls = state.signedUrls - page.storagePath,
              ocrLoadingPageIds = if (!page.id.isNullOrBlank()) state.ocrLoadingPageIds - page.id else state.ocrLoadingPageIds,
              ocrTextByPageId = if (!page.id.isNullOrBlank()) state.ocrTextByPageId - page.id else state.ocrTextByPageId,
              ocrErrorByPageId = if (!page.id.isNullOrBlank()) state.ocrErrorByPageId - page.id else state.ocrErrorByPageId,
              isEvaluatingPaperId = if (shouldClearEvaluating) null else state.isEvaluatingPaperId,
              evaluatedPaperId = if (shouldClearEvaluation) null else state.evaluatedPaperId,
              evaluationResult = if (shouldClearEvaluation) null else state.evaluationResult,
              evaluationResultJson = if (shouldClearEvaluation) null else state.evaluationResultJson,
              evaluationError = if (shouldClearEvaluation) null else state.evaluationError,
              editedQuestions = if (shouldClearEvaluation) null else state.editedQuestions,
              pendingReviewNavigationPaperId = if (state.pendingReviewNavigationPaperId == page.paperId) null else state.pendingReviewNavigationPaperId,
              isApproving = if (shouldClearEvaluation) false else state.isApproving,
              approvalSuccess = if (shouldClearEvaluation) false else state.approvalSuccess,
              approvalError = if (shouldClearEvaluation) null else state.approvalError,
              isSavingCorrections = if (shouldClearEvaluation) false else state.isSavingCorrections,
              saveCorrectionsSuccess = if (shouldClearEvaluation) false else state.saveCorrectionsSuccess,
              saveCorrectionsError = if (shouldClearEvaluation) null else state.saveCorrectionsError,
              isDeletingPaper = false,
              deletePaperError = null,
              feedbackMessage = "Page deleted.",
            )
          }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isDeletingPaper = false,
              deletePaperError = error.localizedMessage ?: "Failed to delete page.",
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
          extractedQuestionTotalMarks = calculateEffectiveQuestionTotalMarks(questionRecords),
          percentage = evalRecord.percentage,
          percentageAvailable = evalRecord.percentage != null,
          reviewRequired = evalRecord.reviewRequired,
          reviewReason = evalRecord.reviewReason,
          createdAt = evalRecord.createdAt,
          updatedAt = evalRecord.updatedAt,
          questions = sortEvaluationQuestionsNumerically(
            questionRecords.map { q ->
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

  fun consumeReviewNavigation() {
    _uiState.update { it.copy(pendingReviewNavigationPaperId = null) }
  }

  internal fun setEvaluationResultForTesting(paperId: String, response: AiEvaluationResponse?) {
    val sortedResponse = response?.let { resp ->
      val eval = resp.evaluation
      if (eval != null) {
        resp.copy(
          evaluation = eval.copy(
            questions = sortEvaluationQuestionsNumerically(eval.questions)
          )
        )
      } else {
        resp
      }
    }
    _uiState.update {
      it.copy(
        evaluatedPaperId = paperId,
        evaluationResult = sortedResponse,
        editedQuestions = null,
        saveCorrectionsError = null,
      )
    }
  }

  fun openApprovedEvaluation(paperId: String) {
    if (paperId.isBlank()) return
    val currentState = _uiState.value
    val currentEval = currentState.evaluationResult?.evaluation
    val isAlreadyLoadedApproved = currentState.evaluatedPaperId == paperId &&
      currentEval != null &&
      (currentEval.status.equals("approved", ignoreCase = true) ||
        currentState.evaluationResult.status.equals("approved", ignoreCase = true))

    if (isAlreadyLoadedApproved) {
      _uiState.update {
        it.copy(
          isEvaluatingPaperId = null,
          evaluationError = null,
          editedQuestions = null,
          feedbackMessage = null,
        )
      }
      return
    }

    evaluationJob?.cancel()
    evaluationJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isEvaluatingPaperId = paperId,
          evaluatedPaperId = paperId,
          evaluationResult = if (it.evaluatedPaperId == paperId) it.evaluationResult else null,
          evaluationResultJson = null,
          evaluationError = null,
          editedQuestions = null,
          feedbackMessage = null,
        )
      }

      val existingEval = loadExistingEvaluationForPaper(paperId)
      if (existingEval != null && existingEval.evaluation != null) {
        val approvedDetail = existingEval.evaluation.copy(
          status = "approved",
          reviewRequired = false,
        )
        val approvedResponse = existingEval.copy(
          status = "approved",
          evaluation = approvedDetail,
        )
        _uiState.update {
          it.copy(
            isEvaluatingPaperId = null,
            evaluatedPaperId = paperId,
            evaluationResult = approvedResponse,
            evaluationResultJson = null,
            evaluationError = null,
            editedQuestions = null,
            feedbackMessage = null,
          )
        }
      } else {
        _uiState.update {
          it.copy(
            isEvaluatingPaperId = null,
            evaluationError = "Unable to load the saved approved evaluation.",
          )
        }
      }
    }
  }

  fun runAiEvaluation(paperId: String) {
    if (paperId.isBlank()) return
    if (_uiState.value.isEvaluatingPaperId == paperId) return

    val currentState = _uiState.value
    val knownPaper = currentState.papers.find { it.id == paperId }
    val isKnownApproved = knownPaper?.status.equals("approved", ignoreCase = true) ||
      knownPaper?.evaluation?.status.equals("approved", ignoreCase = true) ||
      (currentState.evaluatedPaperId == paperId && (
        currentState.evaluationResult?.status.equals("approved", ignoreCase = true) ||
          currentState.evaluationResult?.evaluation?.status.equals("approved", ignoreCase = true)
        ))

    if (isKnownApproved) {
      openApprovedEvaluation(paperId)
      _uiState.update { it.copy(pendingReviewNavigationPaperId = paperId) }
      return
    }

    // Preserve in-memory evaluation and unsaved teacher edits if already loaded for this paper
    if (currentState.evaluatedPaperId == paperId &&
      currentState.evaluationResult?.evaluation != null &&
      currentState.evaluationError == null
    ) {
      _uiState.update {
        it.copy(
          isEvaluatingPaperId = null,
          pendingReviewNavigationPaperId = paperId,
        )
      }
      return
    }

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
        val evalStatus = existingEval.evaluation.status.orEmpty()
        if (evalStatus.equals("approved", ignoreCase = true) ||
          evalStatus.equals("completed", ignoreCase = true) ||
          evalStatus.equals("needs_review", ignoreCase = true)
        ) {
          Log.i("StudentDetailViewModel", "Found existing evaluation for paper $paperId with status $evalStatus. Skipping AI grading pipeline.")
          _uiState.update {
            it.copy(
              isEvaluatingPaperId = null,
              evaluatedPaperId = paperId,
              evaluationResult = existingEval,
              evaluationResultJson = null,
              evaluationError = null,
              editedQuestions = if (evalStatus.equals("approved", ignoreCase = true)) null else it.editedQuestions,
              pendingReviewNavigationPaperId = paperId,
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
              evaluatedPaperId = paperId,
              evaluationResult = if (!isFailedStatus) parsedResponse else null,
              evaluationResultJson = jsonText,
              evaluationError = errorText,
              editedQuestions = null,
              pendingReviewNavigationPaperId = if (!isFailedStatus && parsedResponse != null) paperId else null,
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
                  evaluatedPaperId = paperId,
                  evaluationResult = fallbackEval,
                  evaluationResultJson = null,
                  evaluationError = null,
                  editedQuestions = null,
                  pendingReviewNavigationPaperId = paperId,
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

    val state = _uiState.value
    val currentEval = state.evaluationResult?.evaluation
    val isAlreadyApprovedState = currentEval?.status.equals("approved", ignoreCase = true) ||
      state.evaluationResult?.status.equals("approved", ignoreCase = true)
    if (isAlreadyApprovedState) {
      _uiState.update {
        it.copy(feedbackMessage = "This result has already been approved.")
      }
      return
    }

    if (state.isSavingCorrections) {
      _uiState.update {
        it.copy(
          isApproving = false,
          approvalSuccess = false,
          approvalError = "Please wait for edited marks and feedback to finish saving before approving.",
        )
      }
      return
    }

    val rawQuestions = state.editedQuestions ?: currentEval?.questions.orEmpty()
    val questions = sortEvaluationQuestionsNumerically(rawQuestions)
    val totalPossibleMarks = state.classRoom?.totalMarks?.toDouble()
      ?: currentEval?.totalMarks
      ?: calculateEffectiveEvaluationQuestionTotalMarks(questions)

    val evalTotalObtained = currentEval?.totalMarksObtained
    val summaryTotalError = if (
      evalTotalObtained != null &&
      (evalTotalObtained.isNaN() || evalTotalObtained.isInfinite() || evalTotalObtained < 0.0)
    ) {
      "Total marks obtained cannot be negative or invalid."
    } else if (
      evalTotalObtained != null &&
      totalPossibleMarks > 0.0 &&
      evalTotalObtained > totalPossibleMarks + 1e-6
    ) {
      "Total marks (${evalTotalObtained}) cannot exceed the configured paper total of ${totalPossibleMarks}."
    } else {
      null
    }

    val validationError = state.saveCorrectionsError
      ?: validatePaperEvaluationForSave(questions, totalPossibleMarks)
      ?: summaryTotalError
    if (validationError != null) {
      _uiState.update {
        it.copy(
          isApproving = false,
          approvalSuccess = false,
          approvalError = validationError,
        )
      }
      return
    }

    if (state.editedQuestions != null) {
      _uiState.update {
        it.copy(
          isApproving = false,
          approvalSuccess = false,
          approvalError = "Please save your edited marks and feedback before approving the result.",
        )
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

      val result = if (approveEvaluationAction != null) {
        try {
          approveEvaluationAction.invoke(evaluationId, paperId)
        } catch (e: Exception) {
          Result.failure(e)
        }
      } else {
        withContext(Dispatchers.IO) {
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
            val updatedPapers = state.papers.map { paper ->
              if (paper.id == paperId) {
                paper.copy(
                  status = "approved",
                  evaluation = EvaluationStatus(status = "approved"),
                )
              } else paper
            }
            state.copy(
              isApproving = false,
              approvalSuccess = true,
              approvalError = null,
              evaluationResult = updatedResponse,
              editedQuestions = null,
              papers = updatedPapers,
              feedbackMessage = "Result approved and saved successfully.",
            )
          }
          // Refresh papers list to update the "status" on the paper cards (e.g. from 'scanned' to 'approved')
          _uiState.value.student?.id?.let { sid ->
            loadStudentAndPapers(sid)
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
              val updatedPapers = state.papers.map { paper ->
                if (paper.id == paperId) {
                  paper.copy(
                    status = "approved",
                    evaluation = EvaluationStatus(status = "approved"),
                  )
                } else paper
              }
              state.copy(
                isApproving = false,
                approvalSuccess = true,
                approvalError = null,
                evaluationResult = updatedResponse,
                editedQuestions = null,
                papers = updatedPapers,
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
        isSavingCorrections = false,
        saveCorrectionsSuccess = false,
        saveCorrectionsError = null,
        editedQuestions = null,
        pendingReviewNavigationPaperId = null,
      )
    }
  }

  fun onUpdateQuestion(questionId: String?, questionNumber: String?, awardedMarks: Double?, feedback: String) {
    _uiState.update { state ->
      val isApproved = state.evaluationResult?.status.equals("approved", ignoreCase = true) ||
        state.evaluationResult?.evaluation?.status.equals("approved", ignoreCase = true)
      if (isApproved) return@update state

      val baseQuestions = state.editedQuestions
        ?: state.evaluationResult?.evaluation?.questions
        ?: return@update state
      val currentQuestions = sortEvaluationQuestionsNumerically(baseQuestions)

      val targetIndex = currentQuestions.indexOfFirst { q ->
        if (q.id != null && questionId != null) {
          q.id == questionId
        } else {
          q.questionNumber != null && q.questionNumber == questionNumber
        }
      }
      if (targetIndex == -1) return@update state

      val targetQuestion = currentQuestions[targetIndex]
      val marksUnchanged = targetQuestion.awardedMarks == awardedMarks
      if (marksUnchanged) {
        if (targetQuestion.feedback != feedback) {
          val updated = currentQuestions.toMutableList()
          updated[targetIndex] = targetQuestion.copy(feedback = feedback)
          return@update state.copy(
            editedQuestions = updated,
            saveCorrectionsSuccess = false,
            saveCorrectionsError = null,
          )
        }
        return@update state.copy(
          saveCorrectionsError = null,
        )
      }

      val totalPossibleMarks = state.classRoom?.totalMarks?.toDouble()
        ?: state.evaluationResult?.evaluation?.totalMarks
        ?: calculateEffectiveEvaluationQuestionTotalMarks(currentQuestions).takeIf { it > 0.0 }

      when (
        val validation = validateQuestionMarkEdit(
          questions = currentQuestions,
          index = targetIndex,
          newAwardedMarks = awardedMarks,
          totalPossibleMarks = totalPossibleMarks,
        )
      ) {
        is MarkEditValidationResult.Invalid -> {
          state.copy(
            editedQuestions = state.editedQuestions?.let { sortEvaluationQuestionsNumerically(it) },
            saveCorrectionsSuccess = false,
            saveCorrectionsError = validation.message,
          )
        }
        is MarkEditValidationResult.Valid -> {
          val updated = validation.updatedQuestions.toMutableList()
          updated[targetIndex] = updated[targetIndex].copy(feedback = feedback)
          state.copy(
            editedQuestions = updated,
            saveCorrectionsSuccess = false,
            saveCorrectionsError = null,
          )
        }
      }
    }
  }

  fun clearSaveCorrectionsError() {
    _uiState.update { it.copy(saveCorrectionsError = null) }
  }

  fun saveTeacherCorrections(evaluationId: String, paperId: String) {
    val state = _uiState.value
    val isApproved = state.evaluationResult?.status.equals("approved", ignoreCase = true) ||
      state.evaluationResult?.evaluation?.status.equals("approved", ignoreCase = true)
    if (isApproved) return

    val rawQuestions = state.editedQuestions ?: state.evaluationResult?.evaluation?.questions ?: return
    val questions = sortEvaluationQuestionsNumerically(rawQuestions)
    val totalPossibleMarks = state.classRoom?.totalMarks?.toDouble()
      ?: state.evaluationResult?.evaluation?.totalMarks
      ?: calculateEffectiveEvaluationQuestionTotalMarks(questions)

    val validationError = validatePaperEvaluationForSave(questions, totalPossibleMarks)
    if (validationError != null) {
      _uiState.update {
        it.copy(
          isSavingCorrections = false,
          saveCorrectionsSuccess = false,
          saveCorrectionsError = validationError,
        )
      }
      return
    }

    val studentId = state.student?.id ?: state.evaluationResult?.evaluation?.studentId ?: return
    val classId = state.classRoom?.id ?: state.evaluationResult?.evaluation?.classId ?: return
    val teacherId = state.evaluationResult?.evaluation?.teacherId ?: return

    viewModelScope.launch {
      _uiState.update { it.copy(isSavingCorrections = true, saveCorrectionsError = null) }

      val result = withContext(Dispatchers.IO) {
        try {
          if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.failure<AiEvaluationResponse>(
              IllegalStateException("Supabase is not configured yet.")
            )
          }

          val totalMarksObtained = calculateCountedEvaluationTotalMarks(questions, totalPossibleMarks)
          val effectiveExtractedTotal = calculateEffectiveEvaluationQuestionTotalMarks(questions)
          val marksMatchTotal = effectiveExtractedTotal <= 0.0 ||
            kotlin.math.abs(effectiveExtractedTotal - totalPossibleMarks) <= 0.01
          val percentage = if (totalPossibleMarks > 0.0 && marksMatchTotal) {
            (Math.round((totalMarksObtained / totalPossibleMarks) * 10000) / 100.0).coerceIn(0.0, 100.0)
          } else null

          val questionsJson = questions.map { q ->
            buildJsonObject {
              put("question_number", q.questionNumber)
              put("question_text", q.questionText)
              put("maximum_marks", q.maximumMarks)
              put("student_answer", q.studentAnswer)
              put("expected_answer", q.expectedAnswer)
              put("marking_criteria", q.markingCriteria)
              put("awarded_marks", q.awardedMarks)
              put("feedback", q.feedback)
              put("review_required", q.reviewRequired)
              put("review_reason", q.reviewReason)
            }
          }

          val params = buildJsonObject {
            put("p_paper_id", paperId)
            put("p_student_id", studentId)
            put("p_class_id", classId)
            put("p_teacher_id", teacherId)
            put("p_status", "needs_review") // Keep as needs_review until approved
            put("p_total_marks_obtained", totalMarksObtained)
            put("p_total_marks", totalPossibleMarks)
            put("p_percentage", percentage)
            put("p_review_required", true)
            put("p_review_reason", "Teacher corrected")
            put("p_questions", kotlinx.serialization.json.JsonArray(questionsJson))
          }

          val response = SupabaseClientProvider.client.postgrest.rpc(
            function = "save_provisional_paper_evaluation",
            parameters = params,
          )

          val updatedEval = parseAiEvaluationResponse(response.data, fallbackPaperId = paperId)
          if (updatedEval != null) {
            Result.success(updatedEval)
          } else {
            Result.failure(Exception("Failed to parse updated evaluation."))
          }
        } catch (e: Exception) {
          Log.e("StudentDetailViewModel", "Error saving teacher corrections", e)
          Result.failure(e)
        }
      }

      result.fold(
        onSuccess = { updatedResponse ->
          _uiState.update {
            it.copy(
              isSavingCorrections = false,
              saveCorrectionsSuccess = true,
              approvalError = null,
              evaluationResult = updatedResponse,
              editedQuestions = null,
              feedbackMessage = "Corrections saved successfully.",
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isSavingCorrections = false,
              saveCorrectionsError = error.localizedMessage ?: "Failed to save corrections.",
            )
          }
        }
      )
    }
  }
}
