package com.example.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.TertiaryAmberContainer

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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = student?.name ?: "Student Result Detail",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            val subtext = buildString {
              student?.rollNumber?.let { append("Roll No: $it") }
              if (!student?.rollNumber.isNullOrBlank() && !classRoom?.subjectName.isNullOrBlank()) {
                append(" • ")
              }
              classRoom?.subjectName?.let { append(it) }
            }
            if (subtext.isNotBlank()) {
              Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("student_result_detail_back_button"),
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
    modifier = modifier.testTag("student_result_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      } else if (evaluation == null) {
        // No Approved Result Available state
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            ) {
              Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "No Approved Result Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "This student does not have an approved evaluation for this class yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
              )
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("student_result_detail_list"),
        ) {
          // 1. Overall Score & Percentage Card
          item {
            ResultSummaryCard(evaluation = evaluation)
            Spacer(modifier = Modifier.height(16.dp))
          }

          // 2. Review Warning Banner if required
          if (evaluation.reviewRequired || evaluation.status == "needs_review") {
            item {
              ResultReviewWarningBanner(
                reviewReason = evaluation.reviewReason ?: "Some question marks or grading details require teacher attention.",
              )
              Spacer(modifier = Modifier.height(16.dp))
            }
          }

          // 3. Question breakdown header
          item {
            Text(
              text = "Question Breakdown (${evaluation.questions.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
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
            Spacer(modifier = Modifier.height(12.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ResultSummaryCard(
  evaluation: EvaluationDetail,
  modifier: Modifier = Modifier,
) {
  val obtained = evaluation.totalMarksObtained ?: 0.0
  val total = evaluation.totalMarks ?: 100.0
  val percentage = evaluation.percentage ?: if (total > 0) (obtained / total) * 100 else 0.0

  val formattedObtained = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
  val formattedTotal = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_summary_card"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "OVERALL RESULT",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "$formattedObtained / $formattedTotal Marks",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primary,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Approved (${String.format("%.1f%%", percentage)})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary,
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
      containerColor = TertiaryAmberContainer.copy(alpha = 0.85f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_review_warning_banner"),
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Icon(
        imageVector = Icons.Default.WarningAmber,
        contentDescription = "Review Warning",
        tint = TertiaryAmber,
        modifier = Modifier.size(20.dp),
      )
      Column {
        Text(
          text = "Teacher Review Recommended",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = TertiaryAmber,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = reviewReason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

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
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_question_card_$qNum"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      // Header: Question Number & Awarded Marks
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "Question $qNum",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        val marksContainerColor = when {
          isFullMarks -> Color(0xFFDCFCE7) // Soft Green
          (question.awardedMarks ?: 0.0) > 0.0 -> Color(0xFFDBEAFE) // Soft Blue
          else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val marksTextColor = when {
          isFullMarks -> Color(0xFF166534)
          (question.awardedMarks ?: 0.0) > 0.0 -> Color(0xFF1E40AF)
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = marksContainerColor,
        ) {
          Text(
            text = "${question.displayAwardedMarks} / ${question.displayMaxMarks} Marks",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = marksTextColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Review Reason banner if flagged
      if (question.reviewRequired && !question.reviewReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = TertiaryAmberContainer.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = TertiaryAmber,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = question.reviewReason ?: "Review required",
              style = MaterialTheme.typography.labelSmall,
              color = TertiaryAmber,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Question Text
      Text(
        text = question.questionText ?: "Question text not detected",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
      Spacer(modifier = Modifier.height(10.dp))

      // Student Answer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Student Answer:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = if (question.studentAnswer.isNullOrBlank()) "No answer recorded" else question.studentAnswer,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Expected Answer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = Color(0xFF166534),
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Expected Answer:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF166534),
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFDCFCE7).copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = if (question.expectedAnswer.isNullOrBlank()) "N/A" else question.expectedAnswer,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }

      // Feedback
      if (!question.feedback.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Feedback,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "AI Feedback:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = question.feedback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    }
  }
}
