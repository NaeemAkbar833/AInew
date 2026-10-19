package com.example.ui.results

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClassRoom
import com.example.ui.classes.ClassViewModel
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.components.GradeScanStitchHeader
import com.example.ui.exam.ExamViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
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
          contentPadding = PaddingValues(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(5) { ExamCardSkeleton() }
        }
      } else if (classesForExam.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Surface(
                shape = CircleShape,
                color = SecondaryTealContainer,
                modifier = Modifier.size(64.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.Class,
                    contentDescription = "No Classes",
                    tint = SecondaryTeal,
                    modifier = Modifier.size(30.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "No classes found for this exam",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Add classes and evaluate student papers to view results.",
                fontFamily = FigtreeFontFamily,
                fontSize = 13.5.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
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

@Composable
private fun ResultClassCard(
  classRoom: ClassRoom,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("result_class_card_${classRoom.id}"),
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = SecondaryTealContainer,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Class,
            contentDescription = "Class",
            tint = SecondaryTeal,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = classRoom.subjectName,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${classRoom.session} • ${classRoom.semester} • ${classRoom.timing}",
            fontFamily = FigtreeFontFamily,
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = LightBlueHighlight,
          ) {
            Text(
              text = "${classRoom.totalMarks} Marks",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              color = DarkRoyalBlue,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Open Class Results",
        tint = TextSecondary,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}
