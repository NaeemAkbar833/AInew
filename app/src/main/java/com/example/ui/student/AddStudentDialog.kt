package com.example.ui.student

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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Student

@Composable
fun AddStudentDialog(
  classId: String,
  onDismissRequest: () -> Unit,
  onStudentAdded: (Student) -> Unit,
  viewModel: StudentViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var name by remember { mutableStateOf("") }
  var fatherName by remember { mutableStateOf("") }
  var rollNumber by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = {
      if (!uiState.isAdding) onDismissRequest()
    },
    title = {
      Text(
        text = "Add Student",
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
        val errorMessage = uiState.addErrorMessage
        if (errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .testTag("add_student_error_card"),
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
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }

        // 1. Student Name
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Student Name") },
          placeholder = { Text("e.g. John Doe") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = "Name",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_name_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Father Name
        OutlinedTextField(
          value = fatherName,
          onValueChange = { fatherName = it },
          label = { Text("Father Name") },
          placeholder = { Text("e.g. Robert Doe") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.SupervisorAccount,
              contentDescription = "Father Name",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_father_name_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Roll Number
        OutlinedTextField(
          value = rollNumber,
          onValueChange = { rollNumber = it },
          label = { Text("Roll Number") },
          placeholder = { Text("e.g. CS-2023-01") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Badge,
              contentDescription = "Roll Number",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_roll_number_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          viewModel.addStudent(
            classId = classId,
            name = name,
            fatherName = fatherName,
            rollNumber = rollNumber,
            onSuccess = onStudentAdded,
          )
        },
        enabled = !uiState.isAdding,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag("save_student_button"),
      ) {
        if (uiState.isAdding) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Text("Save Student")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismissRequest,
        enabled = !uiState.isAdding,
        modifier = Modifier.testTag("cancel_add_student_button"),
      ) {
        Text("Cancel")
      }
    },
    shape = RoundedCornerShape(20.dp),
    modifier = modifier.testTag("add_student_dialog"),
  )
}
