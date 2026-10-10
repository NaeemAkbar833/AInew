package com.example.ui.paper

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LocalScannedPage
import com.example.data.repository.PaperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ScanPaperUiState(
  val pages: List<LocalScannedPage> = emptyList(),
  val isUploading: Boolean = false,
  val uploadProgress: Pair<Int, Int>? = null, // current, total
  val uploadError: String? = null,
  val previewPageUri: Uri? = null,
)

class ScanPaperViewModel(
  private val paperRepository: PaperRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ScanPaperUiState())
  val uiState: StateFlow<ScanPaperUiState> = _uiState.asStateFlow()

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
    _uiState.update { current ->
      val filtered = current.pages.filter { it.id != pageId }
      val renumbered = filtered.mapIndexed { index, page ->
        page.copy(pageNumber = index + 1)
      }
      current.copy(pages = renumbered)
    }
  }

  fun setPreviewPageUri(uri: Uri?) {
    _uiState.update { it.copy(previewPageUri = uri) }
  }

  fun clearError() {
    _uiState.update { it.copy(uploadError = null) }
  }

  fun clearAllPages() {
    _uiState.update { it.copy(pages = emptyList(), uploadProgress = null, uploadError = null) }
  }

  fun finishScan(
    context: Context,
    studentId: String,
    classId: String,
    onSuccess: (String) -> Unit,
  ) {
    val currentPages = _uiState.value.pages
    if (currentPages.isEmpty()) {
      _uiState.update { it.copy(uploadError = "Please capture or select at least one page.") }
      return
    }

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isUploading = true,
          uploadProgress = Pair(1, currentPages.size),
          uploadError = null,
        )
      }

      val uris = currentPages.map { it.uri }
      val result = paperRepository.createPaperWithPages(
        context = context,
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
            )
          }
          onSuccess(paperId)
        },
        onFailure = { error ->
          // Scanned pages remain preserved in memory so teacher can retry
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
}
