package com.example.ui.exam

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClassRoom
import com.example.ui.classes.AddClassDialog
import com.example.ui.classes.ClassViewModel
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.TertiaryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
  examId: String,
  examViewModel: ExamViewModel,
  classViewModel: ClassViewModel,
  onNavigateBack: () -> Unit,
  onClassClick: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val examUiState by examViewModel.uiState.collectAsState()
  val classUiState by classViewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollState = rememberScrollState()

  var showDeleteExamDialog by remember { mutableStateOf(false) }
  var classToDelete by remember { mutableStateOf<ClassRoom?>(null) }

  LaunchedEffect(examId) {
    examViewModel.loadExamDetail(examId)
    classViewModel.loadClasses(examId)
  }

  LaunchedEffect(classUiState.feedbackMessage) {
    classUiState.feedbackMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      classViewModel.dismissFeedbackMessage()
    }
  }

  LaunchedEffect(classUiState.deleteErrorMessage) {
    classUiState.deleteErrorMessage?.let { err ->
      snackbarHostState.showSnackbar(err)
      classViewModel.dismissDeleteErrorMessage()
    }
  }

  LaunchedEffect(examUiState.deleteErrorMessage) {
    examUiState.deleteErrorMessage?.let { err ->
      snackbarHostState.showSnackbar(err)
      examViewModel.dismissDeleteErrorMessage()
    }
  }

  if (classUiState.isAddClassDialogOpen) {
    AddClassDialog(
      examId = examId,
      onDismissRequest = classViewModel::closeAddClassDialog,
      onClassCreated = {
        // Class is added to list and dialog dismissed by viewModel
      },
      viewModel = classViewModel,
    )
  }

  // Delete Exam Confirmation Dialog
  if (showDeleteExamDialog) {
    AlertDialog(
      onDismissRequest = {
        if (!examUiState.isDeleting) showDeleteExamDialog = false
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
          text = "Deleting \"${examUiState.selectedExam?.name}\" will also permanently delete all of its classes. This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            examViewModel.deleteExam(examId) {
              showDeleteExamDialog = false
              onNavigateBack()
            }
          },
          enabled = !examUiState.isDeleting,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("confirm_delete_exam_from_detail_button"),
        ) {
          if (examUiState.isDeleting) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onError,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Text("Delete Exam")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteExamDialog = false },
          enabled = !examUiState.isDeleting,
          modifier = Modifier.testTag("cancel_delete_exam_from_detail_button"),
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.testTag("delete_exam_from_detail_dialog"),
    )
  }

  // Delete Class Confirmation Dialog
  if (classToDelete != null) {
    AlertDialog(
      onDismissRequest = {
        if (!classUiState.isDeleting) classToDelete = null
      },
      title = {
        Text(
          text = "Delete this class?",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleLarge,
        )
      },
      text = {
        Text(
          text = "Are you sure you want to permanently delete \"${classToDelete?.subjectName}\"? This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            classToDelete?.id?.let { cid ->
              classViewModel.deleteClass(cid) {
                classToDelete = null
              }
            }
          },
          enabled = !classUiState.isDeleting,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("confirm_delete_class_button"),
        ) {
          if (classUiState.isDeleting) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onError,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Text("Delete Class")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { classToDelete = null },
          enabled = !classUiState.isDeleting,
          modifier = Modifier.testTag("cancel_delete_class_button"),
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.testTag("delete_class_dialog"),
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = examUiState.selectedExam?.name ?: "Exam Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("exam_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          if (examUiState.selectedExam != null) {
            IconButton(
              onClick = { showDeleteExamDialog = true },
              modifier = Modifier.testTag("delete_exam_detail_action"),
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Exam",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("exam_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      if (examUiState.isLoadingDetail && examUiState.selectedExam == null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
          )
        }
      } else {
        val exam = examUiState.selectedExam
        if (exam == null) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = "Exam not found",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
              Text("Back to Exams")
            }
          }
        } else {
          val (badgeBg, badgeFg) = when (exam.type.lowercase()) {
            "mid term" -> Pair(Color(0xFFDBEAFE), PrimaryBlue)
            "final" -> Pair(Color(0xFFEDE9FE), Color(0xFF5B21B6))
            "supply" -> Pair(Color(0xFFFEF3C7), TertiaryAmber)
            else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(horizontal = 20.dp, vertical = 16.dp),
          ) {
            // Exam Information Card (Strictly Exam Name, Exam Type, Exam Date)
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("exam_info_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = exam.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                  )

                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                  ) {
                    Text(
                      text = exam.type,
                      color = badgeFg,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Exam Date",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Exam Date: ${exam.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Classes Section Header with "Add Class" Button
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Classes",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                if (classUiState.classes.isNotEmpty()) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                  ) {
                    Text(
                      text = "${classUiState.classes.size}",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                  }
                }
              }

              // Add Class Button opens Add Class Popup Dialog
              OutlinedButton(
                onClick = classViewModel::openAddClassDialog,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_class_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Add Class",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
              classUiState.isLoading && classUiState.classes.isEmpty() -> {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }

              classUiState.classes.isEmpty() -> {
                // Classes Empty State (Clean placeholder, NO mock classes)
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("classes_empty_state_card"),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      modifier = Modifier.size(48.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.Class,
                          contentDescription = "No classes",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(24.dp),
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                      text = "No classes added yet",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                      text = "Tap '+ Add Class' to add a class session to this exam.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                  }
                }
              }

              else -> {
                // List of Real Classes Belonging to this Exam
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("classes_list"),
                ) {
                  classUiState.classes.forEach { classRoom ->
                    ClassCard(
                      classRoom = classRoom,
                      onClick = {
                        classRoom.id?.let { cid -> onClassClick(cid) }
                      },
                      onDeleteClick = {
                        classToDelete = classRoom
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit = {},
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingBadgeBg = if (isMorning) Color(0xFFFEF3C7) else Color(0xFFEDE9FE)
  val timingBadgeFg = if (isMorning) TertiaryAmber else Color(0xFF5B21B6)
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("class_card_${classRoom.subjectName}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = classRoom.subjectName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = timingBadgeBg,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Icon(
                imageVector = timingIcon,
                contentDescription = null,
                tint = timingBadgeFg,
                modifier = Modifier.size(12.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = classRoom.timing,
                color = timingBadgeFg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
              .size(32.dp)
              .testTag("delete_class_button_${classRoom.subjectName}"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Class",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = classRoom.session,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = classRoom.semester,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Grade,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${classRoom.totalMarks} Marks",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      if (!classRoom.questionPaperPath.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Icon(
              imageVector = Icons.Default.AttachFile,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Question Paper Attached",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}
