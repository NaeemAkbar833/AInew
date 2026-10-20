package com.example.ui.exam

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClassRoom
import com.example.ui.classes.AddClassDialog
import com.example.ui.classes.ClassViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGradient
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// Type-specific palette tokens matching ExamsScreen visual direction
private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamDetailScreen(
  examId: String,
  examViewModel: ExamViewModel,
  classViewModel: ClassViewModel,
  onNavigateBack: () -> Unit,
  onClassClick: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val examUiState by examViewModel.uiState.collectAsStateWithLifecycle()
  val classUiState by classViewModel.uiState.collectAsStateWithLifecycle()
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

  if (showDeleteExamDialog) {
    com.example.ui.components.GradeScanConfirmationDialog(
      title = "Delete this exam?",
      subtitle = "Permanent deletion",
      message = "Deleting \"${examUiState.selectedExam?.name}\" will also permanently delete all of its classes. This action cannot be undone.",
      confirmText = "Delete Exam",
      dismissText = "Cancel",
      icon = Icons.Default.DeleteOutline,
      isDestructive = true,
      isLoading = examUiState.isDeleting,
      dialogTestTag = "delete_exam_from_detail_dialog",
      confirmTestTag = "confirm_delete_exam_from_detail_button",
      dismissTestTag = "cancel_delete_exam_from_detail_button",
      onConfirm = {
        examViewModel.deleteExam(examId) {
          showDeleteExamDialog = false
          onNavigateBack()
        }
      },
      onDismiss = {
        if (!examUiState.isDeleting) showDeleteExamDialog = false
      },
    )
  }

  if (classToDelete != null) {
    com.example.ui.components.GradeScanConfirmationDialog(
      title = "Delete this class?",
      subtitle = "Permanent deletion",
      message = "Are you sure you want to permanently delete \"${classToDelete?.subjectName}\"? This action cannot be undone.",
      confirmText = "Delete Class",
      dismissText = "Cancel",
      icon = Icons.Default.DeleteOutline,
      isDestructive = true,
      isLoading = classUiState.isDeleting,
      dialogTestTag = "delete_class_dialog",
      confirmTestTag = "confirm_delete_class_button",
      dismissTestTag = "cancel_delete_class_button",
      onConfirm = {
        classToDelete?.id?.let { cid ->
          classViewModel.deleteClass(cid) {
            classToDelete = null
          }
        }
      },
      onDismiss = {
        if (!classUiState.isDeleting) classToDelete = null
      },
    )
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
      .testTag("exam_detail_screen"),
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
                  .testTag("exam_detail_back_button"),
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
                  text = "Exam Details",
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
                  text = "View and manage exam classes",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.92f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              if (examUiState.selectedExam != null) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clickable(
                      role = Role.Button,
                      onClick = { showDeleteExamDialog = true },
                    )
                    .testTag("delete_exam_detail_action"),
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
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete Exam",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              } else {
                Spacer(modifier = Modifier.width(6.dp))
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
        if (examUiState.isLoadingDetail && examUiState.selectedExam == null) {
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
                text = "Loading exam details…",
                fontFamily = FigtreeFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
              )
            }
          }
        } else {
          val exam = examUiState.selectedExam
          if (exam == null) {
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
                    text = "Exam not found",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "This exam may have been removed or is no longer available.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(18.dp))
                  Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier.heightIn(min = 44.dp),
                  ) {
                    Text(
                      text = "Back to Exams",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          } else {
            val (badgeBg, badgeFg, iconBg, iconTint) = when (exam.type.lowercase()) {
              "mid term" -> listOf(MidTermTint, MidTermText, MidTermTint, PrimaryBlue)
              "final" -> listOf(FinalTint, FinalText, FinalTint, FinalText)
              "supply" -> listOf(TertiaryAmberContainer, StatusAmber, TertiaryAmberContainer, StatusAmber)
              else -> listOf(SecondaryTealContainer, SecondaryTeal, SecondaryTealContainer, SecondaryTeal)
            }

            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
            ) {
              // Exam Information Card styled to match ExamCard in ExamsScreen
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("exam_info_card"),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = iconBg,
                      border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.14f)),
                      modifier = Modifier.size(42.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Outlined.Description,
                          contentDescription = null,
                          tint = iconTint,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = exam.name,
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )

                      Spacer(modifier = Modifier.height(5.dp))

                      FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = badgeBg,
                          border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.12f)),
                          modifier = Modifier.align(Alignment.CenterVertically),
                        ) {
                          Text(
                            text = exam.type,
                            color = badgeFg,
                            fontFamily = FigtreeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                          )
                        }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))
                  HorizontalDivider(color = BorderGray, thickness = 1.dp)
                  Spacer(modifier = Modifier.height(10.dp))

                  FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = BackgroundLight,
                      modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.CalendarToday,
                          contentDescription = "Exam Date",
                          tint = TextSecondary,
                          modifier = Modifier.size(12.dp),
                        )
                        Text(
                          text = "Exam Date: ${exam.date}",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = TextSecondary,
                        )
                      }
                    }

                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = SoftBlueTint,
                      border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
                      modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                      Text(
                        text = "${classUiState.classes.size} ${if (classUiState.classes.size == 1) "Class" else "Classes"}",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Classes Section Header with "Add Class" Button
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
                    text = "Classes",
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
                      text = "${classUiState.classes.size}",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = PrimaryBlue,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                    )
                  }
                }

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
                      onClick = classViewModel::openAddClassDialog,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("add_class_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "Add Class",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              when {
                classUiState.isLoading && classUiState.classes.isEmpty() -> {
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

                classUiState.classes.isEmpty() -> {
                  Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("classes_empty_state_card"),
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
                            imageVector = Icons.Outlined.School,
                            contentDescription = "No classes",
                            tint = RoyalBlue,
                            modifier = Modifier.size(22.dp),
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(12.dp))

                      Text(
                        text = "No classes added yet",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                      )

                      Spacer(modifier = Modifier.height(5.dp))

                      Text(
                        text = "Tap '+ Add Class' to add a class session to this exam.",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }
                }

                else -> {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit = {},
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingBadgeBg = if (isMorning) TertiaryAmberContainer else FinalTint
  val timingBadgeFg = if (isMorning) StatusAmber else FinalText
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

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
      .testTag("class_card_${classRoom.subjectName}"),
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
            imageVector = Icons.Outlined.School,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = classRoom.subjectName,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(3.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(11.dp),
          )
          Text(
            text = "${classRoom.session} • ${classRoom.semester}",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = timingBadgeBg,
            border = BorderStroke(1.dp, timingBadgeFg.copy(alpha = 0.12f)),
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            ) {
              Icon(
                imageVector = timingIcon,
                contentDescription = null,
                tint = timingBadgeFg,
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = classRoom.timing,
                color = timingBadgeFg,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MidTermTint,
            border = BorderStroke(1.dp, MidTermText.copy(alpha = 0.12f)),
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Grade,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${classRoom.totalMarks} Marks",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MidTermText,
              )
            }
          }

          if (!classRoom.questionPaperPath.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = SecondaryTealContainer,
              border = BorderStroke(1.dp, SecondaryTeal.copy(alpha = 0.14f)),
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.AttachFile,
                  contentDescription = null,
                  tint = SecondaryTeal,
                  modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Question Paper Attached",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.sp,
                  color = SecondaryTeal,
                  fontWeight = FontWeight.Bold,
                )
              }
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
            .testTag("delete_class_button_${classRoom.subjectName}"),
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
              contentDescription = "Delete Class",
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
              contentDescription = "Open Class",
              tint = PrimaryBlue,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}
