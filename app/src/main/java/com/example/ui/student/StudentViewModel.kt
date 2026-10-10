package com.example.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import com.example.data.model.ExtractedStudent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

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
  val isExtracting: Boolean = false,
  val extractionProgress: Float = 0f,
  val extractedStudents: List<ExtractedStudent> = emptyList(),
  val isReviewDialogOpen: Boolean = false,
  val batchErrorMessage: String? = null,
  val isBatchSaving: Boolean = false,
  val pendingCaptureUris: List<Uri> = emptyList(),
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

  fun onImagesSelected(context: Context, classId: String, uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isExtracting = true, extractionProgress = 0.05f, batchErrorMessage = null, pendingCaptureUris = emptyList()) }
      try {
        // 1. Prepare and compress images
        val imagesBase64 = mutableListOf<String>()
        var totalSize = 0L
        
        uris.forEachIndexed { index, uri ->
          val bytes = readCompressedImageBytes(context, uri)
            ?: throw IllegalStateException("Could not process image ${index + 1}")
          
          totalSize += bytes.size
          // Safety: 8MB total limit
          if (totalSize > 8 * 1024 * 1024) {
             throw IllegalStateException("The selected photos are too large. Please capture smaller images or use fewer pages.")
          }

          imagesBase64.add(Base64.encodeToString(bytes, Base64.NO_WRAP))
          _uiState.update { it.copy(extractionProgress = 0.05f + ((index + 1).toFloat() / uris.size * 0.35f)) }
        }

        // 2. Call extraction Edge Function directly
        _uiState.update { it.copy(extractionProgress = 0.5f) }
        val result = studentRepository.extractStudentList(classId, imagesBase64)
        _uiState.update { it.copy(extractionProgress = 0.9f) }
        result.fold(
          onSuccess = { extracted ->
            _uiState.update {
              it.copy(
                isExtracting = false,
                extractionProgress = 1f,
                extractedStudents = extracted,
                isReviewDialogOpen = true,
              )
            }
          },
          onFailure = { error ->
            _uiState.update {
              it.copy(
                isExtracting = false,
                extractionProgress = 0f,
                batchErrorMessage = error.localizedMessage ?: "Failed to extract student list."
              )
            }
          }
        )
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isExtracting = false,
            extractionProgress = 0f,
            batchErrorMessage = e.localizedMessage ?: "An error occurred during import."
          )
        }
      }
    }
  }

  private fun readCompressedImageBytes(context: Context, uri: Uri): ByteArray? {
    return try {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      }

      val maxDimension = 2048 // Sufficient for text extraction
      val srcWidth = options.outWidth
      val srcHeight = options.outHeight

      var inSampleSize = 1
      if (srcWidth > maxDimension || srcHeight > maxDimension) {
        val halfHeight = srcHeight / 2
        val halfWidth = srcWidth / 2
        while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
          inSampleSize *= 2
        }
      }

      val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }

      val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
      } ?: return null

      val outputStream = ByteArrayOutputStream()
      // Quality 85 balances readability and payload size
      bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
      outputStream.toByteArray()
    } catch (e: Exception) {
      Log.e("StudentViewModel", "Failed reading image bytes from Uri: $uri", e)
      null
    }
  }

  fun updateExtractedStudent(index: Int, updated: ExtractedStudent) {
    _uiState.update { state ->
      val newList = state.extractedStudents.toMutableList()
      if (index in newList.indices) {
        newList[index] = updated
      }
      state.copy(extractedStudents = newList)
    }
  }

  fun removeExtractedStudent(index: Int) {
    _uiState.update { state ->
      val newList = state.extractedStudents.toMutableList()
      if (index in newList.indices) {
        newList.removeAt(index)
      }
      state.copy(extractedStudents = newList)
    }
  }

  fun closeReviewDialog() {
    _uiState.update { it.copy(isReviewDialogOpen = false, extractedStudents = emptyList()) }
  }

  fun confirmBatchImport(classId: String) {
    val pendingStudents = _uiState.value.extractedStudents
    if (pendingStudents.isEmpty()) return

    // 1. Internal duplicate check (within the pending import list)
    val trimmedPendingRolls = mutableSetOf<String>()
    for (student in pendingStudents) {
      val roll = student.rollNumber.trim()
      if (roll.isEmpty()) continue
      if (trimmedPendingRolls.contains(roll)) {
        _uiState.update { it.copy(batchErrorMessage = "Duplicate roll number '$roll' found in the import list. Please fix it or delete the row.") }
        return
      }
      trimmedPendingRolls.add(roll)
    }

    // 2. Conflict check against students currently visible in the UI state
    // Note: This is a client-side check of already loaded data; the database may still have others.
    val existingRolls = _uiState.value.students.map { it.rollNumber.trim() }.toSet()
    for (roll in trimmedPendingRolls) {
      if (existingRolls.contains(roll)) {
        _uiState.update { it.copy(batchErrorMessage = "Roll number '$roll' already exists in this class. Please review the list.") }
        return
      }
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isBatchSaving = true, batchErrorMessage = null) }
      val result = studentRepository.addStudentsBatch(classId, pendingStudents)
      result.fold(
        onSuccess = { count ->
          _uiState.update {
            it.copy(
              isBatchSaving = false,
              isReviewDialogOpen = false,
              extractedStudents = emptyList(),
              feedbackMessage = "$count students imported successfully."
            )
          }
          loadClassAndStudents(classId)
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isBatchSaving = false,
              batchErrorMessage = error.localizedMessage ?: "Failed to save students."
            )
          }
        }
      )
    }
  }

  fun addPendingPage(uri: Uri) {
    _uiState.update { it.copy(pendingCaptureUris = it.pendingCaptureUris + uri) }
  }

  fun clearPendingPages() {
    _uiState.update { it.copy(pendingCaptureUris = emptyList()) }
  }

  fun startExtractionFromPending(context: Context, classId: String) {
    val uris = _uiState.value.pendingCaptureUris
    if (uris.isEmpty()) return
    onImagesSelected(context, classId, uris)
  }

  fun dismissBatchErrorMessage() {
    _uiState.update { it.copy(batchErrorMessage = null) }
  }
}
