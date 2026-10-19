package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AccentVioletContainer
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGradient
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
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
      title = {
        Text(
          text = "Sign Out",
          fontFamily = BricolageGrotesqueFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
        )
      },
      text = {
        Text(
          text = "Are you sure you want to sign out of GradeScan?",
          fontFamily = FigtreeFontFamily,
          fontSize = 14.sp,
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
          )
        }
      },
      modifier = Modifier.testTag("sign_out_dialog"),
    )
  }

  val teacherEmail = uiState.teacherEmail ?: "teacher@woofas.com"
  val initial = teacherEmail.take(1).uppercase()

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("home_screen"),
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues)
        .verticalScroll(scrollState),
    ) {
      // 1. Stitch Curved Royal-Blue Gradient Header (#1F3FBF -> #2A4FD6)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
          .background(HeaderBlueGradient)
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 22.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.24f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = initial,
                  color = Color.White,
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 19.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Welcome back,",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
              )
              Text(
                text = teacherEmail,
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          IconButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier
              .size(42.dp)
              .background(Color.White.copy(alpha = 0.18f), CircleShape)
              .testTag("home_sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = "Sign Out",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 18.dp),
      ) {
        // 2. Hero Card matching Stitch ("Ready to grade?")
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("teacher_info_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = LightBlueHighlight,
              modifier = Modifier.padding(bottom = 12.dp),
            ) {
              Text(
                text = "EXAM SETUP",
                color = DarkRoyalBlue,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Ready to grade?",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 22.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                  letterSpacing = (-0.3).sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Set up an exam, organize classes, and scan student papers.",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  color = TextSecondary,
                  lineHeight = 19.sp,
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Surface(
                shape = RoundedCornerShape(16.dp),
                color = SoftBlueTint,
                border = BorderStroke(1.dp, LightBlueHighlight),
                modifier = Modifier.size(64.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(32.dp),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stitch Gradient Primary Action Button (#1F3FBF -> #4262E6)
            val buttonShape = RoundedCornerShape(14.dp)
            Box(
              modifier = Modifier
                .heightIn(min = 46.dp)
                .shadow(
                  elevation = 3.dp,
                  shape = buttonShape,
                  spotColor = DarkRoyalBlue.copy(alpha = 0.25f),
                )
                .clip(buttonShape)
                .background(PrimaryBlueGradient)
                .clickable(onClick = onOpenCreateExam)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("action_create_exam"),
              contentAlignment = Alignment.Center,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Create exam",
                  fontFamily = FigtreeFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = Color.White,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 3. Quick Actions Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Quick actions",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          TextButton(onClick = onNavigateToExams) {
            Text(
              text = "View all >",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = PrimaryBlue,
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3 Quick Action Cards (Exams, Classes, Reports) matching Stitch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          QuickActionCard(
            title = "Exams",
            description = "Manage your exams",
            icon = Icons.Outlined.Description,
            iconTint = PrimaryBlue,
            containerColor = LightBlueHighlight,
            onClick = onNavigateToExams,
            modifier = Modifier
              .weight(1f)
              .testTag("action_exams"),
          )
          QuickActionCard(
            title = "Classes",
            description = "By exam",
            icon = Icons.Outlined.Book,
            iconTint = SecondaryTeal,
            containerColor = SecondaryTealContainer,
            onClick = onNavigateToClasses,
            modifier = Modifier
              .weight(1f)
              .testTag("action_classes"),
          )
          QuickActionCard(
            title = "Reports",
            description = "Results",
            icon = Icons.Outlined.Assessment,
            iconTint = AccentViolet,
            containerColor = AccentVioletContainer,
            onClick = onNavigateToResults,
            modifier = Modifier
              .weight(1f)
              .testTag("action_results"),
          )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 4. Recent activity Section
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Recent activity",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          TextButton(onClick = onNavigateToResults) {
            Text(
              text = "View all >",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = PrimaryBlue,
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, BorderGray),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("continue_empty_state_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Surface(
              shape = CircleShape,
              color = LightBlueHighlight,
              modifier = Modifier.size(52.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.History,
                  contentDescription = null,
                  tint = PrimaryBlue,
                  modifier = Modifier.size(24.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "No recent activity",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "Your activity will appear here once you start using the app.",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
              textAlign = TextAlign.Center,
              lineHeight = 18.sp,
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun QuickActionCard(
  title: String,
  description: String,
  icon: ImageVector,
  iconTint: Color,
  containerColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = title,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = description,
        fontFamily = FigtreeFontFamily,
        color = TextSecondary,
        fontSize = 11.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
