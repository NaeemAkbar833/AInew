package com.example.ui.results

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassResultsScreen(
  classId: String,
  viewModel: ClassResultsViewModel,
  onNavigateBack: () -> Unit,
  onStudentClick: (String) -> Unit,
  onClassReportClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(classId) {
    viewModel.loadClassResults(classId)
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  val classRoom = uiState.classRoom

  Scaffold(
    topBar = {
      GradeScanStitchHeader(
        title = classRoom?.subjectName ?: "Class Results",
        subtitle = classRoom?.let { "${it.session} • ${it.semester} Semester" } ?: "Approved student scores",
        badgeText = "SCORE SHEET",
        onBackClick = onNavigateBack,
        backTestTag = "class_results_back_button",
        actions = {
          IconButton(
            onClick = onClassReportClick,
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.18f))
              .testTag("class_report_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Description,
              contentDescription = "Class Report",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("class_results_screen"),
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
          items(8) { ExamCardSkeleton() }
        }
      } else if (uiState.students.isEmpty()) {
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
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Surface(
                shape = CircleShape,
                color = LightBlueHighlight,
                modifier = Modifier.size(48.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "No Students",
                    tint = RoyalBlue,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No students in this class",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Add students to this class and approve their evaluations to see results.",
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
            .testTag("class_results_list"),
        ) {
          item {
            Text(
              text = "Student Results",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(bottom = 2.dp),
            )
          }
          items(
            items = uiState.students,
            key = { it.id ?: "" }
          ) { student ->
            val evaluation = uiState.evaluationsByStudentId[student.id]
            StudentResultRowCard(
              student = student,
              evaluation = evaluation,
              defaultTotalMarks = classRoom?.totalMarks?.toDouble() ?: 100.0,
              onClick = { student.id?.let { onStudentClick(it) } },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StudentResultRowCard(
  student: Student,
  evaluation: EvaluationDbRecord?,
  defaultTotalMarks: Double,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isApproved = evaluation != null && evaluation.status == "approved"
  val shape = RoundedCornerShape(16.dp)

  Card(
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("student_result_card_${student.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = LightBlueHighlight,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = "Student",
            tint = RoyalBlue,
            modifier = Modifier.size(19.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (isApproved) {
          val obtained = evaluation.totalMarksObtained ?: 0.0
          val total = evaluation.totalMarks ?: defaultTotalMarks
          val obtainedStr = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
          val totalStr = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "$obtainedStr / $totalStr",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold,
              color = DarkRoyalBlue,
            )
            val pct = evaluation.percentage ?: if (total > 0) (obtained / total) * 100 else null
            if (pct != null) {
              val pctStr = if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "${String.format("%.1f", pct)}%"
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = StatusSuccessContainer,
              ) {
                Text(
                  text = pctStr,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.sp,
                  color = StatusSuccess,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
              }
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
              color = TextSecondary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
          contentDescription = "View Result Detail",
          tint = TextSecondary,
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}
