package com.example.ui.student

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGradient
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// Type-specific palette tokens matching ExamsScreen and ExamDetailScreen
private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

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

  var searchQuery by rememberSaveable { mutableStateOf("") }
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

  val filteredStudents = remember(uiState.students, searchQuery) {
    val trimmedQuery = searchQuery.trim()
    if (trimmedQuery.isEmpty()) {
      uiState.students
    } else {
      uiState.students.filter { student ->
        student.name.contains(trimmedQuery, ignoreCase = true) ||
          student.rollNumber.contains(trimmedQuery, ignoreCase = true) ||
          student.fatherName.contains(trimmedQuery, ignoreCase = true)
      }
    }
  }

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

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("class_detail_screen"),
  ) {
    // Backdrop gradient layer extending behind topBar down into upper card area
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(HeaderBlueGradient)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)
            .testTag("top_app_bar"),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clickable(
                    role = Role.Button,
                    onClick = onNavigateBack,
                  )
                  .testTag("class_detail_back_button"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(
                      width = 1.dp,
                      color = Color.White.copy(alpha = 0.30f),
                      shape = CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = "Class Details",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                  color = Color.White,
                  letterSpacing = (-0.2).sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Manage students and their answer papers",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.92f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clickable(
                    role = Role.Button,
                    onClick = { viewModel.loadClassAndStudents(classId) },
                  )
                  .testTag("refresh_class_detail_button"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(
                      width = 1.dp,
                      color = Color.White.copy(alpha = 0.30f),
                      shape = CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
      ) {
        when {
          uiState.isLoading && uiState.students.isEmpty() && uiState.classRoom == null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                CircularProgressIndicator(
                  color = RoyalBlue,
                  strokeWidth = 3.dp,
                  modifier = Modifier.size(34.dp),
                )
                Text(
                  text = "Loading class details…",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextSecondary,
                )
              }
            }
          }

          uiState.errorMessage != null && uiState.students.isEmpty() && uiState.classRoom == null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StatusWarningContainer,
                    border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.18f)),
                    modifier = Modifier.size(48.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier.size(22.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = "Unable to load class details",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = uiState.errorMessage ?: "",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(18.dp))
                  Button(
                    onClick = { viewModel.loadClassAndStudents(classId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier.heightIn(min = 44.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Retry",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }

          else -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
            ) {
              val classRoom = uiState.classRoom
              if (classRoom != null) {
                val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
                val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight
                val timingTint = if (isMorning) StatusAmber else Color(0xFF6B46C1)
                val timingBg = if (isMorning) TertiaryAmberContainer else FinalTint
                val hasPaper = !classRoom.questionPaperPath.isNullOrBlank()

                // Class Information Summary Card
                Card(
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, BorderGray),
                  elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("class_info_card"),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(10.dp),
                  ) {
                    // 1. CLASS TITLE AREA
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = SoftBlueTint,
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.18f)),
                        modifier = Modifier.size(20.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(12.dp),
                          )
                        }
                      }

                      Spacer(modifier = Modifier.width(6.dp))

                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = classRoom.subjectName,
                          fontFamily = BricolageGrotesqueFontFamily,
                          fontSize = 14.5.sp,
                          lineHeight = 18.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextPrimary,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = SoftBlueTint,
                          border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
                        ) {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          ) {
                            Icon(
                              imageVector = Icons.Outlined.Group,
                              contentDescription = null,
                              tint = PrimaryBlue,
                              modifier = Modifier.size(11.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                              text = "${uiState.students.size} ${if (uiState.students.size == 1) "Student" else "Students"}",
                              fontFamily = FigtreeFontFamily,
                              fontSize = 10.5.sp,
                              fontWeight = FontWeight.Bold,
                              color = PrimaryBlue,
                            )
                          }
                        }
                      }
                    }

                    Spacer(modifier = Modifier.height(7.dp))
                    HorizontalDivider(color = BorderGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(7.dp))

                    // 2. ORGANIZED INFORMATION GRID (Two-column layout)
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      ClassInfoGridItem(
                        icon = Icons.Default.CalendarMonth,
                        label = "Session",
                        value = classRoom.session,
                        modifier = Modifier.weight(1f),
                      )
                      ClassInfoGridItem(
                        icon = Icons.Default.Badge,
                        label = "Semester",
                        value = classRoom.semester,
                        modifier = Modifier.weight(1f),
                      )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      ClassInfoGridItem(
                        icon = timingIcon,
                        label = "Timing",
                        value = classRoom.timing,
                        modifier = Modifier.weight(1f),
                        iconTint = timingTint,
                        iconBg = timingBg,
                      )
                      ClassInfoGridItem(
                        icon = Icons.Default.Grade,
                        label = "Total Marks",
                        value = "${classRoom.totalMarks} Marks",
                        modifier = Modifier.weight(1f),
                        iconTint = PrimaryBlue,
                        iconBg = SoftBlueTint,
                      )
                    }

                    Spacer(modifier = Modifier.height(7.dp))
                    HorizontalDivider(color = BorderGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. QUESTION PAPER STATUS SECTION
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = if (hasPaper) SecondaryTealContainer else BackgroundLight,
                      border = BorderStroke(
                        width = 1.dp,
                        color = if (hasPaper) SecondaryTeal.copy(alpha = 0.22f) else BorderGray,
                      ),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Surface(
                          shape = CircleShape,
                          color = if (hasPaper) SecondaryTeal.copy(alpha = 0.16f) else Color(0xFFE2E8F0),
                          modifier = Modifier.size(18.dp),
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Icon(
                              imageVector = if (hasPaper) Icons.Default.AttachFile else Icons.Default.Description,
                              contentDescription = null,
                              tint = if (hasPaper) SecondaryTeal else TextSecondary,
                              modifier = Modifier.size(10.5.dp),
                            )
                          }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = "QUESTION PAPER",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.3.sp,
                          )
                          Spacer(modifier = Modifier.height(1.dp))
                          Text(
                            text = if (hasPaper) "Question Paper Attached" else "Question Paper Not Attached",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPaper) SecondaryTeal else TextSecondary,
                          )
                        }

                        if (hasPaper) {
                          Surface(
                            shape = CircleShape,
                            color = SecondaryTeal.copy(alpha = 0.14f),
                          ) {
                            Icon(
                              imageVector = Icons.Default.Check,
                              contentDescription = null,
                              tint = SecondaryTeal,
                              modifier = Modifier
                                .padding(2.5.dp)
                                .size(10.dp),
                            )
                          }
                        }
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
              }

              // Students Section Header with Actions
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 2.dp, bottom = 2.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = "Students",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Surface(
                    shape = CircleShape,
                    color = SoftBlueTint,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
                  ) {
                    Text(
                      text = "${uiState.students.size}",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = PrimaryBlue,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                    )
                  }
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val scanShape = RoundedCornerShape(12.dp)
                  Surface(
                    shape = scanShape,
                    color = SoftBlueTint,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.22f)),
                    modifier = Modifier
                      .heightIn(min = 38.dp)
                      .clip(scanShape)
                      .clickable(
                        enabled = !uiState.isExtracting,
                        onClick = {
                          val uri = createImageUri(context)
                          photoUri = uri
                          cameraLauncher.launch(uri)
                        }
                      )
                      .testTag("scan_student_list_button"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center,
                    ) {
                      if (uiState.isExtracting) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(16.dp),
                          strokeWidth = 2.dp,
                          color = RoyalBlue
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = "Scanning…",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Bold,
                          color = PrimaryBlue,
                        )
                      } else {
                        Box(
                          modifier = Modifier.size(20.dp),
                          contentAlignment = Alignment.Center,
                        ) {
                          Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = if (uiState.pendingCaptureUris.isEmpty()) "Scan Student List" else "Add another page",
                            tint = if (uiState.pendingCaptureUris.isEmpty()) PrimaryBlue else StatusAmber,
                            modifier = Modifier.size(18.dp),
                          )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = if (uiState.pendingCaptureUris.isEmpty()) "Scan" else "Add Page",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (uiState.pendingCaptureUris.isEmpty()) PrimaryBlue else StatusAmber,
                          maxLines = 1,
                        )
                      }
                    }
                  }

                  if (uiState.pendingCaptureUris.isEmpty()) {
                    val btnShape = RoundedCornerShape(12.dp)
                    Box(
                      modifier = Modifier
                        .heightIn(min = 38.dp)
                        .shadow(
                          elevation = 2.dp,
                          shape = btnShape,
                          spotColor = DarkRoyalBlue.copy(alpha = 0.22f),
                        )
                        .clip(btnShape)
                        .background(PrimaryBlueGradient)
                        .clickable(
                          role = Role.Button,
                          onClick = viewModel::openAddDialog,
                        )
                        .padding(horizontal = 13.dp, vertical = 8.dp)
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

              // Dedicated Pending Scan Action Bar
              if (uiState.pendingCaptureUris.isNotEmpty() && !uiState.isExtracting) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = SoftBlueTint,
                  border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "${uiState.pendingCaptureUris.size} ${if (uiState.pendingCaptureUris.size == 1) "Page" else "Pages"} Ready to Extract",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = PrimaryBlue,
                      modifier = Modifier.weight(1f),
                    )

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Box(
                        modifier = Modifier
                          .size(44.dp)
                          .clickable(
                            role = Role.Button,
                            onClick = {
                              viewModel.startExtractionFromPending(context, classId)
                            },
                          )
                          .testTag("finish_scan_list_button"),
                        contentAlignment = Alignment.Center,
                      ) {
                        Box(
                          modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
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
                          .size(44.dp)
                          .clickable(
                            role = Role.Button,
                            onClick = viewModel::clearPendingPages,
                          )
                          .testTag("cancel_scan_list_button"),
                        contentAlignment = Alignment.Center,
                      ) {
                        Box(
                          modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
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
                  color = SoftBlueTint,
                  border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 12.dp),
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
                      color = PrimaryBlue,
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

              // Search Field for Students
              if (uiState.students.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                StudentsSearchField(
                  query = searchQuery,
                  onQueryChange = { searchQuery = it },
                  onClearQuery = { searchQuery = "" },
                )
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
                      strokeWidth = 2.5.dp,
                      modifier = Modifier.size(28.dp),
                    )
                  }
                }

                uiState.students.isEmpty() -> {
                  Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("students_empty_state_card"),
                  ) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MidTermTint,
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
                        modifier = Modifier.size(48.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = "No students",
                            tint = RoyalBlue,
                            modifier = Modifier.size(22.dp),
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(12.dp))

                      Text(
                        text = "No students added yet",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                      )

                      Spacer(modifier = Modifier.height(5.dp))

                      Text(
                        text = "Tap '+ Add Student' or 'Scan' to enroll students in this class.",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                      )

                      Spacer(modifier = Modifier.height(16.dp))

                      val emptyBtnShape = RoundedCornerShape(12.dp)
                      Box(
                        modifier = Modifier
                          .heightIn(min = 40.dp)
                          .shadow(
                            elevation = 2.dp,
                            shape = emptyBtnShape,
                            spotColor = DarkRoyalBlue.copy(alpha = 0.22f),
                          )
                          .clip(emptyBtnShape)
                          .background(PrimaryBlueGradient)
                          .clickable(
                            role = Role.Button,
                            onClick = viewModel::openAddDialog,
                          )
                          .padding(horizontal = 18.dp, vertical = 10.dp)
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                          )
                        }
                      }
                    }
                  }
                }

                filteredStudents.isEmpty() -> {
                  Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("students_filtered_empty_state"),
                  ) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftBlueTint,
                        border = BorderStroke(1.dp, LightBlueHighlight),
                        modifier = Modifier.size(42.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp),
                          )
                        }
                      }
                      Spacer(modifier = Modifier.height(10.dp))
                      Text(
                        text = "No matching students",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = "No students match \"$searchQuery\".",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                      )
                      Spacer(modifier = Modifier.height(8.dp))
                      TextButton(onClick = { searchQuery = "" }) {
                        Text(
                          text = "Clear search",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Bold,
                          color = PrimaryBlue,
                        )
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
                    filteredStudents.forEach { student ->
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
  }
}

@Composable
private fun StudentsSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val shape = RoundedCornerShape(14.dp)
  val borderColor = if (isFocused) PrimaryBlue else BorderGray
  val iconTint = if (isFocused || query.isNotBlank()) PrimaryBlue else TextSecondary

  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    interactionSource = interactionSource,
    textStyle = TextStyle(
      fontFamily = FigtreeFontFamily,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    cursorBrush = SolidColor(PrimaryBlue),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 46.dp)
      .testTag("students_search_input"),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(Color.White)
          .border(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = borderColor,
            shape = shape,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search students",
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (query.isEmpty()) {
            Text(
              text = "Search by name, roll no, or father's name…",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
        if (query.isNotEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(SoftBlueTint)
              .clickable(
                role = Role.Button,
                onClick = onClearQuery,
              )
              .testTag("students_search_clear_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search",
              tint = TextSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    },
  )
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
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(
        role = Role.Button,
        onClick = onClick,
      )
      .testTag("student_card_${student.rollNumber}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidTermTint,
        border = BorderStroke(1.dp, MidTermText.copy(alpha = 0.14f)),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = student.name,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        if (student.fatherName.isNotBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.SupervisorAccount,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(11.dp),
            )
            Text(
              text = "Father: ${student.fatherName}",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = TextSecondary,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.height(5.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = SoftBlueTint,
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Roll No: ${student.rollNumber}",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(4.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clickable(
              role = Role.Button,
              onClick = onDeleteClick,
            )
            .testTag("student_delete_button_${student.rollNumber}"),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(BackgroundLight)
              .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Student",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = SoftBlueTint,
          modifier = Modifier.size(30.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Open Student",
              tint = PrimaryBlue,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ClassInfoGridItem(
  icon: ImageVector,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  iconTint: Color = PrimaryBlue,
  iconBg: Color = SoftBlueTint,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = RoundedCornerShape(4.dp),
      color = iconBg,
      border = BorderStroke(1.dp, iconTint.copy(alpha = 0.14f)),
      modifier = Modifier.size(18.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(10.5.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(5.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label.uppercase(),
        fontFamily = FigtreeFontFamily,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(1.dp))
      Text(
        text = value,
        fontFamily = FigtreeFontFamily,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
