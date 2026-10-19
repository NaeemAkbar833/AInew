package com.example.ui.paper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ClassRoom
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.StudentPaperPage
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.components.GradeScanConfirmationDialog
import com.example.ui.components.GradeScanEmptyState
import com.example.ui.components.GradeScanPrimaryButton
import com.example.ui.components.SkeletonBox
import com.example.ui.paper.components.TeacherReviewDialog
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Student Detail & Answer Sheets Screen.
 *
 * Displays student metadata, lists all uploaded multi-page answer sheets (`student_papers` + `student_paper_pages`),
 * lets teachers trigger/view per-page and combined OCR extraction, run AI evaluation,
 * inspect/override marks via [TeacherReviewDialog], or launch [ScanPaperScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
  studentId: String,
  viewModel: StudentDetailViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToScan: (studentId: String, classId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var paperToDelete by remember { mutableStateOf<StudentPaper?>(null) }
  var previewPage by remember { mutableStateOf<Pair<StudentPaperPage, String?>?>(null) }
  var selectedPageByPaperId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

  LaunchedEffect(studentId) {
    viewModel.loadStudentAndPapers(studentId)
  }

  LaunchedEffect(uiState.feedbackMessage) {
    uiState.feedbackMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissFeedbackMessage()
    }
  }

  LaunchedEffect(uiState.deletePaperError) {
    uiState.deletePaperError?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissDeletePaperError()
    }
  }

  Scaffold(
    topBar = {
      com.example.ui.components.GradeScanStitchHeader(
        title = uiState.student?.name ?: "Student Details",
        subtitle = "Answer Sheets & Evaluation",
        badgeText = "PAPER EVALUATION",
        onBackClick = onNavigateBack,
        backTestTag = "student_detail_back_button",
        actions = {
          IconButton(
            onClick = { viewModel.loadStudentAndPapers(studentId) },
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.18f))
              .testTag("student_detail_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        },
      )
    },
    floatingActionButton = {
      val student = uiState.student
      if (!uiState.isLoading && student != null) {
        ExtendedFloatingActionButton(
          onClick = { onNavigateToScan(student.id ?: studentId, student.classId) },
          icon = {
            Icon(
              imageVector = Icons.Default.AddAPhoto,
              contentDescription = null,
            )
          },
          text = {
            Text(
              text = "Scan Answer Sheet",
              fontWeight = FontWeight.Bold,
            )
          },
          containerColor = RoyalBlue,
          contentColor = Color.White,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.testTag("scan_new_paper_fab"),
        )
      }
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = com.example.ui.theme.BackgroundLight,
    modifier = modifier.testTag("student_detail_screen"),
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(com.example.ui.theme.BackgroundLight)
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      when {
        uiState.isLoading -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 600.dp)
              .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            SkeletonBox(
              modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
              shape = RoundedCornerShape(16.dp),
            )
            repeat(3) {
              ExamCardSkeleton()
            }
          }
        }

        uiState.student == null -> {
          GradeScanEmptyState(
            icon = Icons.Default.Description,
            title = "Student Not Found",
            message = uiState.errorMessage ?: "Unable to load this student's profile. Please go back and try again.",
            actionText = "Retry",
            onActionClick = { viewModel.loadStudentAndPapers(studentId) },
          )
        }

        else -> {
          val student = uiState.student!!
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 600.dp)
              .testTag("student_papers_list"),
            contentPadding = PaddingValues(
              start = 20.dp,
              end = 20.dp,
              top = 16.dp,
              bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            // 1. Student Header Card
            item {
              StudentInfoHeaderCard(
                student = student,
                classRoom = uiState.classRoom,
              )
            }

            // 2. Section Header for Answer Sheets
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column {
                  Text(
                    text = "Scanned Answer Sheets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Text(
                    text = "Upload pages, extract OCR text, and run AI grading",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                }

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = LightBlueHighlight,
                  border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
                ) {
                  Text(
                    text = "${uiState.papers.size} ${if (uiState.papers.size == 1) "Sheet" else "Sheets"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue,
                    modifier = Modifier
                      .padding(horizontal = 12.dp, vertical = 5.dp)
                      .testTag("papers_count_badge"),
                  )
                }
              }
            }

            // 3. Empty State or List of Answer Sheets
            if (uiState.papers.isEmpty()) {
              item {
                EmptyPapersState(
                  studentName = student.name,
                  onScanClick = {
                    onNavigateToScan(student.id ?: studentId, student.classId)
                  },
                )
              }
            } else {
              itemsIndexed(
                items = uiState.papers,
                key = { index, item -> item.id ?: "paper_$index" },
              ) { index, paper ->
                val paperId = paper.id ?: ""
                val sheetNumber = uiState.papers.size - index
                val selectedPageId = selectedPageByPaperId[paperId]
                  ?: paper.pages.firstOrNull { page ->
                    val pid = page.id ?: ""
                    uiState.ocrTextByPageId.containsKey(pid) ||
                      uiState.ocrErrorByPageId.containsKey(pid) ||
                      uiState.ocrLoadingPageIds.contains(pid)
                  }?.id

                // Ensure signed URLs are loaded for all pages in this paper
                LaunchedEffect(paper.pages) {
                  paper.pages.forEach { p ->
                    viewModel.loadSignedUrlForPage(p.storagePath)
                  }
                }

                PaperCard(
                  sheetNumber = sheetNumber,
                  paper = paper,
                  signedUrls = uiState.signedUrls,
                  selectedPageId = selectedPageId,
                  ocrTextByPageId = uiState.ocrTextByPageId,
                  ocrLoadingPageIds = uiState.ocrLoadingPageIds,
                  ocrErrorByPageId = uiState.ocrErrorByPageId,
                  isEvaluating = uiState.isEvaluatingPaperId == paperId && paperId.isNotBlank(),
                  onSelectPage = { page ->
                    val pid = page.id ?: return@PaperCard
                    selectedPageByPaperId = selectedPageByPaperId.toMutableMap().apply {
                      if (this[paperId] == pid) {
                        remove(paperId)
                      } else {
                        put(paperId, pid)
                      }
                    }
                  },
                  onPreviewPage = { page, signedUrl ->
                    previewPage = page to signedUrl
                  },
                  onRunPageOcr = { page ->
                    val pid = page.id ?: return@PaperCard
                    selectedPageByPaperId = selectedPageByPaperId.toMutableMap().apply {
                      put(paperId, pid)
                    }
                    viewModel.runOcrForPage(paperId, pid)
                  },
                  onClearPageOcrError = { pageId ->
                    viewModel.clearOcrError(pageId)
                  },
                  onRunAiEvaluation = {
                    if (paperId.isNotBlank()) {
                      viewModel.runAiEvaluation(paperId)
                    }
                  },
                  onStopAiEvaluation = {
                    if (paperId.isNotBlank()) {
                      viewModel.stopAiEvaluation(paperId)
                    }
                  },
                  onDeleteClick = {
                    paperToDelete = paper
                  },
                )
              }
            }
          }
        }
      }
    }
  }

  // Delete Confirmation Dialog
  paperToDelete?.let { paper ->
    GradeScanConfirmationDialog(
      title = "Delete Answer Sheet?",
      message = "This will permanently remove this scanned answer sheet and all its pages from cloud storage. This action cannot be undone.",
      confirmText = if (uiState.isDeletingPaper) "Deleting..." else "Delete Sheet",
      dismissText = "Cancel",
      isDestructive = true,
      isLoading = uiState.isDeletingPaper,
      onConfirm = {
        viewModel.deletePaper(paper) {
          paperToDelete = null
        }
      },
      onDismiss = {
        if (!uiState.isDeletingPaper) {
          paperToDelete = null
        }
      },
    )
  }

  // Evaluation Error Alert Dialog
  if (!uiState.evaluationError.isNullOrBlank() && uiState.evaluationResult == null) {
    EvaluationErrorDialog(
      errorMessage = uiState.evaluationError!!,
      onRetry = {
        val targetPaperId = uiState.evaluatedPaperId
        viewModel.dismissEvaluationDialog()
        if (!targetPaperId.isNullOrBlank()) {
          viewModel.runAiEvaluation(targetPaperId)
        }
      },
      onDismiss = { viewModel.dismissEvaluationDialog() },
    )
  }

  // Fullscreen Page Preview Dialog
  previewPage?.let { (page, signedUrl) ->
    val pageId = page.id.orEmpty()
    FullscreenPagePreviewDialog(
      page = page,
      imageUrl = signedUrl,
      ocrText = uiState.ocrTextByPageId[pageId],
      isOcrRunning = uiState.ocrLoadingPageIds.contains(pageId),
      onRunOcr = {
        val pId = page.paperId
        if (pId.isNotBlank() && pageId.isNotBlank()) {
          viewModel.runOcrForPage(pId, pageId)
        }
        previewPage = null
      },
      onDismiss = { previewPage = null },
    )
  }

  // Teacher Review & Approval Bottom Sheet
  uiState.evaluationResult?.let { evalResponse ->
    TeacherReviewDialog(
      evaluationResponse = evalResponse,
      editedQuestions = uiState.editedQuestions,
      studentName = uiState.student?.name.orEmpty(),
      subjectName = uiState.classRoom?.subjectName.orEmpty(),
      isApproving = uiState.isApproving,
      approvalError = uiState.approvalError,
      isSavingCorrections = uiState.isSavingCorrections,
      saveCorrectionsSuccess = uiState.saveCorrectionsSuccess,
      saveCorrectionsError = uiState.saveCorrectionsError,
      onUpdateQuestion = { qId, qNum, marks, feedback ->
        viewModel.onUpdateQuestion(qId, qNum, marks, feedback)
      },
      onSaveCorrections = { evalId, pId ->
        viewModel.saveTeacherCorrections(evalId, pId)
      },
      onApprove = { evalId, pId ->
        viewModel.approveEvaluation(evalId, pId)
      },
      onDismiss = { viewModel.dismissEvaluationDialog() },
    )
  }
}

@Composable
private fun StudentInfoHeaderCard(
  student: Student,
  classRoom: ClassRoom?,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("student_info_header_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Top Royal Blue accent bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(5.dp)
          .background(RoyalBlue)
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(LightBlueHighlight)
              .border(1.dp, RoyalBlue.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
          ) {
            val initial = student.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
            Text(
              text = initial,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = RoyalBlue,
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = student.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = LightBlueHighlight,
                border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
              ) {
                Text(
                  text = "Roll #${student.rollNumber}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = RoyalBlue,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }

            if (student.fatherName.isNotBlank()) {
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = "Father's Name: ${student.fatherName}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
              )
            }
          }
        }

        if (classRoom != null) {
          HorizontalDivider(color = BorderGray)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            MetadataPill(
              icon = Icons.Default.Class,
              label = "${classRoom.semester} (${classRoom.timing})",
              modifier = Modifier.weight(1f),
            )
            MetadataPill(
              icon = Icons.AutoMirrored.Filled.MenuBook,
              label = classRoom.subjectName,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetadataPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = LightBlueHighlight,
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = RoyalBlue,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun EmptyPapersState(
  studentName: String,
  onScanClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("empty_papers_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(68.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(LightBlueHighlight),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.DocumentScanner,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(32.dp),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "No Answer Sheets Scanned",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Capture or upload $studentName's handwritten answer sheet pages to extract OCR text and run AI evaluation.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      GradeScanPrimaryButton(
        text = "Scan First Answer Sheet",
        onClick = onScanClick,
        icon = Icons.Default.AddAPhoto,
        testTag = "empty_scan_paper_button",
      )
    }
  }
}

@Composable
private fun PaperCard(
  sheetNumber: Int,
  paper: StudentPaper,
  signedUrls: Map<String, String>,
  selectedPageId: String?,
  ocrTextByPageId: Map<String, String>,
  ocrLoadingPageIds: Set<String>,
  ocrErrorByPageId: Map<String, String>,
  isEvaluating: Boolean,
  onSelectPage: (StudentPaperPage) -> Unit,
  onPreviewPage: (StudentPaperPage, String?) -> Unit,
  onRunPageOcr: (StudentPaperPage) -> Unit,
  onClearPageOcrError: (String) -> Unit,
  onRunAiEvaluation: () -> Unit,
  onStopAiEvaluation: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  val paperId = paper.id ?: ""
  val pages = paper.pages
  val selectedPage = pages.find { it.id == selectedPageId }
  val evalStatus = paper.evaluation?.status ?: paper.status
  val isApproved = evalStatus.equals("approved", ignoreCase = true)
  val isNeedsReview = evalStatus.equals("needs_review", ignoreCase = true)
  val isCompletedEval = evalStatus.equals("completed", ignoreCase = true) ||
    evalStatus.equals("evaluated", ignoreCase = true)

  var showCombinedOcr by remember(paperId) { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("paper_card_$paperId"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(LightBlueHighlight),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Description,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(20.dp),
            )
          }

          Column {
            Text(
              text = "Answer Sheet #$sheetNumber",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            val pageCount = if (pages.isNotEmpty()) pages.size else paper.totalPages
            Text(
              text = "${formatTimestamp(paper.createdAt)} • $pageCount ${if (pageCount == 1) "Page" else "Pages"}",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          PaperStatusBadge(
            paperStatus = paper.status,
            evalStatus = paper.evaluation?.status,
            ocrStatus = paper.ocrStatus,
          )

          IconButton(
            onClick = onDeleteClick,
            enabled = !isEvaluating,
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(StatusWarningContainer)
              .testTag("delete_paper_$paperId"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete Answer Sheet",
              tint = StatusWarning,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // 2. Page Thumbnails Row
      if (pages.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
        ) {
          items(
            items = pages,
            key = { it.id ?: "page_${it.pageNumber}" },
          ) { page ->
            val pid = page.id.orEmpty()
            val signedUrl = signedUrls[page.storagePath]
            val isPageLoading = ocrLoadingPageIds.contains(pid)
            val hasPageText = !ocrTextByPageId[pid].isNullOrBlank()
            val hasPageError = !ocrErrorByPageId[pid].isNullOrBlank()
            val isSelected = selectedPage?.id == page.id

            Card(
              modifier = Modifier
                .width(116.dp)
                .aspectRatio(0.75f)
                .clickable { onSelectPage(page) }
                .testTag("paper_page_${paperId}_${page.pageNumber}"),
              shape = RoundedCornerShape(12.dp),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                  isSelected -> RoyalBlue
                  hasPageError -> StatusWarning.copy(alpha = 0.6f)
                  hasPageText -> StatusSuccess.copy(alpha = 0.6f)
                  else -> BorderGray
                },
              ),
            ) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(LightBlueHighlight),
              ) {
                if (!signedUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = signedUrl,
                    contentDescription = "Page ${page.pageNumber}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Description,
                      contentDescription = null,
                      tint = RoyalBlue.copy(alpha = 0.45f),
                      modifier = Modifier.size(28.dp),
                    )
                  }
                }

                // Top-left Page Number Pill
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (isSelected) RoyalBlue else Color.Black.copy(alpha = 0.68f),
                  modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                ) {
                  Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }

                // Top-right Fullscreen Preview Icon
                IconButton(
                  onClick = { onPreviewPage(page, signedUrl) },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                  Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Preview Page ${page.pageNumber}",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                  )
                }

                // Bottom-left OCR status / trigger pill
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = when {
                    isPageLoading -> StatusAmber
                    hasPageError -> StatusWarning
                    hasPageText -> StatusSuccess
                    else -> RoyalBlue
                  },
                  modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clickable(enabled = !isPageLoading) {
                      if (hasPageText || hasPageError) {
                        onSelectPage(page)
                      } else {
                        onRunPageOcr(page)
                      }
                    },
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    if (isPageLoading) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(9.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White,
                      )
                    }
                    Text(
                      text = when {
                        isPageLoading -> "OCR..."
                        hasPageError -> "Retry OCR"
                        hasPageText -> "OCR ✓"
                        else -> "Run OCR"
                      },
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = Color.White,
                      fontSize = 9.sp,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 3. Selected Page OCR Result & Retry Panel
      AnimatedVisibility(visible = selectedPage != null) {
        selectedPage?.let { page ->
          val pid = page.id.orEmpty()
          SelectedPageOcrPanel(
            page = page,
            ocrText = ocrTextByPageId[pid],
            ocrError = ocrErrorByPageId[pid],
            isRunning = ocrLoadingPageIds.contains(pid),
            onRunOrRetryOcr = { onRunPageOcr(page) },
            onClose = {
              onClearPageOcrError(pid)
              onSelectPage(page)
            },
          )
        }
      }

      // 4. Combined OCR Text Banner (if available on paper record)
      if (!paper.combinedOcrText.isNullOrBlank()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showCombinedOcr = !showCombinedOcr },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.TextSnippet,
                  contentDescription = null,
                  tint = RoyalBlue,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Combined Answer Sheet Text (OCR)",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
              }
              Text(
                text = if (showCombinedOcr) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = RoyalBlue,
              )
            }

            AnimatedVisibility(visible = showCombinedOcr) {
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 10.dp)
                  .heightIn(max = 180.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderGray),
              ) {
                Text(
                  text = paper.combinedOcrText,
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                  ),
                  color = TextPrimary,
                  modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                )
              }
            }
          }
        }
      }

      // 5. AI Evaluation & Teacher Review Section
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("paper_evaluation_section_$paperId"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(
          width = 1.dp,
          color = when {
            isEvaluating -> StatusAmber.copy(alpha = 0.45f)
            isApproved -> StatusSuccess.copy(alpha = 0.4f)
            isNeedsReview -> StatusAmber.copy(alpha = 0.45f)
            else -> BorderGray
          },
        ),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    when {
                      isApproved -> StatusSuccessContainer
                      isNeedsReview -> TertiaryAmberContainer
                      else -> LightBlueHighlight
                    }
                  ),
                contentAlignment = Alignment.Center,
              ) {
                if (isEvaluating) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RoyalBlue,
                  )
                } else {
                  Icon(
                    imageVector = when {
                      isApproved -> Icons.Default.Verified
                      isNeedsReview -> Icons.Default.WarningAmber
                      else -> Icons.Default.AutoAwesome
                    },
                    contentDescription = null,
                    tint = when {
                      isApproved -> StatusSuccess
                      isNeedsReview -> StatusAmber
                      else -> RoyalBlue
                    },
                    modifier = Modifier.size(20.dp),
                  )
                }
              }

              Column {
                Text(
                  text = when {
                    isEvaluating -> "AI Evaluation in Progress..."
                    isApproved -> "Teacher Approved & Finalized"
                    isNeedsReview -> "AI Evaluated • Teacher Review Needed"
                    isCompletedEval -> "AI Evaluation Ready"
                    else -> "AI Evaluation & Grading"
                  },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Text(
                  text = when {
                    isEvaluating -> "Extracting answers & grading against class question paper"
                    isApproved -> "Tap below to view the finalized question breakdown"
                    isNeedsReview -> "Tap below to inspect answers, adjust marks, and approve"
                    isCompletedEval -> "Tap below to review marks and finalize result"
                    else -> "Run AI grading to evaluate handwritten answers automatically"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                  fontSize = 11.sp,
                )
              }
            }
          }

          if (isEvaluating) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = onStopAiEvaluation,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = StatusWarningContainer,
                  contentColor = StatusWarning,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(44.dp)
                  .testTag("stop_evaluate_$paperId"),
              ) {
                Icon(
                  imageVector = Icons.Default.StopCircle,
                  contentDescription = null,
                  tint = StatusWarning,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Stop Evaluation",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = StatusWarning,
                )
              }
            }
          } else {
            val ctaLabel = when {
              isApproved -> "View Approved Result"
              isNeedsReview -> "Review & Approve Evaluation"
              isCompletedEval -> "View Evaluation & Approve"
              else -> "Evaluate Answer Sheet"
            }

            Button(
              onClick = onRunAiEvaluation,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = RoyalBlue,
                contentColor = Color.White,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("evaluate_paper_$paperId"),
            ) {
              Icon(
                imageVector = if (isApproved) Icons.Default.Verified else Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = ctaLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SelectedPageOcrPanel(
  page: StudentPaperPage,
  ocrText: String?,
  ocrError: String?,
  isRunning: Boolean,
  onRunOrRetryOcr: () -> Unit,
  onClose: () -> Unit,
) {
  val pageId = page.id.orEmpty()
  val hasText = !ocrText.isNullOrBlank()
  val isFailed = !ocrError.isNullOrBlank()

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("ocr_panel_page_${page.pageNumber}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(
      width = 1.dp,
      color = when {
        isFailed -> StatusWarning.copy(alpha = 0.45f)
        hasText -> StatusSuccess.copy(alpha = 0.4f)
        else -> BorderGray
      },
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Header strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(LightBlueHighlight)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.TextSnippet,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Page ${page.pageNumber} — Extracted Text",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!isRunning) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = RoyalBlue,
              modifier = Modifier
                .clickable(onClick = onRunOrRetryOcr)
                .testTag("run_page_ocr_$pageId"),
            ) {
              Text(
                text = when {
                  isFailed -> "Retry Page OCR"
                  hasText -> "Re-extract"
                  else -> "Extract Page"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier
              .clickable(onClick = onClose)
              .testTag("close_ocr_panel_$pageId"),
          ) {
            Text(
              text = "Close",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = TextSecondary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      HorizontalDivider(color = BorderGray)

      // Body content
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
      ) {
        when {
          isRunning -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = RoyalBlue,
              )
              Column {
                Text(
                  text = "Running OCR on Page ${page.pageNumber}...",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary,
                )
                Text(
                  text = "Transcribing student handwriting...",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
            }
          }

          isFailed -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = StatusWarning,
                modifier = Modifier.size(20.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "OCR Extraction Failed",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = StatusWarning,
                )
                Text(
                  text = ocrError ?: "Could not extract text from this page. Tap 'Retry Page OCR' above.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
            }
          }

          hasText -> {
            val scrollState = rememberScrollState()
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 220.dp)
                .testTag("ocr_text_box_$pageId"),
              shape = RoundedCornerShape(8.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Text(
                text = ocrText.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  lineHeight = 18.sp,
                ),
                color = TextPrimary,
                modifier = Modifier
                  .fillMaxWidth()
                  .verticalScroll(scrollState)
                  .padding(10.dp),
              )
            }
          }

          else -> {
            Text(
              text = "OCR has not been run on Page ${page.pageNumber} yet. Tap 'Extract Page' above to convert handwriting into text.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EvaluationErrorDialog(
  errorMessage: String,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 380.dp),
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      border = BorderStroke(1.dp, BorderGray),
      tonalElevation = 0.dp,
      shadowElevation = 10.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(StatusWarningContainer)
            .border(
              width = 1.dp,
              color = StatusWarning.copy(alpha = 0.3f),
              shape = RoundedCornerShape(14.dp),
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = StatusWarning,
            modifier = Modifier.size(22.dp),
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "AI Evaluation Issue",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = errorMessage,
          fontSize = 13.sp,
          color = TextSecondary,
          textAlign = TextAlign.Center,
          lineHeight = 19.sp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderGray),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = TextPrimary,
            ),
            modifier = Modifier
              .weight(1f)
              .height(46.dp),
          ) {
            Text(
              text = "Close",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.Transparent,
              contentColor = Color.White,
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
              .weight(1.2f)
              .height(46.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(RoyalBlue, DarkRoyalBlue)
                )
              ),
          ) {
            Text(
              text = "Retry",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PaperStatusBadge(
  paperStatus: String,
  evalStatus: String?,
  ocrStatus: String,
) {
  val effectiveStatus = (evalStatus ?: paperStatus).lowercase(Locale.ROOT)
  val (label, containerColor, contentColor, icon) = when {
    effectiveStatus == "approved" -> StatusBadgeStyle(
      label = "Approved",
      containerColor = StatusSuccessContainer,
      contentColor = StatusSuccess,
      icon = Icons.Default.Verified,
    )
    effectiveStatus == "needs_review" -> StatusBadgeStyle(
      label = "Needs Review",
      containerColor = TertiaryAmberContainer,
      contentColor = StatusAmber,
      icon = Icons.Default.WarningAmber,
    )
    effectiveStatus == "completed" || effectiveStatus == "evaluated" -> StatusBadgeStyle(
      label = "Evaluated",
      containerColor = LightBlueHighlight,
      contentColor = RoyalBlue,
      icon = Icons.Default.CheckCircle,
    )
    effectiveStatus == "failed" || ocrStatus.equals("failed", ignoreCase = true) -> StatusBadgeStyle(
      label = "Needs Retry",
      containerColor = StatusWarningContainer,
      contentColor = StatusWarning,
      icon = Icons.Default.ErrorOutline,
    )
    effectiveStatus == "processing" || ocrStatus.equals("processing", ignoreCase = true) -> StatusBadgeStyle(
      label = "Processing",
      containerColor = TertiaryAmberContainer,
      contentColor = StatusAmber,
      icon = Icons.Default.HourglassEmpty,
    )
    ocrStatus.equals("completed", ignoreCase = true) -> StatusBadgeStyle(
      label = "OCR Ready",
      containerColor = LightBlueHighlight,
      contentColor = RoyalBlue,
      icon = Icons.Default.CheckCircle,
    )
    else -> StatusBadgeStyle(
      label = "Scanned",
      containerColor = LightBlueHighlight,
      contentColor = RoyalBlue,
      icon = Icons.Default.CheckCircle,
    )
  }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = containerColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}

private data class StatusBadgeStyle(
  val label: String,
  val containerColor: Color,
  val contentColor: Color,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun FullscreenPagePreviewDialog(
  page: StudentPaperPage,
  imageUrl: String?,
  ocrText: String?,
  isOcrRunning: Boolean,
  onRunOcr: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, BorderGray),
      elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LightBlueHighlight),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            }
            Column {
              Text(
                text = "Page ${page.pageNumber} Preview",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Text(
                text = if (!ocrText.isNullOrBlank()) "OCR Extracted" else "Handwritten Page",
                fontSize = 12.sp,
                color = TextSecondary,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (!isOcrRunning) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = LightBlueHighlight,
                border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
                modifier = Modifier.clickable(onClick = onRunOcr),
              ) {
                Text(
                  text = if (!ocrText.isNullOrBlank()) "Re-run OCR" else "Extract OCR",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = RoyalBlue,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(LightBlueHighlight)
                .border(1.dp, BorderGray, CircleShape),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Preview",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(14.dp))
            .background(LightBlueHighlight)
            .border(1.dp, BorderGray, RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
        ) {
          if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
              model = imageUrl,
              contentDescription = "Page ${page.pageNumber} Full Image",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Text(
              text = "Loading page image...",
              fontSize = 13.sp,
              color = TextSecondary,
            )
          }
        }

        if (!ocrText.isNullOrBlank()) {
          Text(
            text = "Extracted Text:",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
          )
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 120.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderGray),
          ) {
            Text(
              text = ocrText,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
              ),
              color = TextPrimary,
              modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            )
          }
        }
      }
    }
  }
}

private fun formatTimestamp(isoString: String?): String {
  if (isoString.isNullOrBlank()) return "Just now"
  return try {
    val instant = Instant.parse(isoString)
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.US)
      .withZone(ZoneId.systemDefault())
    formatter.format(instant)
  } catch (e: Exception) {
    isoString.take(10)
  }
}
