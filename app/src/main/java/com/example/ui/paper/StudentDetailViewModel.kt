package com.example.ui.paper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.repository.ClassRepository
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class StudentDetailViewModel(
  private val studentRepository: StudentRepository,
  private val classRepository: ClassRepository,
  private val paperRepository: PaperRepository,
) : ViewModel() {

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
}
