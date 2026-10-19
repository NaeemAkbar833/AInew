package com.example.ui.student

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AttachFile
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
import androidx.compose.ui.semantics.Role
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        ) {
          val classRoom = uiState.classRoom
          if (classRoom != null) {
            val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
            val timingBadgeBg = if (isMorning) TertiaryAmberContainer else AccentVioletContainer
            val timingBadgeFg = if (isMorning) StatusAmber else AccentViolet
            val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_info_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(15.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SecondaryTealContainer,
                    modifier = Modifier.size(40.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = SecondaryTeal,
                        modifier = Modifier.size(19.dp),
                      )
                    }
                  }

                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                      text = classRoom.subjectName,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 16.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      lineHeight = 21.sp,
                    )

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp),
                      )
                      Text(
                        text = "${classRoom.session} • ${classRoom.semester}",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.5.sp,
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = LightBlueHighlight,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Grade,
                        contentDescription = null,
                        tint = RoyalBlue,
                        modifier = Modifier.size(12.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "${classRoom.totalMarks} Marks",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkRoyalBlue,
                      )
                    }
                  }

                  if (!classRoom.questionPaperPath.isNullOrBlank()) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = LightBlueHighlight,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Default.AttachFile,
                          contentDescription = null,
                          tint = DarkRoyalBlue,
                          modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = "Question Paper Attached",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 11.5.sp,
                          color = DarkRoyalBlue,
                          fontWeight = FontWeight.Bold,
                        )
                      }
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(18.dp))
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
              modifier = Modifier.weight(1f),
            ) {
              Text(
                text = "Students",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              if (uiState.students.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = LightBlueHighlight,
                ) {
                  Text(
                    text = "${uiState.students.size}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkRoyalBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val scanShape = RoundedCornerShape(10.dp)
              Surface(
                shape = scanShape,
                color = LightBlueHighlight,
                border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.22f)),
                modifier = Modifier
                  .heightIn(min = 36.dp)
                  .clip(scanShape)
                  .clickable(
                    enabled = !uiState.isExtracting,
                    onClick = {
                      val uri = createImageUri(context)
                      photoUri = uri
                      cameraLauncher.launch(uri)
                    }
                  )
                  .padding(horizontal = 10.dp, vertical = 7.dp)
                  .testTag("scan_student_list_button"),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  if (uiState.isExtracting) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(14.dp),
                      strokeWidth = 2.dp,
                      color = RoyalBlue
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.PhotoCamera,
                      contentDescription = if (uiState.pendingCaptureUris.isEmpty()) "Scan Student List" else "Add another page",
                      tint = if (uiState.pendingCaptureUris.isEmpty()) DarkRoyalBlue else StatusAmber,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (uiState.pendingCaptureUris.isEmpty()) "Scan" else "Add Page",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (uiState.pendingCaptureUris.isEmpty()) DarkRoyalBlue else StatusAmber,
                    )
                  }
                }
              }

              if (uiState.pendingCaptureUris.isEmpty()) {
                val btnShape = RoundedCornerShape(10.dp)
                Box(
                  modifier = Modifier
                    .heightIn(min = 36.dp)
                    .shadow(elevation = 2.dp, shape = btnShape, spotColor = DarkRoyalBlue.copy(alpha = 0.25f))
                    .clip(btnShape)
                    .background(PrimaryBlueGradient)
                    .clickable(onClick = viewModel::openAddDialog)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("add_student_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Add Student",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                  }
                }
              }
            }
          }

          // Dedicated Pending Scan Action Bar (prevents header button collision)
          if (uiState.pendingCaptureUris.isNotEmpty() && !uiState.isExtracting) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = LightBlueHighlight,
              border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "${uiState.pendingCaptureUris.size} ${if (uiState.pendingCaptureUris.size == 1) "Page" else "Pages"} Ready to Extract",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = DarkRoyalBlue,
                  modifier = Modifier.weight(1f),
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clickable(
                        role = Role.Button,
                        onClick = {
                          viewModel.startExtractionFromPending(context, classId)
                        },
                      )
                      .testTag("finish_scan_list_button"),
                    contentAlignment = Alignment.CenterEnd,
                  ) {
                    Box(
                      modifier = Modifier
                        .padding(end = 3.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                        .border(1.dp, BorderGray, RoundedCornerShape(9.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finish and Extract",
                        tint = StatusSuccess,
                        modifier = Modifier.size(17.dp),
                      )
                    }
                  }

                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clickable(
                        role = Role.Button,
                        onClick = viewModel::clearPendingPages,
                      )
                      .testTag("cancel_scan_list_button"),
                    contentAlignment = Alignment.CenterStart,
                  ) {
                    Box(
                      modifier = Modifier
                        .padding(start = 3.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                        .border(1.dp, BorderGray, RoundedCornerShape(9.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Scan",
                        tint = StatusWarning,
                        modifier = Modifier.size(17.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          if (uiState.isExtracting) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = LightBlueHighlight,
              border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = when {
                    uiState.extractionProgress < 0.1f -> "Starting extraction..."
                    uiState.extractionProgress < 0.4f -> "Optimizing photos for AI..."
                    uiState.extractionProgress < 0.6f -> "Sending to GradeScan AI..."
                    uiState.extractionProgress < 0.95f -> "Processing student list..."
                    else -> "Finalizing..."
                  },
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.sp,
                  color = DarkRoyalBlue,
                  fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                  progress = { uiState.extractionProgress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = RoyalBlue,
                  trackColor = Color.White,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("students_empty_state_card"),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Surface(
                    shape = CircleShape,
                    color = LightBlueHighlight,
                    modifier = Modifier.size(46.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "No students",
                        tint = RoyalBlue,
                        modifier = Modifier.size(22.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))

                  Text(
                    text = "No students added yet",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = "Tap '+ Add Student' to enroll students in this class.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )

                  Spacer(modifier = Modifier.height(14.dp))

                  val emptyBtnShape = RoundedCornerShape(12.dp)
                  Box(
                    modifier = Modifier
                      .heightIn(min = 40.dp)
                      .shadow(elevation = 2.dp, shape = emptyBtnShape, spotColor = DarkRoyalBlue.copy(alpha = 0.25f))
                      .clip(emptyBtnShape)
                      .background(PrimaryBlueGradient)
                      .clickable(onClick = viewModel::openAddDialog)
                      .padding(horizontal = 16.dp, vertical = 8.dp)
                      .testTag("empty_state_add_student_button"),
                    contentAlignment = Alignment.Center,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
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

            else -> {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentCard(
  student: Student,
  onClick: () -> Unit = {},
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(16.dp)
  Card(
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("student_card_${student.rollNumber}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 13.dp, vertical = 12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Surface(
        shape = RoundedCornerShape(11.dp),
        color = LightBlueHighlight,
        modifier = Modifier
          .padding(top = 1.dp)
          .size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(11.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = student.name,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 20.sp,
        )

        if (student.fatherName.isNotBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.SupervisorAccount,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "Father: ${student.fatherName}",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              color = TextSecondary,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              lineHeight = 16.5.sp,
            )
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = LightBlueHighlight,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = DarkRoyalBlue,
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = student.rollNumber,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = DarkRoyalBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        IconButton(
          onClick = onDeleteClick,
          modifier = Modifier
            .size(48.dp)
            .testTag("student_delete_button_${student.rollNumber}"),
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFF8FAFC))
              .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Student",
              tint = Color(0xFF94A3B8),
              modifier = Modifier.size(16.dp),
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
          contentDescription = "Open Student",
          tint = TextSecondary,
          modifier = Modifier.size(12.dp),
        )
      }
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
