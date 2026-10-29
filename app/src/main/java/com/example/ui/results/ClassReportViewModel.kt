package com.example.ui.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Exam
import com.example.data.model.Student
import com.example.data.remote.SupabaseClientProvider
import com.example.data.repository.ClassRepository
import com.example.data.repository.ExamRepository
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

data class ClassReportUiState(
  val classRoom: ClassRoom? = null,
  val exam: Exam? = null,
  val students: List<Student> = emptyList(),
  val evaluationsByStudentId: Map<String, EvaluationDbRecord> = emptyMap(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
)

class ClassReportViewModel(
  private val classRepository: ClassRepository,
  private val examRepository: ExamRepository,
  private val studentRepository: StudentRepository,
  private val approvedEvaluationsFetcher: (suspend (classId: String) -> List<EvaluationDbRecord>)? = null,
) : ViewModel() {
  private val TAG = "ClassReportViewModel"

  private val _uiState = MutableStateFlow(ClassReportUiState())
  val uiState: StateFlow<ClassReportUiState> = _uiState.asStateFlow()

  private var loadGeneration: Long = 0L
  private var loadJob: Job? = null
  private var activeClassId: String? = null

  private suspend fun fetchApprovedEvaluationsForClass(
    classId: String,
  ): List<EvaluationDbRecord> = withContext(Dispatchers.IO) {
    if (approvedEvaluationsFetcher != null) {
      return@withContext approvedEvaluationsFetcher.invoke(classId)
    }

    if (!SupabaseClientProvider.isConfigured()) {
      throw IllegalStateException("Supabase is not configured yet.")
    }

    SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
      .select {
        filter {
          eq("class_id", classId)
          eq("status", "approved")
        }
        order("updated_at", order = Order.ASCENDING)
      }
      .decodeList<EvaluationDbRecord>()
  }

  fun loadClassReport(classId: String) {
    if (classId.isBlank()) return
    val myGeneration = ++loadGeneration
    val isClassSwitch = activeClassId != null && activeClassId != classId
    activeClassId = classId
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      val knownExamId = if (!isClassSwitch) {
        _uiState.value.classRoom
          ?.takeIf { it.id == classId }
          ?.examId
          ?.takeIf { it.isNotBlank() }
      } else {
        null
      }

      _uiState.update { state ->
        if (isClassSwitch) {
          ClassReportUiState(isLoading = true, errorMessage = null)
        } else {
          state.copy(isLoading = true, errorMessage = null)
        }
      }

      try {
        val speculativeExamDeferred = knownExamId?.let { eid ->
          async {
            try {
              val res = examRepository.getExamById(eid)
              res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
              res
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Result.failure(e)
            }
          }
        }

        val classAndExamDeferred = async {
          val classRes = try {
            val res = classRepository.getClassById(classId)
            res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            res
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Result.failure(e)
          }

          val loadedClass = classRes.getOrNull()?.takeIf { it.id == classId }
          val examRes: Result<Exam?> = when {
            classRes.isFailure || loadedClass == null || loadedClass.examId.isBlank() -> {
              speculativeExamDeferred?.cancel()
              Result.success(null)
            }
            knownExamId != null && loadedClass.examId == knownExamId && speculativeExamDeferred != null -> {
              speculativeExamDeferred.await()
            }
            else -> {
              speculativeExamDeferred?.cancel()
              try {
                val res = examRepository.getExamById(loadedClass.examId)
                res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                res
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Result.failure(e)
              }
            }
          }
          Triple(classRes, loadedClass, examRes)
        }

        val studentsDeferred = async {
          try {
            val res = studentRepository.getStudentsByClassId(classId)
            res.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            res
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Result.failure(e)
          }
        }

        val evaluationsDeferred = async {
          try {
            Result.success(fetchApprovedEvaluationsForClass(classId))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Log.e(TAG, "Error fetching approved evaluations for class report $classId", e)
            Result.failure(e)
          }
        }

        val (classResult, classRoom, examResult) = classAndExamDeferred.await()
        val studentsResult = studentsDeferred.await()
        val evaluationsResult = evaluationsDeferred.await()

        if (myGeneration != loadGeneration) return@launch

        classResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        examResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        studentsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        evaluationsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        val failure = evaluationsResult.exceptionOrNull()
          ?: studentsResult.exceptionOrNull()
          ?: classResult.exceptionOrNull()
          ?: examResult.exceptionOrNull()

        if (failure != null) {
          val errorMsg = failure.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Unable to load class report. Please try again."
          _uiState.update { state ->
            if (myGeneration != loadGeneration) return@update state
            state.copy(
              isLoading = false,
              errorMessage = errorMsg,
            )
          }
          return@launch
        }

        val exam = examResult.getOrNull()?.takeIf {
          classRoom == null || it.id == null || it.id == classRoom.examId
        }
        val students = studentsResult.getOrNull().orEmpty()
          .filter { it.classId.isBlank() || it.classId == classId }
        val validStudentIds = students.mapNotNull { it.id }.toSet()

        val evaluationsMap = LinkedHashMap<String, EvaluationDbRecord>()
        evaluationsResult.getOrNull().orEmpty().forEach { eval ->
          if (eval.status.equals("approved", ignoreCase = true) &&
            eval.classId == classId &&
            eval.studentId in validStudentIds
          ) {
            evaluationsMap[eval.studentId] = eval.copy(status = "approved")
          }
        }

        _uiState.update { state ->
          if (myGeneration != loadGeneration) return@update state
          state.copy(
            classRoom = classRoom,
            exam = exam,
            students = students,
            evaluationsByStudentId = evaluationsMap,
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
