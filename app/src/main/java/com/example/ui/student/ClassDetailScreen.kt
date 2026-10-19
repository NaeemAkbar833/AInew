package com.example.ui.student

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
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
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
  classId: String,
  viewModel: StudentViewModel,
  onNavigateBack: () -> Unit,
  onStudentClick: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollState = rememberScrollState()
  val context = LocalContext.current

  var photoUri by remember { mutableStateOf<Uri?>(null) }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
    onResult = { success ->
      if (success) {
        photoUri?.let { uri ->
          viewModel.addPendingPage(uri)
        }
      }
    }
  )

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

  LaunchedEffect(uiState.batchErrorMessage) {
    uiState.batchErrorMessage?.let { message ->
      if (!uiState.isReviewDialogOpen) {
        snackbarHostState.showSnackbar(message)
        viewModel.dismissBatchErrorMessage()
      }
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

  if (uiState.isReviewDialogOpen) {
    ImportReviewDialog(
      extractedStudents = uiState.extractedStudents,
      isSaving = uiState.isBatchSaving,
      errorMessage = uiState.batchErrorMessage,
      onDismissRequest = viewModel::closeReviewDialog,
      onConfirm = { viewModel.confirmBatchImport(classId) },
      onUpdateStudent = viewModel::updateExtractedStudent,
      onDeleteStudent = viewModel::removeExtractedStudent,
      onDismissError = viewModel::dismissBatchErrorMessage
    )
  }

  if (studentToDelete != null) {
    com.example.ui.components.GradeScanConfirmationDialog(
      title = "Delete this student?",
      subtitle = "Permanent deletion",
      message = "Are you sure you want to permanently delete \"${studentToDelete?.name}\" (Roll No: ${studentToDelete?.rollNumber})? This action cannot be undone.",
      confirmText = "Delete",
      dismissText = "Cancel",
      icon = Icons.Default.DeleteOutline,
      isDestructive = true,
      isLoading = uiState.isDeleting,
      dialogTestTag = "delete_student_dialog",
      confirmTestTag = "confirm_delete_student_button",
      dismissTestTag = "cancel_delete_student_button",
      onConfirm = {
        studentToDelete?.id?.let { sid ->
          viewModel.deleteStudent(sid) {
            studentToDelete = null
          }
        }
      },
      onDismiss = {
        if (!uiState.isDeleting) studentToDelete = null
      },
    )
  }

  Scaffold(
    containerColor = BackgroundLight,
    topBar = {
      GradeScanStitchHeader(
        title = uiState.classRoom?.subjectName ?: "Class Details",
        subtitle = uiState.classRoom?.let { "${it.session} • ${it.semester} Semester • ${it.timing}" } ?: "Enrolled student roster",
        badgeText = "CLASS ROSTER",
        onBackClick = onNavigateBack,
        backTestTag = "class_detail_back_button",
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("class_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading && uiState.students.isEmpty() && uiState.classRoom == null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = RoyalBlue,
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
          val classRoom = uiState.classRoom
          if (classRoom != null) {
            val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
            val timingBadgeBg = if (isMorning) TertiaryAmberContainer else AccentVioletContainer
            val timingBadgeFg = if (isMorning) StatusAmber else AccentViolet
            val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
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
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = SecondaryTealContainer,
                      modifier = Modifier.size(48.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.School,
                          contentDescription = null,
                          tint = SecondaryTeal,
                          modifier = Modifier.size(24.dp),
                        )
                      }
                    }

                    Text(
                      text = classRoom.subjectName,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 19.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                    )
                  }

                  Spacer(modifier = Modifier.width(8.dp))

                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = timingBadgeBg,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.CalendarMonth,
                      contentDescription = null,
                      tint = TextSecondary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = classRoom.session,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
                      color = TextSecondary,
                      fontWeight = FontWeight.Medium,
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.School,
                      contentDescription = null,
                      tint = TextSecondary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = classRoom.semester,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
                      color = TextSecondary,
                      fontWeight = FontWeight.Medium,
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Grade,
                      contentDescription = null,
                      tint = RoyalBlue,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "${classRoom.totalMarks} Marks",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(24.dp))
          }

          // Students Section Header
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
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              if (uiState.students.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = LightBlueHighlight,
                ) {
                  Text(
                    text = "${uiState.students.size}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkRoyalBlue,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                  )
                }
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (uiState.pendingCaptureUris.isNotEmpty() && !uiState.isExtracting) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = LightBlueHighlight,
                ) {
                  Text(
                    text = "${uiState.pendingCaptureUris.size} Pages",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkRoyalBlue,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                  )
                }

                IconButton(
                  onClick = {
                    viewModel.startExtractionFromPending(context, classId)
                  },
                  modifier = Modifier.testTag("finish_scan_list_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Finish and Extract",
                    tint = StatusSuccess
                  )
                }

                IconButton(
                  onClick = viewModel::clearPendingPages,
                  modifier = Modifier.testTag("cancel_scan_list_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Scan",
                    tint = StatusWarning
                  )
                }
              }

              IconButton(
                onClick = {
                  val uri = createImageUri(context)
                  photoUri = uri
                  cameraLauncher.launch(uri)
                },
                enabled = !uiState.isExtracting,
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(LightBlueHighlight)
                  .size(40.dp)
                  .testTag("scan_student_list_button")
              ) {
                if (uiState.isExtracting) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RoyalBlue
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = if (uiState.pendingCaptureUris.isEmpty()) "Scan Student List" else "Add another page",
                    tint = if (uiState.pendingCaptureUris.isEmpty()) DarkRoyalBlue else StatusAmber,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }

              if (uiState.pendingCaptureUris.isEmpty()) {
                val btnShape = RoundedCornerShape(12.dp)
                Box(
                  modifier = Modifier
                    .heightIn(min = 40.dp)
                    .shadow(elevation = 2.dp, shape = btnShape, spotColor = DarkRoyalBlue.copy(alpha = 0.25f))
                    .clip(btnShape)
                    .background(PrimaryBlueGradient)
                    .clickable(onClick = viewModel::openAddDialog)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("add_student_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Add Student",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                  }
                }
              }
            }
          }

          if (uiState.isExtracting) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
              progress = { uiState.extractionProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
              color = RoyalBlue,
              trackColor = LightBlueHighlight,
            )
            Text(
              text = when {
                uiState.extractionProgress < 0.1f -> "Starting extraction..."
                uiState.extractionProgress < 0.4f -> "Optimizing photos for AI..."
                uiState.extractionProgress < 0.6f -> "Sending to GradeScan AI..."
                uiState.extractionProgress < 0.95f -> "Processing student list..."
                else -> "Finalizing..."
              },
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              color = RoyalBlue,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(top = 4.dp)
            )
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
                  color = RoyalBlue,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(24.dp),
                )
              }
            }

            uiState.students.isEmpty() -> {
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    color = LightBlueHighlight,
                    modifier = Modifier.size(52.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "No students",
                        tint = RoyalBlue,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  Text(
                    text = "No students added yet",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = "Tap '+ Add Student' to enroll students in this class.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )

                  Spacer(modifier = Modifier.height(18.dp))

                  val emptyBtnShape = RoundedCornerShape(14.dp)
                  Box(
                    modifier = Modifier
                      .heightIn(min = 44.dp)
                      .shadow(elevation = 2.dp, shape = emptyBtnShape, spotColor = DarkRoyalBlue.copy(alpha = 0.25f))
                      .clip(emptyBtnShape)
                      .background(PrimaryBlueGradient)
                      .clickable(onClick = viewModel::openAddDialog)
                      .padding(horizontal = 20.dp, vertical = 10.dp)
                      .testTag("empty_state_add_student_button"),
                    contentAlignment = Alignment.Center,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "Add Student",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      )
                    }
                  }
                }
              }
            }

            else -> {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("students_list"),
              ) {
                uiState.students.forEach { student ->
                  StudentCard(
                    student = student,
                    onClick = {
                      student.id?.let { sid -> onStudentClick(sid) }
                    },
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
  onClick: () -> Unit = {},
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
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
      .testTag("student_card_${student.rollNumber}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = LightBlueHighlight,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = student.name,
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = LightBlueHighlight,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = DarkRoyalBlue,
                modifier = Modifier.size(12.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = student.rollNumber,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = DarkRoyalBlue,
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
            tint = TextSecondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Father: ${student.fatherName}",
            fontFamily = FigtreeFontFamily,
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      IconButton(
        onClick = onDeleteClick,
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(StatusWarningContainer.copy(alpha = 0.6f))
          .testTag("student_delete_button_${student.rollNumber}"),
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete Student",
          tint = StatusWarning,
          modifier = Modifier.size(18.dp),
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Student",
        tint = TextSecondary,
        modifier = Modifier.size(13.dp),
      )
    }
  }
}

private fun createImageUri(context: Context): Uri {
  val tempFile = java.io.File.createTempFile("student_list_", ".jpg", context.cacheDir).apply {
    createNewFile()
    deleteOnExit()
  }
  return androidx.core.content.FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    tempFile
  )
}
