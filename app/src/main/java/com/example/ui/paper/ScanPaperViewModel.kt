package com.example.ui.paper

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LocalScannedPage
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.deleteAppOwnedTempCameraFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanPaperUiState(
  val pages: List<LocalScannedPage> = emptyList(),
  val isUploading: Boolean = false,
  val uploadProgress: Pair<Int, Int>? = null, // current, total
  val uploadError: String? = null,
  val previewPageUri: Uri? = null,
  val studentName: String? = null,
  val studentRollNumber: String? = null,
)

class ScanPaperViewModel(
  private val paperRepository: PaperRepository,
  private val studentRepository: StudentRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ScanPaperUiState())
  val uiState: StateFlow<ScanPaperUiState> = _uiState.asStateFlow()
  @Volatile
  private var cachedCacheDir: File? = null

  fun registerCacheDir(cacheDir: File?) {
    if (cacheDir != null) {
      cachedCacheDir = cacheDir
    }
  }

  fun loadStudentInfo(studentId: String) {
    if (studentId.isBlank() || studentRepository == null) return
    viewModelScope.launch {
      val result = studentRepository.getStudentById(studentId)
      result.getOrNull()?.let { student ->
        _uiState.update { current ->
          current.copy(
            studentName = student.name,
            studentRollNumber = student.rollNumber,
          )
        }
      }
    }
  }

  fun addPage(uri: Uri) {
    _uiState.update { current ->
      val newPage = LocalScannedPage(
        id = UUID.randomUUID().toString(),
        uri = uri,
        pageNumber = current.pages.size + 1,
      )
      current.copy(pages = current.pages + newPage, uploadError = null)
    }
  }

  fun addPages(uris: List<Uri>) {
    if (uris.isEmpty()) return
    _uiState.update { current ->
      var startNum = current.pages.size + 1
      val newPages = uris.map { uri ->
        LocalScannedPage(
          id = UUID.randomUUID().toString(),
          uri = uri,
          pageNumber = startNum++,
        )
      }
      current.copy(pages = current.pages + newPages, uploadError = null)
    }
  }

  fun removePage(pageId: String) {
    var removedUri: Uri? = null
    _uiState.update { current ->
      val removedPage = current.pages.firstOrNull { it.id == pageId }
      removedUri = removedPage?.uri
      val filtered = current.pages.filter { it.id != pageId }
      val renumbered = filtered.mapIndexed { index, page ->
        page.copy(pageNumber = index + 1)
      }
      val shouldClearPreview = removedPage != null && current.previewPageUri == removedPage.uri
      current.copy(
        pages = renumbered,
        previewPageUri = if (shouldClearPreview) null else current.previewPageUri,
      )
    }
    val uriToClean = removedUri
    if (uriToClean != null && _uiState.value.pages.none { it.uri == uriToClean }) {
      deleteAppOwnedTempCameraFile(null, uriToClean, cachedCacheDir)
    }
  }

  fun setPreviewPageUri(uri: Uri?) {
    _uiState.update { it.copy(previewPageUri = uri) }
  }

  fun setPreviewPage(page: LocalScannedPage?) {
    _uiState.update { it.copy(previewPageUri = page?.uri) }
  }

  fun clearError() {
    _uiState.update { it.copy(uploadError = null) }
  }

  fun clearAllPages() {
    val urisToClean = _uiState.value.pages.map { it.uri }
    _uiState.update {
      it.copy(
        pages = emptyList(),
        uploadProgress = null,
        uploadError = null,
        previewPageUri = null,
      )
    }
    urisToClean.forEach { uri ->
      deleteAppOwnedTempCameraFile(null, uri, cachedCacheDir)
    }
  }

  fun finishScan(
    context: Context,
    studentId: String,
    classId: String,
    onSuccess: (String) -> Unit,
  ) {
    val appContext = context.applicationContext ?: context
    cachedCacheDir = appContext.cacheDir
    val currentPages = _uiState.value.pages
    if (currentPages.isEmpty()) {
      _uiState.update { it.copy(uploadError = "Please capture or select at least one page.") }
      return
    }

    _uiState.update {
      it.copy(
        isUploading = true,
        uploadProgress = Pair(1, currentPages.size),
        uploadError = null,
      )
    }

    viewModelScope.launch {
      val uris = currentPages.map { it.uri }
      val result = paperRepository.createPaperWithPages(
        context = appContext,
        studentId = studentId,
        classId = classId,
        pageUris = uris,
        onProgress = { current, total ->
          _uiState.update { it.copy(uploadProgress = Pair(current, total)) }
        }
      )

      result.fold(
        onSuccess = { createdPaper ->
          val paperId = createdPaper.id ?: ""
          _uiState.update {
            it.copy(
              pages = emptyList(),
              isUploading = false,
              uploadProgress = null,
              uploadError = null,
              previewPageUri = null,
            )
          }
          onSuccess(paperId)
          viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
              deleteAppOwnedTempCameraFile(appContext, uri, cachedCacheDir)
            }
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isUploading = false,
              uploadProgress = null,
              uploadError = error.localizedMessage ?: "Failed to upload and save paper.",
            )
          }
        }
      )
    }
  }

  override fun onCleared() {
    val urisToClean = _uiState.value.pages.map { it.uri }
    urisToClean.forEach { uri ->
      deleteAppOwnedTempCameraFile(null, uri, cachedCacheDir)
    }
    super.onCleared()
  }
}
