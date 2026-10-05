package com.example.ui.exam

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exam
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TertiaryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
  viewModel: ExamViewModel,
  onNavigateBack: () -> Unit,
  onExamClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
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
    AlertDialog(
      onDismissRequest = {
        if (!uiState.isDeleting) examToDelete = null
      },
      title = {
        Text(
          text = "Delete this exam?",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleLarge,
        )
      },
      text = {
        Text(
          text = "Deleting \"${examToDelete?.name}\" will permanently delete the exam and all of its associated classes. This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            examToDelete?.id?.let { id ->
              viewModel.deleteExam(id) {
                examToDelete = null
              }
            }
          },
          enabled = !uiState.isDeleting,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("confirm_delete_exam_button"),
        ) {
          if (uiState.isDeleting) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onError,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Text("Delete")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { examToDelete = null },
          enabled = !uiState.isDeleting,
          modifier = Modifier.testTag("cancel_delete_exam_button"),
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.testTag("delete_exam_dialog"),
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Exams",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("exams_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          IconButton(
            onClick = viewModel::loadExams,
            modifier = Modifier.testTag("refresh_exams_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = viewModel::openCreateDialog,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
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
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      when {
        uiState.isLoading && uiState.exams.isEmpty() -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.primary,
              strokeWidth = 3.dp,
            )
          }
        }

        uiState.errorMessage != null && uiState.exams.isEmpty() -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = "Unable to load exams",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = uiState.errorMessage ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = viewModel::loadExams,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
              Text("Retry")
            }
          }
        }

        uiState.exams.isEmpty() -> {
          // Clean empty state (NO mock data!)
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(64.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.FolderOpen,
                  contentDescription = "No exams",
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(32.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "No exams found",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Create your first exam to organize classes and scan papers.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
              onClick = viewModel::openCreateDialog,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.testTag("empty_state_create_exam_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Create Exam")
            }
          }
        }

        else -> {
          LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
              .fillMaxSize()
              .testTag("exams_list"),
          ) {
            items(uiState.exams, key = { it.id ?: it.name }) { exam ->
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
  val (badgeBg, badgeFg) = when (exam.type.lowercase()) {
    "mid term" -> Pair(Color(0xFFDBEAFE), PrimaryBlue)
    "final" -> Pair(Color(0xFFEDE9FE), Color(0xFF5B21B6))
    "supply" -> Pair(Color(0xFFFEF3C7), TertiaryAmber)
    else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("exam_card_${exam.name}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = exam.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeBg,
          ) {
            Text(
              text = exam.type,
              color = badgeFg,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Date",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = exam.date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Delete exam button with trash icon
      IconButton(
        onClick = onDeleteClick,
        modifier = Modifier
          .size(40.dp)
          .testTag("delete_exam_button_${exam.name}"),
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete Exam",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp),
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Exam",
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}
