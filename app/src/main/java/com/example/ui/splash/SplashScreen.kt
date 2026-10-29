package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SplashGradientBrush
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  authRepository: AuthRepository,
  onNavigateToHome: () -> Unit,
  onNavigateToLogin: () -> Unit,
) {
  val scale = remember { Animatable(0.75f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    scale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
    )
  }

  LaunchedEffect(Unit) {
    alpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 500))
    delay(700)
    val isSignedIn = authRepository.checkSession()
    if (isSignedIn) {
      onNavigateToHome()
    } else {
      onNavigateToLogin()
    }
  }

  SplashScreenContent(scale = scale.value, alpha = alpha.value)
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
  val scale = remember { Animatable(0.75f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    scale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
    )
  }

  LaunchedEffect(Unit) {
    alpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 500))
    delay(700)
    onTimeout()
  }

  SplashScreenContent(scale = scale.value, alpha = alpha.value)
}

@Composable
private fun SplashScreenContent(scale: Float, alpha: Float) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by
    infiniteTransition.animateFloat(
      initialValue = 0.96f,
      targetValue = 1.05f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1600, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_scale",
    )

  Box(
    modifier =
      Modifier.fillMaxSize().background(brush = SplashGradientBrush).testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.size(220.dp)
          .offset(x = 100.dp, y = (-180).dp)
          .background(color = Color.White.copy(alpha = 0.07f), shape = CircleShape)
    )
    Box(
      modifier =
        Modifier.size(180.dp)
          .offset(x = (-90).dp, y = 190.dp)
          .background(color = Color.White.copy(alpha = 0.05f), shape = CircleShape)
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.scale(scale).alpha(alpha).padding(horizontal = 28.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Box(
          modifier =
            Modifier.size(96.dp)
              .scale(pulseScale)
              .background(
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(26.dp),
              )
        )
        Surface(
          modifier = Modifier.size(80.dp),
          shape = RoundedCornerShape(22.dp),
          color = Color.White,
          shadowElevation = 10.dp,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Surface(
              modifier = Modifier.size(58.dp),
              shape = RoundedCornerShape(16.dp),
              color = RoyalBlue.copy(alpha = 0.10f),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Image(
                  painter = painterResource(R.drawable.ic_paperwise_logo),
                  contentDescription = "PaperWise Logo",
                  modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
      ) {
        Text(
          text = "AI EXAM EVALUATION",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "PaperWise",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = (-0.4).sp,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Smart Answer Sheet Scanning & Instant Grading",
        fontFamily = FigtreeFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Color.White.copy(alpha = 0.88f),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(32.dp))

      Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp).testTag("splash_loading_indicator"),
            color = Color.White,
            strokeWidth = 2.dp,
          )
          Text(
            text = "Preparing workspace...",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
