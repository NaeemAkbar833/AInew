package com.example.ui.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Student
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

data class ClassResultsUiState(
  val classRoom: ClassRoom? = null,
  val students: List<Student> = emptyList(),
  val evaluationsByStudentId: Map<String, EvaluationDbRecord> = emptyMap(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
)

class ClassResultsViewModel(
  private val classRepository: ClassRepository,
  private val studentRepository: StudentRepository,
  private val approvedEvaluationsFetcher: (suspend (classId: String) -> List<EvaluationDbRecord>)? = null,
) : ViewModel() {
  private val TAG = "ClassResultsViewModel"

  private val _uiState = MutableStateFlow(ClassResultsUiState())
  val uiState: StateFlow<ClassResultsUiState> = _uiState.asStateFlow()

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

  fun loadClassResults(classId: String) {
    if (classId.isBlank()) return
    val myGeneration = ++loadGeneration
    val isClassSwitch = activeClassId != null && activeClassId != classId
    activeClassId = classId
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.update { state ->
        if (isClassSwitch) {
          ClassResultsUiState(isLoading = true, errorMessage = null)
        } else {
          state.copy(isLoading = true, errorMessage = null)
        }
      }

      try {
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
            Log.e(TAG, "Error fetching approved evaluations for class $classId", e)
            Result.failure(e)
          }
        }

        val classResult = classDeferred.await()
        val studentsResult = studentsDeferred.await()
        val evaluationsResult = evaluationsDeferred.await()

        if (myGeneration != loadGeneration) return@launch

        classResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        studentsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        evaluationsResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        val failure = evaluationsResult.exceptionOrNull()
          ?: studentsResult.exceptionOrNull()
          ?: classResult.exceptionOrNull()

        if (failure != null) {
          val errorMsg = failure.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Unable to load student results. Please try again."
          _uiState.update { state ->
            if (myGeneration != loadGeneration) return@update state
            state.copy(
              isLoading = false,
              errorMessage = errorMsg,
            )
          }
          return@launch
        }

        val classRoom = classResult.getOrNull()?.takeIf { it.id == classId }
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
