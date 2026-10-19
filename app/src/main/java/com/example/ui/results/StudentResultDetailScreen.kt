package com.example.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.data.model.Student
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.components.SkeletonBox
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentResultDetailScreen(
  studentId: String,
  classId: String,
  viewModel: StudentResultDetailViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(studentId, classId) {
    viewModel.loadStudentResult(studentId, classId)
  }

  val student = uiState.student
  val classRoom = uiState.classRoom
  val evaluation = uiState.evaluation

  val subtext = buildString {
    student?.rollNumber?.let { append("Roll No: $it") }
    if (!student?.rollNumber.isNullOrBlank() && !classRoom?.subjectName.isNullOrBlank()) {
      append(" • ")
    }
    classRoom?.subjectName?.let { append(it) }
  }

  Scaffold(
    topBar = {
      GradeScanStitchHeader(
        title = student?.name ?: "Student Result Detail",
        subtitle = subtext.ifBlank { "Approved Evaluation Breakdown" },
        badgeText = "STUDENT REPORT",
        onBackClick = onNavigateBack,
        backTestTag = "student_result_detail_back_button",
      )
    },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("student_result_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          SkeletonBox(modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(16.dp))
          repeat(4) { ExamCardSkeleton() }
        }
      } else if (evaluation == null) {
        // No Approved Result Available state
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              )
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("student_result_detail_list"),
        ) {
          // 1. Overall Score & Percentage Card
          item {
            ResultSummaryCard(
              evaluation = evaluation,
              student = student,
            )
          }

          // 2. Review Warning Banner if required
          if (evaluation.reviewRequired || evaluation.status == "needs_review") {
            item {
              ResultReviewWarningBanner(
                reviewReason = evaluation.reviewReason ?: "Some question marks or grading details require teacher attention.",
              )
            }
          }

          // 3. Question breakdown header
          item {
            Text(
              text = "Question Breakdown (${evaluation.questions.size})",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 2.dp),
            )
          }

          // 4. Questions list
          itemsIndexed(
            items = evaluation.questions,
            key = { _, question -> question.id!! }
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

@Composable
private fun ResultSummaryCard(
  evaluation: EvaluationDetail,
  student: Student? = null,
  modifier: Modifier = Modifier,
) {
  val obtained = evaluation.totalMarksObtained ?: 0.0
  val total = evaluation.totalMarks ?: 100.0
  val percentage = evaluation.percentage ?: if (total > 0) (obtained / total) * 100 else 0.0

  val formattedObtained = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
  val formattedTotal = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_summary_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(15.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (student != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = LightBlueHighlight,
            modifier = Modifier.size(40.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(19.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
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

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = LightBlueHighlight,
          ) {
            Text(
              text = "Roll No: ${student.rollNumber}",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              color = DarkRoyalBlue,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        HorizontalDivider(color = BorderGray, thickness = 1.dp)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "OVERALL RESULT",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
            letterSpacing = 0.4.sp,
          )
          Text(
            text = "$formattedObtained / $formattedTotal Marks",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = StatusSuccessContainer,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = StatusSuccess,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Approved (${String.format("%.1f%%", percentage)})",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = StatusSuccess,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ResultReviewWarningBanner(
  reviewReason: String,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = TertiaryAmberContainer
    ),
    border = BorderStroke(1.dp, TertiaryAmber.copy(alpha = 0.3f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_review_warning_banner"),
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(12.dp),
    ) {
      Icon(
        imageVector = Icons.Default.WarningAmber,
        contentDescription = "Review Warning",
        tint = TertiaryAmber,
        modifier = Modifier.size(16.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Teacher Review Recommended",
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TertiaryAmber,
        )
        Text(
          text = reviewReason,
          fontFamily = FigtreeFontFamily,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          color = TextPrimary,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultQuestionCard(
  question: EvaluationQuestion,
  index: Int,
  modifier: Modifier = Modifier,
) {
  val qNum = question.questionNumber ?: "${index + 1}"
  val isFullMarks = question.maximumMarks != null &&
    question.maximumMarks > 0 &&
    question.awardedMarks == question.maximumMarks

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_question_card_$qNum"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      // Header: Question Number & Awarded Marks
      FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = LightBlueHighlight,
          modifier = Modifier.align(Alignment.CenterVertically),
        ) {
          Text(
            text = "Question $qNum",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }

        val marksContainerColor = when {
          isFullMarks -> StatusSuccessContainer
          (question.awardedMarks ?: 0.0) > 0.0 -> LightBlueHighlight
          else -> Color(0xFFF1F5F9)
        }
        val marksTextColor = when {
          isFullMarks -> StatusSuccess
          (question.awardedMarks ?: 0.0) > 0.0 -> DarkRoyalBlue
          else -> TextSecondary
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = marksContainerColor,
          modifier = Modifier.align(Alignment.CenterVertically),
        ) {
          Text(
            text = "${question.displayAwardedMarks} / ${question.displayMaxMarks} Marks",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = marksTextColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      // Review Reason banner if flagged
      if (question.reviewRequired && !question.reviewReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = TertiaryAmberContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = TertiaryAmber,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = question.reviewReason ?: "Review required",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              lineHeight = 15.sp,
              color = TertiaryAmber,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Question Text
      Text(
        text = question.questionText ?: "Question text not detected",
        fontFamily = FigtreeFontFamily,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = BorderGray, thickness = 1.dp)
      Spacer(modifier = Modifier.height(10.dp))

      // Student Answer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Student Answer:",
          fontFamily = FigtreeFontFamily,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = RoyalBlue,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = if (question.studentAnswer.isNullOrBlank()) "No answer recorded" else question.studentAnswer,
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = TextPrimary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Expected Answer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = StatusSuccess,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Expected Answer:",
          fontFamily = FigtreeFontFamily,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = StatusSuccess,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = StatusSuccessContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = if (question.expectedAnswer.isNullOrBlank()) "N/A" else question.expectedAnswer,
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = TextPrimary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
      }

      // Feedback
      if (!question.feedback.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Feedback,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = "AI Feedback:",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = LightBlueHighlight,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = question.feedback,
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }
    }
  }
}
