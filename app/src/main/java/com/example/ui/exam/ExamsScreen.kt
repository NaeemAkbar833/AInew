package com.example.ui.exam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Exam
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AccentVioletContainer
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlueGradient
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
  viewModel: ExamViewModel,
  onNavigateBack: () -> Unit,
  onExamClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var examToDelete by remember { mutableStateOf<Exam?>(null) }

  LaunchedEffect(Unit) {
    viewModel.loadExams()
  }

  LaunchedEffect(uiState.feedbackMessage) {
    uiState.feedbackMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissFeedbackMessage()
    }
  }

  LaunchedEffect(uiState.deleteErrorMessage) {
    uiState.deleteErrorMessage?.let { err ->
      snackbarHostState.showSnackbar(err)
      viewModel.dismissDeleteErrorMessage()
    }
  }

  if (uiState.isCreateDialogOpen) {
    CreateExamDialog(
      onDismissRequest = viewModel::closeCreateDialog,
      onExamCreated = {
        // Dialog already adds exam and closes
      },
      viewModel = viewModel,
    )
  }

  if (examToDelete != null) {
    com.example.ui.components.GradeScanConfirmationDialog(
      title = "Delete this exam?",
      subtitle = "Permanent deletion",
      message = "Deleting \"${examToDelete?.name}\" will permanently delete the exam and all of its associated classes. This action cannot be undone.",
      confirmText = "Delete",
      dismissText = "Cancel",
      icon = Icons.Default.DeleteOutline,
      isDestructive = true,
      isLoading = uiState.isDeleting,
      dialogTestTag = "delete_exam_dialog",
      confirmTestTag = "confirm_delete_exam_button",
      dismissTestTag = "cancel_delete_exam_button",
      onConfirm = {
        examToDelete?.id?.let { id ->
          viewModel.deleteExam(id) {
            examToDelete = null
          }
        }
      },
      onDismiss = {
        if (!uiState.isDeleting) examToDelete = null
      },
    )
  }

  Scaffold(
    containerColor = BackgroundLight,
    topBar = {
      GradeScanStitchHeader(
        title = "Exams",
        subtitle = "Create and manage examination schedules",
        badgeText = "EXAM MANAGEMENT",
        onBackClick = onNavigateBack,
        backTestTag = "exams_back_button",
        actions = {
          IconButton(
            onClick = viewModel::loadExams,
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.18f))
              .testTag("refresh_exams_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = viewModel::openCreateDialog,
        containerColor = RoyalBlue,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("create_exam_fab"),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Create Exam",
        )
      }
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("exams_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      when {
        uiState.isLoading && uiState.exams.isEmpty() -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              color = RoyalBlue,
            )
          }
        }

        uiState.errorMessage != null && uiState.exams.isEmpty() -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Surface(
                  shape = CircleShape,
                  color = StatusWarningContainer,
                  modifier = Modifier.size(60.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.ErrorOutline,
                      contentDescription = null,
                      tint = StatusWarning,
                      modifier = Modifier.size(28.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = "Unable to load exams",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = uiState.errorMessage ?: "",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  color = TextSecondary,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                  onClick = viewModel::loadExams,
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlue,
                    contentColor = Color.White,
                  ),
                ) {
                  Text(
                    text = "Retry",
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }

        uiState.exams.isEmpty() -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Surface(
                  shape = CircleShape,
                  color = LightBlueHighlight,
                  modifier = Modifier.size(68.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.FolderOpen,
                      contentDescription = "No exams",
                      tint = RoyalBlue,
                      modifier = Modifier.size(32.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "No exams found",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 19.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = "Create your first exam to organize classes and scan papers.",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 14.sp,
                  color = TextSecondary,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(22.dp))

                val buttonShape = RoundedCornerShape(14.dp)
                Box(
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .shadow(
                      elevation = 3.dp,
                      shape = buttonShape,
                      spotColor = DarkRoyalBlue.copy(alpha = 0.25f),
                    )
                    .clip(buttonShape)
                    .background(PrimaryBlueGradient)
                    .clickable(onClick = viewModel::openCreateDialog)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag("empty_state_create_exam_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Create Exam",
                      fontFamily = FigtreeFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      color = Color.White,
                    )
                  }
                }
              }
            }
          }
        }

        else -> {
          LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
              .fillMaxSize()
              .testTag("exams_list"),
          ) {
            items(uiState.exams, key = { it.id!! }) { exam ->
              ExamCard(
                exam = exam,
                onClick = {
                  exam.id?.let { id -> onExamClick(id) }
                },
                onDeleteClick = {
                  examToDelete = exam
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ExamCard(
  exam: Exam,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (badgeBg, badgeFg, iconBg, iconTint) = when (exam.type.lowercase()) {
    "mid term" -> listOf(LightBlueHighlight, DarkRoyalBlue, LightBlueHighlight, RoyalBlue)
    "final" -> listOf(AccentVioletContainer, AccentViolet, AccentVioletContainer, AccentViolet)
    "supply" -> listOf(TertiaryAmberContainer, StatusAmber, TertiaryAmberContainer, StatusAmber)
    else -> listOf(SecondaryTealContainer, SecondaryTeal, SecondaryTealContainer, SecondaryTeal)
  }

  val shape = RoundedCornerShape(20.dp)
  Card(
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("exam_card_${exam.name}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = iconBg,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = exam.name,
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeBg,
          ) {
            Text(
              text = exam.type,
              color = badgeFg,
              fontFamily = FigtreeFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.5.sp,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Date",
            tint = TextSecondary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = exam.date,
            fontFamily = FigtreeFontFamily,
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      IconButton(
        onClick = onDeleteClick,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(StatusWarningContainer.copy(alpha = 0.6f))
          .testTag("delete_exam_button_${exam.name}"),
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete Exam",
          tint = StatusWarning,
          modifier = Modifier.size(19.dp),
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Exam",
        tint = TextSecondary,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}
