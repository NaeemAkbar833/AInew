package com.example.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClassRoom
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.Student
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGradient
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

// Type-specific palette tokens matching approved GradeScan screens
private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

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
  var searchQuery by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(classId) {
    viewModel.loadClassResults(classId)
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
    }
  }

  val classRoom = uiState.classRoom
  val students = uiState.students
  val evaluationsMap = uiState.evaluationsByStudentId

  val filteredStudents = remember(students, searchQuery) {
    val trimmedQuery = searchQuery.trim()
    if (trimmedQuery.isEmpty()) {
      students
    } else {
      students.filter { student ->
        student.name.contains(trimmedQuery, ignoreCase = true) ||
          student.rollNumber.contains(trimmedQuery, ignoreCase = true) ||
          student.fatherName.contains(trimmedQuery, ignoreCase = true)
      }
    }
  }

  val approvedCount = remember(students, evaluationsMap) {
    students.count { student ->
      val eval = evaluationsMap[student.id]
      eval != null && eval.status == "approved"
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

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("class_results_screen"),
  ) {
    // Six-stop backdrop gradient layer extending behind topBar down into upper summary card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
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
                  .testTag("class_results_back_button"),
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
                  text = classRoom?.subjectName ?: "Class Results",
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
                  text = "Student results",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.92f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clickable(
                      role = Role.Button,
                      onClick = onClassReportClick,
                    )
                    .testTag("class_report_button"),
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
                      imageVector = Icons.Outlined.Description,
                      contentDescription = "Class Report",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clickable(
                      role = Role.Button,
                      onClick = { viewModel.loadClassResults(classId) },
                    )
                    .testTag("refresh_class_results_button"),
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
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
      ) {
        when {
          uiState.isLoading && students.isEmpty() && classRoom == null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("class_results_loading_indicator"),
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
                  text = "Loading student results…",
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
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("class_results_error_state"),
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
                    text = "Unable to load student results",
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
                    onClick = { viewModel.loadClassResults(classId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("class_results_retry_button"),
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
              contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxSize()
                .testTag("class_results_list"),
            ) {
              if (classRoom != null) {
                item(key = "class_results_summary_card") {
                  ClassResultsSummaryCard(
                    classRoom = classRoom,
                    studentCount = students.size,
                    approvedCount = approvedCount,
                  )
                }
              }

              item(key = "class_results_section_header") {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                      modifier = Modifier.weight(1f),
                    ) {
                      Text(
                        text = "Student Results",
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

                    val reportBtnShape = RoundedCornerShape(12.dp)
                    Box(
                      modifier = Modifier
                        .heightIn(min = 38.dp)
                        .shadow(
                          elevation = 2.dp,
                          shape = reportBtnShape,
                          spotColor = DarkRoyalBlue.copy(alpha = 0.22f),
                        )
                        .clip(reportBtnShape)
                        .background(PrimaryBlueGradient)
                        .clickable(
                          role = Role.Button,
                          onClick = onClassReportClick,
                        )
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                        .testTag("view_class_report_button"),
                      contentAlignment = Alignment.Center,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          imageVector = Icons.Outlined.Assessment,
                          contentDescription = null,
                          tint = Color.White,
                          modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                          text = "Class Report",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 12.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                        )
                      }
                    }
                  }

                  if (students.isNotEmpty()) {
                    ClassResultsSearchField(
                      query = searchQuery,
                      onQueryChange = { searchQuery = it },
                      onClearQuery = { searchQuery = "" },
                    )
                  }
                }
              }

              when {
                uiState.isLoading && students.isEmpty() -> {
                  item(key = "class_results_inline_loading") {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .testTag("class_results_loading_indicator"),
                      contentAlignment = Alignment.Center,
                    ) {
                      CircularProgressIndicator(
                        color = RoyalBlue,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp),
                      )
                    }
                  }
                }

                students.isEmpty() -> {
                  item(key = "class_results_empty_state") {
                    Card(
                      shape = RoundedCornerShape(18.dp),
                      colors = CardDefaults.cardColors(containerColor = Color.White),
                      border = BorderStroke(1.dp, BorderGray),
                      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_results_empty_state"),
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
                          text = "No students in this class",
                          fontFamily = BricolageGrotesqueFontFamily,
                          fontSize = 18.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                          text = "Add students to this class and approve their evaluations to see results.",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 13.sp,
                          lineHeight = 18.sp,
                          color = TextSecondary,
                          textAlign = TextAlign.Center,
                        )
                      }
                    }
                  }
                }

                filteredStudents.isEmpty() -> {
                  item(key = "class_results_filtered_empty_state") {
                    Surface(
                      shape = RoundedCornerShape(18.dp),
                      color = Color.White,
                      border = BorderStroke(1.dp, BorderGray),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_results_filtered_empty_state"),
                    ) {
                      Column(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                      ) {
                        Surface(
                          shape = RoundedCornerShape(12.dp),
                          color = SoftBlueTint,
                          border = BorderStroke(1.dp, LightBlueHighlight),
                          modifier = Modifier.size(42.dp),
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Icon(
                              imageVector = Icons.Outlined.SearchOff,
                              contentDescription = null,
                              tint = PrimaryBlue,
                              modifier = Modifier.size(20.dp),
                            )
                          }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                          text = "No matching students",
                          fontFamily = BricolageGrotesqueFontFamily,
                          fontSize = 15.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                          text = "No students match \"$searchQuery\".",
                          fontFamily = FigtreeFontFamily,
                          fontSize = 12.5.sp,
                          lineHeight = 17.sp,
                          color = TextSecondary,
                          textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { searchQuery = "" }) {
                          Text(
                            text = "Clear search",
                            fontFamily = FigtreeFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                          )
                        }
                      }
                    }
                  }
                }

                else -> {
                  item(key = "student_results_table") {
                    StudentResultsTable(
                      students = filteredStudents,
                      evaluationsMap = evaluationsMap,
                      defaultTotalMarks = classRoom?.totalMarks?.toDouble() ?: 100.0,
                      onStudentClick = onStudentClick,
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

@Composable
private fun ClassResultsSummaryCard(
  classRoom: ClassRoom,
  studentCount: Int,
  approvedCount: Int,
  modifier: Modifier = Modifier,
) {
  val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
  val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight
  val timingTint = if (isMorning) StatusAmber else FinalText
  val timingBg = if (isMorning) TertiaryAmberContainer else FinalTint

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("class_results_summary_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
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
            shape = RoundedCornerShape(8.dp),
            color = SoftBlueTint,
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
            modifier = Modifier.size(30.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Assessment,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = classRoom.subjectName,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 15.sp,
              lineHeight = 19.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$approvedCount of $studentCount approved",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium,
              color = TextSecondary,
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (approvedCount > 0) StatusSuccessContainer else SoftBlueTint,
          border = BorderStroke(
            width = 1.dp,
            color = if (approvedCount > 0) StatusSuccess.copy(alpha = 0.20f) else PrimaryBlue.copy(alpha = 0.14f),
          ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Icon(
              imageVector = if (approvedCount > 0) Icons.Default.Verified else Icons.Outlined.Group,
              contentDescription = null,
              tint = if (approvedCount > 0) StatusSuccess else PrimaryBlue,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "$approvedCount/$studentCount Approved",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (approvedCount > 0) StatusSuccess else PrimaryBlue,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = BorderGray, thickness = 1.dp)
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ClassSummaryGridItem(
          icon = Icons.Default.CalendarMonth,
          label = "Session",
          value = classRoom.session.ifBlank { "—" },
          modifier = Modifier.weight(1f),
        )
        ClassSummaryGridItem(
          icon = Icons.Default.Badge,
          label = "Semester",
          value = classRoom.semester.ifBlank { "—" },
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ClassSummaryGridItem(
          icon = timingIcon,
          label = "Timing",
          value = classRoom.timing.ifBlank { "—" },
          modifier = Modifier.weight(1f),
          iconTint = timingTint,
          iconBg = timingBg,
        )
        ClassSummaryGridItem(
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

@Composable
private fun ClassSummaryGridItem(
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

@Composable
private fun ClassResultsSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val shape = RoundedCornerShape(14.dp)
  val borderColor = if (isFocused) PrimaryBlue else BorderGray
  val iconTint = if (isFocused || query.isNotBlank()) PrimaryBlue else TextSecondary

  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    interactionSource = interactionSource,
    textStyle = TextStyle(
      fontFamily = FigtreeFontFamily,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    cursorBrush = SolidColor(PrimaryBlue),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 46.dp)
      .testTag("class_results_search_input"),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(Color.White)
          .border(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = borderColor,
            shape = shape,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search students",
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (query.isEmpty()) {
            Text(
              text = "Search by student name or roll number…",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
        if (query.isNotEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(SoftBlueTint)
              .clickable(
                role = Role.Button,
                onClick = onClearQuery,
              )
              .testTag("class_results_search_clear_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search",
              tint = TextSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    },
  )
}

@Composable
private fun StudentResultsTable(
  students: List<Student>,
  evaluationsMap: Map<String, EvaluationDbRecord>,
  defaultTotalMarks: Double,
  onStudentClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tableShape = RoundedCornerShape(16.dp)

  Card(
    shape = tableShape,
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(tableShape)
      .testTag("student_results_table"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Table Header Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SoftBlueTint)
          .padding(start = 10.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "R/o",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = DarkRoyalBlue,
          textAlign = TextAlign.Start,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.weight(0.55f),
        )
        Text(
          text = "Student",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = DarkRoyalBlue,
          textAlign = TextAlign.Start,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.weight(2.20f),
        )
        Text(
          text = "Marks",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = DarkRoyalBlue,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.weight(0.80f),
        )
        Text(
          text = "%",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = DarkRoyalBlue,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.weight(0.60f),
        )
        Text(
          text = "Status",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = DarkRoyalBlue,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.width(36.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      // Table Student Rows
      students.forEachIndexed { index, student ->
        val evaluation = evaluationsMap[student.id]
        StudentResultTableRow(
          student = student,
          evaluation = evaluation,
          defaultTotalMarks = defaultTotalMarks,
          onClick = { student.id?.let { onStudentClick(it) } },
        )
        if (index < students.lastIndex) {
          HorizontalDivider(
            color = BorderGray.copy(alpha = 0.7f),
            thickness = 0.8.dp,
          )
        }
      }
    }
  }
}

@Composable
private fun StudentResultTableRow(
  student: Student,
  evaluation: EvaluationDbRecord?,
  defaultTotalMarks: Double,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isApproved = evaluation != null && evaluation.status == "approved"

  val obtained = evaluation?.totalMarksObtained ?: 0.0
  val total = evaluation?.totalMarks ?: defaultTotalMarks
  val obtainedStr = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else obtained.toString()
  val totalStr = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()

  val pct = if (isApproved) {
    evaluation?.percentage ?: if (total > 0) (obtained / total) * 100 else null
  } else {
    null
  }
  val pctStr = if (pct != null) {
    if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "${String.format("%.1f", pct)}%"
  } else {
    "—"
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (isApproved) {
          Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
          )
        } else {
          Modifier
        }
      )
      .padding(start = 10.dp, top = 10.dp, end = 4.dp, bottom = 10.dp)
      .testTag("student_result_card_${student.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // 1. Roll No.
    Text(
      text = student.rollNumber.ifBlank { "—" },
      fontFamily = FigtreeFontFamily,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = PrimaryBlue,
      textAlign = TextAlign.Start,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(0.55f),
    )

    // 2. Student
    Text(
      text = student.name,
      fontFamily = BricolageGrotesqueFontFamily,
      fontSize = 13.sp,
      lineHeight = 17.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
      textAlign = TextAlign.Start,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(2.20f),
    )

    // 3. Marks
    Text(
      text = if (isApproved) "$obtainedStr/$totalStr" else "—",
      fontFamily = if (isApproved) BricolageGrotesqueFontFamily else FigtreeFontFamily,
      fontSize = 12.5.sp,
      fontWeight = if (isApproved) FontWeight.Bold else FontWeight.Medium,
      color = if (isApproved) TextPrimary else TextSecondary,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      modifier = Modifier.weight(0.80f),
    )

    // 4. %
    Text(
      text = pctStr,
      fontFamily = FigtreeFontFamily,
      fontSize = 12.sp,
      fontWeight = if (isApproved) FontWeight.Bold else FontWeight.Medium,
      color = if (isApproved) StatusSuccess else TextSecondary,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      modifier = Modifier.weight(0.60f),
    )

    // 5. Status (icon only)
    Box(
      modifier = Modifier.width(36.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (isApproved) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = "Approved",
          tint = StatusSuccess,
          modifier = Modifier.size(18.dp),
        )
      } else {
        Icon(
          imageVector = Icons.Outlined.Schedule,
          contentDescription = "Pending",
          tint = TextSecondary.copy(alpha = 0.7f),
          modifier = Modifier.size(18.dp),
        )
      }
    }

    // 6. Trailing chevron (>)
    Box(
      modifier = Modifier
        .width(16.dp)
        .height(24.dp)
        .then(
          if (isApproved) {
            Modifier.clickable(
              role = Role.Button,
              onClick = onClick,
            )
          } else {
            Modifier
          }
        )
        .testTag("view_result_chevron_${student.id}"),
      contentAlignment = Alignment.CenterEnd,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = if (isApproved) "View Result Detail" else null,
        tint = if (isApproved) PrimaryBlue else TextSecondary.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}



