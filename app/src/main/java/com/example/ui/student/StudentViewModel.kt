package com.example.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentsUiState(
  val classRoom: ClassRoom? = null,
  val students: List<Student> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val isAddDialogOpen: Boolean = false,
  val isAdding: Boolean = false,
  val addErrorMessage: String? = null,
  val isDeleting: Boolean = false,
  val deleteErrorMessage: String? = null,
  val feedbackMessage: String? = null,
)

class StudentViewModel(
  private val studentRepository: StudentRepository,
  private val classRepository: ClassRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(StudentsUiState())
  val uiState: StateFlow<StudentsUiState> = _uiState.asStateFlow()

  fun loadClassAndStudents(classId: String) {
    if (classId.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }

      // 1. Fetch class metadata
      val classResult = classRepository.getClassById(classId)
      val loadedClass = classResult.getOrNull()

      // 2. Fetch students
      val studentsResult = studentRepository.getStudentsByClassId(classId)
      studentsResult.fold(
        onSuccess = { list ->
          _uiState.update {
            it.copy(
              classRoom = loadedClass ?: it.classRoom,
              students = list,
              isLoading = false,
              errorMessage = null,
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              classRoom = loadedClass ?: it.classRoom,
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to load students.",
            )
          }
        }
      )
    }
  }

  fun openAddDialog() {
    _uiState.update { it.copy(isAddDialogOpen = true, addErrorMessage = null) }
  }

  fun closeAddDialog() {
    _uiState.update { it.copy(isAddDialogOpen = false, addErrorMessage = null) }
  }

  fun addStudent(
    classId: String,
    name: String,
    fatherName: String,
    rollNumber: String,
    onSuccess: (Student) -> Unit,
  ) {
    if (name.isBlank()) {
      _uiState.update { it.copy(addErrorMessage = "Student name is required.") }
      return
    }
    if (fatherName.isBlank()) {
      _uiState.update { it.copy(addErrorMessage = "Father name is required.") }
      return
    }
    if (rollNumber.isBlank()) {
      _uiState.update { it.copy(addErrorMessage = "Roll number is required.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isAdding = true, addErrorMessage = null) }
      val result = studentRepository.addStudent(
        classId = classId,
        name = name,
        fatherName = fatherName,
        rollNumber = rollNumber,
      )
      result.fold(
        onSuccess = { newStudent ->
          _uiState.update {
            it.copy(
              students = (it.students + newStudent).sortedBy { s -> s.rollNumber },
              isAdding = false,
              isAddDialogOpen = false,
              addErrorMessage = null,
              feedbackMessage = "Student added successfully.",
            )
          }
          onSuccess(newStudent)
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isAdding = false,
              addErrorMessage = error.localizedMessage ?: "Failed to add student.",
            )
          }
        }
      )
    }
  }

  fun deleteStudent(studentId: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _uiState.update { it.copy(isDeleting = true, deleteErrorMessage = null) }
      val result = studentRepository.deleteStudent(studentId)
      result.fold(
        onSuccess = {
          // Guaranteed verified database deletion before updating local state
          _uiState.update {
            it.copy(
              students = it.students.filter { s -> s.id != studentId },
              isDeleting = false,
              deleteErrorMessage = null,
              feedbackMessage = "Student deleted successfully.",
            )
          }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isDeleting = false,
              deleteErrorMessage = error.localizedMessage ?: "Failed to delete student.",
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
