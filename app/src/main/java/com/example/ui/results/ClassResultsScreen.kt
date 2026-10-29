package com.example.ui.results

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas as PdfCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
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
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  onClassReportClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var isExportingPdf by rememberSaveable { mutableStateOf(false) }
  val exportInProgressGuard = remember { AtomicBoolean(false) }

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
      eval != null && eval.status.equals("approved", ignoreCase = true)
    }
  }

  val canExportPdf = !uiState.isLoading && !isExportingPdf && (classRoom != null || students.isNotEmpty())

  val handleExportPdf: () -> Unit = {
    if (!isExportingPdf && !uiState.isLoading) {
      if (classRoom == null && students.isEmpty()) {
        coroutineScope.launch {
          snackbarHostState.showSnackbar(
            message = "No class results available to export.",
            duration = SnackbarDuration.Short,
          )
        }
      } else if (exportInProgressGuard.compareAndSet(false, true)) {
        isExportingPdf = true
        coroutineScope.launch {
          val result = withContext(Dispatchers.IO) {
            runCatching {
              exportClassResultsToPdf(
                context = context,
                classRoom = classRoom,
                students = students,
                evaluationsMap = evaluationsMap,
              )
            }
          }
          isExportingPdf = false
          exportInProgressGuard.set(false)
          result.fold(
            onSuccess = {
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar(
                message = "PDF exported successfully",
                duration = SnackbarDuration.Short,
              )
            },
            onFailure = { error ->
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar(
                message = "Unable to export PDF: ${error.localizedMessage ?: "Unexpected error"}",
                duration = SnackbarDuration.Short,
              )
            },
          )
        }
      }
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
                      enabled = canExportPdf,
                      role = Role.Button,
                      onClick = handleExportPdf,
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
                      contentDescription = "Export PDF",
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
      snackbarHost = {
        SnackbarHost(hostState = snackbarHostState) { snackbarData ->
          val message = snackbarData.visuals.message
          val isError = message.startsWith("Unable", ignoreCase = true) ||
            message.startsWith("No ", ignoreCase = true) ||
            message.contains("error", ignoreCase = true) ||
            message.contains("fail", ignoreCase = true)

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = Color.White,
              border = BorderStroke(
                width = 1.dp,
                color = if (isError) {
                  StatusWarning.copy(alpha = 0.28f)
                } else {
                  PrimaryBlue.copy(alpha = 0.22f)
                },
              ),
              shadowElevation = 6.dp,
              modifier = Modifier.testTag("class_results_snackbar"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isError) StatusWarningContainer else SoftBlueTint,
                  border = BorderStroke(
                    width = 1.dp,
                    color = if (isError) {
                      StatusWarning.copy(alpha = 0.20f)
                    } else {
                      PrimaryBlue.copy(alpha = 0.16f)
                    },
                  ),
                  modifier = Modifier.size(26.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = if (isError) StatusWarning else PrimaryBlue,
                      modifier = Modifier.size(15.dp),
                    )
                  }
                }

                Text(
                  text = message,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.sp,
                  lineHeight = 17.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      },
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
              if (classRoom != null || !uiState.isLoading || students.isNotEmpty()) {
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

                    val exportBtnShape = RoundedCornerShape(12.dp)
                    Box(
                      modifier = Modifier
                        .heightIn(min = 38.dp)
                        .shadow(
                          elevation = if (canExportPdf) 2.dp else 0.dp,
                          shape = exportBtnShape,
                          spotColor = DarkRoyalBlue.copy(alpha = 0.22f),
                        )
                        .clip(exportBtnShape)
                        .background(
                          brush = PrimaryBlueGradient,
                          alpha = if (canExportPdf) 1f else 0.6f,
                        )
                        .clickable(
                          enabled = canExportPdf,
                          role = Role.Button,
                          onClick = handleExportPdf,
                        )
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                        .testTag("export_pdf_button"),
                      contentAlignment = Alignment.Center,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExportingPdf) {
                          CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                          )
                        } else {
                          Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "Export PDF",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                          )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                          text = if (isExportingPdf) "Exporting…" else "Export PDF",
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
  classRoom: ClassRoom?,
  studentCount: Int,
  approvedCount: Int,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
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
        .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
      if (classRoom != null) {
        val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
        val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight
        val timingTint = if (isMorning) StatusAmber else FinalText
        val timingBg = if (isMorning) TertiaryAmberContainer else FinalTint

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
              shape = RoundedCornerShape(7.dp),
              color = SoftBlueTint,
              border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
              modifier = Modifier.size(24.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.Assessment,
                  contentDescription = null,
                  tint = PrimaryBlue,
                  modifier = Modifier.size(13.dp),
                )
              }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
              text = classRoom.subjectName,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 14.sp,
              lineHeight = 17.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.width(6.dp))

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
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
              Icon(
                imageVector = if (approvedCount > 0) Icons.Default.Verified else Icons.Outlined.Group,
                contentDescription = null,
                tint = if (approvedCount > 0) StatusSuccess else PrimaryBlue,
                modifier = Modifier.size(11.dp),
              )
              Spacer(modifier = Modifier.width(3.5.dp))
              Text(
                text = "$approvedCount/$studentCount Approved",
                fontFamily = FigtreeFontFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (approvedCount > 0) StatusSuccess else PrimaryBlue,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BorderGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
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

        Spacer(modifier = Modifier.height(5.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
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

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BorderGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(6.dp))
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("class_results_performance_summary_card"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ClassResultsSummaryMetricColumn(
          label = "Total Students",
          value = studentCount.toString(),
          icon = Icons.Outlined.Group,
          iconTint = PrimaryBlue,
          iconContainerColor = LightBlueHighlight,
          iconBorderColor = PrimaryBlue.copy(alpha = 0.20f),
          modifier = Modifier.weight(1f),
        )

        Box(
          modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(BorderGray),
        )

        ClassResultsSummaryMetricColumn(
          label = "Graded / Approved",
          value = approvedCount.toString(),
          icon = Icons.Default.Verified,
          iconTint = StatusSuccess,
          iconContainerColor = StatusSuccessContainer,
          iconBorderColor = StatusSuccess.copy(alpha = 0.25f),
          modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp),
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
      modifier = Modifier.size(18.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(10.5.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(5.dp))
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "$label: ",
        fontFamily = FigtreeFontFamily,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        maxLines = 1,
      )
      Text(
        text = value,
        fontFamily = FigtreeFontFamily,
        fontSize = 11.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ClassResultsSummaryMetricColumn(
  label: String,
  value: String,
  icon: ImageVector,
  iconTint: Color,
  iconContainerColor: Color,
  iconBorderColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(7.dp),
      color = iconContainerColor,
      border = BorderStroke(1.dp, iconBorderColor),
      modifier = Modifier.size(26.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(14.dp),
        )
      }
    }

    Column {
      Text(
        text = value,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextPrimary,
        lineHeight = 17.sp,
      )
      Text(
        text = label,
        fontFamily = FigtreeFontFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        lineHeight = 12.sp,
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

internal fun exportClassResultsToPdf(
  context: Context,
  classRoom: ClassRoom?,
  students: List<Student>,
  evaluationsMap: Map<String, EvaluationDbRecord>,
): String {
  val pdfBytes = generateClassResultsPdfBytes(
    classRoom = classRoom,
    students = students,
    evaluationsMap = evaluationsMap,
  )

  val subjectSlug = (classRoom?.subjectName ?: "Class_Results")
    .trim()
    .replace(Regex("[^A-Za-z0-9]+"), "_")
    .trim('_')
    .ifBlank { "Class_Results" }
  val semesterSlug = (classRoom?.semester ?: "")
    .trim()
    .replace(Regex("[^A-Za-z0-9]+"), "_")
    .trim('_')
  val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
  val fileName = buildString {
    append("PaperWise_")
    append(subjectSlug)
    if (semesterSlug.isNotBlank()) {
      append("_")
      append(semesterSlug)
    }
    append("_Results_")
    append(timestamp)
    append(".pdf")
  }

  // Save a single copy of the PDF into the device's Downloads destination
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val contentValues = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, fileName)
      put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
      put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
      ?: throw IOException("Unable to create PDF entry in Downloads")
    try {
      resolver.openOutputStream(downloadUri)?.use { out ->
        out.write(pdfBytes)
        out.flush()
      } ?: throw IOException("Unable to open PDF output stream")

      contentValues.clear()
      contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(downloadUri, contentValues, null, null)
    } catch (e: Exception) {
      runCatching { resolver.delete(downloadUri, null, null) }
      throw e
    }
  } else {
    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
      ?: context.cacheDir
    if (!downloadsDir.exists()) {
      downloadsDir.mkdirs()
    }
    val outputFile = File(downloadsDir, fileName)
    FileOutputStream(outputFile).use { out ->
      out.write(pdfBytes)
      out.flush()
    }
  }

  return fileName
}

internal fun generateClassResultsPdfBytes(
  classRoom: ClassRoom?,
  students: List<Student>,
  evaluationsMap: Map<String, EvaluationDbRecord>,
): ByteArray {
  val pageWidth = 595
  val pageHeight = 842
  val marginH = 36f
  val marginTop = 34f
  val marginBottom = 40f
  val contentWidth = pageWidth - (marginH * 2f) // 523f

  val defaultTotalMarks = classRoom?.totalMarks?.toDouble() ?: 100.0
  val totalStudents = students.size
  val approvedCount = students.count { student ->
    val eval = evaluationsMap[student.id]
    eval != null && eval.status.equals("approved", ignoreCase = true)
  }

  val exportDateText = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())

  // GradeScan PDF Color Tokens (ARGB ints)
  val colorDarkRoyalBlue = 0xFF00174B.toInt()
  val colorPrimaryBlue = 0xFF0A3299.toInt()
  val colorRoyalBlue = 0xFF1D4ED8.toInt()
  val colorSoftBlueTint = 0xFFEEF3FF.toInt()
  val colorLightBlueHighlight = 0xFFDCE6FF.toInt()
  val colorBorderGray = 0xFFE2E8F0.toInt()
  val colorTableBorder = 0xFFCBD5E1.toInt()
  val colorTextPrimary = 0xFF0F172A.toInt()
  val colorTextSecondary = 0xFF475569.toInt()
  val colorStatusSuccess = 0xFF15803D.toInt()
  val colorStatusSuccessBg = 0xFFDCFCE7.toInt()
  val colorStatusSuccessBorder = 0xFF86EFAC.toInt()
  val colorAmberText = 0xFFB45309.toInt()
  val colorAmberBg = 0xFFFEF3C7.toInt()
  val colorFinalText = 0xFF4A1F8F.toInt()
  val colorFinalBg = 0xFFEEDFFF.toInt()
  val colorPendingBg = 0xFFF1F5F9.toInt()
  val colorPendingText = 0xFF64748B.toInt()
  val colorRowAltBg = 0xFFF8FAFC.toInt()
  val colorWhite = 0xFFFFFFFF.toInt()

  val bannerHeight = 68f
  val mergedCardHeight = 106f
  val tableHeaderHeight = 26f
  val rowHeight = 25f
  val footerReserve = 28f

  // Calculate pagination up front so "Page X of Y" is accurate on every page
  val firstPageTableTopY = marginTop + bannerHeight + 10f + mergedCardHeight + 14f + 18f // 250f
  val firstPageAvailableHeight = (pageHeight - marginBottom - footerReserve) - (firstPageTableTopY + tableHeaderHeight)
  val rowsOnFirstPage = max(1, (firstPageAvailableHeight / rowHeight).toInt())

  val continuationTableTopY = marginTop + 36f + 12f
  val continuationAvailableHeight = (pageHeight - marginBottom - footerReserve) - (continuationTableTopY + tableHeaderHeight)
  val rowsPerContinuationPage = max(1, (continuationAvailableHeight / rowHeight).toInt())

  val totalPages = if (students.size <= rowsOnFirstPage) {
    1
  } else {
    val remaining = students.size - rowsOnFirstPage
    1 + ceil(remaining.toDouble() / rowsPerContinuationPage.toDouble()).toInt()
  }

  // Column widths totaling 523f (contentWidth) for 6 columns:
  // 1. R/o | 2. Student Name | 3. Father's Name | 4. Marks | 5. Percentage | 6. Result Status
  val colRollWidth = 48f
  val colStudentWidth = 128f
  val colFatherWidth = 125f
  val colMarksWidth = 74f
  val colPctWidth = 68f
  val colStatusWidth = 80f

  val colRollX = marginH
  val colStudentX = colRollX + colRollWidth
  val colFatherX = colStudentX + colStudentWidth
  val colMarksX = colFatherX + colFatherWidth
  val colPctX = colMarksX + colMarksWidth
  val colStatusX = colPctX + colPctWidth

  val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1f
  }
  val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

  fun drawCompactGridDetailItem(
    canvas: PdfCanvas,
    startX: Float,
    centerY: Float,
    maxWidth: Float,
    badgeSymbol: String,
    badgeBgColor: Int,
    badgeTextColor: Int,
    label: String,
    value: String,
  ) {
    val badgeSize = 14f
    val badgeRect = RectF(
      startX,
      centerY - (badgeSize / 2f),
      startX + badgeSize,
      centerY + (badgeSize / 2f),
    )
    fillPaint.shader = null
    fillPaint.color = badgeBgColor
    canvas.drawRoundRect(badgeRect, 3.5f, 3.5f, fillPaint)

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 7.2f
    textPaint.color = badgeTextColor
    canvas.drawText(
      badgeSymbol,
      badgeRect.centerX(),
      badgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
      textPaint,
    )

    val textStartX = startX + badgeSize + 5f
    val labelText = "$label: "
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 8.5f
    textPaint.color = colorTextSecondary
    val labelWidth = textPaint.measureText(labelText)
    val baselineY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(labelText, textStartX, baselineY, textPaint)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 9.2f
    textPaint.color = colorTextPrimary
    val availableValWidth = max(20f, maxWidth - (badgeSize + 5f + labelWidth + 6f))
    val safeValue = TextUtils.ellipsize(
      value,
      textPaint,
      availableValWidth,
      TextUtils.TruncateAt.END,
    ).toString()
    canvas.drawText(safeValue, textStartX + labelWidth, baselineY, textPaint)
  }

  fun drawTableHeader(canvas: PdfCanvas, topY: Float) {
    val headerRect = RectF(marginH, topY, marginH + contentWidth, topY + tableHeaderHeight)
    fillPaint.shader = null
    fillPaint.color = colorSoftBlueTint
    canvas.drawRoundRect(headerRect, 8f, 8f, fillPaint)

    // Square off bottom corners of header row so it joins cleanly with data rows
    canvas.drawRect(marginH, topY + tableHeaderHeight - 8f, marginH + contentWidth, topY + tableHeaderHeight, fillPaint)

    strokePaint.color = colorTableBorder
    strokePaint.strokeWidth = 1f
    canvas.drawLine(marginH, topY + tableHeaderHeight, marginH + contentWidth, topY + tableHeaderHeight, strokePaint)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    textPaint.textSize = 8.8f
    textPaint.color = colorDarkRoyalBlue

    val baselineY = topY + (tableHeaderHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

    textPaint.textAlign = Paint.Align.LEFT
    canvas.drawText("R/o", colRollX + 8f, baselineY, textPaint)
    canvas.drawText("Student Name", colStudentX + 6f, baselineY, textPaint)
    canvas.drawText("Father's Name", colFatherX + 6f, baselineY, textPaint)

    textPaint.textAlign = Paint.Align.CENTER
    canvas.drawText("Marks", colMarksX + (colMarksWidth / 2f), baselineY, textPaint)
    canvas.drawText("Percentage", colPctX + (colPctWidth / 2f), baselineY, textPaint)
    canvas.drawText("Result Status", colStatusX + (colStatusWidth / 2f), baselineY, textPaint)
  }

  fun drawPageFooter(canvas: PdfCanvas, pageNumber: Int) {
    val footerLineY = pageHeight - marginBottom - 12f
    strokePaint.color = colorBorderGray
    strokePaint.strokeWidth = 0.8f
    canvas.drawLine(marginH, footerLineY, marginH + contentWidth, footerLineY, strokePaint)

    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    textPaint.textSize = 8.5f
    textPaint.color = colorTextSecondary
    val textY = pageHeight - marginBottom + 2f

    textPaint.textAlign = Paint.Align.LEFT
    canvas.drawText("PaperWise • Official Class Results Report", marginH, textY, textPaint)

    textPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Page $pageNumber of $totalPages", marginH + contentWidth, textY, textPaint)
  }

  val pdfDocument = PdfDocument()
  try {
    var studentIndex = 0
    for (pageNum in 1..totalPages) {
      val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
      val page = pdfDocument.startPage(pageInfo)
      val canvas = page.canvas

      // Clean page background
      fillPaint.shader = null
      fillPaint.color = colorWhite
      canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint)

      var currentY = marginTop

      if (pageNum == 1) {
        // 1. Header Gradient Banner
        val bannerRect = RectF(marginH, currentY, marginH + contentWidth, currentY + bannerHeight)
        fillPaint.shader = LinearGradient(
          marginH,
          currentY,
          marginH + contentWidth,
          currentY + bannerHeight,
          intArrayOf(colorDarkRoyalBlue, colorPrimaryBlue, colorRoyalBlue),
          floatArrayOf(0f, 0.6f, 1f),
          Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bannerRect, 12f, 12f, fillPaint)
        fillPaint.shader = null

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8f
        textPaint.color = 0xFFDCE6FF.toInt()
        canvas.drawText("GRADESCAN • CLASS RESULTS REPORT", marginH + 16f, currentY + 19f, textPaint)

        val subjectTitle = classRoom?.subjectName?.ifBlank { "Class Results" } ?: "Class Results"
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 16.5f
        textPaint.color = colorWhite
        val truncatedTitle = TextUtils.ellipsize(
          subjectTitle,
          textPaint,
          contentWidth - 155f,
          TextUtils.TruncateAt.END,
        ).toString()
        canvas.drawText(truncatedTitle, marginH + 16f, currentY + 41f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 9.2f
        textPaint.color = 0xFFE2E8F0.toInt()
        canvas.drawText("Student results & approved evaluations", marginH + 16f, currentY + 56f, textPaint)

        // Export date pill on right side of banner
        val dateBadgeWidth = 118f
        val dateBadgeHeight = 23f
        val dateBadgeRect = RectF(
          marginH + contentWidth - dateBadgeWidth - 14f,
          currentY + 15f,
          marginH + contentWidth - 14f,
          currentY + 15f + dateBadgeHeight,
        )
        fillPaint.color = 0x33FFFFFF
        canvas.drawRoundRect(dateBadgeRect, 7f, 7f, fillPaint)
        strokePaint.color = 0x55FFFFFF
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(dateBadgeRect, 7f, 7f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8.5f
        textPaint.color = colorWhite
        canvas.drawText(
          exportDateText,
          dateBadgeRect.centerX(),
          dateBadgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        currentY += bannerHeight + 10f

        // 2. Merged Compact Class Details & Performance Summary Card
        val mergedCardRect = RectF(marginH, currentY, marginH + contentWidth, currentY + mergedCardHeight)
        fillPaint.color = colorWhite
        canvas.drawRoundRect(mergedCardRect, 10f, 10f, fillPaint)
        strokePaint.color = colorBorderGray
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(mergedCardRect, 10f, 10f, strokePaint)

        // Top Row inside Merged Card: Icon badge + Subject Name + Approved Count Pill
        val headerBadgeRect = RectF(marginH + 12f, currentY + 6f, marginH + 28f, currentY + 22f)
        fillPaint.color = colorSoftBlueTint
        canvas.drawRoundRect(headerBadgeRect, 4.5f, 4.5f, fillPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8.5f
        textPaint.color = colorPrimaryBlue
        canvas.drawText(
          "≡",
          headerBadgeRect.centerX(),
          headerBadgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        val approvedBadgeText = "$approvedCount/$totalStudents Approved"
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8.2f
        val pillWidth = textPaint.measureText(approvedBadgeText) + 14f
        val pillRect = RectF(
          marginH + contentWidth - pillWidth - 12f,
          currentY + 6f,
          marginH + contentWidth - 12f,
          currentY + 22f,
        )
        fillPaint.color = if (approvedCount > 0) colorStatusSuccessBg else colorSoftBlueTint
        canvas.drawRoundRect(pillRect, 4.5f, 4.5f, fillPaint)
        strokePaint.color = if (approvedCount > 0) colorStatusSuccessBorder else colorLightBlueHighlight
        strokePaint.strokeWidth = 0.7f
        canvas.drawRoundRect(pillRect, 4.5f, 4.5f, strokePaint)
        textPaint.color = if (approvedCount > 0) colorStatusSuccess else colorPrimaryBlue
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
          approvedBadgeText,
          pillRect.centerX(),
          pillRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 11f
        textPaint.color = colorTextPrimary
        val classCardTitle = classRoom?.subjectName?.ifBlank { "Class Details" } ?: "Class Details"
        val truncatedCardTitle = TextUtils.ellipsize(
          classCardTitle,
          textPaint,
          contentWidth - pillWidth - 56f,
          TextUtils.TruncateAt.END,
        ).toString()
        canvas.drawText(
          truncatedCardTitle,
          marginH + 34f,
          headerBadgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        // Divider 1: between subject header and 2x2 details grid
        strokePaint.color = colorBorderGray
        strokePaint.strokeWidth = 0.8f
        canvas.drawLine(marginH + 12f, currentY + 26f, marginH + contentWidth - 12f, currentY + 26f, strokePaint)

        // Compact 2x2 Grid: Session | Semester (row 1), Timing | Total Marks (row 2)
        val halfCardWidth = (contentWidth - 24f) / 2f
        val gridCol1X = marginH + 12f
        val gridCol2X = marginH + 12f + halfCardWidth + 6f
        val gridColMaxW = halfCardWidth - 6f

        val isMorning = classRoom?.timing?.equals("Morning", ignoreCase = true) == true
        val timingBgColor = if (isMorning) colorAmberBg else colorFinalBg
        val timingTextColor = if (isMorning) colorAmberText else colorFinalText

        // Grid Row 1
        drawCompactGridDetailItem(
          canvas = canvas,
          startX = gridCol1X,
          centerY = currentY + 37f,
          maxWidth = gridColMaxW,
          badgeSymbol = "S",
          badgeBgColor = colorSoftBlueTint,
          badgeTextColor = colorPrimaryBlue,
          label = "Session",
          value = classRoom?.session?.ifBlank { "—" } ?: "—",
        )
        drawCompactGridDetailItem(
          canvas = canvas,
          startX = gridCol2X,
          centerY = currentY + 37f,
          maxWidth = gridColMaxW,
          badgeSymbol = "Sem",
          badgeBgColor = colorSoftBlueTint,
          badgeTextColor = colorPrimaryBlue,
          label = "Semester",
          value = classRoom?.semester?.ifBlank { "—" } ?: "—",
        )

        // Grid Row 2
        drawCompactGridDetailItem(
          canvas = canvas,
          startX = gridCol1X,
          centerY = currentY + 55f,
          maxWidth = gridColMaxW,
          badgeSymbol = if (isMorning) "☀" else "☾",
          badgeBgColor = timingBgColor,
          badgeTextColor = timingTextColor,
          label = "Timing",
          value = classRoom?.timing?.ifBlank { "—" } ?: "—",
        )
        drawCompactGridDetailItem(
          canvas = canvas,
          startX = gridCol2X,
          centerY = currentY + 55f,
          maxWidth = gridColMaxW,
          badgeSymbol = "M",
          badgeBgColor = colorSoftBlueTint,
          badgeTextColor = colorPrimaryBlue,
          label = "Total Marks",
          value = if (classRoom != null) "${classRoom.totalMarks} Marks" else "—",
        )

        // Divider 2: between 2x2 details grid and side-by-side performance metrics
        strokePaint.color = colorBorderGray
        strokePaint.strokeWidth = 0.8f
        canvas.drawLine(marginH + 12f, currentY + 67f, marginH + contentWidth - 12f, currentY + 67f, strokePaint)

        // Side-by-Side Performance Summary Metrics at bottom of unified card
        val halfWidth = contentWidth / 2f

        // Metric 1: Total Students (Blue styling)
        val m1BadgeRect = RectF(marginH + 12f, currentY + 75f, marginH + 34f, currentY + 97f)
        fillPaint.color = colorLightBlueHighlight
        canvas.drawRoundRect(m1BadgeRect, 5.5f, 5.5f, fillPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 9.5f
        textPaint.color = colorPrimaryBlue
        canvas.drawText(
          "#",
          m1BadgeRect.centerX(),
          m1BadgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 12.5f
        textPaint.color = colorTextPrimary
        canvas.drawText(totalStudents.toString(), marginH + 41f, currentY + 86f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8.2f
        textPaint.color = colorTextSecondary
        canvas.drawText("Total Students", marginH + 41f, currentY + 97f, textPaint)

        // Center vertical divider
        strokePaint.color = colorBorderGray
        strokePaint.strokeWidth = 1f
        canvas.drawLine(marginH + halfWidth, currentY + 74f, marginH + halfWidth, currentY + 98f, strokePaint)

        // Metric 2: Graded / Approved (Green styling)
        val m2StartX = marginH + halfWidth + 12f
        val m2BadgeRect = RectF(m2StartX, currentY + 75f, m2StartX + 22f, currentY + 97f)
        fillPaint.color = colorStatusSuccessBg
        canvas.drawRoundRect(m2BadgeRect, 5.5f, 5.5f, fillPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 9.5f
        textPaint.color = colorStatusSuccess
        canvas.drawText(
          "✓",
          m2BadgeRect.centerX(),
          m2BadgeRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 12.5f
        textPaint.color = colorTextPrimary
        canvas.drawText(approvedCount.toString(), m2StartX + 29f, currentY + 86f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 8.2f
        textPaint.color = colorTextSecondary
        canvas.drawText("Graded / Approved", m2StartX + 29f, currentY + 97f, textPaint)

        currentY += mergedCardHeight + 14f

        // Section Heading for Table
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 11.5f
        textPaint.color = colorTextPrimary
        canvas.drawText("Student Results ($totalStudents)", marginH, currentY + 11f, textPaint)

        currentY += 18f
      } else {
        // Continuation Page Header
        val contBannerHeight = 36f
        val contRect = RectF(marginH, currentY, marginH + contentWidth, currentY + contBannerHeight)
        fillPaint.color = colorSoftBlueTint
        canvas.drawRoundRect(contRect, 8f, 8f, fillPaint)
        strokePaint.color = colorTableBorder
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(contRect, 8f, 8f, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 11f
        textPaint.color = colorDarkRoyalBlue
        val contTitle = "${classRoom?.subjectName?.ifBlank { "Class Results" } ?: "Class Results"} — Student Results (Continued)"
        val safeContTitle = TextUtils.ellipsize(contTitle, textPaint, contentWidth - 110f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(
          safeContTitle,
          marginH + 12f,
          contRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 9f
        textPaint.color = colorTextSecondary
        canvas.drawText(
          exportDateText,
          marginH + contentWidth - 12f,
          contRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )

        currentY += contBannerHeight + 12f
      }

      // Draw Table Header
      val tableStartY = currentY
      drawTableHeader(canvas, currentY)
      currentY += tableHeaderHeight

      if (students.isEmpty()) {
        val emptyRowHeight = 44f
        fillPaint.color = colorWhite
        canvas.drawRect(marginH, currentY, marginH + contentWidth, currentY + emptyRowHeight, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = colorTextSecondary
        canvas.drawText(
          "No students enrolled in this class.",
          marginH + (contentWidth / 2f),
          currentY + (emptyRowHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f),
          textPaint,
        )
        currentY += emptyRowHeight
      } else {
        val maxRowsThisPage = if (pageNum == 1) rowsOnFirstPage else rowsPerContinuationPage
        var rowsDrawn = 0
        while (studentIndex < students.size && rowsDrawn < maxRowsThisPage) {
          val student = students[studentIndex]
          val evaluation = evaluationsMap[student.id]
          val isApproved = evaluation != null && evaluation.status.equals("approved", ignoreCase = true)

          val obtained = evaluation?.totalMarksObtained ?: 0.0
          val total = evaluation?.totalMarks ?: defaultTotalMarks
          val obtainedStr = if (obtained % 1.0 == 0.0) obtained.toInt().toString() else String.format(Locale.US, "%.1f", obtained)
          val totalStr = if (total % 1.0 == 0.0) total.toInt().toString() else String.format(Locale.US, "%.1f", total)

          val pct = if (isApproved) {
            evaluation?.percentage ?: if (total > 0.0) (obtained / total) * 100.0 else null
          } else {
            null
          }
          val pctStr = if (pct != null) {
            if (pct % 1.0 == 0.0) "${pct.toInt()}%" else String.format(Locale.US, "%.1f%%", pct)
          } else {
            "—"
          }

          // Alternating row tint
          fillPaint.color = if (studentIndex % 2 == 1) colorRowAltBg else colorWhite
          canvas.drawRect(marginH, currentY, marginH + contentWidth, currentY + rowHeight, fillPaint)

          val rowBaselineY = currentY + (rowHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

          // 1. R/o (Roll Number)
          textPaint.textAlign = Paint.Align.LEFT
          textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          textPaint.textSize = 8.8f
          textPaint.color = colorPrimaryBlue
          val safeRoll = TextUtils.ellipsize(
            student.rollNumber.trim().ifBlank { "—" },
            textPaint,
            colRollWidth - 12f,
            TextUtils.TruncateAt.END,
          ).toString()
          canvas.drawText(safeRoll, colRollX + 8f, rowBaselineY, textPaint)

          // 2. Student Name
          textPaint.textAlign = Paint.Align.LEFT
          textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          textPaint.textSize = 9.2f
          textPaint.color = colorTextPrimary
          val safeName = TextUtils.ellipsize(
            student.name.trim().ifBlank { "—" },
            textPaint,
            colStudentWidth - 10f,
            TextUtils.TruncateAt.END,
          ).toString()
          canvas.drawText(safeName, colStudentX + 6f, rowBaselineY, textPaint)

          // 3. Father's Name
          textPaint.textAlign = Paint.Align.LEFT
          textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
          textPaint.textSize = 9f
          textPaint.color = colorTextSecondary
          val safeFatherName = TextUtils.ellipsize(
            student.fatherName.trim().ifBlank { "—" },
            textPaint,
            colFatherWidth - 10f,
            TextUtils.TruncateAt.END,
          ).toString()
          canvas.drawText(safeFatherName, colFatherX + 6f, rowBaselineY, textPaint)

          // 4. Marks Obtained / Total Marks
          textPaint.textAlign = Paint.Align.CENTER
          textPaint.typeface = Typeface.create(
            Typeface.SANS_SERIF,
            if (isApproved) Typeface.BOLD else Typeface.NORMAL,
          )
          textPaint.textSize = 9.2f
          textPaint.color = if (isApproved) colorTextPrimary else colorTextSecondary
          val marksStr = if (isApproved) "$obtainedStr/$totalStr" else "—"
          canvas.drawText(marksStr, colMarksX + (colMarksWidth / 2f), rowBaselineY, textPaint)

          // 5. Percentage
          textPaint.textAlign = Paint.Align.CENTER
          textPaint.typeface = Typeface.create(
            Typeface.SANS_SERIF,
            if (isApproved) Typeface.BOLD else Typeface.NORMAL,
          )
          textPaint.textSize = 9.2f
          textPaint.color = if (isApproved) colorStatusSuccess else colorTextSecondary
          canvas.drawText(pctStr, colPctX + (colPctWidth / 2f), rowBaselineY, textPaint)

          // 6. Result Status badge
          val statusLabel = if (isApproved) "Approved" else "Pending"
          val badgeW = 54f
          val badgeH = 15f
          val badgeCenterX = colStatusX + (colStatusWidth / 2f)
          val badgeCenterY = currentY + (rowHeight / 2f)
          val statusRect = RectF(
            badgeCenterX - (badgeW / 2f),
            badgeCenterY - (badgeH / 2f),
            badgeCenterX + (badgeW / 2f),
            badgeCenterY + (badgeH / 2f),
          )
          fillPaint.color = if (isApproved) colorStatusSuccessBg else colorPendingBg
          canvas.drawRoundRect(statusRect, 4.5f, 4.5f, fillPaint)
          strokePaint.color = if (isApproved) colorStatusSuccessBorder else colorTableBorder
          strokePaint.strokeWidth = 0.6f
          canvas.drawRoundRect(statusRect, 4.5f, 4.5f, strokePaint)

          textPaint.textAlign = Paint.Align.CENTER
          textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          textPaint.textSize = 7.8f
          textPaint.color = if (isApproved) colorStatusSuccess else colorPendingText
          canvas.drawText(
            statusLabel,
            badgeCenterX,
            badgeCenterY - ((textPaint.descent() + textPaint.ascent()) / 2f),
            textPaint,
          )

          currentY += rowHeight
          studentIndex++
          rowsDrawn++

          // Row divider
          if (rowsDrawn < maxRowsThisPage && studentIndex < students.size) {
            strokePaint.color = colorBorderGray
            strokePaint.strokeWidth = 0.7f
            canvas.drawLine(marginH, currentY, marginH + contentWidth, currentY, strokePaint)
          }
        }
      }

      // Outer border around the table on this page
      val tableOutlineRect = RectF(marginH, tableStartY, marginH + contentWidth, currentY)
      strokePaint.color = colorTableBorder
      strokePaint.strokeWidth = 1f
      canvas.drawRoundRect(tableOutlineRect, 8f, 8f, strokePaint)

      // Page Footer
      drawPageFooter(canvas, pageNum)

      pdfDocument.finishPage(page)
    }

    val outputStream = ByteArrayOutputStream()
    pdfDocument.writeTo(outputStream)
    return outputStream.toByteArray()
  } finally {
    pdfDocument.close()
  }
}




