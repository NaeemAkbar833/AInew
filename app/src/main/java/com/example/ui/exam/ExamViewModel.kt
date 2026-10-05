package com.example.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Exam
import com.example.data.model.ExamType
import com.example.data.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExamsUiState(
  val exams: List<Exam> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val selectedExam: Exam? = null,
  val isLoadingDetail: Boolean = false,
  val isCreateDialogOpen: Boolean = false,
  val isCreating: Boolean = false,
  val createErrorMessage: String? = null,
  val placeholderMessage: String? = null,
  val isDeleting: Boolean = false,
  val deleteErrorMessage: String? = null,
  val feedbackMessage: String? = null,
)

class ExamViewModel(
  private val examRepository: ExamRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ExamsUiState())
  val uiState: StateFlow<ExamsUiState> = _uiState.asStateFlow()

  init {
    loadExams()
  }

  fun loadExams() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = examRepository.getExams()
      result.fold(
        onSuccess = { list ->
          _uiState.update {
            it.copy(
              exams = list,
              isLoading = false,
              errorMessage = null,
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to load exams.",
            )
          }
        }
      )
    }
  }

  fun loadExamDetail(examId: String) {
    // If already in list, pre-select
    val existing = _uiState.value.exams.firstOrNull { it.id == examId }
    if (existing != null) {
      _uiState.update { it.copy(selectedExam = existing, isLoadingDetail = false) }
    } else {
      viewModelScope.launch {
        _uiState.update { it.copy(isLoadingDetail = true) }
        val result = examRepository.getExamById(examId)
        result.fold(
          onSuccess = { exam ->
            _uiState.update { it.copy(selectedExam = exam, isLoadingDetail = false) }
          },
          onFailure = { error ->
            _uiState.update {
              it.copy(
                isLoadingDetail = false,
                errorMessage = error.localizedMessage ?: "Failed to load exam details."
              )
            }
          }
        )
      }
    }
  }

  fun openCreateDialog() {
    _uiState.update { it.copy(isCreateDialogOpen = true, createErrorMessage = null) }
  }

  fun closeCreateDialog() {
    _uiState.update { it.copy(isCreateDialogOpen = false, createErrorMessage = null) }
  }

  fun createExam(
    name: String,
    type: ExamType,
    date: String,
    onSuccess: (Exam) -> Unit,
  ) {
    if (name.isBlank()) {
      _uiState.update { it.copy(createErrorMessage = "Exam name cannot be empty.") }
      return
    }
    if (date.isBlank()) {
      _uiState.update { it.copy(createErrorMessage = "Exam date is required.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isCreating = true, createErrorMessage = null) }
      val result = examRepository.createExam(name = name, type = type, date = date)
      result.fold(
        onSuccess = { newExam ->
          _uiState.update {
            it.copy(
              exams = listOf(newExam) + it.exams,
              isCreating = false,
              isCreateDialogOpen = false,
              createErrorMessage = null,
            )
          }
          onSuccess(newExam)
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isCreating = false,
              createErrorMessage = error.localizedMessage ?: "Failed to create exam. Please try again."
            )
          }
        }
      )
    }
  }

  fun onAddClassClick() {
    _uiState.update {
      it.copy(placeholderMessage = "Class management will be implemented in Phase 3.")
    }
  }

  fun dismissPlaceholderMessage() {
    _uiState.update { it.copy(placeholderMessage = null) }
  }

  fun deleteExam(examId: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _uiState.update { it.copy(isDeleting = true, deleteErrorMessage = null) }
      val result = examRepository.deleteExam(examId)
      result.fold(
        onSuccess = {
          _uiState.update {
            it.copy(
              exams = it.exams.filter { exam -> exam.id != examId },
              selectedExam = if (it.selectedExam?.id == examId) null else it.selectedExam,
              isDeleting = false,
              deleteErrorMessage = null,
              feedbackMessage = "Exam deleted successfully.",
            )
          }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isDeleting = false,
              deleteErrorMessage = error.localizedMessage ?: "Failed to delete exam.",
            )
          }
        }
      )
    }
  }

  fun dismissFeedbackMessage() {
    _uiState.update { it.copy(feedbackMessage = null) }
  }

  fun dismissDeleteErrorMessage() {
    _uiState.update { it.copy(deleteErrorMessage = null) }
  }
}
