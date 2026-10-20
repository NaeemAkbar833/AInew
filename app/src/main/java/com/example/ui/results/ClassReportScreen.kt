package com.example.ui.results

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClassRoom
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Exam
import com.example.data.model.Student
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassReportScreen(
  classId: String,
  viewModel: ClassReportViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler(onBack = onNavigateBack)

  LaunchedEffect(classId) {
    viewModel.loadClassReport(classId)
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  val classRoom = uiState.classRoom
  val exam = uiState.exam
  val students = uiState.students
  val evaluationsMap = uiState.evaluationsByStudentId

  val approvedCount = remember(students, evaluationsMap) {
    students.count { student ->
      val eval = evaluationsMap[student.id]
      eval != null && eval.status.equals("approved", ignoreCase = true)
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

  val headerSubtitle = remember(classRoom?.subjectName, exam?.name) {
    when {
      !classRoom?.subjectName.isNullOrBlank() && !exam?.name.isNullOrBlank() ->
        "${classRoom?.subjectName} • ${exam?.name}"
      !classRoom?.subjectName.isNullOrBlank() ->
        "${classRoom?.subjectName} • Approved class report"
      else -> "Approved class summary & student results"
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("class_report_screen"),
  ) {
    // Six-stop backdrop gradient layer extending behind topBar down into upper cards
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                  .testTag("class_report_back_button"),
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
                  text = "Class Report",
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
                  text = headerSubtitle,
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
                    onClick = { viewModel.loadClassReport(classId) },
                  )
                  .testTag("refresh_class_report_button"),
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
        contentAlignment = Alignment.TopCenter,
      ) {
        when {
          uiState.isLoading && students.isEmpty() && classRoom == null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("class_report_loading_indicator"),
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
                  text = "Loading class report…",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextSecondary,
                )
              }
            }
          }

          uiState.errorMessage != null && students.isEmpty() && classRoom == null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("class_report_error_state"),
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
                    text = "Unable to load class report",
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
                    onClick = { viewModel.loadClassReport(classId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("class_report_retry_button"),
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
            LazyColumn(
              contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 88.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("class_report_list"),
            ) {
              // 1. Compact Class & Exam Information Card
              if (classRoom != null || exam != null) {
                item(key = "class_report_info_card") {
                  ClassReportInfoCard(
                    classRoom = classRoom,
                    exam = exam,
                  )
                }
              }

              // 2. Two-Metric Performance Summary Card (Total Students & Graded / Approved)
              item(key = "class_report_stats_card") {
                PerformanceSummaryCard(
                  totalStudents = students.size,
                  approvedCount = approvedCount,
                )
              }

              // 3. Student Result Breakdown Section Header
              item(key = "class_report_section_header") {
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
                  ) {
                    Text(
                      text = "Student Result Breakdown",
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
                        text = "${students.size}",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                      )
                    }
                  }
                }
              }

              // 4. Student Result Breakdown List
              if (students.isEmpty()) {
                item(key = "class_report_empty_state") {
                  Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("class_report_empty_state"),
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
                            imageVector = Icons.Outlined.Group,
                            contentDescription = "No Students",
                            tint = RoyalBlue,
                            modifier = Modifier.size(24.dp),
                          )
                        }
                      }
                      Spacer(modifier = Modifier.height(14.dp))
                      Text(
                        text = "No students enrolled in this class.",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                      )
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        text = "Add students to this class and approve their evaluations to build the class report.",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }
                }
              } else {
                items(
                  items = students,
                  key = { it.id ?: "${it.rollNumber}_${it.name}" },
                ) { student ->
                  val evaluation = evaluationsMap[student.id]?.takeIf {
                    it.status.equals("approved", ignoreCase = true)
                  }
                  ClassReportStudentCard(
                    student = student,
                    evaluation = evaluation,
                    classTotalMarks = classRoom?.totalMarks?.toDouble() ?: 100.0,
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
private fun ClassReportInfoCard(
  classRoom: ClassRoom?,
  exam: Exam?,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom?.timing.equals("Morning", ignoreCase = true)
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight
  val timingTint = if (isMorning) StatusAmber else FinalText
  val timingBg = if (isMorning) TertiaryAmberContainer else FinalTint

  val isFinalExam = exam?.type.equals("Final", ignoreCase = true)
  val examBadgeBg = if (isFinalExam) FinalTint else MidTermTint
  val examBadgeFg = if (isFinalExam) FinalText else MidTermText

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("class_report_info_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = SoftBlueTint,
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = classRoom?.subjectName?.ifBlank { "Class" } ?: exam?.name ?: "Class Report",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 15.5.sp,
              lineHeight = 19.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )

            if (exam != null) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (exam.name.isNotBlank()) {
                  Text(
                    text = exam.name,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RoyalBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                  )
                }

                if (exam.type.isNotBlank()) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = examBadgeBg,
                    modifier = Modifier.align(Alignment.CenterVertically),
                  ) {
                    Text(
                      text = exam.type,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = examBadgeFg,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                  }
                }

                if (exam.date.isNotBlank()) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterVertically),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.CalendarToday,
                      contentDescription = null,
                      tint = TextSecondary,
                      modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = exam.date,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Medium,
                      color = TextSecondary,
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (classRoom != null) {
        HorizontalDivider(color = BorderGray, thickness = 1.dp)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ClassReportMetadataItem(
              icon = Icons.Default.CalendarMonth,
              label = "Session",
              value = classRoom.session.ifBlank { "—" },
              modifier = Modifier.weight(1f),
            )
            ClassReportMetadataItem(
              icon = Icons.Default.Badge,
              label = "Semester",
              value = classRoom.semester.ifBlank { "—" },
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ClassReportMetadataItem(
              icon = timingIcon,
              label = "Timing",
              value = classRoom.timing.ifBlank { "—" },
              modifier = Modifier.weight(1f),
              iconTint = timingTint,
              iconBg = timingBg,
            )
            ClassReportMetadataItem(
              icon = Icons.Default.Grade,
              label = "Total Marks",
              value = "${classRoom.totalMarks} Marks",
              modifier = Modifier.weight(1f),
              iconTint = PrimaryBlue,
              iconBg = SoftBlueTint,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ClassReportMetadataItem(
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
      shape = RoundedCornerShape(5.dp),
      color = iconBg,
      border = BorderStroke(1.dp, iconTint.copy(alpha = 0.14f)),
      modifier = Modifier.size(22.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(12.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(6.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label.uppercase(),
        fontFamily = FigtreeFontFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        fontFamily = FigtreeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Balanced Two-Metric Performance Summary Card:
 * 1. Total Students (blue icon + light-blue container)
 * 2. Graded / Approved (green check/approval icon + light-green container)
 */
@Composable
private fun PerformanceSummaryCard(
  totalStudents: Int,
  approvedCount: Int,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("class_report_stats_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = SoftBlueTint,
          border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
          modifier = Modifier.size(24.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.Assessment,
              contentDescription = null,
              tint = PrimaryBlue,
              modifier = Modifier.size(13.dp),
            )
          }
        }
        Text(
          text = "Performance Summary",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Metric 1: Total Students
        SummaryMetricColumn(
          label = "Total Students",
          value = totalStudents.toString(),
          icon = Icons.Outlined.Group,
          iconTint = PrimaryBlue,
          iconContainerColor = LightBlueHighlight,
          iconBorderColor = PrimaryBlue.copy(alpha = 0.20f),
          modifier = Modifier.weight(1f),
        )

        Box(
          modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(BorderGray),
        )

        // Metric 2: Graded / Approved
        SummaryMetricColumn(
          label = "Graded / Approved",
          value = approvedCount.toString(),
          icon = Icons.Default.Verified,
          iconTint = StatusSuccess,
          iconContainerColor = StatusSuccessContainer,
          iconBorderColor = StatusSuccess.copy(alpha = 0.25f),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun SummaryMetricColumn(
  label: String,
  value: String,
  icon: ImageVector,
  iconTint: Color,
  iconContainerColor: Color,
  iconBorderColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = iconContainerColor,
      border = BorderStroke(1.dp, iconBorderColor),
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = value,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextPrimary,
        lineHeight = 22.sp,
      )
      Text(
        text = label,
        fontFamily = FigtreeFontFamily,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassReportStudentCard(
  student: Student,
  evaluation: EvaluationDbRecord?,
  classTotalMarks: Double,
  modifier: Modifier = Modifier,
) {
  val isApproved = evaluation != null && evaluation.status.equals("approved", ignoreCase = true)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
    modifier = modifier
      .fillMaxWidth()
      .testTag("class_report_student_card_${student.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
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
            contentDescription = "Student",
            tint = PrimaryBlue,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = student.name,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 19.sp,
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

      Spacer(modifier = Modifier.width(10.dp))

      if (isApproved && evaluation != null) {
        val obtained = evaluation.totalMarksObtained ?: 0.0
        val total = evaluation.totalMarks ?: classTotalMarks
        val pct = evaluation.percentage ?: if (total > 0.0) (obtained / total) * 100.0 else null

        val formattedObtained = formatReportMarks(obtained)
        val formattedTotal = formatReportMarks(total)

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "$formattedObtained / $formattedTotal",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          if (pct != null) {
            val pctStr = if (pct % 1.0 == 0.0) {
              "${pct.toInt()}%"
            } else {
              String.format(Locale.US, "%.1f%%", pct)
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = StatusSuccessContainer,
              border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.22f)),
            ) {
              Text(
                text = pctStr,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = StatusSuccess,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              )
            }
          }
        }
      } else {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = BackgroundLight,
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Schedule,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Pending",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TextSecondary,
            )
          }
        }
      }
    }
  }
}

private fun formatReportMarks(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}

