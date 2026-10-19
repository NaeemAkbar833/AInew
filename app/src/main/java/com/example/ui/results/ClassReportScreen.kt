package com.example.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Student
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassReportScreen(
  classId: String,
  viewModel: ClassReportViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(classId) {
    viewModel.loadClassReport(classId)
  }

  val classRoom = uiState.classRoom
  val exam = uiState.exam

  Scaffold(
    topBar = {
      GradeScanStitchHeader(
        title = "Class Report",
        subtitle = classRoom?.subjectName ?: "Summary & Statistics",
        badgeText = "CLASS ANALYTICS",
        onBackClick = onNavigateBack,
        backTestTag = "class_report_back_button",
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("class_report_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading) {
        LazyColumn(
          contentPadding = PaddingValues(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(6) { ExamCardSkeleton() }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("class_report_list"),
        ) {
          // Exam / Class Information Card
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = LightBlueHighlight,
              ),
              border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_report_info_card"),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = exam?.name ?: "Exam",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Text(
                  text = "Type: ${exam?.type ?: "N/A"} | Date: ${exam?.date ?: "N/A"}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
                Text(
                  text = "Subject: ${classRoom?.subjectName ?: "N/A"}",
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = RoyalBlue,
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Session: ${classRoom?.session ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                  Text(
                    text = "Semester: ${classRoom?.semester ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                }
              }
            }
          }

          // Statistics Overview Card
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_report_stats_card"),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                  text = "Performance Summary",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )

                val evaluatedCount = uiState.evaluationsByStudentId.size
                val totalStudents = uiState.students.size
                val passingCount = uiState.evaluationsByStudentId.values.count { ev ->
                  val ob = ev.totalMarksObtained ?: 0.0
                  val tot = ev.totalMarks ?: classRoom?.totalMarks?.toDouble() ?: 100.0
                  val pct = ev.percentage ?: if (tot > 0) (ob / tot) * 100 else 0.0
                  pct >= 50.0
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  StatItem(label = "Total Students", value = totalStudents.toString())
                  StatItem(label = "Graded / Approved", value = evaluatedCount.toString())
                  StatItem(label = "Passing (>=50%)", value = passingCount.toString())
                }
              }
            }
          }

          item {
            Text(
              text = "Student Result Breakdown",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
          }

          if (uiState.students.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "No students enrolled in this class.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
            }
          } else {
            items(
              items = uiState.students,
              key = { it.id!! }
            ) { student ->
              val evaluation = uiState.evaluationsByStudentId[student.id]
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

@Composable
private fun StatItem(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = RoyalBlue,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = TextSecondary,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}

@Composable
private fun ClassReportStudentCard(
  student: Student,
  evaluation: EvaluationDbRecord?,
  classTotalMarks: Double,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border = BorderStroke(1.dp, BorderGray),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = student.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary,
        )
        Text(
          text = "Roll No: ${student.rollNumber}",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
      }

      if (evaluation != null) {
        val obtained = evaluation.totalMarksObtained ?: 0.0
        val total = evaluation.totalMarks ?: classTotalMarks
        val pct = evaluation.percentage ?: if (total > 0) (obtained / total) * 100 else 0.0

        val formattedObtained = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
        val formattedTotal = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "$formattedObtained / $formattedTotal",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = String.format("%.0f%%", pct),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
          )
        }
      } else {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = LightBlueHighlight,
        ) {
          Text(
            text = "Pending",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}
