package com.example.ui.student

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
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
import com.example.data.model.Student
import com.example.ui.theme.TertiaryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
  classId: String,
  viewModel: StudentViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollState = rememberScrollState()

  var studentToDelete by remember { mutableStateOf<Student?>(null) }

  LaunchedEffect(classId) {
    viewModel.loadClassAndStudents(classId)
  }

  LaunchedEffect(uiState.feedbackMessage) {
    uiState.feedbackMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.dismissFeedbackMessage()
    }
  }

  LaunchedEffect(uiState.deleteErrorMessage) {
    uiState.deleteErrorMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.dismissDeleteErrorMessage()
    }
  }

  if (uiState.isAddDialogOpen) {
    AddStudentDialog(
      classId = classId,
      onDismissRequest = viewModel::closeAddDialog,
      onStudentAdded = {
        // Dialog handles insertion and viewmodel updates list
      },
      viewModel = viewModel,
    )
  }

  // Delete Student Confirmation Dialog
  if (studentToDelete != null) {
    AlertDialog(
      onDismissRequest = {
        if (!uiState.isDeleting) studentToDelete = null
      },
      title = {
        Text(
          text = "Delete this student?",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleLarge,
        )
      },
      text = {
        Text(
          text = "Are you sure you want to permanently delete \"${studentToDelete?.name}\" (Roll No: ${studentToDelete?.rollNumber})? This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            studentToDelete?.id?.let { sid ->
              viewModel.deleteStudent(sid) {
                studentToDelete = null
              }
            }
          },
          enabled = !uiState.isDeleting,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("confirm_delete_student_button"),
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
          onClick = { studentToDelete = null },
          enabled = !uiState.isDeleting,
          modifier = Modifier.testTag("cancel_delete_student_button"),
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.testTag("delete_student_dialog"),
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = uiState.classRoom?.subjectName ?: "Class Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("class_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("class_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading && uiState.students.isEmpty() && uiState.classRoom == null) {
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
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          // Class Information Card
          val classRoom = uiState.classRoom
          if (classRoom != null) {
            val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
            val timingBadgeBg = if (isMorning) Color(0xFFFEF3C7) else Color(0xFFEDE9FE)
            val timingBadgeFg = if (isMorning) TertiaryAmber else Color(0xFF5B21B6)
            val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_info_card"),
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
                    text = classRoom.subjectName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                  )

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
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.CalendarMonth,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = classRoom.session,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.School,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = classRoom.semester,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Grade,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "${classRoom.totalMarks} Marks",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(28.dp))
          }

          // Students Section Header with "+ Add Student" Button
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
                text = "Students",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
              )
              if (uiState.students.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Text(
                    text = "${uiState.students.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }

            OutlinedButton(
              onClick = viewModel::openAddDialog,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("add_student_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Add Student",
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          when {
            uiState.isLoading && uiState.students.isEmpty() -> {
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

            uiState.students.isEmpty() -> {
              // Students Empty State
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("students_empty_state_card"),
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
                        imageVector = Icons.Default.Group,
                        contentDescription = "No students",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  Text(
                    text = "No students added yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = "Tap '+ Add Student' to enroll students in this class.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  )

                  Spacer(modifier = Modifier.height(16.dp))

                  Button(
                    onClick = viewModel::openAddDialog,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("empty_state_add_student_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Student")
                  }
                }
              }
            }

            else -> {
              // Student List
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("students_list"),
              ) {
                uiState.students.forEach { student ->
                  StudentCard(
                    student = student,
                    onDeleteClick = {
                      studentToDelete = student
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

@Composable
fun StudentCard(
  student: Student,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("student_card_${student.rollNumber}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = student.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = student.rollNumber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.SupervisorAccount,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Father: ${student.fatherName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      IconButton(
        onClick = onDeleteClick,
        modifier = Modifier
          .size(36.dp)
          .testTag("delete_student_button_${student.rollNumber}"),
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete Student",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
