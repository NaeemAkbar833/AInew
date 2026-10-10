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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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
) : ViewModel() {
  private val TAG = "ClassResultsViewModel"

  private val _uiState = MutableStateFlow(ClassResultsUiState())
  val uiState: StateFlow<ClassResultsUiState> = _uiState.asStateFlow()

  fun loadClassResults(classId: String) {
    if (classId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }

      val classDeferred = async { classRepository.getClassById(classId) }
      val studentsDeferred = async { studentRepository.getStudentsByClassId(classId) }
      val evaluationsDeferred = async {
        val evaluationsMap = mutableMapOf<String, EvaluationDbRecord>()
        try {
          if (SupabaseClientProvider.isConfigured()) {
            val evalRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
              .select {
                filter {
                  eq("class_id", classId)
                  eq("status", "approved")
                }
              }
              .decodeList<EvaluationDbRecord>()

            evalRecords.forEach { eval ->
              evaluationsMap[eval.studentId] = eval
            }
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error fetching approved evaluations for class $classId", e)
        }
        evaluationsMap
      }

      val classRoom = classDeferred.await().getOrNull()
      val students = studentsDeferred.await().getOrDefault(emptyList())
      val evaluationsMap = evaluationsDeferred.await()

      _uiState.update {
        it.copy(
          classRoom = classRoom,
          students = students,
          evaluationsByStudentId = evaluationsMap,
          isLoading = false,
        )
      }
    }
  }
}
