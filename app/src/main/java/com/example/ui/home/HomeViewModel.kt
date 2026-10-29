package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.ExamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
  val teacherEmail: String? = null,
  val placeholderMessage: String? = null,
  val isSigningOut: Boolean = false,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val examCount: Int = 0,
  val classCount: Int = 0,
)

class HomeViewModel(
  private val authRepository: AuthRepository,
  private val examRepository: ExamRepository? = null,
  private val classRepository: ClassRepository? = null,
  private val onClearExamState: (() -> Unit)? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null
  private var requestGeneration: Long = 0L

  init {
    loadUserData()
  }

  fun loadTeacherInfo() {
    val email = authRepository.getCurrentUserEmail() ?: "Teacher"
    _uiState.update { it.copy(teacherEmail = email) }
  }

  /**
   * Loads teacher information, exams, and class counts.
   * Concurrent in-flight calls are deduplicated unless forceRefresh is true.
   * Each request is assigned a generation ID so cancelled or stale requests cannot
   * overwrite state belonging to a newer request or a different user session.
   */
  fun loadUserData(forceRefresh: Boolean = false) {
    if (loadJob?.isActive == true) {
      if (!forceRefresh) {
        return
      }
      loadJob?.cancel()
      loadJob = null
    }

    val requestedUserId = authRepository.getCurrentUserId()
    val requestedEmail = authRepository.getCurrentUserEmail()
    val myGeneration = ++requestGeneration

    loadJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        loadTeacherInfo()

        if (examRepository != null) {
          // Fetch exams and class count concurrently in parallel using a single count query
          val examsDeferred = async { examRepository.getExams() }
          val classCountDeferred = if (classRepository != null) {
            async {
              val res = classRepository.getClassCountForTeacher()
              res.exceptionOrNull()?.let { ex ->
                if (ex is CancellationException) throw ex
              }
              res.getOrNull() ?: 0
            }
          } else {
            null
          }

          val examsResult = examsDeferred.await()
          val fetchedClassCount = classCountDeferred?.await() ?: 0

          if (myGeneration != requestGeneration) {
            return@launch
          }
          if (
            authRepository.getCurrentUserId() != requestedUserId ||
            authRepository.getCurrentUserEmail() != requestedEmail
          ) {
            _uiState.update {
              it.copy(
                isLoading = false,
                examCount = 0,
                classCount = 0,
                errorMessage = null,
              )
            }
            return@launch
          }

          examsResult.exceptionOrNull()?.let { ex ->
            if (ex is CancellationException) throw ex
          }

          val exams = examsResult.getOrNull().orEmpty()
          val classCount = if (exams.isEmpty()) 0 else fetchedClassCount

          _uiState.update {
            it.copy(
              isLoading = false,
              examCount = exams.size,
              classCount = classCount,
              errorMessage = null,
            )
          }
        } else {
          if (myGeneration == requestGeneration) {
            _uiState.update { it.copy(isLoading = false) }
          }
        }
      } catch (e: CancellationException) {
        if (myGeneration == requestGeneration) {
          _uiState.update { it.copy(isLoading = false) }
        }
        throw e
      } catch (e: Exception) {
        if (myGeneration == requestGeneration) {
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = e.localizedMessage ?: "Failed to load dashboard data.",
            )
          }
        }
      }
    }
  }

  fun dismissErrorMessage() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun onQuickActionClick(actionName: String) {
    _uiState.update {
      it.copy(placeholderMessage = "$actionName is scheduled for a subsequent phase.")
    }
  }

  fun dismissPlaceholderMessage() {
    _uiState.update { it.copy(placeholderMessage = null) }
  }

  fun signOut(onSignedOut: () -> Unit) {
    requestGeneration++
    loadJob?.cancel()
    loadJob = null
    viewModelScope.launch {
      _uiState.update { it.copy(isSigningOut = true, errorMessage = null) }
      try {
        val signOutResult = try {
          authRepository.signOut()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }

        val failure = signOutResult.exceptionOrNull()
        if (failure is CancellationException) {
          throw failure
        }

        if (signOutResult.isFailure && authRepository.isUserSignedIn()) {
          _uiState.update {
            it.copy(
              isSigningOut = false,
              isLoading = false,
              errorMessage = failure?.localizedMessage ?: "Failed to sign out. Please try again.",
            )
          }
          return@launch
        }

        try {
          onClearExamState?.invoke()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // Do not allow cleanup failure to block sign-out completion once session is ended
        } finally {
          _uiState.update {
            it.copy(
              isSigningOut = false,
              isLoading = false,
              examCount = 0,
              classCount = 0,
              errorMessage = null,
            )
          }
        }
        onSignedOut()
      } catch (e: CancellationException) {
        _uiState.update { it.copy(isSigningOut = false, isLoading = false) }
        throw e
      }
    }
  }
}
