package com.example.ui.paper.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.EvaluationQuestion
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
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
import java.util.Locale

/**
 * Interactive Teacher Review & Approval ModalBottomSheet harmonized with the
 * GradeScan Royal Blue + White design system.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeacherReviewDialog(
  evaluationResponse: AiEvaluationResponse,
  editedQuestions: List<EvaluationQuestion>? = null,
  studentName: String = "",
  studentRollNumber: String = "",
  fatherName: String = "",
  subjectName: String = "",
  isApproving: Boolean = false,
  approvalError: String? = null,
  isSavingCorrections: Boolean = false,
  saveCorrectionsSuccess: Boolean = false,
  saveCorrectionsError: String? = null,
  onUpdateQuestion: (questionId: String?, questionNumber: String?, awardedMarks: Double?, feedback: String) -> Unit,
  onSaveCorrections: (evaluationId: String, paperId: String) -> Unit,
  onApprove: (evaluationId: String, paperId: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.84f).dp

  val evaluation = evaluationResponse.evaluation
  val questions = editedQuestions ?: evaluation?.questions.orEmpty()
  val isApproved = evaluation?.status == "approved" || evaluationResponse.status == "approved"
  val isReviewRequired = !isApproved && (
    evaluation?.reviewRequired == true ||
      evaluation?.status == "needs_review" ||
      evaluationResponse.status == "needs_review"
    )
  val hasUnsavedEdits = editedQuestions != null && !isApproved
  val isBusy = isApproving || isSavingCorrections

  val liveTotalObtained = if (editedQuestions != null) {
    editedQuestions.sumOf { it.awardedMarks ?: 0.0 }
  } else {
    evaluation?.totalMarksObtained ?: questions.sumOf { it.awardedMarks ?: 0.0 }
  }
  val totalPossible = evaluation?.totalMarks ?: questions.sumOf { it.maximumMarks ?: 0.0 }

  val headerGradient = Brush.linearGradient(
    colors = listOf(RoyalBlue, DarkRoyalBlue)
  )
  val primaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(RoyalBlue, DarkRoyalBlue)
  )

  val (badgeBg, badgeFg, badgeIcon, badgeText) = when {
    isApproved -> StatusPillSpec(
      bg = StatusSuccessContainer,
      fg = StatusSuccess,
      icon = Icons.Default.Verified,
      text = "Approved",
    )
    isReviewRequired -> StatusPillSpec(
      bg = TertiaryAmberContainer,
      fg = StatusAmber,
      icon = Icons.Default.WarningAmber,
      text = "Needs Review",
    )
    else -> StatusPillSpec(
      bg = LightBlueHighlight,
      fg = RoyalBlue,
      icon = Icons.Default.CheckCircle,
      text = "AI Graded",
    )
  }

  ModalBottomSheet(
    onDismissRequest = { if (!isBusy) onDismiss() },
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = Color.White,
    scrimColor = Color.Black.copy(alpha = 0.45f),
    tonalElevation = 0.dp,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    dragHandle = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .width(34.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(BorderGray)
        )
      }
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .background(Color.White)
        .navigationBarsPadding()
        .imePadding()
        .testTag("teacher_review_dialog"),
    ) {
      // 1. Compact Header with Student Name, Roll Number & Status Badge
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(headerGradient),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (isApproved) Icons.Default.Verified else Icons.Default.RateReview,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = if (isApproved) "Approved Evaluation Result" else "Review & Approve Evaluation",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.2).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              if (studentName.isBlank() && subjectName.isBlank()) {
                Text(
                  text = "Inspect AI grading, edit marks, and approve",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = TextSecondary,
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(CircleShape)
              .background(LightBlueHighlight)
              .border(1.dp, BorderGray, CircleShape)
              .clickable(
                enabled = !isBusy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
              )
              .testTag("teacher_review_close_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextSecondary,
              modifier = Modifier.size(15.dp),
            )
          }
        }

        // Student & Subject Context Strip (readable student name, father name, roll number, and status badge)
        if (studentName.isNotBlank() || studentRollNumber.isNotBlank() || subjectName.isNotBlank()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = LightBlueHighlight,
            border = BorderStroke(1.dp, BorderGray),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  if (studentName.isNotBlank()) {
                    Text(
                      text = studentName,
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 14.sp,
                      lineHeight = 18.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (fatherName.isNotBlank()) {
                    Text(
                      text = "Father: $fatherName",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 11.5.sp,
                      lineHeight = 15.5.sp,
                      color = TextSecondary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = badgeBg,
                  border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.25f)),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = badgeFg,
                    )
                  }
                }
              }

              if (studentRollNumber.isNotBlank() || subjectName.isNotBlank()) {
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (studentRollNumber.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = Color.White,
                      border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
                    ) {
                      Text(
                        text = "Roll No: $studentRollNumber",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                      )
                    }
                  }
                  if (subjectName.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = Color.White,
                      border = BorderStroke(1.dp, BorderGray),
                    ) {
                      Text(
                        text = subjectName,
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      HorizontalDivider(thickness = 1.dp, color = BorderGray)

      // 2. Scrollable Review Content
      Column(
        modifier = Modifier
          .weight(1f, fill = false)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Error Banner (Approval or Save Corrections Error)
        val activeError = approvalError ?: saveCorrectionsError
        if (!activeError.isNullOrBlank()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = StatusWarningContainer,
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = StatusWarning,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = activeError,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = StatusWarning,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // Save Corrections Success Banner
        if (saveCorrectionsSuccess) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = StatusSuccessContainer,
            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.3f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Teacher adjustments saved. Click 'Approve & Finalize' when ready.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = StatusSuccess,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // Overall Score Summary Card
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("teacher_review_score_summary"),
          shape = RoundedCornerShape(12.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.22f)),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = if (isApproved) "Final Approved Score" else "Total Evaluation Score",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextSecondary,
                )
                Row(
                  verticalAlignment = Alignment.Bottom,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = formatMarks(liveTotalObtained),
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RoyalBlue,
                  )
                  Text(
                    text = "/ ${formatMarks(totalPossible)}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (badgeBg == LightBlueHighlight) Color.White else badgeBg,
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
                    modifier = Modifier.size(13.dp),
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

            // Additional Metrics Row (Questions count, Extracted Marks, Percentage)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MetricChip(
                label = "Questions",
                value = "${questions.size}",
                modifier = Modifier.weight(1f),
              )
              val extractedTotal = evaluation?.extractedQuestionTotalMarks
              if (extractedTotal != null && extractedTotal > 0.0) {
                MetricChip(
                  label = "Extracted Max",
                  value = formatMarks(extractedTotal),
                  modifier = Modifier.weight(1f),
                )
              }
              val pct = if (totalPossible > 0.0 && (evaluation?.percentageAvailable == true || editedQuestions != null)) {
                String.format(Locale.US, "%.1f%%", (liveTotalObtained / totalPossible) * 100.0)
              } else if (evaluation?.percentageAvailable == true && evaluation.percentage != null) {
                String.format(Locale.US, "%.1f%%", evaluation.percentage)
              } else {
                "N/A"
              }
              MetricChip(
                label = "Percentage",
                value = pct,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // Review Required Banner
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

        // Question-by-Question Section Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Question Breakdown (${questions.size})",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          if (!isApproved) {
            Text(
              text = "Tap Edit to adjust marks",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = RoyalBlue,
            )
          }
        }

        if (questions.isEmpty()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderGray),
          ) {
            Text(
              text = "No individual question evaluations were returned for this paper.",
              fontSize = 12.5.sp,
              color = TextSecondary,
              modifier = Modifier.padding(14.dp),
            )
          }
        } else {
          questions.forEachIndexed { index, question ->
            QuestionReviewCard(
              index = index,
              question = question,
              isEditable = !isApproved && !isBusy,
              onUpdate = { newMarks, newFeedback ->
                onUpdateQuestion(
                  question.id,
                  question.questionNumber,
                  newMarks,
                  newFeedback,
                )
              },
            )
          }
        }
      }

      // 3. Pinned Action Footer
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White),
      ) {
        HorizontalDivider(thickness = 1.dp, color = BorderGray)

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Optional Save Corrections row when teacher has edited questions
          val evalId = evaluation?.id.orEmpty()
          val paperId = evaluation?.paperId.orEmpty()

          if (hasUnsavedEdits && evalId.isNotBlank() && paperId.isNotBlank()) {
            OutlinedButton(
              onClick = { onSaveCorrections(evalId, paperId) },
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
              if (isSavingCorrections) {
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
              onClick = onDismiss,
              enabled = !isBusy,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, BorderGray),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = TextPrimary,
              ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("teacher_review_cancel_button"),
            ) {
              Text(
                text = if (isApproved) "Close" else "Cancel",
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
              )
            }

            if (!isApproved && evalId.isNotBlank() && paperId.isNotBlank()) {
              Button(
                onClick = { onApprove(evalId, paperId) },
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
                  .weight(1.45f)
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
                  modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                  if (isApproving) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(15.dp),
                      strokeWidth = 2.dp,
                      color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Approving...",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = Color.White,
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Approve & Finalize",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = Color.White,
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
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = Color.White,
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        fontFamily = FigtreeFontFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
      )
      Text(
        text = value,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionReviewCard(
  index: Int,
  question: EvaluationQuestion,
  isEditable: Boolean,
  onUpdate: (awardedMarks: Double?, feedback: String) -> Unit,
) {
  val qLabel = question.questionNumber ?: "${index + 1}"
  val maxMarks = question.maximumMarks ?: 0.0
  val awarded = question.awardedMarks ?: 0.0

  var isEditing by remember(question.id, qLabel) { mutableStateOf(false) }
  var showReferenceDetails by remember(question.id, qLabel) { mutableStateOf(false) }
  var marksInput by remember(question.awardedMarks) {
    mutableStateOf(formatMarks(awarded))
  }
  var feedbackInput by remember(question.feedback) {
    mutableStateOf(question.feedback.orEmpty())
  }
  var marksError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(question.awardedMarks, question.feedback) {
    marksInput = formatMarks(question.awardedMarks ?: 0.0)
    feedbackInput = question.feedback.orEmpty()
  }

  val scoreRatio = if (maxMarks > 0.0) awarded / maxMarks else 0.0
  val scoreBg = when {
    scoreRatio >= 0.7 -> StatusSuccessContainer
    scoreRatio > 0.0 -> TertiaryAmberContainer
    else -> StatusWarningContainer
  }
  val scoreFg = when {
    scoreRatio >= 0.7 -> StatusSuccess
    scoreRatio > 0.0 -> StatusAmber
    else -> StatusWarning
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("review_question_card_$qLabel"),
    shape = RoundedCornerShape(12.dp),
    color = Color.White,
    border = BorderStroke(
      width = 1.dp,
      color = if (question.reviewRequired) StatusAmber.copy(alpha = 0.5f) else BorderGray,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // 1. Dedicated Top Header Row: Q# Pill + Status Badge + Marks Pill (separated from question text)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }

          if (question.reviewRequired) {
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

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = scoreBg,
          border = BorderStroke(1.dp, scoreFg.copy(alpha = 0.25f)),
        ) {
          Text(
            text = "${formatMarks(awarded)} / ${formatMarks(maxMarks)} marks",
            fontFamily = FigtreeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = scoreFg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      // 2. Full-Width Question Text
      Text(
        text = question.questionText?.ifBlank { "Question $qLabel" } ?: "Question $qLabel",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        lineHeight = 18.5.sp,
        modifier = Modifier.fillMaxWidth(),
      )

      // Question-level Review Reason if flagged
      if (question.reviewRequired && !question.reviewReason.isNullOrBlank()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = TertiaryAmberContainer,
          border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = StatusAmber,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = question.reviewReason,
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              lineHeight = 15.5.sp,
              fontWeight = FontWeight.Medium,
              color = StatusAmber,
            )
          }
        }
      }

      // 3. Student's Extracted Answer
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = LightBlueHighlight,
        border = BorderStroke(1.dp, BorderGray),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "Student Answer (OCR)",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
          )
          Text(
            text = question.studentAnswer?.ifBlank { "No answer text extracted." }
              ?: "No answer text extracted.",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            color = TextPrimary,
            lineHeight = 16.5.sp,
          )
        }
      }

      // 4. Grading Feedback (clearly separated)
      if (!question.feedback.isNullOrBlank() && !isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Grading Feedback",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
          )
          Text(
            text = question.feedback,
            fontFamily = FigtreeFontFamily,
            fontSize = 12.5.sp,
            color = TextPrimary,
            lineHeight = 17.sp,
          )
        }
      }

      // 5. Collapsible Expected Answer & Marking Criteria + Edit Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!question.expectedAnswer.isNullOrBlank() || !question.markingCriteria.isNullOrBlank()) {
          Row(
            modifier = Modifier
              .weight(1f)
              .clickable { showReferenceDetails = !showReferenceDetails }
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
              text = if (showReferenceDetails) "Hide Rubric" else "Show Rubric",
              fontFamily = FigtreeFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = RoyalBlue,
            )
            Icon(
              imageVector = if (showReferenceDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = RoyalBlue,
              modifier = Modifier.size(15.dp),
            )
          }
        } else {
          Spacer(modifier = Modifier.weight(1f))
        }

        if (isEditable && !isEditing) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = LightBlueHighlight,
            border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
            modifier = Modifier
              .clickable { isEditing = true }
              .testTag("edit_question_$qLabel"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "Edit Marks / Feedback",
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RoyalBlue,
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = showReferenceDetails) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = Color.White,
          border = BorderStroke(1.dp, BorderGray),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!question.expectedAnswer.isNullOrBlank()) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Expected Answer",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = RoyalBlue,
                )
                Text(
                  text = question.expectedAnswer,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.sp,
                  color = TextSecondary,
                  lineHeight = 16.sp,
                )
              }
            }
            if (!question.markingCriteria.isNullOrBlank()) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Marking Criteria",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = RoyalBlue,
                )
                Text(
                  text = question.markingCriteria,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.sp,
                  color = TextSecondary,
                  lineHeight = 16.sp,
                )
              }
            }
          }
        }
      }

      // 6. Edit Controls for Teacher Override (clearly separated Marks Input, Max Marks, and Feedback Input)
      if (isEditable && isEditing) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = LightBlueHighlight,
          border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Override Q$qLabel",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color.White,
                  border = BorderStroke(1.dp, BorderGray),
                ) {
                  Text(
                    text = "Max: ${formatMarks(maxMarks)}",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = RoyalBlue,
                modifier = Modifier.clickable { isEditing = false },
              ) {
                Text(
                  text = "Done",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }

            OutlinedTextField(
              value = marksInput,
              onValueChange = { input ->
                marksInput = input
                val parsed = input.trim().toDoubleOrNull()
                if (parsed == null) {
                  marksError = "Enter a valid number"
                } else if (parsed < 0.0 || (maxMarks > 0.0 && parsed > maxMarks)) {
                  marksError = "Marks must be between 0 and ${formatMarks(maxMarks)}"
                } else {
                  marksError = null
                  onUpdate(parsed, feedbackInput)
                }
              },
              label = { Text("Awarded Marks (Max ${formatMarks(maxMarks)})", fontFamily = FigtreeFontFamily, fontSize = 11.5.sp) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Grade,
                  contentDescription = null,
                  tint = RoyalBlue,
                  modifier = Modifier.size(16.dp),
                )
              },
              singleLine = true,
              isError = marksError != null,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              textStyle = TextStyle(
                fontFamily = BricolageGrotesqueFontFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              ),
              shape = RoundedCornerShape(8.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = RoyalBlue,
                unfocusedBorderColor = BorderGray,
                errorBorderColor = StatusWarning,
                cursorColor = RoyalBlue,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("question_marks_input_$qLabel"),
            )

            if (marksError != null) {
              Text(
                text = marksError!!,
                fontFamily = FigtreeFontFamily,
                fontSize = 11.sp,
                color = StatusWarning,
                fontWeight = FontWeight.Medium,
              )
            }

            OutlinedTextField(
              value = feedbackInput,
              onValueChange = { updatedFeedback ->
                feedbackInput = updatedFeedback
                val parsedMarks = marksInput.trim().toDoubleOrNull() ?: awarded
                onUpdate(parsedMarks, updatedFeedback)
              },
              label = { Text("Feedback", fontFamily = FigtreeFontFamily, fontSize = 11.5.sp) },
              minLines = 2,
              maxLines = 4,
              keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
              ),
              textStyle = TextStyle(
                fontFamily = FigtreeFontFamily,
                fontSize = 12.5.sp,
                color = TextPrimary,
                lineHeight = 17.sp,
              ),
              shape = RoundedCornerShape(8.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = RoyalBlue,
                unfocusedBorderColor = BorderGray,
                cursorColor = RoyalBlue,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("question_feedback_input_$qLabel"),
            )
          }
        }
      }
    }
  }
}

private fun formatMarks(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}
