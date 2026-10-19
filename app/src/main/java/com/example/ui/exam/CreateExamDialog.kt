package com.example.ui.exam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exam
import com.example.data.model.ExamType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExamDialog(
  onDismissRequest: () -> Unit,
  onExamCreated: (Exam) -> Unit,
  viewModel: ExamViewModel,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.78f).dp

  var examName by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(ExamType.MID_TERM) }

  val defaultDate = remember {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
  }
  var examDate by remember { mutableStateOf(defaultDate) }

  val uiState = viewModel.uiState.value
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  val saveButtonBrush = Brush.horizontalGradient(
    colors = listOf(DarkRoyalBlue, RoyalBlue)
  )

  fun safeDismiss() {
    if (!uiState.isCreating) {
      coroutineScope.launch {
        sheetState.hide()
        onDismissRequest()
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = { if (!uiState.isCreating) onDismissRequest() },
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
    modifier = modifier.testTag("create_exam_dialog"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .navigationBarsPadding()
        .imePadding(),
    ) {
      // Header
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
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = "Create Exam",
              style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
              ),
              color = TextPrimary,
            )
            Text(
              text = "Set up details for your upcoming exam.",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
              color = TextSecondary,
            )
          }
        }
        IconButton(
          onClick = { safeDismiss() },
          enabled = !uiState.isCreating,
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

      // Scrollable Form Content
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (uiState.createErrorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = StatusWarningContainer),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = StatusWarning,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.createErrorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = StatusWarning,
                ),
              )
            }
          }
        }

        // Exam Name
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Exam Name",
            style = MaterialTheme.typography.labelMedium.copy(
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
            ),
            color = TextPrimary,
          )
          OutlinedTextField(
            value = examName,
            onValueChange = { examName = it },
            placeholder = {
              Text(
                text = "e.g. Midterm Examination 2026",
                fontSize = 14.sp,
                color = TextSecondary,
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = RoyalBlue,
              unfocusedBorderColor = BorderGray,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              cursorColor = RoyalBlue,
              focusedContainerColor = Color.White,
              unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        // Exam Type Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Category,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Exam Type",
              style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = TextPrimary,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ExamType.entries.forEach { type ->
              val isSelected = selectedType == type
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) LightBlueHighlight else Color.White,
                border = BorderStroke(
                  width = 1.dp,
                  color = if (isSelected) RoyalBlue else BorderGray,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(enabled = !uiState.isCreating) { selectedType = type },
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = DarkRoyalBlue,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(Modifier.width(4.dp))
                    }
                    Text(
                      text = type.displayName,
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      ),
                      color = if (isSelected) DarkRoyalBlue else TextPrimary,
                    )
                  }
                }
              }
            }
          }
        }

        // Exam Date
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Exam Date",
            style = MaterialTheme.typography.labelMedium.copy(
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
            ),
            color = TextPrimary,
          )
          OutlinedTextField(
            value = examDate,
            onValueChange = { examDate = it },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = RoyalBlue,
              unfocusedBorderColor = BorderGray,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              cursorColor = RoyalBlue,
              focusedContainerColor = Color.White,
              unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      HorizontalDivider(color = BorderGray, thickness = 1.dp)

      // Pinned Bottom Action Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(50),
          color = Color.White,
          border = BorderStroke(1.dp, BorderGray),
          modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !uiState.isCreating) { safeDismiss() },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "Cancel",
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
              ),
            )
          }
        }
        Button(
          onClick = {
            viewModel.createExam(examName, selectedType, examDate) {
              coroutineScope.launch {
                sheetState.hide()
                onExamCreated(it)
              }
            }
          },
          enabled = !uiState.isCreating,
          shape = RoundedCornerShape(50),
          modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(saveButtonBrush),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
          ),
        ) {
          if (uiState.isCreating) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp),
            )
          } else {
            Text(
              text = "Save Exam",
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
              ),
            )
          }
        }
      }
    }
  }
}
