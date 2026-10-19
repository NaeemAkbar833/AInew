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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.DisabledBackground
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentDialog(
  classId: String,
  onDismissRequest: () -> Unit,
  onStudentAdded: (Student) -> Unit,
  viewModel: StudentViewModel,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.78f).dp

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()

  var name by remember { mutableStateOf("") }
  var fatherName by remember { mutableStateOf("") }
  var rollNumber by remember { mutableStateOf("") }

  val saveButtonBrush = Brush.horizontalGradient(
    colors = listOf(DarkRoyalBlue, RoyalBlue)
  )

  LaunchedEffect(uiState.addErrorMessage) {
    if (uiState.addErrorMessage != null) {
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      if (!uiState.isAdding) onDismissRequest()
    },
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
    modifier = modifier.testTag("add_student_dialog"),
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
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = "Add Student",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = "Enter the student's details",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
              color = TextSecondary,
            )
          }
        }

        IconButton(
          onClick = {
            if (!uiState.isAdding) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
          },
          enabled = !uiState.isAdding,
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

      // Content-sized scrollable form area
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(scrollState)
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // 1. Student Name (supports 2-line wrapping for full names)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          StudentFieldLabel(text = "Student Name")
          CompactStudentTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "e.g. Muhammad Imtiaz Ahmed",
            leadingIcon = Icons.Default.Person,
            enabled = !uiState.isAdding,
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.testTag("student_name_input"),
          )
        }

        // 2. Father's Name (supports 2-line wrapping for full names)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          StudentFieldLabel(text = "Father's Name")
          CompactStudentTextField(
            value = fatherName,
            onValueChange = { fatherName = it },
            placeholder = "e.g. Abdul Rehman Khan",
            leadingIcon = Icons.Default.SupervisorAccount,
            enabled = !uiState.isAdding,
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.testTag("student_father_name_input"),
          )
        }

        // 3. Roll Number
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          StudentFieldLabel(text = "Roll Number")
          CompactStudentTextField(
            value = rollNumber,
            onValueChange = { rollNumber = it },
            placeholder = "e.g. CS-2023-01",
            leadingIcon = Icons.Default.Badge,
            enabled = !uiState.isAdding,
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.testTag("student_roll_number_input"),
          )
        }

        // Error message card if any
        val errorMessage = uiState.addErrorMessage
        if (errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = StatusWarningContainer),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_student_error_card"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = StatusWarning,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = errorMessage,
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = StatusWarning,
              )
            }
          }
        }
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      // Pinned Bottom Action Row: Cancel and Add Student
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.White,
          border = BorderStroke(1.dp, BorderGray),
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !uiState.isAdding) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
            .testTag("cancel_add_student_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "Cancel",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
            )
          }
        }

        Button(
          onClick = {
            viewModel.addStudent(
              classId = classId,
              name = name,
              fatherName = fatherName,
              rollNumber = rollNumber,
              onSuccess = { addedStudent ->
                coroutineScope.launch {
                  sheetState.hide()
                  onStudentAdded(addedStudent)
                }
              },
            )
          },
          enabled = !uiState.isAdding,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
          ),
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
              brush = if (!uiState.isAdding) {
                saveButtonBrush
              } else {
                Brush.horizontalGradient(listOf(DisabledBackground, DisabledBackground))
              }
            )
            .testTag("save_student_button"),
        ) {
          if (uiState.isAdding) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp),
            )
          } else {
            Text(
              text = "Add Student",
              fontFamily = FigtreeFontFamily,
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

@Composable
private fun StudentFieldLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    fontFamily = FigtreeFontFamily,
    fontSize = 12.5.sp,
    fontWeight = FontWeight.SemiBold,
    color = TextPrimary,
    modifier = modifier,
  )
}

@Composable
private fun CompactStudentTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  leadingIcon: ImageVector,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  singleLine: Boolean = true,
  maxLines: Int = 1,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val shape = RoundedCornerShape(10.dp)
  val borderColor = if (isFocused) RoyalBlue else BorderGray
  val containerColor = if (isFocused) Color.White else SoftBlueTint
  val iconTint = if (isFocused || value.isNotBlank()) RoyalBlue else TextSecondary

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = singleLine,
    maxLines = maxLines,
    interactionSource = interactionSource,
    textStyle = TextStyle(
      fontFamily = FigtreeFontFamily,
      fontSize = 13.5.sp,
      lineHeight = 18.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    cursorBrush = SolidColor(RoyalBlue),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(containerColor)
          .border(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = borderColor,
            shape = shape,
          )
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
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
