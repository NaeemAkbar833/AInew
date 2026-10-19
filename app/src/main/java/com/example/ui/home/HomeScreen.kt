package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onSignedOut: () -> Unit,
  onNavigateToExams: () -> Unit = {},
  onOpenCreateExam: () -> Unit = {},
  onNavigateToResults: () -> Unit = {},
  onNavigateToClasses: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var showSignOutDialog by remember { mutableStateOf(false) }
  val scrollState = rememberScrollState()

  LaunchedEffect(uiState.placeholderMessage) {
    uiState.placeholderMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.dismissPlaceholderMessage()
    }
  }

  if (showSignOutDialog) {
    AlertDialog(
      onDismissRequest = { showSignOutDialog = false },
      shape = RoundedCornerShape(20.dp),
      containerColor = Color.White,
      title = {
        Text(
          text = "Sign Out",
          fontFamily = BricolageGrotesqueFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextPrimary,
        )
      },
      text = {
        Text(
          text = "Are you sure you want to sign out of GradeScan?",
          fontFamily = FigtreeFontFamily,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = TextSecondary,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showSignOutDialog = false
            viewModel.signOut(onSignedOut)
          },
          modifier = Modifier.testTag("confirm_sign_out_button"),
        ) {
          Text(
            text = "Sign Out",
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showSignOutDialog = false }) {
          Text(
            text = "Cancel",
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = TextSecondary,
          )
        }
      },
      modifier = Modifier.testTag("sign_out_dialog"),
    )
  }

  val teacherEmail = uiState.teacherEmail ?: "teacher@woofas.com"
  val initial = teacherEmail.take(1).uppercase()

  // Royal-blue gradient starting at the top of the screen, extending behind the
  // welcome/greeting area, and blending smoothly into BackgroundLight (#EEF2F9)
  // behind the first row of quick-action cards.
  val topHeaderBlendGradient = remember {
    Brush.verticalGradient(
      colorStops = arrayOf(
        0.00f to DarkRoyalBlue,
        0.36f to PrimaryBlue,
        0.58f to Color(0xFF3358DF),
        0.76f to Color(0xFF6E8CEF),
        0.90f to Color(0xFFC2D3FA),
        1.00f to BackgroundLight,
      )
    )
  }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    modifier = modifier.testTag("home_screen"),
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues)
        .verticalScroll(scrollState),
    ) {
      // 1. Top Gradient Backdrop + Welcome Area + First Row of Quick Actions
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(topHeaderBlendGradient),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        ) {
          Spacer(modifier = Modifier.height(8.dp))

          // Top Bar: Brand Identity & Sign Out Action
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier.size(36.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = initial,
                    color = Color.White,
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "GradeScan",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "AI Exam Evaluation",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.80f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Box(
              modifier = Modifier
                .size(48.dp)
                .clickable(
                  role = Role.Button,
                  onClick = { showSignOutDialog = true },
                )
                .testTag("home_sign_out_button"),
              contentAlignment = Alignment.CenterEnd,
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color.White.copy(alpha = 0.16f))
                  .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(10.dp),
                  ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = "Sign Out",
                  tint = Color.White,
                  modifier = Modifier.size(17.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 2. Welcome / Greeting Area
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("teacher_info_card"),
          ) {
            Text(
              text = "Welcome back,",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = teacherEmail,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 20.sp,
              lineHeight = 25.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Set up exams, organize class rosters, and review AI-graded answer sheets.",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              color = Color.White.copy(alpha = 0.88f),
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          // 3. First Row of Quick-Action Cards (sits across the smooth blue-to-light fade)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            HomeQuickActionCard(
              title = "Create Exam",
              description = "Set up a new exam & date",
              icon = Icons.AutoMirrored.Outlined.NoteAdd,
              iconTint = DarkRoyalBlue,
              iconContainerColor = LightBlueHighlight,
              isPrimaryAccent = true,
              onClick = onOpenCreateExam,
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("action_create_exam"),
            )

            HomeQuickActionCard(
              title = "Exams",
              description = "Manage exams & schedules",
              icon = Icons.Outlined.Description,
              iconTint = PrimaryBlue,
              iconContainerColor = SoftBlueTint,
              onClick = onNavigateToExams,
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("action_exams"),
            )
          }
        }
      }

      // Main Body below the gradient blend
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp),
      ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Second Row of Quick-Action Cards
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          HomeQuickActionCard(
            title = "Classes",
            description = "Rosters & question papers",
            icon = Icons.Outlined.Book,
            iconTint = PrimaryBlue,
            iconContainerColor = SoftBlueTint,
            onClick = onNavigateToClasses,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .testTag("action_classes"),
          )

          HomeQuickActionCard(
            title = "Results",
            description = "Approved scores & reports",
            icon = Icons.Outlined.Assessment,
            iconTint = PrimaryBlue,
            iconContainerColor = SoftBlueTint,
            onClick = onNavigateToResults,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .testTag("action_results"),
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Recent Activity Section
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Recent activity",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          TextButton(onClick = onNavigateToResults) {
            Text(
              text = "View results",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = PrimaryBlue,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle, lightweight empty state
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color.White.copy(alpha = 0.60f),
          border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.65f)),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_empty_state_card"),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(9.dp),
              color = SoftBlueTint,
              border = BorderStroke(1.dp, LightBlueHighlight),
              modifier = Modifier.size(34.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.History,
                  contentDescription = null,
                  tint = PrimaryBlue.copy(alpha = 0.85f),
                  modifier = Modifier.size(17.dp),
                )
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "No recent activity",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Scanned answer sheets and approved evaluations will appear here.",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
private fun HomeQuickActionCard(
  title: String,
  description: String,
  icon: ImageVector,
  iconTint: Color,
  iconContainerColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isPrimaryAccent: Boolean = false,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(
      width = 1.dp,
      color = if (isPrimaryAccent) PrimaryBlue.copy(alpha = 0.28f) else BorderGray,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .heightIn(min = 112.dp)
      .clickable(
        role = Role.Button,
        onClick = onClick,
      ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          shape = RoundedCornerShape(9.dp),
          color = iconContainerColor,
          border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
          modifier = Modifier.size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = title,
              tint = iconTint,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = if (isPrimaryAccent) PrimaryBlue else TextSecondary.copy(alpha = 0.6f),
          modifier = Modifier.size(16.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = title,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          fontFamily = FigtreeFontFamily,
          color = TextSecondary,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

