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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarToday
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Exam
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.exam.ExamViewModel
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AccentVioletContainer
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
  viewModel: ExamViewModel,
  onNavigateBack: () -> Unit,
  onExamClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.loadExams()
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  Scaffold(
    topBar = {
      GradeScanStitchHeader(
        title = "Results & Reports",
        subtitle = "Select an exam to view class results and analytics",
        badgeText = "ANALYTICS & REPORTS",
        onBackClick = onNavigateBack,
        backTestTag = "results_back_button",
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("results_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading && uiState.exams.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = RoyalBlue,
          )
        }
      } else if (uiState.exams.isEmpty()) {
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
                color = AccentVioletContainer,
                modifier = Modifier.size(48.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.Assessment,
                    contentDescription = "No Exams",
                    tint = AccentViolet,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No exams found",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Create an exam first to view results and reports.",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.5.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("results_exams_list"),
        ) {
          item {
            Text(
              text = "Select an exam to inspect approved student marks:",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
            )
          }
          items(
            items = uiState.exams,
            key = { it.id ?: "" }
          ) { exam ->
            ResultExamCard(
              exam = exam,
              onClick = { exam.id?.let { onExamClick(it) } },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultExamCard(
  exam: Exam,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (badgeBg, badgeFg) = when (exam.type.lowercase()) {
    "mid term" -> LightBlueHighlight to DarkRoyalBlue
    "final" -> AccentVioletContainer to AccentViolet
    "supply" -> TertiaryAmberContainer to StatusAmber
    else -> SecondaryTealContainer to SecondaryTeal
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_exam_card_${exam.id}"),
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = AccentVioletContainer,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Assessment,
            contentDescription = "Results",
            tint = AccentViolet,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = exam.name,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 20.sp,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeBg,
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Text(
              text = exam.type,
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              color = badgeFg,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          if (exam.date.isNotBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = "Date",
                tint = TextSecondary,
                modifier = Modifier.size(13.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = exam.date,
                fontFamily = FigtreeFontFamily,
                fontSize = 12.5.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Results",
        tint = TextSecondary,
        modifier = Modifier.size(12.dp),
      )
    }
  }
}
