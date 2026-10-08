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
import com.example.data.remote.SupabaseClientProvider
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
  private val TAG = "StudentResultDetailVM"

  private val _uiState = MutableStateFlow(StudentResultDetailUiState())
  val uiState: StateFlow<StudentResultDetailUiState> = _uiState.asStateFlow()

  fun loadStudentResult(studentId: String, classId: String) {
    if (studentId.isBlank() || classId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }

      val studentResult = studentRepository.getStudentById(studentId)
      val student = studentResult.getOrNull()

      val classResult = classRepository.getClassById(classId)
      val classRoom = classResult.getOrNull()

      var evaluationDetail: EvaluationDetail? = null

      try {
        if (SupabaseClientProvider.isConfigured()) {
          // Fetch student papers for this student and class (using student_id and class_id)
          val papers = SupabaseClientProvider.client.postgrest["student_papers"]
            .select {
              filter {
                eq("student_id", studentId)
                eq("class_id", classId)
              }
            }
            .decodeList<StudentPaper>()

          for (paper in papers) {
            val paperId = paper.id ?: continue
            val evalRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
              .select {
                filter {
                  eq("paper_id", paperId)
                  eq("status", "approved")
                }
              }
              .decodeList<EvaluationDbRecord>()

            val evalRecord = evalRecords.firstOrNull()
            if (evalRecord != null) {
              val questionRecords = SupabaseClientProvider.client.postgrest["student_paper_evaluation_questions"]
                .select {
                  filter {
                    eq("evaluation_id", evalRecord.id as Any)
                  }
                }
                .decodeList<QuestionDbRecord>()

              evaluationDetail = EvaluationDetail(
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
              break
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading approved evaluation for student $studentId", e)
      }

      _uiState.update {
        it.copy(
          student = student,
          classRoom = classRoom,
          evaluation = evaluationDetail,
          isLoading = false,
        )
      }
    }
  }
}
