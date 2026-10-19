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
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.ClassRoom
import com.example.ui.classes.ClassViewModel
import com.example.ui.components.ExamCardSkeleton
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

  LaunchedEffect(classUiState.errorMessage) {
    classUiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  LaunchedEffect(examUiState.errorMessage) {
    examUiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  val selectedExam = examUiState.selectedExam
  val classesForExam = classUiState.classes

  Scaffold(
    topBar = {
      GradeScanStitchHeader(
        title = selectedExam?.name ?: "Exam Classes",
        subtitle = "Select a class to view student results",
        badgeText = "CLASS RESULTS",
        onBackClick = onNavigateBack,
        backTestTag = "exam_classes_results_back_button",
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = BackgroundLight,
    modifier = modifier.testTag("exam_classes_results_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BackgroundLight)
        .padding(paddingValues),
    ) {
      if (examUiState.isLoadingDetail || classUiState.isLoading) {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(5) { ExamCardSkeleton() }
        }
      } else if (classesForExam.isEmpty()) {
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
                color = SecondaryTealContainer,
                modifier = Modifier.size(48.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.Class,
                    contentDescription = "No Classes",
                    tint = SecondaryTeal,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No classes found for this exam",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Add classes and evaluate student papers to view results.",
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
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("exam_classes_results_list"),
        ) {
          items(
            items = classesForExam,
            key = { it.id ?: "" }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingBadgeBg = if (isMorning) TertiaryAmberContainer else AccentVioletContainer
  val timingBadgeFg = if (isMorning) StatusAmber else AccentViolet
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_class_card_${classRoom.id}"),
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
        color = SecondaryTealContainer,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Class,
            contentDescription = "Class",
            tint = SecondaryTeal,
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
          text = classRoom.subjectName,
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
            color = timingBadgeBg,
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
                fontSize = 11.5.sp,
                color = timingBadgeFg,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = LightBlueHighlight,
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Text(
              text = "${classRoom.totalMarks} Marks",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              color = DarkRoyalBlue,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }

          Text(
            text = "${classRoom.session} • ${classRoom.semester}",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Class Results",
        tint = TextSecondary,
        modifier = Modifier.size(12.dp),
      )
    }
  }
}
