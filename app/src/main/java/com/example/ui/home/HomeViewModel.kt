package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
  val teacherEmail: String? = null,
  val placeholderMessage: String? = null,
  val isSigningOut: Boolean = false,
)

class HomeViewModel(
  private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  init {
    loadTeacherInfo()
  }

  fun loadTeacherInfo() {
    val email = authRepository.getCurrentUserEmail() ?: "Teacher"
    _uiState.update { it.copy(teacherEmail = email) }
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
    viewModelScope.launch {
      _uiState.update { it.copy(isSigningOut = true) }
      authRepository.signOut()
      _uiState.update { it.copy(isSigningOut = false) }
      onSignedOut()
    }
  }
}
