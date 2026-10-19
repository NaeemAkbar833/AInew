package com.example.ui.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtractedStudent
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.DisabledBackground
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewDialog(
  extractedStudents: List<ExtractedStudent>,
  isSaving: Boolean,
  errorMessage: String?,
  onDismissRequest: () -> Unit,
  onConfirm: () -> Unit,
  onUpdateStudent: (Int, ExtractedStudent) -> Unit,
  onDeleteStudent: (Int) -> Unit,
  onDismissError: () -> Unit,
) {
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.85f).dp
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()

  val saveButtonBrush = Brush.horizontalGradient(
    colors = listOf(DarkRoyalBlue, RoyalBlue)
  )
  val disabledButtonBrush = Brush.horizontalGradient(
    colors = listOf(DisabledBackground, DisabledBackground)
  )

  ModalBottomSheet(
    onDismissRequest = { if (!isSaving) onDismissRequest() },
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = Color.White,
    tonalElevation = 2.dp,
    scrimColor = Color.Black.copy(alpha = 0.4f),
    dragHandle = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BorderGray)
        )
      }
    },
    modifier = Modifier.testTag("import_review_dialog"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .navigationBarsPadding()
        .imePadding(),
    ) {
      // Fixed Compact Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = LightBlueHighlight,
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.SupervisorAccount,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = "Review Student List",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = "Verify and edit extracted information before saving",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = TextSecondary,
            )
          }
        }

        IconButton(
          onClick = {
            if (!isSaving) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
          },
          enabled = !isSaving,
          modifier = Modifier.size(48.dp),
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(DisabledBackground),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      if (errorMessage != null) {
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = StatusWarningContainer),
          border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
          onClick = onDismissError,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
              text = errorMessage,
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              lineHeight = 16.sp,
              fontWeight = FontWeight.Medium,
              color = StatusWarning,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Content Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false),
      ) {
        if (extractedStudents.isEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = LightBlueHighlight,
              modifier = Modifier.size(42.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.SupervisorAccount,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = RoyalBlue,
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No students in list",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Try scanning again or add students manually.",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = TextSecondary,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          ) {
            itemsIndexed(extractedStudents) { index, student ->
              ExtractedStudentItem(
                student = student,
                onUpdate = { onUpdateStudent(index, it) },
                onDelete = { onDeleteStudent(index) },
              )
            }
          }
        }
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      // Pinned Compact Bottom Action Buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          onClick = {
            if (!isSaving) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
          },
          enabled = !isSaving,
          shape = RoundedCornerShape(11.dp),
          color = Color.White,
          border = BorderStroke(1.dp, BorderGray),
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = TextPrimary,
            )
          }
        }

        val canConfirm = !isSaving && extractedStudents.isNotEmpty()
        Button(
          onClick = onConfirm,
          enabled = canConfirm,
          shape = RoundedCornerShape(11.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = TextSecondary,
          ),
          contentPadding = PaddingValues(),
          modifier = Modifier
            .weight(1.4f)
            .height(44.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                brush = if (canConfirm) saveButtonBrush else disabledButtonBrush,
                shape = RoundedCornerShape(11.dp),
              ),
            contentAlignment = Alignment.Center,
          ) {
            if (isSaving) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
            } else {
              Text(
                text = "Add ${extractedStudents.size} Students",
                style = MaterialTheme.typography.labelLarge.copy(
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.SemiBold,
                ),
                color = if (canConfirm) Color.White else TextSecondary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ExtractedStudentItem(
  student: ExtractedStudent,
  onUpdate: (ExtractedStudent) -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (student.isUnclear) StatusWarningContainer else Color.White
    ),
    border = BorderStroke(
      width = 1.dp,
      color = if (student.isUnclear) StatusWarning.copy(alpha = 0.35f) else BorderGray,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (student.rollNumber.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = LightBlueHighlight,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Badge,
                      contentDescription = null,
                      tint = DarkRoyalBlue,
                      modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = student.rollNumber,
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                      ),
                      color = DarkRoyalBlue,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }

              if (student.isUnclear) {
                Row(
                  modifier = Modifier.weight(1f, fill = false),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = student.reviewReason ?: "Incomplete data detected",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.SemiBold,
                    ),
                    color = StatusWarning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              } else if (student.rollNumber.isBlank()) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = LightBlueHighlight,
                ) {
                  Text(
                    text = "Extracted Student",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                    ),
                    color = RoyalBlue,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                  )
                }
              }
            }

            Text(
              text = "Student Name",
              style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
              ),
              color = TextSecondary,
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Box(
            modifier = Modifier
              .size(48.dp)
              .clickable(
                role = Role.Button,
                onClick = onDelete,
              ),
            contentAlignment = Alignment.TopEnd,
          ) {
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete student",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }
        CompactReviewTextField(
          value = student.name,
          onValueChange = { onUpdate(student.copy(name = it)) },
          placeholder = "Student Name",
          leadingIcon = Icons.Default.Person,
          isError = student.name.isBlank(),
          singleLine = false,
          maxLines = 2,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = "Father's Name",
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = TextSecondary,
        )
        CompactReviewTextField(
          value = student.fatherName,
          onValueChange = { onUpdate(student.copy(fatherName = it)) },
          placeholder = "Father Name",
          leadingIcon = Icons.Default.SupervisorAccount,
          isError = student.fatherName.isBlank(),
          singleLine = false,
          maxLines = 2,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = "Roll Number",
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = TextSecondary,
        )
        CompactReviewTextField(
          value = student.rollNumber,
          onValueChange = { onUpdate(student.copy(rollNumber = it)) },
          placeholder = "Roll Number",
          leadingIcon = Icons.Default.Badge,
          isError = student.rollNumber.isBlank(),
          singleLine = true,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CompactReviewTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  leadingIcon: ImageVector,
  isError: Boolean,
  singleLine: Boolean = false,
  maxLines: Int = 2,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val shape = RoundedCornerShape(10.dp)

  val borderColor = when {
    isError -> StatusWarning
    isFocused -> RoyalBlue
    else -> BorderGray
  }
  val borderWidth = if (isFocused || isError) 1.5.dp else 1.dp
  val iconTint = when {
    isError -> StatusWarning
    isFocused || value.isNotBlank() -> RoyalBlue
    else -> TextSecondary
  }

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = singleLine,
    maxLines = maxLines,
    interactionSource = interactionSource,
    cursorBrush = SolidColor(RoyalBlue),
    textStyle = TextStyle(
      fontSize = 14.sp,
      lineHeight = 18.sp,
      fontWeight = FontWeight.Medium,
      color = TextPrimary,
    ),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 40.dp),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(Color.White)
          .border(borderWidth, borderColor, shape)
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
      }
    },
  )
}
