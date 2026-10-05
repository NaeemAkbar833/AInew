package com.example.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  authRepository: AuthRepository,
  onNavigateToHome: () -> Unit,
  onNavigateToLogin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) {
    // Brief splash pause for branding recognition and session restoration
    delay(1000)
    val hasActiveSession = authRepository.checkSession()
    if (hasActiveSession) {
      onNavigateToHome()
    } else {
      onNavigateToLogin()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(32.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(80.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.AssignmentTurnedIn,
            contentDescription = "GradeScan Icon",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(44.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "GradeScan",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = (-0.5).sp,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Smart Paper Evaluation for Teachers",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(48.dp))

      CircularProgressIndicator(
        modifier = Modifier
          .size(28.dp)
          .testTag("splash_loading_indicator"),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 3.dp,
      )
    }
  }
}
