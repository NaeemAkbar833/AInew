package com.example.ui.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.data.model.QuestionDbRecord
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.calculateEffectiveQuestionTotalMarks
import com.example.data.remote.SupabaseClientProvider
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.model.sortEvaluationQuestionsNumerically

data class StudentResultDetailUiState(
  val student: Student? = null,
  val classRoom: ClassRoom? = null,
  val evaluation: EvaluationDetail? = null,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
)

class StudentResultDetailViewModel(
  private val studentRepository: StudentRepository,
  private val classRepository: ClassRepository,
  private val approvedEvaluationFetcher: (suspend (studentId: String, classId: String) -> Pair<EvaluationDbRecord, List<QuestionDbRecord>>?)? = null,
) : ViewModel() {
  private val TAG = "StudentResultDetailVM"

  private val _uiState = MutableStateFlow(StudentResultDetailUiState())
  val uiState: StateFlow<StudentResultDetailUiState> = _uiState.asStateFlow()

  private var loadGeneration: Long = 0L
  private var loadJob: Job? = null
  private var activeStudentId: String? = null
  private var activeClassId: String? = null

  private suspend fun fetchApprovedEvaluationFromDb(
    studentId: String,
    classId: String,
  ): Pair<EvaluationDbRecord, List<QuestionDbRecord>>? = withContext(Dispatchers.IO) {
    if (approvedEvaluationFetcher != null) {
      return@withContext approvedEvaluationFetcher.invoke(studentId, classId)
    }

    if (!SupabaseClientProvider.isConfigured()) {
      throw IllegalStateException("Supabase is not configured yet.")
    }

    val evalRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
      .select {
        filter {
          eq("student_id", studentId)
          eq("class_id", classId)
          eq("status", "approved")
        }
        order("updated_at", order = Order.DESCENDING)
      }
      .decodeList<EvaluationDbRecord>()

    val evalRecord = evalRecords.firstOrNull {
      it.status.equals("approved", ignoreCase = true) &&
        it.studentId == studentId &&
        it.classId == classId
    } ?: return@withContext null

    val questionRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluation_questions"]
      .select {
        filter {
          eq("evaluation_id", evalRecord.id as Any)
        }
      }
      .decodeList<QuestionDbRecord>()

    evalRecord to questionRecords
  }

  fun loadStudentResult(studentId: String, classId: String) {
    if (studentId.isBlank() || classId.isBlank()) return
    val myGeneration = ++loadGeneration
    val isTargetSwitch =
      (activeStudentId != null && activeStudentId != studentId) ||
        (activeClassId != null && activeClassId != classId)
    activeStudentId = studentId
    activeClassId = classId
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.update { state ->
        if (isTargetSwitch) {
          StudentResultDetailUiState(isLoading = true, errorMessage = null)
        } else {
          state.copy(isLoading = true, errorMessage = null)
        }
      }

      try {
        val studentDeferred = async {
          try {
            val res = studentRepository.getStudentById(studentId)
            res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            res
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Result.failure(e)
          }
        }
        val classDeferred = async {
          try {
            val res = classRepository.getClassById(classId)
            res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            res
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Result.failure(e)
          }
        }
        val evaluationDeferred = async {
          try {
            Result.success(fetchApprovedEvaluationFromDb(studentId, classId))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Log.e(TAG, "Error loading approved evaluation for student $studentId", e)
            Result.failure(e)
          }
        }

        val studentResult = studentDeferred.await()
        val classResult = classDeferred.await()
        val evaluationResult = evaluationDeferred.await()

        if (myGeneration != loadGeneration) return@launch

        studentResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        classResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        evaluationResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        val rawStudent = studentResult.getOrNull()
        val student = rawStudent?.takeIf {
          it.id == studentId && (it.classId.isBlank() || it.classId == classId)
        }
        val classRoom = classResult.getOrNull()?.takeIf { it.id == classId }

        val failure = evaluationResult.exceptionOrNull()
          ?: studentResult.exceptionOrNull()
          ?: classResult.exceptionOrNull()

        if (failure != null) {
          val errorMsg = failure.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Unable to load student result. Please try again."
          _uiState.update { state ->
            if (myGeneration != loadGeneration) return@update state
            state.copy(
              student = student ?: state.student?.takeIf { it.id == studentId && (it.classId.isBlank() || it.classId == classId) },
              classRoom = classRoom ?: state.classRoom?.takeIf { it.id == classId },
              evaluation = state.evaluation?.takeIf { it.studentId == studentId && it.classId == classId },
              isLoading = false,
              errorMessage = errorMsg,
            )
          }
          return@launch
        }

        val isOwnerMatch = rawStudent == null || (rawStudent.id == studentId && (rawStudent.classId.isBlank() || rawStudent.classId == classId))
        val evaluationPair = evaluationResult.getOrNull()
        val evaluationDetail = if (evaluationPair != null && isOwnerMatch) {
          val (evalRecord, questionRecords) = evaluationPair
          if (evalRecord.status.equals("approved", ignoreCase = true) &&
            evalRecord.studentId == studentId &&
            evalRecord.classId == classId
          ) {
            val filteredQuestionRecords = questionRecords.filter {
              it.evaluationId.isBlank() || it.evaluationId == evalRecord.id
            }
            val sortedQuestions = sortEvaluationQuestionsNumerically(
              filteredQuestionRecords.map { q ->
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
            EvaluationDetail(
              id = evalRecord.id,
              paperId = evalRecord.paperId,
              studentId = evalRecord.studentId,
              classId = evalRecord.classId,
              teacherId = evalRecord.teacherId,
              status = "approved",
              totalMarksObtained = evalRecord.totalMarksObtained,
              totalMarks = evalRecord.totalMarks,
              extractedQuestionTotalMarks = calculateEffectiveQuestionTotalMarks(filteredQuestionRecords),
              percentage = evalRecord.percentage,
              percentageAvailable = evalRecord.percentage != null,
              reviewRequired = evalRecord.reviewRequired,
              reviewReason = evalRecord.reviewReason,
              createdAt = evalRecord.createdAt,
              updatedAt = evalRecord.updatedAt,
              questions = sortedQuestions,
            )
          } else {
            null
          }
        } else {
          null
        }

        _uiState.update { state ->
          if (myGeneration != loadGeneration) return@update state
          state.copy(
            student = student,
            classRoom = classRoom,
            evaluation = evaluationDetail,
            isLoading = false,
            errorMessage = null,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } finally {
        if (myGeneration == loadGeneration) {
          _uiState.update { state ->
            if (state.isLoading) state.copy(isLoading = false) else state
          }
        }
      }
    }
  }
}
