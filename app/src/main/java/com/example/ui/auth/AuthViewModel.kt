package com.example.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
  val email: String = "",
  val password: String = "",
  val confirmPassword: String = "",
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val isSuccess: Boolean = false,
)

class AuthViewModel(
  private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onEmailChanged(value: String) {
    _uiState.update { it.copy(email = value, errorMessage = null) }
  }

  fun onPasswordChanged(value: String) {
    _uiState.update { it.copy(password = value, errorMessage = null) }
  }

  fun onConfirmPasswordChanged(value: String) {
    _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun resetSuccess() {
    _uiState.update { it.copy(isSuccess = false) }
  }

  fun login(onSuccess: () -> Unit) {
    val email = _uiState.value.email.trim()
    val password = _uiState.value.password

    if (email.isBlank()) {
      _uiState.update { it.copy(errorMessage = "Please enter your email address.") }
      return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
      _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
      return
    }
    if (password.isBlank()) {
      _uiState.update { it.copy(errorMessage = "Please enter your password.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.signIn(email, password)
      result.fold(
        onSuccess = {
          _uiState.update { it.copy(isLoading = false, isSuccess = true) }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Invalid email or password. Please try again."
            )
          }
        }
      )
    }
  }

  fun signUp(onSuccess: () -> Unit) {
    val email = _uiState.value.email.trim()
    val password = _uiState.value.password
    val confirmPassword = _uiState.value.confirmPassword

    if (email.isBlank()) {
      _uiState.update { it.copy(errorMessage = "Please enter your email address.") }
      return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
      _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
      return
    }
    if (password.length < 6) {
      _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
      return
    }
    if (password != confirmPassword) {
      _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.signUp(email, password)
      result.fold(
        onSuccess = {
          _uiState.update { it.copy(isLoading = false, isSuccess = true) }
          onSuccess()
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Registration failed. Please check your credentials."
            )
          }
        }
      )
    }
  }
}
