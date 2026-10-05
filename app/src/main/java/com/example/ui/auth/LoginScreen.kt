package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
  viewModel: AuthViewModel,
  onNavigateToSignUp: () -> Unit,
  onLoginSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var passwordVisible by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("login_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp, vertical = 32.dp)
        .widthIn(max = 480.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Header & Logo
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(64.dp),
        shadowElevation = 4.dp,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.AssignmentTurnedIn,
            contentDescription = "GradeScan Logo",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Welcome to GradeScan",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Sign in to evaluate student papers with OCR & AI",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(28.dp))

      // Error message card if present
      if (uiState.errorMessage != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("login_error_card"),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Error",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uiState.errorMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 13.sp,
            )
          }
        }
      }

      // Card Container for Inputs
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        ) {
          Text(
            text = "Teacher Login",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Spacer(modifier = Modifier.height(16.dp))

          OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email Address") },
            placeholder = { Text("teacher@school.edu") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email Icon",
                modifier = Modifier.size(20.dp),
              )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("login_email_input"),
          )

          Spacer(modifier = Modifier.height(16.dp))

          OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Password Icon",
                modifier = Modifier.size(20.dp),
              )
            },
            trailingIcon = {
              IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.testTag("toggle_password_visibility"),
              ) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password",
                  modifier = Modifier.size(20.dp),
                )
              }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
              focusManager.clearFocus()
              viewModel.login(onLoginSuccess)
            }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("login_password_input"),
          )

          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = {
              focusManager.clearFocus()
              viewModel.login(onLoginSuccess)
            },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("login_button"),
          ) {
            if (uiState.isLoading) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp),
              )
            } else {
              Text(
                text = "Sign In",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 16.sp,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          text = "Don't have an account?",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(
          onClick = onNavigateToSignUp,
          modifier = Modifier.testTag("navigate_to_signup_button"),
        ) {
          Text(
            text = "Sign Up",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
