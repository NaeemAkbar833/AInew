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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Student
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(6) { ExamCardSkeleton() }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("class_report_list"),
        ) {
          // Exam / Class Information Card
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = Color.White,
              ),
              border = BorderStroke(1.dp, BorderGray),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_report_info_card"),
            ) {
              Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SecondaryTealContainer,
                    modifier = Modifier.size(40.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Outlined.School,
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
                      text = exam?.name ?: "Exam",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      lineHeight = 20.sp,
                    )

                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LightBlueHighlight,
                        modifier = Modifier.align(Alignment.CenterVertically),
                      ) {
                        Text(
                          text = "Type: ${exam?.type ?: "N/A"}",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = DarkRoyalBlue,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                      }

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterVertically),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.CalendarToday,
                          contentDescription = null,
                          tint = TextSecondary,
                          modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = "Date: ${exam?.date ?: "N/A"}",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 12.sp,
                          color = TextSecondary,
                          fontWeight = FontWeight.Medium,
                        )
                      }
                    }
                  }
                }

                HorizontalDivider(color = BorderGray, thickness = 1.dp)

                Text(
                  text = "Subject: ${classRoom?.subjectName ?: "N/A"}",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = RoyalBlue,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  lineHeight = 19.sp,
                )

                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "Session: ${classRoom?.session ?: "N/A"}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                  )
                  Text(
                    text = "Semester: ${classRoom?.semester ?: "N/A"}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
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
              elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("class_report_stats_card"),
            ) {
              Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                  text = "Performance Summary",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 15.sp,
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
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  StatItem(
                    label = "Total Students",
                    value = totalStudents.toString(),
                    modifier = Modifier.weight(1f),
                  )
                  StatItem(
                    label = "Graded / Approved",
                    value = evaluatedCount.toString(),
                    modifier = Modifier.weight(1f),
                  )
                  StatItem(
                    label = "Passing (>=50%)",
                    value = passingCount.toString(),
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }
          }

          item {
            Text(
              text = "Student Result Breakdown",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(top = 2.dp, start = 2.dp),
            )
          }

          if (uiState.students.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(28.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "No students enrolled in this class.",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.sp,
                  color = TextSecondary,
                )
              }
            }
          } else {
            itemsIndexed(
              items = uiState.students,
              key = { _, it -> it.id!! }
            ) { index, student ->
              val evaluation = uiState.evaluationsByStudentId[student.id]
              ClassReportStudentCard(
                rank = index + 1,
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
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = LightBlueHighlight.copy(alpha = 0.55f),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = RoyalBlue,
      )
      Text(
        text = label,
        fontFamily = FigtreeFontFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ClassReportStudentCard(
  rank: Int,
  student: Student,
  evaluation: EvaluationDbRecord?,
  classTotalMarks: Double,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = LightBlueHighlight,
        modifier = Modifier.size(34.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "#$rank",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DarkRoyalBlue,
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
          Text(
            text = "Father: ${student.fatherName}",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
          )
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = LightBlueHighlight,
        ) {
          Text(
            text = "Roll No: ${student.rollNumber}",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DarkRoyalBlue,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      if (evaluation != null) {
        val obtained = evaluation.totalMarksObtained ?: 0.0
        val total = evaluation.totalMarks ?: classTotalMarks
        val pct = evaluation.percentage ?: if (total > 0) (obtained / total) * 100 else 0.0

        val formattedObtained = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
        val formattedTotal = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

        val isPassing = pct >= 50.0
        val pctBg = if (isPassing) StatusSuccessContainer else TertiaryAmberContainer
        val pctFg = if (isPassing) StatusSuccess else TertiaryAmber

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "$formattedObtained / $formattedTotal",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkRoyalBlue,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = pctBg,
          ) {
            Text(
              text = String.format("%.0f%%", pct),
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = pctFg,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      } else {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xFFEEF2F9),
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Text(
            text = "Pending",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
          )
        }
      }
    }
  }
}
