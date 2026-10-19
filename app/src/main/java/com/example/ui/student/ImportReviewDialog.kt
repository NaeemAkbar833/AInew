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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtractedStudent
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.DisabledBackground
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
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
          .padding(horizontal = 20.dp)
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = LightBlueHighlight,
            modifier = Modifier.size(38.dp),
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
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = TextPrimary,
            )
            Text(
              text = "Verify and edit extracted information before saving",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(DisabledBackground),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
          )
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
              style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
              ),
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
              .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = LightBlueHighlight,
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = null,
                  modifier = Modifier.size(22.dp),
                  tint = RoyalBlue,
                )
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No students in list",
              style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Try scanning again or add students manually.",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
              color = TextSecondary,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
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
          .padding(horizontal = 20.dp, vertical = 12.dp),
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
            .height(45.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
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
            .height(45.dp),
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
                  fontSize = 14.sp,
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
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (student.isUnclear) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = StatusWarning,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = student.reviewReason ?: "Incomplete data detected",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = StatusWarning,
            )
          }
        } else {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = LightBlueHighlight,
          ) {
            Text(
              text = "Extracted Student",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = RoyalBlue,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        Surface(
          shape = CircleShape,
          color = StatusWarningContainer,
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onDelete),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete student",
              tint = StatusWarning,
              modifier = Modifier.size(15.dp),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Student Name",
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = TextPrimary,
        )
        CompactReviewTextField(
          value = student.name,
          onValueChange = { onUpdate(student.copy(name = it)) },
          placeholder = "Student Name",
          leadingIcon = Icons.Default.Person,
          isError = student.name.isBlank(),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Father's Name",
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = TextPrimary,
        )
        CompactReviewTextField(
          value = student.fatherName,
          onValueChange = { onUpdate(student.copy(fatherName = it)) },
          placeholder = "Father Name",
          leadingIcon = Icons.Default.SupervisorAccount,
          isError = student.fatherName.isBlank(),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Roll Number",
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
          ),
          color = TextPrimary,
        )
        CompactReviewTextField(
          value = student.rollNumber,
          onValueChange = { onUpdate(student.copy(rollNumber = it)) },
          placeholder = "Roll Number",
          leadingIcon = Icons.Default.Badge,
          isError = student.rollNumber.isBlank(),
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
    singleLine = true,
    interactionSource = interactionSource,
    cursorBrush = SolidColor(RoyalBlue),
    textStyle = TextStyle(
      fontSize = 14.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .clip(shape)
          .background(Color.White)
          .border(borderWidth, borderColor, shape)
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
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
