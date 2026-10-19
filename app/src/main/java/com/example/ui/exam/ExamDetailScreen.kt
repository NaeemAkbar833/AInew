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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.model.ClassRoom
import com.example.ui.classes.AddClassDialog
import com.example.ui.classes.ClassViewModel
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

  Scaffold(
    containerColor = BackgroundLight,
    topBar = {
      GradeScanStitchHeader(
        title = examUiState.selectedExam?.name ?: "Exam Details",
        subtitle = examUiState.selectedExam?.let { "${it.type} • ${it.date}" } ?: "Manage classes for this exam",
        badgeText = "EXAM OVERVIEW",
        onBackClick = onNavigateBack,
        backTestTag = "exam_detail_back_button",
        actions = {
          if (examUiState.selectedExam != null) {
            IconButton(
              onClick = { showDeleteExamDialog = true },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .testTag("delete_exam_detail_action"),
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Exam",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("exam_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (examUiState.isLoadingDetail && examUiState.selectedExam == null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = RoyalBlue,
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
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = StatusWarning,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onNavigateBack,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = RoyalBlue,
                contentColor = Color.White,
              ),
            ) {
              Text(
                text = "Back to Exams",
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        } else {
          val (badgeBg, badgeFg) = when (exam.type.lowercase()) {
            "mid term" -> Pair(LightBlueHighlight, DarkRoyalBlue)
            "final" -> Pair(AccentVioletContainer, AccentViolet)
            "supply" -> Pair(TertiaryAmberContainer, StatusAmber)
            else -> Pair(LightBlueHighlight, RoyalBlue)
          }

          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(horizontal = 20.dp, vertical = 18.dp),
          ) {
            // Exam Information Card
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
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
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = LightBlueHighlight,
                      modifier = Modifier.size(48.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.Description,
                          contentDescription = null,
                          tint = RoyalBlue,
                          modifier = Modifier.size(24.dp),
                        )
                      }
                    }

                    Text(
                      text = exam.name,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 19.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                    )
                  }

                  Spacer(modifier = Modifier.width(8.dp))

                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                  ) {
                    Text(
                      text = exam.type,
                      color = badgeFg,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Exam Date",
                    tint = RoyalBlue,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Exam Date: ${exam.date}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                if (classUiState.classes.isNotEmpty()) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = LightBlueHighlight,
                  ) {
                    Text(
                      text = "${classUiState.classes.size}",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = DarkRoyalBlue,
                      modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                  }
                }
              }

              val btnShape = RoundedCornerShape(12.dp)
              Box(
                modifier = Modifier
                  .heightIn(min = 38.dp)
                  .shadow(elevation = 2.dp, shape = btnShape, spotColor = DarkRoyalBlue.copy(alpha = 0.25f))
                  .clip(btnShape)
                  .background(PrimaryBlueGradient)
                  .clickable(onClick = classViewModel::openAddClassDialog)
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
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Add Class",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                }
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
                    color = RoyalBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }

              classUiState.classes.isEmpty() -> {
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, BorderGray),
                  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                      color = LightBlueHighlight,
                      modifier = Modifier.size(52.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.Class,
                          contentDescription = "No classes",
                          tint = RoyalBlue,
                          modifier = Modifier.size(24.dp),
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                      text = "No classes added yet",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                      text = "Tap '+ Add Class' to add a class session to this exam.",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
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

@Composable
fun ClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit = {},
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingBadgeBg = if (isMorning) TertiaryAmberContainer else AccentVioletContainer
  val timingBadgeFg = if (isMorning) StatusAmber else AccentViolet
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

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
      .testTag("class_card_${classRoom.subjectName}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
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
            shape = RoundedCornerShape(12.dp),
            color = SecondaryTealContainer,
            modifier = Modifier.size(42.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = SecondaryTeal,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          Text(
            text = classRoom.subjectName,
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = timingBadgeBg,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

          IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(StatusWarningContainer.copy(alpha = 0.6f))
              .testTag("delete_class_button_${classRoom.subjectName}"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Class",
              tint = StatusWarning,
              modifier = Modifier.size(18.dp),
            )
          }

          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open Class",
            tint = TextSecondary,
            modifier = Modifier.size(13.dp),
          )
        }
      }

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
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = classRoom.session,
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = classRoom.semester,
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Grade,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${classRoom.totalMarks} Marks",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
        }
      }

      if (!classRoom.questionPaperPath.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = LightBlueHighlight,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
