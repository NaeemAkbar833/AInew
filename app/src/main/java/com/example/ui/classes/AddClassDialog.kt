package com.example.ui.classes

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import java.io.File

@Composable
fun AddClassDialog(
  examId: String,
  onDismissRequest: () -> Unit,
  onClassCreated: (ClassRoom) -> Unit,
  viewModel: ClassViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uiState = viewModel.uiState.value
  val scrollState = rememberScrollState()

  var session by remember { mutableStateOf("") }
  var semester by remember { mutableStateOf("") }
  var selectedTiming by remember { mutableStateOf(ClassTiming.MORNING) }
  var subjectName by remember { mutableStateOf("") }
  var totalMarksText by remember { mutableStateOf("100") }

  var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
  var selectedFileName by remember { mutableStateOf<String?>(null) }
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  // Gallery Picker
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          selectedImageBytes = stream.readBytes()
          selectedFileName = "question_paper_gallery.jpg"
        }
      } catch (e: Exception) {
        // Handle read error gracefully
      }
    }
  }

  // Camera Launcher
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success: Boolean ->
    if (success && tempCameraUri != null) {
      try {
        context.contentResolver.openInputStream(tempCameraUri!!)?.use { stream ->
          selectedImageBytes = stream.readBytes()
          selectedFileName = "question_paper_camera.jpg"
        }
      } catch (e: Exception) {
        // Handle read error gracefully
      }
    }
  }

  fun launchCamera() {
    try {
      val tempFile = File.createTempFile("qp_capture_", ".jpg", context.cacheDir)
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
      )
      tempCameraUri = uri
      cameraLauncher.launch(uri)
    } catch (e: Exception) {
      // Fallback
    }
  }

  AlertDialog(
    onDismissRequest = {
      if (!uiState.isSaving) onDismissRequest()
    },
    title = {
      Text(
        text = "Add Class",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState)
          .padding(vertical = 4.dp),
      ) {
        if (uiState.saveErrorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .testTag("add_class_error_card"),
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
                text = uiState.saveErrorMessage,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }

        // 1. Class Session Field
        OutlinedTextField(
          value = session,
          onValueChange = { session = it },
          label = { Text("Class Session") },
          placeholder = { Text("e.g. 2022-2026") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.CalendarMonth,
              contentDescription = "Session",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("class_session_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Semester Field
        OutlinedTextField(
          value = semester,
          onValueChange = { semester = it },
          label = { Text("Semester") },
          placeholder = { Text("e.g. 4th Semester") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.School,
              contentDescription = "Semester",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("class_semester_input"),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Morning / Evening Timing Selector
        Text(
          text = "Class Timing",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          TimingChoiceChip(
            title = "Morning",
            icon = Icons.Default.WbSunny,
            isSelected = selectedTiming == ClassTiming.MORNING,
            onClick = { selectedTiming = ClassTiming.MORNING },
            modifier = Modifier
              .weight(1f)
              .testTag("timing_morning_chip"),
          )
          TimingChoiceChip(
            title = "Evening",
            icon = Icons.Default.WbTwilight,
            isSelected = selectedTiming == ClassTiming.EVENING,
            onClick = { selectedTiming = ClassTiming.EVENING },
            modifier = Modifier
              .weight(1f)
              .testTag("timing_evening_chip"),
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Subject Name Field
        OutlinedTextField(
          value = subjectName,
          onValueChange = { subjectName = it },
          label = { Text("Subject Name") },
          placeholder = { Text("e.g. Data Structures") },
          leadingIcon = {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.MenuBook,
              contentDescription = "Subject",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("class_subject_input"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Total Marks Field
        OutlinedTextField(
          value = totalMarksText,
          onValueChange = { totalMarksText = it.filter { ch -> ch.isDigit() } },
          label = { Text("Total Marks") },
          placeholder = { Text("e.g. 100") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Grade,
              contentDescription = "Marks",
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("class_total_marks_input"),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Question Paper Upload (Optional)
        Text(
          text = "Question Paper (Optional)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Recommended for future automated paper grading.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedImageBytes != null) {
          // Attached Indicator Card
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Default.AttachFile,
                  contentDescription = "Paper attached",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = selectedFileName ?: "Question Paper Attached",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 1,
                )
              }
              IconButton(
                onClick = {
                  selectedImageBytes = null
                  selectedFileName = null
                },
                modifier = Modifier.size(24.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Clear,
                  contentDescription = "Remove paper",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        } else {
          // Buttons for Camera and Gallery
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { launchCamera() },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("camera_paper_button"),
            ) {
              Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Camera", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
              onClick = {
                galleryLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("gallery_paper_button"),
            ) {
              Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Gallery", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          viewModel.createClass(
            examId = examId,
            session = session,
            semester = semester,
            timing = selectedTiming,
            subjectName = subjectName,
            totalMarksText = totalMarksText,
            questionPaperBytes = selectedImageBytes,
            fileExtension = "jpg",
            onSuccess = onClassCreated,
          )
        },
        enabled = !uiState.isSaving,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag("save_class_button"),
      ) {
        if (uiState.isSaving) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Text("Save Class")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismissRequest,
        enabled = !uiState.isSaving,
        modifier = Modifier.testTag("cancel_add_class_button"),
      ) {
        Text("Cancel")
      }
    },
    shape = RoundedCornerShape(20.dp),
    modifier = modifier.testTag("add_class_dialog"),
  )
}

@Composable
private fun TimingChoiceChip(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val backgroundColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surface
  }
  val borderColor = if (isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outlineVariant
  }
  val contentColor = if (isSelected) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(backgroundColor)
      .border(1.dp, borderColor, RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp, horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = contentColor,
      )
    }
  }
}
