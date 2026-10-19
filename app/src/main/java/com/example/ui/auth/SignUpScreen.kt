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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.TextStyle
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
fun SignUpScreen(
  viewModel: AuthViewModel,
  onNavigateBack: () -> Unit,
  onSignUpSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .imePadding()
      .testTag("signup_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(scrollState)
        .widthIn(max = 440.dp)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. Compact Curved Royal-Blue Gradient Header
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
          .background(HeaderBlueGradient)
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 16.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(11.dp),
                color = Color.White.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.size(38.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "GradeScan Logo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
              Column {
                Text(
                  text = "GradeScan",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  letterSpacing = (-0.4).sp,
                )
                Text(
                  text = "Smart Paper Evaluation",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.84f),
                )
              }
            }

            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .testTag("signup_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
          ) {
            Text(
              text = "Teacher Account Registration",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2. Main Body Section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        Text(
          text = "Create account",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 20.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          letterSpacing = (-0.2).sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Set up your teacher account to start creating exams and scanning student papers.",
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          color = TextSecondary,
          lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Inline Error Card
        if (uiState.errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = StatusWarningContainer,
              contentColor = StatusWarning,
            ),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 14.dp)
              .testTag("signup_error_card"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = StatusWarning,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.errorMessage ?: "",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = StatusWarning,
              )
            }
          }
        }

        // Form Card Container
        Card(
          shape = RoundedCornerShape(16.dp),
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
              .padding(16.dp),
          ) {
            Text(
              text = "Email address",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 5.dp),
            )

            OutlinedTextField(
              value = uiState.email,
              onValueChange = viewModel::onEmailChanged,
              textStyle = TextStyle(
                fontFamily = FigtreeFontFamily,
                fontSize = 13.5.sp,
                color = TextPrimary,
              ),
              placeholder = {
                Text(
                  text = "teacher@school.edu",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.sp,
                  color = TextSecondary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = "Email Icon",
                  tint = RoyalBlue,
                  modifier = Modifier.size(18.dp),
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
              shape = RoundedCornerShape(12.dp),
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
                .testTag("signup_email_input"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Password (min 6 characters)",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 5.dp),
            )

            OutlinedTextField(
              value = uiState.password,
              onValueChange = viewModel::onPasswordChanged,
              textStyle = TextStyle(
                fontFamily = FigtreeFontFamily,
                fontSize = 13.5.sp,
                color = TextPrimary,
              ),
              placeholder = {
                Text(
                  text = "Create password",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.sp,
                  color = TextSecondary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Password Icon",
                  tint = RoyalBlue,
                  modifier = Modifier.size(18.dp),
                )
              },
              trailingIcon = {
                IconButton(
                  onClick = { passwordVisible = !passwordVisible },
                  modifier = Modifier.testTag("toggle_signup_password_visibility"),
                ) {
                  Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              },
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
              ),
              keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
              ),
              shape = RoundedCornerShape(12.dp),
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
                .testTag("signup_password_input"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Confirm password",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 5.dp),
            )

            OutlinedTextField(
              value = uiState.confirmPassword,
              onValueChange = viewModel::onConfirmPasswordChanged,
              textStyle = TextStyle(
                fontFamily = FigtreeFontFamily,
                fontSize = 13.5.sp,
                color = TextPrimary,
              ),
              placeholder = {
                Text(
                  text = "Re-enter password",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.sp,
                  color = TextSecondary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Confirm Password Icon",
                  tint = RoyalBlue,
                  modifier = Modifier.size(18.dp),
                )
              },
              trailingIcon = {
                IconButton(
                  onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                  modifier = Modifier.testTag("toggle_confirm_password_visibility"),
                ) {
                  Icon(
                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              },
              visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(
                onDone = {
                  focusManager.clearFocus()
                  viewModel.signUp(onSignUpSuccess)
                }
              ),
              shape = RoundedCornerShape(12.dp),
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
                .testTag("signup_confirm_password_input"),
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Gradient Create Account Button (#1F3FBF -> #4262E6)
            GradeScanPrimaryButton(
              text = "Create Account",
              onClick = {
                focusManager.clearFocus()
                viewModel.signUp(onSignUpSuccess)
              },
              enabled = !uiState.isLoading,
              isLoading = uiState.isLoading,
              cornerRadius = 12.dp,
              testTag = "signup_submit_button",
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Return to Sign In Footer
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, BorderGray),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Text(
              text = "Already have an account?",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.width(2.dp))
            TextButton(
              onClick = onNavigateBack,
              modifier = Modifier.testTag("navigate_to_login_button"),
            ) {
              Text(
                text = "Sign In",
                fontFamily = FigtreeFontFamily,
                fontSize = 13.sp,
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
