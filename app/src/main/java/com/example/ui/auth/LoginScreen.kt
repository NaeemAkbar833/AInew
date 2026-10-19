package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GradeScanPrimaryButton
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LoginScreen(
  viewModel: AuthViewModel,
  onNavigateToSignUp: () -> Unit,
  onLoginSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var passwordVisible by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .imePadding()
      .testTag("login_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(scrollState)
        .widthIn(max = 440.dp)
        .padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. Stitch Curved Royal-Blue Gradient Header (#1F3FBF -> #2A4FD6)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
          .background(HeaderBlueGradient)
          .statusBarsPadding()
          .padding(horizontal = 24.dp, vertical = 24.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.size(44.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "GradeScan Logo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }
              Column {
                Text(
                  text = "GradeScan",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  letterSpacing = (-0.5).sp,
                )
                Text(
                  text = "Smart Paper Evaluation",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.sp,
                  color = Color.White.copy(alpha = 0.82f),
                )
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
          ) {
            Text(
              text = "AI-Powered Paper Grading",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // 2. Main Body Section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        Text(
          text = "Welcome back",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          letterSpacing = (-0.3).sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Sign in to grade student papers with OCR and AI.",
          fontFamily = FigtreeFontFamily,
          fontSize = 14.sp,
          color = TextSecondary,
          lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Inline Error Card
        if (uiState.errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = StatusWarningContainer,
              contentColor = StatusWarning,
            ),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
              .testTag("login_error_card"),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = StatusWarning,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = uiState.errorMessage ?: "",
                fontFamily = FigtreeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = StatusWarning,
              )
            }
          }
        }

        // Credentials Form Card Container (#FFFFFF surface, #D1D9EC border, 20.dp radius)
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = Color.White,
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          border = BorderStroke(
            width = 1.dp,
            color = BorderGray,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
          ) {
            Text(
              text = "Email address",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 6.dp),
            )

            OutlinedTextField(
              value = uiState.email,
              onValueChange = viewModel::onEmailChanged,
              placeholder = {
                Text(
                  text = "you@school.edu",
                  fontFamily = FigtreeFontFamily,
                  color = TextSecondary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = "Email Icon",
                  tint = RoyalBlue,
                  modifier = Modifier.size(20.dp),
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
              ),
              keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
              ),
              shape = RoundedCornerShape(14.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RoyalBlue,
                unfocusedBorderColor = BorderGray,
                focusedLabelColor = RoyalBlue,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = RoyalBlue,
                unfocusedContainerColor = SoftBlueTint,
                focusedContainerColor = Color.White,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("login_email_input"),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Password",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 6.dp),
            )

            OutlinedTextField(
              value = uiState.password,
              onValueChange = viewModel::onPasswordChanged,
              placeholder = {
                Text(
                  text = "Enter your password",
                  fontFamily = FigtreeFontFamily,
                  color = TextSecondary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Password Icon",
                  tint = RoyalBlue,
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
                    tint = TextSecondary,
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
              keyboardActions = KeyboardActions(
                onDone = {
                  focusManager.clearFocus()
                  viewModel.login(onLoginSuccess)
                }
              ),
              shape = RoundedCornerShape(14.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RoyalBlue,
                unfocusedBorderColor = BorderGray,
                focusedLabelColor = RoyalBlue,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = RoyalBlue,
                unfocusedContainerColor = SoftBlueTint,
                focusedContainerColor = Color.White,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input"),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Gradient Sign In Button (#1F3FBF -> #4262E6)
            GradeScanPrimaryButton(
              text = "Sign in",
              onClick = {
                focusManager.clearFocus()
                viewModel.login(onLoginSuccess)
              },
              enabled = !uiState.isLoading,
              isLoading = uiState.isLoading,
              cornerRadius = 14.dp,
              testTag = "login_button",
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sign Up Footer Card
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, BorderGray),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Text(
              text = "Don't have an account?",
              fontFamily = FigtreeFontFamily,
              fontSize = 14.sp,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
              onClick = onNavigateToSignUp,
              modifier = Modifier.testTag("navigate_to_signup_button"),
            ) {
              Text(
                text = "Sign up",
                fontFamily = FigtreeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkRoyalBlue,
              )
            }
          }
        }
      }
    }
  }
}
