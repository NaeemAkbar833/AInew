package com.example.ui.paper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.EvaluationQuestion
import com.example.data.model.calculateCountedEvaluationTotalMarks
import com.example.data.model.calculateEffectiveEvaluationQuestionTotalMarks
import com.example.data.model.isQuestionUnattemptedForScoring
import com.example.data.model.sortEvaluationQuestionsNumerically
import com.example.data.model.stripSelectionRuleTagFromCriteria
import com.example.ui.components.ExamCardSkeleton
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

private val ApprovedGreenSurface = Color(0xFFE6F8ED)
private val ApprovedGreenAccent = Color(0xFF0E7A3D)

/**
 * Full-Screen Teacher Review Page for GradeScan.
 * Displays student info, bold score summary, interactive question cards with direct numeric +/- controls,
 * scrubbed provisional labels, missing answer special handling, and approval flow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeacherReviewScreen(
  studentId: String,
  paperId: String,
  viewModel: StudentDetailViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // Consume any pending one-shot navigation event while TeacherReviewScreen is active
  LaunchedEffect(uiState.pendingReviewNavigationPaperId) {
    if (uiState.pendingReviewNavigationPaperId != null) {
      viewModel.consumeReviewNavigation()
    }
  }

  // Load student data and evaluation if not already present
  LaunchedEffect(studentId, paperId, uiState.student?.id, uiState.isLoading, uiState.papers, uiState.errorMessage) {
    viewModel.ensureEvaluationLoadedForReview(studentId = studentId, paperId = paperId)
  }

  // Show feedback or mark validation error in snackbar if available
  LaunchedEffect(uiState.feedbackMessage) {
    uiState.feedbackMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissFeedbackMessage()
    }
  }

  LaunchedEffect(uiState.saveCorrectionsError) {
    uiState.saveCorrectionsError?.let { errMsg ->
      snackbarHostState.showSnackbar(errMsg)
    }
  }

  val evalResponse = uiState.evaluationResult
  val evaluation = evalResponse?.evaluation
  val knownPaper = uiState.papers.find { it.id == paperId }
  val isApproved = evaluation?.status.equals("approved", ignoreCase = true) ||
    evalResponse?.status.equals("approved", ignoreCase = true) ||
    knownPaper?.status.equals("approved", ignoreCase = true) ||
    knownPaper?.evaluation?.status.equals("approved", ignoreCase = true)
  val rawQuestions = if (isApproved) {
    evaluation?.questions.orEmpty()
  } else {
    uiState.editedQuestions ?: evaluation?.questions.orEmpty()
  }
  val questions = remember(rawQuestions) {
    sortEvaluationQuestionsNumerically(rawQuestions)
  }
  val isReviewRequired = !isApproved && (
    evaluation?.reviewRequired == true ||
      evaluation?.status.equals("needs_review", ignoreCase = true) ||
      evalResponse?.status.equals("needs_review", ignoreCase = true)
    )
  val hasUnsavedEdits = uiState.editedQuestions != null && !isApproved
  val isBusy = uiState.isApproving || uiState.isSavingCorrections

  val handleBack: () -> Unit = {
    if (!isBusy) {
      viewModel.consumeReviewNavigation()
      onNavigateBack()
    }
  }

  BackHandler(enabled = !isBusy) {
    handleBack()
  }

  val totalPossible = uiState.classRoom?.totalMarks?.toDouble()
    ?: evaluation?.totalMarks
    ?: calculateEffectiveEvaluationQuestionTotalMarks(questions)
  val liveTotalObtained = if (!isApproved && uiState.editedQuestions != null) {
    calculateCountedEvaluationTotalMarks(questions, totalPossible)
  } else {
    evaluation?.totalMarksObtained ?: calculateCountedEvaluationTotalMarks(questions, totalPossible)
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

  val primaryButtonGradient = remember {
    Brush.horizontalGradient(colors = listOf(RoyalBlue, DarkRoyalBlue))
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("teacher_review_screen"),
  ) {
    // Backdrop gradient layer extending behind topBar down into upper content area
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    onClick = handleBack,
                  )
                  .testTag("teacher_review_back_button"),
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
                  text = if (isApproved) "Approved Result" else "Teacher Review",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = if (isApproved) "Finalized evaluation result" else "Inspect AI grading, adjust marks, and approve",
                  fontFamily = FigtreeFontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 12.sp,
                  color = Color.White.copy(alpha = 0.85f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              val (badgeBg, badgeFg, badgeIcon, badgeText) = when {
                isApproved -> StatusPillSpec(ApprovedGreenSurface, ApprovedGreenAccent, Icons.Default.Verified, "Approved")
                isReviewRequired -> StatusPillSpec(TertiaryAmberContainer, StatusAmber, Icons.Default.WarningAmber, "Needs Review")
                else -> StatusPillSpec(LightBlueHighlight, RoyalBlue, Icons.Default.CheckCircle, "AI Graded")
              }

              Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isApproved) ApprovedGreenSurface else Color.White.copy(alpha = 0.2f),
                border = BorderStroke(
                  1.dp,
                  if (isApproved) ApprovedGreenAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.padding(end = 6.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = if (isApproved) ApprovedGreenAccent else Color.White,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isApproved) ApprovedGreenAccent else Color.White,
                  )
                }
              }
            }
          }
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        val evalId = evaluation?.id.orEmpty()
        val pId = evaluation?.paperId.orEmpty().ifBlank { paperId }

        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = Color.White,
          shadowElevation = 8.dp,
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (hasUnsavedEdits && evalId.isNotBlank() && pId.isNotBlank()) {
              OutlinedButton(
                onClick = { viewModel.saveTeacherCorrections(evalId, pId) },
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RoyalBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = LightBlueHighlight,
                  contentColor = RoyalBlue,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(42.dp)
                  .testTag("teacher_review_save_button"),
              ) {
                if (uiState.isSavingCorrections) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = RoyalBlue,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Saving Corrections...",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = RoyalBlue,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Save Edited Marks & Feedback",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue,
                  )
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = handleBack,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                  width = 1.dp,
                  color = if (isApproved) RoyalBlue.copy(alpha = 0.28f) else BorderGray,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (isApproved) LightBlueHighlight else Color.White,
                  contentColor = if (isApproved) RoyalBlue else TextPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("teacher_review_cancel_button"),
              ) {
                if (isApproved) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = RoyalBlue,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                  text = if (isApproved) "Close" else "Back",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 13.5.sp,
                  fontWeight = if (isApproved) FontWeight.Bold else FontWeight.SemiBold,
                  color = if (isApproved) RoyalBlue else TextPrimary,
                )
              }

              if (!isApproved && evalId.isNotBlank() && pId.isNotBlank()) {
                Button(
                  onClick = { viewModel.approveEvaluation(evalId, pId) },
                  enabled = !isBusy,
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = BorderGray,
                    disabledContentColor = TextSecondary,
                  ),
                  contentPadding = PaddingValues(0.dp),
                  modifier = Modifier
                    .weight(1.6f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      brush = if (!isBusy) primaryButtonGradient
                      else Brush.horizontalGradient(listOf(BorderGray, BorderGray))
                    )
                    .testTag("teacher_review_approve_button"),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                  ) {
                    if (uiState.isApproving) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "Approving...",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "Approve & Save Result",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Loading state for evaluation / approved result retrieval
          if (uiState.isEvaluatingPaperId == paperId || (uiState.isLoading && uiState.evaluationResult == null)) {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(36.dp),
                  color = if (isApproved) ApprovedGreenAccent else RoyalBlue,
                  strokeWidth = 3.dp,
                )
                Text(
                  text = if (isApproved) "Loading Approved Evaluation..." else "Analyzing & Grading Answer Sheet...",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Text(
                  text = if (isApproved) {
                    "Retrieving finalized marks and question breakdown."
                  } else {
                    "AI model is extracting student answers and calculating scores."
                  },
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.5.sp,
                  color = TextSecondary,
                )
              }
            }
          }

          // Error Banner
          val activeError = uiState.approvalError ?: uiState.saveCorrectionsError ?: uiState.evaluationError
          if (!activeError.isNullOrBlank()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = StatusWarningContainer,
              border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = StatusWarning,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = activeError,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.5.sp,
                  lineHeight = 16.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = StatusWarning,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }

          // 1. Student Information Card
          val student = uiState.student
          val classRoom = uiState.classRoom
          val studentName = student?.name.orEmpty().ifBlank { "Student" }
          val fatherName = student?.fatherName.orEmpty()
          val rollNumber = student?.rollNumber.orEmpty()
          val subjectName = classRoom?.subjectName.orEmpty()

          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Box(
                    modifier = Modifier
                      .size(42.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(LightBlueHighlight)
                      .border(1.dp, RoyalBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Person,
                      contentDescription = null,
                      tint = RoyalBlue,
                      modifier = Modifier.size(20.dp),
                    )
                  }

                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = studentName,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (fatherName.isNotBlank()) {
                      Text(
                        text = "Father: $fatherName",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.sp,
                        color = TextSecondary,
                      )
                    }
                  }
                }

                val (badgeBg, badgeFg, badgeIcon, badgeText) = when {
                  isApproved -> StatusPillSpec(StatusSuccessContainer, StatusSuccess, Icons.Default.Verified, "Approved")
                  isReviewRequired -> StatusPillSpec(TertiaryAmberContainer, StatusAmber, Icons.Default.WarningAmber, "Needs Review")
                  else -> StatusPillSpec(LightBlueHighlight, RoyalBlue, Icons.Default.CheckCircle, "AI Graded")
                }

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = badgeBg,
                  border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.25f)),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = badgeIcon,
                      contentDescription = null,
                      tint = badgeFg,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = badgeText,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = badgeFg,
                    )
                  }
                }
              }

              if (rollNumber.isNotBlank() || subjectName.isNotBlank()) {
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  if (rollNumber.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = LightBlueHighlight,
                      border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
                    ) {
                      Text(
                        text = "Roll No: $rollNumber",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                  }
                  if (subjectName.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = Color.White,
                      border = BorderStroke(1.dp, BorderGray),
                    ) {
                      Text(
                        text = subjectName,
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                  }
                }
              }

              // Amber Needs Review Warning Panel
              if (isReviewRequired && !evaluation?.reviewReason.isNullOrBlank()) {
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp),
                  color = TertiaryAmberContainer,
                  border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f)),
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.WarningAmber,
                      contentDescription = null,
                      tint = StatusAmber,
                      modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text(
                        text = "Teacher Review Recommended",
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusAmber,
                      )
                      Text(
                        text = evaluation?.reviewReason.orEmpty(),
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.5.sp,
                        color = TextPrimary,
                        lineHeight = 16.sp,
                      )
                    }
                  }
                }
              }
            }
          }

          // 2. Bold Total Evaluation Score Card
          val extractedTotal = evaluation?.extractedQuestionTotalMarks
          val hasDiscrepancy = extractedTotal != null && extractedTotal > 0.0 && extractedTotal != totalPossible

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("teacher_review_score_summary"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isApproved) ApprovedGreenSurface.copy(alpha = 0.72f) else Color.White,
            ),
            border = BorderStroke(
              width = 1.dp,
              color = if (isApproved) ApprovedGreenAccent.copy(alpha = 0.32f) else RoyalBlue.copy(alpha = 0.25f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isApproved) 1.5.dp else 2.dp),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              // Top Banner: Glassy Green when approved, Royal Blue Gradient before approval
              val scoreBannerBrush = if (isApproved) {
                Brush.horizontalGradient(
                  listOf(
                    ApprovedGreenSurface,
                    Color(0xFFD3F2E0),
                  )
                )
              } else {
                Brush.horizontalGradient(listOf(RoyalBlue, DarkRoyalBlue))
              }

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(scoreBannerBrush)
                  .padding(horizontal = 16.dp, vertical = 14.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      if (isApproved) {
                        Icon(
                          imageVector = Icons.Default.Verified,
                          contentDescription = null,
                          tint = ApprovedGreenAccent,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                      Text(
                        text = if (isApproved) "Final Approved Score" else "Total Awarded Score",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isApproved) TextSecondary else Color.White.copy(alpha = 0.85f),
                      )
                    }
                    Row(
                      verticalAlignment = Alignment.Bottom,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Text(
                        text = formatMarks(liveTotalObtained),
                        fontFamily = BricolageGrotesqueFontFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isApproved) TextPrimary else Color.White,
                      )
                      Text(
                        text = "/ ${formatMarks(totalPossible)} marks",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isApproved) TextSecondary else Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.padding(bottom = 3.dp),
                      )
                    }
                  }

                  // Percentage Circle Pill
                  val pctStr = if (evaluation?.percentageAvailable == true && totalPossible > 0.0) {
                    String.format(Locale.US, "%.1f%%", (liveTotalObtained / totalPossible) * 100.0)
                  } else "N/A"

                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isApproved) Color.White.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.20f),
                    border = BorderStroke(
                      1.dp,
                      if (isApproved) ApprovedGreenAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f),
                    ),
                  ) {
                    Text(
                      text = pctStr,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isApproved) TextPrimary else Color.White,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                  }
                }
              }

              if (isApproved) {
                HorizontalDivider(
                  thickness = 1.dp,
                  color = BorderGray.copy(alpha = 0.7f),
                )

                // Minimalist Metrics Strip inside approved card
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                  MetricChip(
                    label = "Questions",
                    value = "${questions.size}",
                    isApproved = true,
                    icon = Icons.Default.FormatListNumbered,
                    modifier = Modifier.weight(1f),
                  )
                  if (extractedTotal != null && extractedTotal > 0.0) {
                    Box(
                      modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(BorderGray.copy(alpha = 0.75f)),
                    )
                    MetricChip(
                      label = "Extracted Max",
                      value = formatMarks(extractedTotal),
                      isApproved = true,
                      icon = Icons.Default.Calculate,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  Box(
                    modifier = Modifier
                      .width(1.dp)
                      .height(28.dp)
                      .background(BorderGray.copy(alpha = 0.75f)),
                  )
                  MetricChip(
                    label = "Class Max",
                    value = formatMarks(totalPossible),
                    isApproved = true,
                    icon = Icons.Default.School,
                    modifier = Modifier.weight(1f),
                  )
                }
              } else {
                // Metrics Strip inside unapproved card
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  MetricChip(
                    label = "Questions",
                    value = "${questions.size}",
                    isApproved = false,
                    modifier = Modifier.weight(1f),
                  )
                  if (extractedTotal != null && extractedTotal > 0.0) {
                    MetricChip(
                      label = "Extracted Max",
                      value = formatMarks(extractedTotal),
                      isApproved = false,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  MetricChip(
                    label = "Class Max",
                    value = formatMarks(totalPossible),
                    isApproved = false,
                    modifier = Modifier.weight(1f),
                  )
                }
              }

              if (!isApproved && hasDiscrepancy) {
                Surface(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                  shape = RoundedCornerShape(8.dp),
                  color = TertiaryAmberContainer,
                  border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f)),
                ) {
                  Text(
                    text = "Notice: Extracted question max (${formatMarks(extractedTotal!!)}) differs from class total marks (${formatMarks(totalPossible)}). Review recommended.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 11.5.sp,
                    color = StatusAmber,
                    lineHeight = 15.5.sp,
                    modifier = Modifier.padding(8.dp),
                  )
                }
              }
            }
          }

          // 3. Question Breakdown Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Question Breakdown (${questions.size})",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Text(
                text = if (isApproved) "Finalized question marks and grading details" else "Adjust marks using - and + controls below",
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                color = TextSecondary,
              )
            }
          }

          if (questions.isEmpty()) {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Text(
                text = "No question breakdown available for this answer sheet.",
                fontFamily = FigtreeFontFamily,
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(16.dp),
              )
            }
          } else {
            questions.forEachIndexed { index, question ->
              FullQuestionCard(
                index = index,
                question = question,
                isApproved = isApproved,
                isEditable = !isApproved && !isBusy,
                onUpdateMarks = { newMarks ->
                  if (!isApproved && !isBusy) {
                    viewModel.onUpdateQuestion(
                      question.id,
                      question.questionNumber,
                      newMarks,
                      question.feedback.orEmpty(),
                    )
                  }
                },
                onUpdateFeedback = { updatedFeedback ->
                  if (!isApproved && !isBusy) {
                    viewModel.onUpdateQuestion(
                      question.id,
                      question.questionNumber,
                      question.awardedMarks,
                      updatedFeedback,
                    )
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

private data class StatusPillSpec(
  val bg: Color,
  val fg: Color,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val text: String,
)

@Composable
private fun MetricChip(
  label: String,
  value: String,
  isApproved: Boolean = false,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  modifier: Modifier = Modifier,
) {
  if (isApproved) {
    Column(
      modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(12.dp),
          )
        }
        Text(
          text = label,
          fontFamily = FigtreeFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )
    }
  } else {
    Surface(
      modifier = modifier,
      shape = RoundedCornerShape(10.dp),
      color = LightBlueHighlight,
      border = BorderStroke(1.dp, BorderGray),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = label,
          fontFamily = FigtreeFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = TextSecondary,
        )
        Text(
          text = value,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
      }
    }
  }
}

/**
 * Full Question Review Card with direct numeric increment/decrement controls,
 * missing answer special handling, and clean scrubbed rubric sections.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullQuestionCard(
  index: Int,
  question: EvaluationQuestion,
  isApproved: Boolean,
  isEditable: Boolean,
  onUpdateMarks: (Double) -> Unit,
  onUpdateFeedback: (String) -> Unit,
) {
  val qLabel = question.questionNumber ?: "${index + 1}"
  val maxMarks = question.maximumMarks ?: 0.0
  val awarded = question.awardedMarks ?: 0.0

  // Check if student answer is genuinely missing / unattempted
  val rawAnswer = question.studentAnswer?.trim().orEmpty()
  val isAnswerMissing = isQuestionUnattemptedForScoring(question)

  var showRubric by remember(question.id, qLabel) { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("review_question_card_$qLabel"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(
      width = 1.dp,
      color = if (!isApproved && question.reviewRequired) StatusAmber.copy(alpha = 0.5f) else BorderGray,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Header Row: Q# Badge + Review Needed Badge + Marks Display / Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = RoyalBlue,
          ) {
            Text(
              text = "Q$qLabel",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }

          if (!isApproved && question.reviewRequired) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = TertiaryAmberContainer,
              border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = StatusAmber,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "Review Needed",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = StatusAmber,
                )
              }
            }
          }
        }

        // Marks Display: Read-only non-editable text when approved, or Direct Increment/Decrement Control Row when unapproved
        val isZero = awarded == 0.0
        val boxBg = if (isZero) Color(0xFFFDF2F2) else LightBlueHighlight
        val boxBorder = if (isZero) BorderStroke(1.dp, Color(0xFFE5484D).copy(alpha = 0.5f)) else BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f))
        val boxContentColor = if (isZero) Color(0xFFE5484D) else RoyalBlue

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = boxBg,
          border = boxBorder,
          modifier = Modifier.testTag("question_marks_box_$qLabel"),
        ) {
          if (isApproved) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Text(
                text = formatMarks(awarded),
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = boxContentColor,
              )
              Text(
                text = "/ ${formatMarks(maxMarks)} marks",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isZero) Color(0xFFE5484D).copy(alpha = 0.8f) else TextSecondary,
              )
            }
          } else {
            val canIncrease = isEditable && !isAnswerMissing && awarded < maxMarks
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
              if (isEditable && awarded > 0.0) {
                Surface(
                  shape = RoundedCornerShape(5.dp),
                  color = Color.White,
                  border = BorderStroke(1.dp, boxContentColor.copy(alpha = 0.35f)),
                  modifier = Modifier
                    .padding(end = 2.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(role = Role.Button) { onUpdateMarks(0.0) }
                    .testTag("question_marks_zero_$qLabel"),
                ) {
                  Text(
                    text = "Set 0",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = boxContentColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                  )
                }
              }

              // Decrement Button (-)
              IconButton(
                onClick = {
                  if (isEditable) {
                    val step = if (maxMarks <= 10.0) 0.5 else 1.0
                    val newMarks = (awarded - step).coerceIn(0.0, maxMarks)
                    onUpdateMarks(newMarks)
                  }
                },
                enabled = isEditable && awarded > 0.0,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("question_marks_decrement_$qLabel"),
              ) {
                Icon(
                  imageVector = Icons.Default.Remove,
                  contentDescription = "Decrease Marks",
                  tint = if (isEditable && awarded > 0.0) boxContentColor else BorderGray,
                  modifier = Modifier.size(14.dp),
                )
              }

              // Awarded Marks Value Display
              Text(
                text = formatMarks(awarded),
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = boxContentColor,
                modifier = Modifier.padding(horizontal = 4.dp),
              )

              Text(
                text = "/ ${formatMarks(maxMarks)}",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isZero) Color(0xFFE5484D).copy(alpha = 0.8f) else TextSecondary,
              )

              // Increment Button (+)
              IconButton(
                onClick = {
                  if (canIncrease) {
                    val step = if (maxMarks <= 10.0) 0.5 else 1.0
                    val newMarks = (awarded + step).coerceIn(0.0, maxMarks)
                    onUpdateMarks(newMarks)
                  }
                },
                enabled = canIncrease,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("question_marks_increment_$qLabel"),
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "Increase Marks",
                  tint = if (canIncrease) boxContentColor else BorderGray,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }

      // 2. Question Text
      Text(
        text = question.questionText?.ifBlank { "Question $qLabel" } ?: "Question $qLabel",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth(),
      )

      // 3. Genuine Missing Answer Special Handling
      if (isAnswerMissing) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFF8FAFC),
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "No answer detected",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold,
              color = TextSecondary,
            )
          }
        }
      } else {
        // 4. Student's Extracted Answer Section (when present)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Student Answer (OCR)",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = RoyalBlue,
            )
            Text(
              text = rawAnswer,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              color = TextPrimary,
              lineHeight = 17.5.sp,
            )
          }
        }

        // 5. Grading Feedback Section
        if (!question.feedback.isNullOrBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = "Grading Feedback",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextSecondary,
            )
            Text(
              text = question.feedback,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.5.sp,
              color = TextPrimary,
              lineHeight = 17.5.sp,
            )
          }
        }

        // 6. Collapsible Rubric Section (Expected Answer & Marking Criteria)
        val cleanExpected = cleanProvisionalText(question.expectedAnswer)
        val cleanCriteria = cleanProvisionalText(question.markingCriteria)

        if (cleanExpected.isNotBlank() || cleanCriteria.isNotBlank()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showRubric = !showRubric }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = if (showRubric) "Hide Rubric" else "Show Rubric",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = RoyalBlue,
            )
            Icon(
              imageVector = if (showRubric) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(15.dp),
            )
          }

          AnimatedVisibility(visible = showRubric) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (cleanExpected.isNotBlank()) {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = "Expected Answer",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = RoyalBlue,
                    )
                    Text(
                      text = cleanExpected,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      color = TextSecondary,
                      lineHeight = 16.5.sp,
                    )
                  }
                }

                if (cleanCriteria.isNotBlank()) {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = "Marking Criteria",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = RoyalBlue,
                    )
                    Text(
                      text = cleanCriteria,
                      fontFamily = FigtreeFontFamily,
                      fontSize = 12.sp,
                      color = TextSecondary,
                      lineHeight = 16.5.sp,
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

private val PROVISIONAL_REF_ANSWER_COLON_REGEX =
  Regex("\\[Provisional Reference Answer\\]:\\s*", RegexOption.IGNORE_CASE)
private val PROVISIONAL_CRITERIA_COLON_REGEX =
  Regex("\\[Provisional Criteria\\]:\\s*", RegexOption.IGNORE_CASE)
private val PROVISIONAL_REF_ANSWER_TAG_REGEX =
  Regex("\\[provisional reference answer\\]", RegexOption.IGNORE_CASE)
private val PROVISIONAL_CRITERIA_TAG_REGEX =
  Regex("\\[provisional criteria\\]", RegexOption.IGNORE_CASE)
private val PROVISIONAL_RUBRIC_COLON_REGEX =
  Regex("\\[Provisional Rubric\\]:\\s*", RegexOption.IGNORE_CASE)
private val PROVISIONAL_RUBRIC_TAG_REGEX =
  Regex("\\[provisional rubric\\]", RegexOption.IGNORE_CASE)

/**
 * Scrubs provisional bracket labels like [provisional reference answer] from UI text.
 */
private fun cleanProvisionalText(text: String?): String {
  if (text.isNullOrBlank()) return ""
  return stripSelectionRuleTagFromCriteria(text)
    .replace(PROVISIONAL_REF_ANSWER_COLON_REGEX, "")
    .replace(PROVISIONAL_CRITERIA_COLON_REGEX, "")
    .replace(PROVISIONAL_REF_ANSWER_TAG_REGEX, "")
    .replace(PROVISIONAL_CRITERIA_TAG_REGEX, "")
    .replace(PROVISIONAL_RUBRIC_COLON_REGEX, "")
    .replace(PROVISIONAL_RUBRIC_TAG_REGEX, "")
    .trim()
}

private fun formatMarks(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}
