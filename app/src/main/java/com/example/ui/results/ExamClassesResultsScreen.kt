package com.example.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.classes.ClassViewModel
import com.example.ui.exam.ExamViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.PrimaryBlue
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

// Type-specific palette tokens matching approved GradeScan screens
private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamClassesResultsScreen(
  examId: String,
  examViewModel: ExamViewModel,
  classViewModel: ClassViewModel,
  onNavigateBack: () -> Unit,
  onClassClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val examUiState by examViewModel.uiState.collectAsStateWithLifecycle()
  val classUiState by classViewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(examId) {
    examViewModel.loadExamDetail(examId)
    classViewModel.loadClasses(examId)
  }

  LaunchedEffect(classUiState.feedbackMessage) {
    classUiState.feedbackMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      classViewModel.dismissFeedbackMessage()
    }
  }

  val selectedExam = examUiState.selectedExam
  val classesForExam = classUiState.classes
  val isLoading = (examUiState.isLoadingDetail || classUiState.isLoading) && classesForExam.isEmpty()
  val errorMessage = classUiState.errorMessage ?: examUiState.errorMessage

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
      .testTag("exam_classes_results_screen"),
  ) {
    // Six-stop backdrop gradient layer extending behind topBar down into upper content area
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
                  .testTag("exam_classes_results_back_button"),
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
                  text = selectedExam?.name ?: "Exam Classes",
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
                  text = "Classes and their results",
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
                    onClick = {
                      examViewModel.loadExamDetail(examId)
                      classViewModel.loadClasses(examId)
                    },
                  )
                  .testTag("refresh_exam_classes_results_button"),
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
          isLoading -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("exam_classes_results_loading_indicator"),
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
                  text = "Loading classes and results…",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextSecondary,
                )
              }
            }
          }

          errorMessage != null && classesForExam.isEmpty() -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("exam_classes_results_error_state"),
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
                    text = "Unable to load classes",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = errorMessage,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(18.dp))
                  Button(
                    onClick = {
                      examViewModel.loadExamDetail(examId)
                      classViewModel.loadClasses(examId)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("exam_classes_results_retry_button"),
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

          classesForExam.isEmpty() -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("exam_classes_results_empty_state"),
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
                    color = MidTermTint,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
                    modifier = Modifier.size(52.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = "No Classes",
                        tint = RoyalBlue,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = "No classes found for this exam",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Add classes to this exam and approve student evaluations to view class results and reports.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          else -> {
            LazyColumn(
              contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxSize()
                .testTag("exam_classes_results_list"),
            ) {
              item(key = "exam_classes_results_header_summary") {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  if (selectedExam != null) {
                    val (badgeBg, badgeFg) = when (selectedExam.type.lowercase()) {
                      "mid term" -> MidTermTint to MidTermText
                      "final" -> FinalTint to FinalText
                      "supply" -> TertiaryAmberContainer to StatusAmber
                      else -> SecondaryTealContainer to SecondaryTeal
                    }

                    Card(
                      shape = RoundedCornerShape(18.dp),
                      colors = CardDefaults.cardColors(containerColor = Color.White),
                      border = BorderStroke(1.dp, BorderGray),
                      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_classes_results_exam_summary_card"),
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        FlowRow(
                          modifier = Modifier.weight(1f),
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                          Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBg,
                            border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.12f)),
                            modifier = Modifier.align(Alignment.CenterVertically),
                          ) {
                            Text(
                              text = selectedExam.type,
                              fontFamily = FigtreeFontFamily,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold,
                              color = badgeFg,
                              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            )
                          }

                          if (selectedExam.date.isNotBlank()) {
                            Surface(
                              shape = RoundedCornerShape(6.dp),
                              color = BackgroundLight,
                              modifier = Modifier.align(Alignment.CenterVertically),
                            ) {
                              Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                              ) {
                                Icon(
                                  imageVector = Icons.Outlined.CalendarToday,
                                  contentDescription = "Exam Date",
                                  tint = TextSecondary,
                                  modifier = Modifier.size(11.dp),
                                )
                                Text(
                                  text = selectedExam.date,
                                  fontFamily = FigtreeFontFamily,
                                  fontSize = 11.sp,
                                  fontWeight = FontWeight.SemiBold,
                                  color = TextSecondary,
                                )
                              }
                            }
                          }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = SoftBlueTint,
                          border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
                        ) {
                          Text(
                            text = "${classesForExam.size} ${if (classesForExam.size == 1) "Class" else "Classes"}",
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

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Select a class to view student results:",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 14.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      modifier = Modifier.weight(1f),
                    )
                    if (selectedExam == null) {
                      Spacer(modifier = Modifier.width(8.dp))
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SoftBlueTint,
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
                      ) {
                        Text(
                          text = "${classesForExam.size} ${if (classesForExam.size == 1) "class" else "classes"}",
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
              }

              items(
                items = classesForExam,
                key = { it.id ?: "${it.subjectName}_${it.session}_${it.semester}_${it.timing}" },
              ) { classRoom ->
                ResultClassCard(
                  classRoom = classRoom,
                  onClick = { classRoom.id?.let { onClassClick(it) } },
                )
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
private fun ResultClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingBadgeBg = if (isMorning) TertiaryAmberContainer else FinalTint
  val timingBadgeFg = if (isMorning) StatusAmber else FinalText
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

  val shape = RoundedCornerShape(18.dp)
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
      .testTag("result_class_card_${classRoom.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
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
            imageVector = Icons.Outlined.Assessment,
            contentDescription = "Class Results",
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

        val sessionSemesterText = listOf(classRoom.session.trim(), classRoom.semester.trim())
          .filter { it.isNotEmpty() }
          .joinToString(" • ")

        if (sessionSemesterText.isNotEmpty()) {
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
              text = sessionSemesterText,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = TextSecondary,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (classRoom.timing.isNotBlank()) {
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
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.sp,
                  color = timingBadgeFg,
                  fontWeight = FontWeight.Bold,
                )
              }
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
                color = MidTermText,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = SoftBlueTint,
        modifier = Modifier.size(30.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Class Results",
            tint = PrimaryBlue,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

