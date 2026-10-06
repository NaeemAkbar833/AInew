package com.example.ui.paper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.StudentPaper
import com.example.data.model.StudentPaperPage
import com.example.ui.paper.components.TeacherReviewDialog
import com.example.ui.theme.TertiaryAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
  studentId: String,
  viewModel: StudentDetailViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToScan: (studentId: String, classId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollState = rememberScrollState()
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  var paperToDelete by remember { mutableStateOf<StudentPaper?>(null) }
  var activePreviewPage by remember { mutableStateOf<StudentPaperPage?>(null) }

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
    uiState.deletePaperError?.let { err ->
      snackbarHostState.showSnackbar(err)
      viewModel.dismissDeletePaperError()
    }
  }

  // Delete Paper Confirmation Dialog
  if (paperToDelete != null) {
    AlertDialog(
      onDismissRequest = {
        if (!uiState.isDeletingPaper) paperToDelete = null
      },
      title = { Text("Delete this paper submission?") },
      text = {
        Text("This will permanently remove this paper submission and all its ${paperToDelete?.totalPages ?: 0} scanned pages from storage. This cannot be undone.")
      },
      confirmButton = {
        Button(
          onClick = {
            paperToDelete?.let { paper ->
              viewModel.deletePaper(paper) {
                paperToDelete = null
              }
            }
          },
          enabled = !uiState.isDeletingPaper,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_paper_button"),
        ) {
          if (uiState.isDeletingPaper) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onError,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Text("Delete")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { paperToDelete = null },
          enabled = !uiState.isDeletingPaper,
          modifier = Modifier.testTag("cancel_delete_paper_button"),
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag("delete_paper_dialog"),
    )
  }

  // Teacher Review UI Dialog (when evaluation result is available)
  if (uiState.evaluationResult != null || (uiState.evaluationResultJson != null && uiState.evaluationError == null)) {
    val studentName = uiState.student?.name ?: "Student"
    val className = uiState.classRoom?.subjectName ?: "Class"

    TeacherReviewDialog(
      evaluationResponse = uiState.evaluationResult,
      rawJson = uiState.evaluationResultJson,
      studentName = studentName,
      className = className,
      onDismiss = { viewModel.dismissEvaluationDialog() },
      onCopyJson = {
        clipboardManager.setText(AnnotatedString(uiState.evaluationResultJson.orEmpty()))
        coroutineScope.launch {
          snackbarHostState.showSnackbar("Evaluation JSON copied to clipboard")
        }
      },
    )
  }

  // Evaluation Error Dialog (when evaluation fails)
  if (uiState.evaluationError != null && uiState.evaluationResult == null) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissEvaluationDialog() },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
          Text(
            text = "AI Evaluation Failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      text = {
        Column {
          Text(
            text = uiState.evaluationError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val pid = uiState.evaluatedPaperId
            viewModel.dismissEvaluationDialog()
            if (!pid.isNullOrBlank()) {
              viewModel.runAiEvaluation(pid)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.testTag("retry_evaluation_button"),
        ) {
          Text("Retry")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.dismissEvaluationDialog() },
          modifier = Modifier.testTag("close_evaluation_dialog_button"),
        ) {
          Text("Close")
        }
      },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag("evaluation_error_dialog"),
    )
  }

  // Page Inspection & Handwriting OCR Dialog
  if (activePreviewPage != null) {
    val page = activePreviewPage!!
    val signedUrl = uiState.signedUrls[page.storagePath]
    val pageId = page.id ?: ""
    val isOcrLoading = uiState.ocrLoadingPageIds.contains(pageId)
    val ocrText = uiState.ocrTextByPageId[pageId]
    val ocrError = uiState.ocrErrorByPageId[pageId]

    Dialog(
      onDismissRequest = {
        if (!isOcrLoading) activePreviewPage = null
      },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
          .fillMaxWidth(0.95f)
          .fillMaxHeight(0.92f)
          .testTag("paper_page_ocr_dialog"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        ) {
          // Dialog Header
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column {
              Text(
                text = "Page ${page.pageNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Handwritten Student Answer Sheet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = { activePreviewPage = null },
              enabled = !isOcrLoading,
              modifier = Modifier.testTag("close_ocr_dialog_button"),
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close preview")
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Scrollable Body: Image + OCR
          Column(
            modifier = Modifier
              .weight(1f)
              .verticalScroll(rememberScrollState()),
          ) {
            // High-fidelity Scanned Image Preview
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.05f)),
              contentAlignment = Alignment.Center,
            ) {
              if (signedUrl != null) {
                AsyncImage(
                  model = signedUrl,
                  contentDescription = "Page ${page.pageNumber}",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview_page_image"),
                )
              } else {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(32.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OCR Action & Result Card
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ocr_section_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.TextFields,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp),
                    )
                    Text(
                      text = "Handwriting OCR",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                    )
                  }

                  if (ocrText != null && !isOcrLoading) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Default.CheckCircle,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(12.dp),
                        )
                        Text(
                          text = "Transcribed",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                  // 1. OCR In Progress
                  isOcrLoading -> {
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("ocr_loading_state"),
                    ) {
                      CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp),
                      )
                      Spacer(modifier = Modifier.height(10.dp))
                      Text(
                        text = "Transcribing handwriting with Gemini...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = "Fetching private scan via secure Edge Function",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }

                  // 2. OCR Succeeded
                  ocrText != null -> {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_completed_state"),
                    ) {
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(vertical = 4.dp),
                      ) {
                        SelectionContainer {
                          Text(
                            text = ocrText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                              .padding(12.dp)
                              .testTag("ocr_result_text"),
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(12.dp))

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Button(
                          onClick = {
                            clipboardManager.setText(AnnotatedString(ocrText))
                            coroutineScope.launch {
                              snackbarHostState.showSnackbar("Transcribed text copied to clipboard")
                            }
                          },
                          shape = RoundedCornerShape(10.dp),
                          colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                          ),
                          modifier = Modifier
                            .weight(1f)
                            .testTag("copy_ocr_text_button"),
                        ) {
                          Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Copy Text")
                        }

                        OutlinedButton(
                          onClick = {
                            viewModel.runOcrForPage(page.paperId, pageId)
                          },
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.testTag("rerun_ocr_button"),
                        ) {
                          Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("Re-run")
                        }
                      }
                    }
                  }

                  // 3. OCR Failed
                  ocrError != null -> {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_error_state"),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Default.ErrorOutline,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.error,
                          modifier = Modifier.size(20.dp),
                        )
                        Text(
                          text = "OCR Failed",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.error,
                        )
                      }
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = ocrError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Spacer(modifier = Modifier.height(12.dp))
                      Button(
                        onClick = {
                          viewModel.runOcrForPage(page.paperId, pageId)
                        },
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("retry_ocr_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Default.Refresh,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry OCR")
                      }
                    }
                  }

                  // 4. Initial State: Run OCR
                  else -> {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_initial_state"),
                    ) {
                      Text(
                        text = "Transcribe handwritten student answers from this page into clean, editable text using the process-paper-ocr service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Spacer(modifier = Modifier.height(12.dp))
                      Button(
                        onClick = {
                          viewModel.runOcrForPage(page.paperId, pageId)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("run_ocr_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Default.TextFields,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run OCR")
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
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = uiState.student?.name ?: "Student Profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("student_detail_back_button"),
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
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("student_detail_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      if (uiState.isLoading && uiState.student == null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          val student = uiState.student
          val classRoom = uiState.classRoom

          // Student Info Card
          if (student != null) {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("student_info_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = student.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )

                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(12.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = student.rollNumber,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.SupervisorAccount,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = "Father: ${student.fatherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (classRoom != null) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = classRoom.subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }

                    val isMorning = classRoom.timing.equals("Morning", ignoreCase = true)
                    val timingIcon = if (isMorning) Icons.Default.WbSunny else Icons.Default.WbTwilight
                    val timingFg = if (isMorning) TertiaryAmber else Color(0xFF5B21B6)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = timingIcon,
                        contentDescription = null,
                        tint = timingFg,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = classRoom.timing,
                        style = MaterialTheme.typography.bodySmall,
                        color = timingFg,
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(28.dp))
          }

          // Papers Header & Action
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Scanned Papers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
              )
              if (uiState.papers.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Text(
                    text = "${uiState.papers.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }

            OutlinedButton(
              onClick = {
                student?.let { s ->
                  onNavigateToScan(s.id ?: "", s.classId)
                }
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("scan_paper_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Scan Paper")
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          when {
            uiState.isLoading && uiState.papers.isEmpty() -> {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(24.dp),
                )
              }
            }

            uiState.papers.isEmpty() -> {
              // Empty State
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("papers_empty_state_card"),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  Text(
                    text = "No paper scanned yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = "Capture or select handwritten answer sheet pages for this student.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )

                  Spacer(modifier = Modifier.height(16.dp))

                  Button(
                    onClick = {
                      student?.let { s ->
                        onNavigateToScan(s.id ?: "", s.classId)
                      }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("empty_state_scan_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan Paper")
                  }
                }
              }
            }

            else -> {
              // List of Submitted Papers
              Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("papers_list"),
              ) {
                uiState.papers.forEachIndexed { index, paper ->
                  StudentPaperCard(
                    paper = paper,
                    index = index + 1,
                    signedUrls = uiState.signedUrls,
                    ocrTextByPageId = uiState.ocrTextByPageId,
                    ocrLoadingPageIds = uiState.ocrLoadingPageIds,
                    isEvaluating = uiState.isEvaluatingPaperId == paper.id,
                    onEvaluateClick = {
                      paper.id?.let { pid -> viewModel.runAiEvaluation(pid) }
                    },
                    onPageClick = { page ->
                      activePreviewPage = page
                    },
                    onDeleteClick = {
                      paperToDelete = paper
                    },
                    onRequestSignedUrl = { storagePath ->
                      viewModel.loadSignedUrlForPage(storagePath)
                    },
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

@Composable
fun StudentPaperCard(
  paper: StudentPaper,
  index: Int,
  signedUrls: Map<String, String>,
  ocrTextByPageId: Map<String, String> = emptyMap(),
  ocrLoadingPageIds: Set<String> = emptySet(),
  isEvaluating: Boolean = false,
  onEvaluateClick: () -> Unit = {},
  onPageClick: (StudentPaperPage) -> Unit,
  onDeleteClick: () -> Unit,
  onRequestSignedUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("student_paper_card_${paper.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Submission #$index",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "${paper.totalPages} Pages",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }

          if (paper.createdAt != null) {
            val formattedDate = paper.createdAt.substringBefore("T")
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        IconButton(
          onClick = onDeleteClick,
          modifier = Modifier
            .size(36.dp)
            .testTag("delete_paper_button_${paper.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete paper",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      // Thumbnail preview strip of pages
      if (paper.pages.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 4.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(paper.pages, key = { it.id ?: "${it.paperId}_${it.pageNumber}" }) { page ->
            PaperPageThumbnail(
              page = page,
              signedUrl = signedUrls[page.storagePath],
              hasOcr = page.id != null && ocrTextByPageId.containsKey(page.id),
              isOcrLoading = page.id != null && ocrLoadingPageIds.contains(page.id),
              onClick = { onPageClick(page) },
              onRequestSignedUrl = { onRequestSignedUrl(page.storagePath) },
            )
          }
        }
      }

      // AI Evaluation & Teacher Review Action
      Spacer(modifier = Modifier.height(14.dp))
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "AI Grading Pipeline",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Text(
              text = "Evaluation",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Button(
            onClick = onEvaluateClick,
            enabled = !isEvaluating,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("run_ai_evaluation_button_${paper.id}"),
          ) {
            if (isEvaluating) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Processing & Grading Paper...")
            } else {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Run AI Evaluation & Review")
            }
          }
        }
      }
    }
  }
}

@Composable
fun PaperPageThumbnail(
  page: StudentPaperPage,
  signedUrl: String?,
  hasOcr: Boolean = false,
  isOcrLoading: Boolean = false,
  onClick: () -> Unit,
  onRequestSignedUrl: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(page.storagePath) {
    if (signedUrl == null) {
      onRequestSignedUrl()
    }
  }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier
      .size(width = 84.dp, height = 110.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable { onClick() }
      .testTag("page_thumbnail_${page.id ?: page.pageNumber}"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (signedUrl != null) {
        AsyncImage(
          model = signedUrl,
          contentDescription = "Page ${page.pageNumber}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          imageVector = Icons.Default.PhotoLibrary,
          contentDescription = "Loading image",
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.size(24.dp),
        )
      }

      if (isOcrLoading) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // Small Page indicator
      Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 4.dp),
      ) {
        Text(
          text = "P.${page.pageNumber}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
      }

      if (hasOcr) {
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp),
        ) {
          Text(
            text = "OCR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
          )
        }
      }
    }
  }
}
