package com.example.ui.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exam
import com.example.data.model.ExamType
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
  var examName by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(ExamType.MID_TERM) }
  var isTypeDropdownExpanded by remember { mutableStateOf(false) }

  val defaultDate = remember {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
  }
  var examDate by remember { mutableStateOf(defaultDate) }

  val uiState = viewModel.uiState.value

  AlertDialog(
    onDismissRequest = onDismissRequest,
    title = {
      Text(
        text = "Create Exam",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
      ) {
        if (uiState.createErrorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .testTag("create_exam_error_card"),
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.createErrorMessage,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }

        // Exam Name Field
        OutlinedTextField(
          value = examName,
          onValueChange = { examName = it },
          label = { Text("Exam Name") },
          placeholder = { Text("e.g. Mathematics Mid Term") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Description,
              contentDescription = "Exam Name Icon",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_exam_name_input"),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Exam Type Dropdown
        ExposedDropdownMenuBox(
          expanded = isTypeDropdownExpanded,
          onExpandedChange = { isTypeDropdownExpanded = !isTypeDropdownExpanded },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = selectedType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Exam Type") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeDropdownExpanded)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth()
              .testTag("create_exam_type_dropdown"),
          )

          ExposedDropdownMenu(
            expanded = isTypeDropdownExpanded,
            onDismissRequest = { isTypeDropdownExpanded = false },
          ) {
            ExamType.entries.forEach { type ->
              DropdownMenuItem(
                text = { Text(type.displayName) },
                onClick = {
                  selectedType = type
                  isTypeDropdownExpanded = false
                },
                modifier = Modifier.testTag("exam_type_option_${type.name}"),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Exam Date Field
        OutlinedTextField(
          value = examDate,
          onValueChange = { examDate = it },
          label = { Text("Exam Date (YYYY-MM-DD)") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = "Date Icon",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_exam_date_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          viewModel.createExam(
            name = examName,
            type = selectedType,
            date = examDate,
            onSuccess = onExamCreated,
          )
        },
        enabled = !uiState.isCreating,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag("save_exam_button"),
      ) {
        if (uiState.isCreating) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Text("Save Exam")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismissRequest,
        enabled = !uiState.isCreating,
        modifier = Modifier.testTag("cancel_create_exam_button"),
      ) {
        Text("Cancel")
      }
    },
    shape = RoundedCornerShape(20.dp),
    modifier = modifier.testTag("create_exam_dialog"),
  )
}
