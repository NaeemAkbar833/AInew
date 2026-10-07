package com.example.ui.paper.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationQuestion
import com.example.data.model.parseAiEvaluationResponse
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.TertiaryAmberContainer

@Composable
fun TeacherReviewDialog(
  evaluationResponse: AiEvaluationResponse?,
  rawJson: String?,
  studentName: String,
  className: String,
  fallbackPaperId: String? = null,
  isApproving: Boolean = false,
  approvalError: String? = null,
  onDismiss: () -> Unit,
  onCopyJson: () -> Unit,
  onApprove: (evaluationId: String, paperId: String) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  val effectiveResponse = remember(evaluationResponse, rawJson) {
    if (evaluationResponse?.evaluation != null && evaluationResponse.evaluation.questions.isNotEmpty()) {
      evaluationResponse
    } else if (!rawJson.isNullOrBlank()) {
      parseAiEvaluationResponse(rawJson, fallbackPaperId) ?: evaluationResponse
    } else {
      evaluationResponse
    }
  }

  val evaluation = effectiveResponse?.evaluation
  val overallStatus = effectiveResponse?.status ?: evaluation?.status
  val isApproved = overallStatus == "approved" || evaluation?.status == "approved"
  val evalId = evaluation?.id
  val paperId = evaluation?.paperId ?: fallbackPaperId
  val canApprove = !evalId.isNullOrBlank() && !paperId.isNullOrBlank() && !isApproved

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = modifier
        .fillMaxWidth(0.96f)
        .fillMaxHeight(0.92f)
        .testTag("teacher_review_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
      ) {
        // 1. Header Bar
        TeacherReviewHeader(
          evaluation = evaluation,
          overallStatus = overallStatus,
          studentName = studentName,
          className = className,
          onDismiss = onDismiss,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Scrollable Content
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        ) {
          if (evaluation != null) {
            // A. Score & Percentage Summary Card
            EvaluationSummaryCard(
              evaluation = evaluation,
              percentageAvailable = effectiveResponse?.percentageAvailable == true || evaluation.percentageAvailable,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // B. Overall Review Flag Banner (if required)
            if (evaluation.reviewRequired || overallStatus == "needs_review") {
              EvaluationReviewWarningBanner(
                reviewReason = evaluation.reviewReason
                  ?: "Some question marks, answer keys, or class total discrepancies require teacher review.",
              )
              Spacer(modifier = Modifier.height(14.dp))
            }

            // C. Question Breakdown Header
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
                  text = "Question Breakdown",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Text(
                    text = "${evaluation.questions.size} Questions",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }

            Text(
              text = "Review handwritten OCR, provisional criteria, and awarded marks.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            // D. List of Questions
            if (evaluation.questions.isEmpty()) {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 12.dp),
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                ) {
                  Text(
                    text = "No questions found in this evaluation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                evaluation.questions.forEachIndexed { index, question ->
                  TeacherQuestionReviewCard(
                    question = question,
                    index = index + 1,
                  )
                }
              }
            }
          } else {
            // Fallback: If evaluation failed to parse or is raw text
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  text = "Raw Evaluation Response",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                  Text(
                    text = rawJson.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                  )
                }
              }
            }
          }
        }

        // Optional Approval Error Alert Banner
        if (!approvalError.isNullOrBlank()) {
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .testTag("approval_error_banner"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = approvalError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Footer Action Bar
        TeacherReviewFooter(
          isApproved = isApproved,
          isApproving = isApproving,
          canApprove = canApprove,
          onApprove = {
            if (canApprove) {
              onApprove(evalId!!, paperId!!)
            }
          },
          onCopyJson = onCopyJson,
          onDismiss = onDismiss,
        )
      }
    }
  }
}

@Composable
private fun TeacherReviewHeader(
  evaluation: EvaluationDetail?,
  overallStatus: String?,
  studentName: String,
  className: String,
  onDismiss: () -> Unit,
) {
  val isApproved = overallStatus == "approved" || evaluation?.status == "approved"
  val isReviewRequired = evaluation?.reviewRequired == true || overallStatus == "needs_review"

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Assignment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Teacher Review",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("evaluation_review_title"),
          )

          // Status Badge
          if (isApproved) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFDCFCE7),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF166534),
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "Approved",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF166534),
                )
              }
            }
          } else if (isReviewRequired) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = TertiaryAmberContainer,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.PendingActions,
                  contentDescription = null,
                  tint = TertiaryAmber,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "Needs Review",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = TertiaryAmber,
                )
              }
            }
          } else {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "AI Graded",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
        }

        Text(
          text = "$studentName • $className",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    IconButton(
      onClick = onDismiss,
      modifier = Modifier.testTag("close_teacher_review_header_button"),
    ) {
      Icon(imageVector = Icons.Default.Close, contentDescription = "Close review")
    }
  }
}

@Composable
private fun EvaluationSummaryCard(
  evaluation: EvaluationDetail,
  percentageAvailable: Boolean,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("evaluation_score_summary_card"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      // Total Marks Column
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "TOTAL SCORE",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = evaluation.displayTotalMarksObtained,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = " / ${evaluation.displayTotalMarks}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
          )
        }

        if (evaluation.extractedQuestionTotalMarks != null &&
          evaluation.extractedQuestionTotalMarks != evaluation.totalMarks
        ) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Extracted Questions Total: ${evaluation.displayExtractedTotalMarks} marks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }

      // Percentage Column
      Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = "PERCENTAGE",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (percentageAvailable && evaluation.percentage != null) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary,
          ) {
            Text(
              text = "${evaluation.percentage}%",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        } else {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = TertiaryAmberContainer,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = "Pending Review",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TertiaryAmber,
              )
              Text(
                text = "Requires confirmation",
                style = MaterialTheme.typography.labelSmall,
                color = TertiaryAmber.copy(alpha = 0.85f),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EvaluationReviewWarningBanner(
  reviewReason: String,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = TertiaryAmberContainer.copy(alpha = 0.85f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("evaluation_review_warning_banner"),
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Icon(
        imageVector = Icons.Default.WarningAmber,
        contentDescription = "Review Warning",
        tint = TertiaryAmber,
        modifier = Modifier.size(20.dp),
      )
      Column {
        Text(
          text = "Teacher Review Recommended",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = TertiaryAmber,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = reviewReason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun TeacherQuestionReviewCard(
  question: EvaluationQuestion,
  index: Int,
) {
  val qNum = question.questionNumber ?: "$index"
  val isFullMarks = question.maximumMarks != null &&
    question.maximumMarks > 0 &&
    question.awardedMarks == question.maximumMarks

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = if (question.reviewRequired) TertiaryAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("evaluation_question_card_$qNum"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      // Header: Question Number & Marks Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "Question $qNum",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }

          if (question.reviewRequired) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = TertiaryAmberContainer,
            ) {
              Text(
                text = "Review Flagged",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TertiaryAmber,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        // Marks Awarded Badge
        val marksContainerColor = when {
          isFullMarks -> Color(0xFFDCFCE7) // Soft Green
          (question.awardedMarks ?: 0.0) > 0.0 -> Color(0xFFDBEAFE) // Soft Blue
          else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val marksTextColor = when {
          isFullMarks -> Color(0xFF166534)
          (question.awardedMarks ?: 0.0) > 0.0 -> Color(0xFF1E40AF)
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = marksContainerColor,
        ) {
          Text(
            text = "${question.displayAwardedMarks} / ${question.displayMaxMarks} Marks",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = marksTextColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Review Reason banner if flagged
      if (question.reviewRequired && !question.reviewReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = TertiaryAmberContainer.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = null,
              tint = TertiaryAmber,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = question.reviewReason,
              style = MaterialTheme.typography.labelSmall,
              color = TertiaryAmber,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Question Text
      Text(
        text = question.questionText ?: "Question text not detected",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
      Spacer(modifier = Modifier.height(10.dp))

      // Student's Handwritten Answer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Student's Answer (Handwriting OCR)",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        val studentAns = question.studentAnswer?.trim()
        val isNoAns = studentAns.isNullOrEmpty() || studentAns == "[No answer detected]"
        Text(
          text = if (isNoAns) "[No answer detected]" else studentAns.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          fontStyle = if (isNoAns) FontStyle.Italic else FontStyle.Normal,
          color = if (isNoAns) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Expected Answer & Source
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
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = Color(0xFF0D9488),
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Expected Answer",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Source Chip
        SourceIndicatorChip(source = question.expectedAnswerSource)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = question.expectedAnswer ?: "No expected answer established.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Marking Criteria & Source
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
            imageVector = Icons.AutoMirrored.Filled.Rule,
            contentDescription = null,
            tint = Color(0xFF6366F1),
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Marking Criteria",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Source Chip
        SourceIndicatorChip(source = question.markingCriteriaSource)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = question.markingCriteria ?: "No marking criteria established.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }

      // Feedback
      if (!question.feedback.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Feedback,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "AI Feedback & Rationale",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = question.feedback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun SourceIndicatorChip(source: String?) {
  when (source) {
    "question_paper" -> {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFDCFCE7), // Soft Green
      ) {
        Text(
          text = "From Question Paper",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF166534),
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
    "ai_generated" -> {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFEDE9FE), // Soft Purple / Lavender
      ) {
        Text(
          text = "AI Generated (Provisional)",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF6B21A8),
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
    else -> {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Text(
          text = "Not Specified",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun TeacherReviewFooter(
  isApproved: Boolean,
  isApproving: Boolean,
  canApprove: Boolean,
  onApprove: () -> Unit,
  onCopyJson: () -> Unit,
  onDismiss: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    // Row 1: Copy JSON & Close secondary actions
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedButton(
        onClick = onCopyJson,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .testTag("copy_evaluation_json_button"),
      ) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Copy JSON", maxLines = 1)
      }

      OutlinedButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .testTag("close_teacher_review_dialog_button"),
      ) {
        Text("Close", maxLines = 1)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Row 2: Full-width primary Approve & Save Result action / Approved badge
    if (isApproved) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFDCFCE7),
        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("evaluation_approved_badge"),
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF166534),
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "✓ Approved",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF166534),
            maxLines = 1,
          )
        }
      }
    } else {
      Button(
        onClick = onApprove,
        enabled = !isApproving && canApprove,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("approve_evaluation_button"),
      ) {
        if (isApproving) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Approving & Saving...", maxLines = 1)
        } else {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Approve & Save Result", maxLines = 1)
        }
      }
    }
  }
}
