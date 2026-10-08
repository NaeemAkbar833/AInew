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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
  private val TAG = "ClassReportViewModel"

  private val _uiState = MutableStateFlow(ClassReportUiState())
  val uiState: StateFlow<ClassReportUiState> = _uiState.asStateFlow()

  fun loadClassReport(classId: String) {
    if (classId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }

      val classResult = classRepository.getClassById(classId)
      val classRoom = classResult.getOrNull()

      var exam: Exam? = null
      if (classRoom != null) {
        val examResult = examRepository.getExamById(classRoom.examId)
        exam = examResult.getOrNull()
      }

      val studentsResult = studentRepository.getStudentsByClassId(classId)
      val students = studentsResult.getOrDefault(emptyList())

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
        Log.e(TAG, "Error fetching approved evaluations for class report $classId", e)
      }

      _uiState.update {
        it.copy(
          classRoom = classRoom,
          exam = exam,
          students = students,
          evaluationsByStudentId = evaluationsMap,
          isLoading = false,
        )
      }
    }
  }
}
