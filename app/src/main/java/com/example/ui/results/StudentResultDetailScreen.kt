package com.example.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
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
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

private val ApprovedGreenSurface = Color(0xFFE6F8ED)
private val ApprovedGreenAccent = Color(0xFF0E7A3D)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentResultDetailScreen(
  studentId: String,
  classId: String,
  viewModel: StudentResultDetailViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler(onBack = onNavigateBack)

  LaunchedEffect(studentId, classId) {
    viewModel.loadStudentResult(studentId, classId)
  }

  val student = uiState.student
  val classRoom = uiState.classRoom
  val evaluation = uiState.evaluation?.takeIf {
    it.status.equals("approved", ignoreCase = true)
  }
  val hasApprovedResult = evaluation != null

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
      .testTag("student_result_detail_screen"),
  ) {
    // Backdrop gradient layer extending behind topBar down into upper content area
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
                  .testTag("student_result_detail_back_button"),
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
                  text = "Student Result",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Approved examination result",
                  fontFamily = FigtreeFontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 12.sp,
                  color = Color.White.copy(alpha = 0.85f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              if (hasApprovedResult) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = ApprovedGreenSurface,
                  border = BorderStroke(1.dp, ApprovedGreenAccent.copy(alpha = 0.35f)),
                  modifier = Modifier.padding(end = 6.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Verified,
                      contentDescription = null,
                      tint = ApprovedGreenAccent,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = "Approved",
                      fontSize = 11.sp,
                      fontFamily = FigtreeFontFamily,
                      fontWeight = FontWeight.Bold,
                      color = ApprovedGreenAccent,
                    )
                  }
                }
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        when {
          uiState.isLoading -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("student_result_detail_loading_indicator"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = ApprovedGreenAccent,
                    strokeWidth = 3.dp,
                  )
                  Text(
                    text = "Loading Approved Result...",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Text(
                    text = "Retrieving finalized marks and question breakdown.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          !uiState.errorMessage.isNullOrBlank() -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              if (student != null) {
                StudentResultInfoCard(
                  student = student,
                  classRoom = classRoom,
                  isApproved = false,
                )
              }

              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("student_result_detail_error_state"),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                ) {
                  Surface(
                    shape = CircleShape,
                    color = StatusWarningContainer,
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
                  Text(
                    text = "Unable to Load Student Result",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Text(
                    text = uiState.errorMessage.orEmpty(),
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                  Button(
                    onClick = { viewModel.loadStudentResult(studentId, classId) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier.testTag("student_result_detail_retry_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Retry",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                    )
                  }
                }
              }
            }
          }

          evaluation == null -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              if (student != null) {
                StudentResultInfoCard(
                  student = student,
                  classRoom = classRoom,
                  isApproved = false,
                )
              }

              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("student_result_detail_empty_state"),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                ) {
                  Surface(
                    shape = CircleShape,
                    color = LightBlueHighlight,
                    modifier = Modifier.size(48.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = RoyalBlue,
                        modifier = Modifier.size(22.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = "No Approved Result Available",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "This student does not have an approved evaluation for this class yet.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          else -> {
            val questions = evaluation.questions
            val totalObtained = evaluation.totalMarksObtained
              ?: questions.sumOf { it.awardedMarks ?: 0.0 }
            val totalPossible = classRoom?.totalMarks?.toDouble()
              ?: evaluation.totalMarks
              ?: questions.sumOf { it.maximumMarks ?: 0.0 }

            LazyColumn(
              contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 32.dp,
              ),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("student_result_detail_list"),
            ) {
              // 1. Student Information Card (matching TeacherReviewScreen)
              item {
                StudentResultInfoCard(
                  student = student,
                  classRoom = classRoom,
                  isApproved = true,
                )
              }

              // 2. Approved Score Summary Card (matching TeacherReviewScreen's glassy-green approved card)
              item {
                ApprovedScoreSummaryCard(
                  evaluation = evaluation,
                  totalObtained = totalObtained,
                  totalPossible = totalPossible,
                  questionsCount = questions.size,
                )
              }

              // 3. Question Breakdown Header
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "Question Breakdown (${questions.size})",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                    )
                    Text(
                      text = "Finalized question marks and grading details",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      color = TextSecondary,
                    )
                  }
                }
              }

              // 4. Question Breakdown List (Read-only cards matching TeacherReviewScreen)
              if (questions.isEmpty()) {
                item {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderGray),
                  ) {
                    Text(
                      text = "No question breakdown available for this answer sheet.",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.sp,
                      color = TextSecondary,
                      modifier = Modifier.padding(16.dp),
                    )
                  }
                }
              } else {
                itemsIndexed(
                  items = questions,
                  key = { index, question -> question.id ?: "${question.questionNumber}_$index" }
                ) { index, question ->
                  ResultQuestionCard(
                    question = question,
                    index = index,
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
private fun StudentResultInfoCard(
  student: Student?,
  classRoom: ClassRoom?,
  isApproved: Boolean,
  modifier: Modifier = Modifier,
) {
  val studentName = student?.name.orEmpty().ifBlank { "Student" }
  val fatherName = student?.fatherName.orEmpty()
  val rollNumber = student?.rollNumber.orEmpty()
  val subjectName = classRoom?.subjectName.orEmpty()

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("student_result_info_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(LightBlueHighlight)
              .border(1.dp, RoyalBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(20.dp),
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = studentName,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (fatherName.isNotBlank()) {
              Text(
                text = "Father: $fatherName",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                color = TextSecondary,
              )
            }
          }
        }

        if (isApproved) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = StatusSuccessContainer,
            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "Approved",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = StatusSuccess,
              )
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, BorderGray),
          ) {
            Text(
              text = "Pending",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TextSecondary,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
          }
        }
      }

      if (rollNumber.isNotBlank() || subjectName.isNotBlank()) {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (rollNumber.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = LightBlueHighlight,
              border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
            ) {
              Text(
                text = "Roll No: $rollNumber",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = RoyalBlue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          if (subjectName.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Text(
                text = subjectName,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ApprovedScoreSummaryCard(
  evaluation: EvaluationDetail,
  totalObtained: Double,
  totalPossible: Double,
  questionsCount: Int,
  modifier: Modifier = Modifier,
) {
  val extractedTotal = evaluation.extractedQuestionTotalMarks
  val scoreBannerBrush = remember {
    Brush.horizontalGradient(
      listOf(
        ApprovedGreenSurface,
        Color(0xFFD3F2E0),
      )
    )
  }

  val percentageValue = evaluation.percentage
    ?: if (totalPossible > 0.0) (totalObtained / totalPossible) * 100.0 else null
  val pctStr = if (percentageValue != null) {
    String.format(Locale.US, "%.1f%%", percentageValue)
  } else {
    "N/A"
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_summary_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = ApprovedGreenSurface.copy(alpha = 0.72f),
    ),
    border = BorderStroke(
      width = 1.dp,
      color = ApprovedGreenAccent.copy(alpha = 0.32f),
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(scoreBannerBrush)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = ApprovedGreenAccent,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Final Approved Score",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
              )
            }
            Row(
              verticalAlignment = Alignment.Bottom,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = formatMarks(totalObtained),
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
              )
              Text(
                text = "/ ${formatMarks(totalPossible)} marks",
                fontFamily = FigtreeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, ApprovedGreenAccent.copy(alpha = 0.35f)),
          ) {
            Text(
              text = pctStr,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }

      HorizontalDivider(
        thickness = 1.dp,
        color = BorderGray.copy(alpha = 0.7f),
      )

      // Minimalist Metrics Strip inside approved card (matching TeacherReviewScreen)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        ResultMetricChip(
          label = "Questions",
          value = "$questionsCount",
          icon = Icons.Default.FormatListNumbered,
          modifier = Modifier.weight(1f),
        )
        if (extractedTotal != null && extractedTotal > 0.0) {
          Box(
            modifier = Modifier
              .width(1.dp)
              .height(28.dp)
              .background(BorderGray.copy(alpha = 0.75f)),
          )
          ResultMetricChip(
            label = "Extracted Max",
            value = formatMarks(extractedTotal),
            icon = Icons.Default.Calculate,
            modifier = Modifier.weight(1f),
          )
        }
        Box(
          modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(BorderGray.copy(alpha = 0.75f)),
        )
        ResultMetricChip(
          label = "Class Max",
          value = formatMarks(totalPossible),
          icon = Icons.Default.School,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ResultMetricChip(
  label: String,
  value: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = label,
        fontFamily = FigtreeFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = value,
      fontFamily = BricolageGrotesqueFontFamily,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultQuestionCard(
  question: EvaluationQuestion,
  index: Int,
  modifier: Modifier = Modifier,
) {
  val qLabel = question.questionNumber ?: "${index + 1}"
  val maxMarks = question.maximumMarks ?: 0.0
  val awarded = question.awardedMarks ?: 0.0

  // Check if student answer is genuinely missing
  val rawAnswer = question.studentAnswer?.trim().orEmpty()
  val isAnswerMissing = rawAnswer.isBlank() ||
    rawAnswer.equals("No answer text extracted.", ignoreCase = true)

  var showRubric by remember(question.id, qLabel) { mutableStateOf(false) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_question_card_$qLabel"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Header Row: Q# Badge + Read-only Marks Box (matching TeacherReviewScreen approved state)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = RoyalBlue,
        ) {
          Text(
            text = "Q$qLabel",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        val isZero = awarded == 0.0
        val boxBg = if (isZero) Color(0xFFFDF2F2) else LightBlueHighlight
        val boxBorder = if (isZero) {
          BorderStroke(1.dp, Color(0xFFE5484D).copy(alpha = 0.5f))
        } else {
          BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f))
        }
        val boxContentColor = if (isZero) Color(0xFFE5484D) else RoyalBlue

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = boxBg,
          border = boxBorder,
          modifier = Modifier.testTag("question_marks_box_$qLabel"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Text(
              text = formatMarks(awarded),
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = boxContentColor,
            )
            Text(
              text = "/ ${formatMarks(maxMarks)} marks",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = if (isZero) Color(0xFFE5484D).copy(alpha = 0.8f) else TextSecondary,
            )
          }
        }
      }

      // 2. Question Text
      Text(
        text = question.questionText?.ifBlank { "Question $qLabel" } ?: "Question $qLabel",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth(),
      )

      // 3. Genuine Missing Answer Special Handling
      if (isAnswerMissing) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFF8FAFC),
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "No answer detected",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextSecondary,
            )
          }
        }
      } else {
        // 4. Student's Extracted Answer Section (when present)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Student Answer (OCR)",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = RoyalBlue,
            )
            Text(
              text = rawAnswer,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              color = TextPrimary,
              lineHeight = 17.5.sp,
            )
          }
        }

        // 5. Grading Feedback Section
        if (!question.feedback.isNullOrBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = "Grading Feedback",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextSecondary,
            )
            Text(
              text = question.feedback,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              color = TextPrimary,
              lineHeight = 17.5.sp,
            )
          }
        }

        // 6. Collapsible Rubric Section (Expected Answer & Marking Criteria)
        val cleanExpected = cleanProvisionalText(question.expectedAnswer)
        val cleanCriteria = cleanProvisionalText(question.markingCriteria)

        if (cleanExpected.isNotBlank() || cleanCriteria.isNotBlank()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showRubric = !showRubric }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = if (showRubric) "Hide Rubric" else "Show Rubric",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = RoyalBlue,
            )
            Icon(
              imageVector = if (showRubric) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(15.dp),
            )
          }

          AnimatedVisibility(visible = showRubric) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (cleanExpected.isNotBlank()) {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = "Expected Answer",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = RoyalBlue,
                    )
                    Text(
                      text = cleanExpected,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      color = TextSecondary,
                      lineHeight = 16.5.sp,
                    )
                  }
                }

                if (cleanCriteria.isNotBlank()) {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = "Marking Criteria",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = RoyalBlue,
                    )
                    Text(
                      text = cleanCriteria,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      color = TextSecondary,
                      lineHeight = 16.5.sp,
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

private fun cleanProvisionalText(text: String?): String {
  if (text.isNullOrBlank()) return ""
  return text
    .replace(Regex("\\[Provisional Reference Answer\\]:\\s*", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\[Provisional Criteria\\]:\\s*", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\[provisional reference answer\\]", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\[provisional criteria\\]", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\[Provisional Rubric\\]:\\s*", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\[provisional rubric\\]", RegexOption.IGNORE_CASE), "")
    .trim()
}

private fun formatMarks(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}

