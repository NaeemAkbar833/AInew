package com.example.ui.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.repository.ClassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClassesUiState(
  val classes: List<ClassRoom> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val isAddClassDialogOpen: Boolean = false,
  val isSaving: Boolean = false,
  val saveErrorMessage: String? = null,
  val feedbackMessage: String? = null,
  val isDeleting: Boolean = false,
  val deleteErrorMessage: String? = null,
)

class ClassViewModel(
  private val classRepository: ClassRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ClassesUiState())
  val uiState: StateFlow<ClassesUiState> = _uiState.asStateFlow()

  fun loadClasses(examId: String) {
    if (examId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = classRepository.getClassesByExamId(examId)
      result.fold(
        onSuccess = { list ->
          _uiState.update {
            it.copy(
              classes = list,
              isLoading = false,
              errorMessage = null,
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to load classes.",
            )
          }
        }
      )
    }
  }

  fun openAddClassDialog() {
    _uiState.update { it.copy(isAddClassDialogOpen = true, saveErrorMessage = null) }
  }

  fun closeAddClassDialog() {
    _uiState.update { it.copy(isAddClassDialogOpen = false, saveErrorMessage = null) }
  }

  fun createClass(
    examId: String,
    session: String,
    semester: String,
    timing: ClassTiming,
    subjectName: String,
    totalMarksText: String,
    questionPaperBytes: ByteArray?,
    fileExtension: String?,
    onSuccess: (ClassRoom) -> Unit,
  ) {
    // Validation
    if (session.isBlank()) {
      _uiState.update { it.copy(saveErrorMessage = "Class Session is required (e.g. 2022-2026).") }
      return
    }
    if (semester.isBlank()) {
      _uiState.update { it.copy(saveErrorMessage = "Semester is required (e.g. 4th).") }
      return
    }
    if (subjectName.isBlank()) {
      _uiState.update { it.copy(saveErrorMessage = "Subject Name is required.") }
      return
    }
    val totalMarks = totalMarksText.toIntOrNull()
    if (totalMarks == null || totalMarks <= 0) {
      _uiState.update { it.copy(saveErrorMessage = "Total Marks must be a positive number.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
      val result = classRepository.createClass(
        examId = examId,
        session = session,
        semester = semester,
        timing = timing,
        subjectName = subjectName,
        totalMarks = totalMarks,
        questionPaperBytes = questionPaperBytes,
        fileExtension = fileExtension,
      )
      result.fold(
        onSuccess = { newClass ->
          _uiState.update {
            it.copy(
              classes = it.classes + newClass,
              isSaving = false,
              isAddClassDialogOpen = false,
              saveErrorMessage = null,
              feedbackMessage = "Class added successfully.",
            )
          }
          onSuccess(newClass)
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isSaving = false,
              saveErrorMessage = error.localizedMessage ?: "Failed to save class.",
            )
          }
        }
      )
    }
  }

  fun dismissFeedbackMessage() {
    _uiState.update { it.copy(feedbackMessage = null) }
  }

  fun deleteClass(classId: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _uiState.update { it.copy(isDeleting = true, deleteErrorMessage = null) }
      val result = classRepository.deleteClass(classId)
      result.fold(
        onSuccess = {
          _uiState.update {
            it.copy(
              classes = it.classes.filter { c -> c.id != classId },
              isDeleting = false,
              deleteErrorMessage = null,
              feedbackMessage = "Class deleted successfully.",
            )
          }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isDeleting = false,
              deleteErrorMessage = error.localizedMessage ?: "Failed to delete class.",
            )
          }
        }
      )
    }
  }

  fun dismissDeleteErrorMessage() {
    _uiState.update { it.copy(deleteErrorMessage = null) }
  }
}
