package com.example.ui.classes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
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
import java.io.File

private val SEMESTER_OPTIONS = listOf("1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClassDialog(
  examId: String,
  onDismissRequest: () -> Unit,
  onClassCreated: (ClassRoom) -> Unit,
  viewModel: ClassViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.78f).dp

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()

  var session by remember { mutableStateOf("") }
  var semester by remember { mutableStateOf("") }
  var selectedTiming by remember { mutableStateOf(ClassTiming.MORNING) }
  var subjectName by remember { mutableStateOf("") }
  var totalMarksText by remember { mutableStateOf("100") }

  var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
  var selectedFileName by remember { mutableStateOf<String?>(null) }
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  val saveButtonBrush = Brush.horizontalGradient(
    colors = listOf(DarkRoyalBlue, RoyalBlue)
  )

  LaunchedEffect(uiState.saveErrorMessage) {
    if (uiState.saveErrorMessage != null) {
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }

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

  ModalBottomSheet(
    onDismissRequest = { if (!uiState.isSaving) onDismissRequest() },
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
    modifier = modifier.testTag("add_class_dialog"),
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
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = RoyalBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = "Add Class",
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
              ),
              color = TextPrimary,
            )
            Text(
              text = "Configure class details and question paper.",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
              color = TextSecondary,
            )
          }
        }

        IconButton(
          onClick = {
            if (!uiState.isSaving) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
          },
          enabled = !uiState.isSaving,
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
        // 1. Class Session
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FieldLabel(text = "Class Session")
          CompactFormTextField(
            value = session,
            onValueChange = { session = it },
            placeholder = "e.g. 2022-2026",
            leadingIcon = Icons.Default.CalendarMonth,
            enabled = !uiState.isSaving,
            modifier = Modifier.testTag("add_class_session_input"),
          )
        }

        // 2. Semester
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Layers,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(15.dp),
            )
            FieldLabel(text = "Semester")
          }
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_class_semester_input"),
          ) {
            SEMESTER_OPTIONS.chunked(4).forEach { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                rowItems.forEach { option ->
                  val isSelected = semester == option
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) LightBlueHighlight else Color.White,
                    border = BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) RoyalBlue else BorderGray,
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .height(38.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .clickable(enabled = !uiState.isSaving) { semester = option }
                      .testTag("semester_option_$option"),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = option,
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
        }

        // 3. Timing — Morning / Evening
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(15.dp),
            )
            FieldLabel(text = "Timing")
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_class_timing_selector"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            ClassTiming.entries.forEach { timing ->
              val isSelected = selectedTiming == timing
              val timingIcon = if (timing == ClassTiming.MORNING) {
                Icons.Default.WbSunny
              } else {
                Icons.Default.WbTwilight
              }
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) LightBlueHighlight else Color.White,
                border = BorderStroke(
                  width = 1.dp,
                  color = if (isSelected) RoyalBlue else BorderGray,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(enabled = !uiState.isSaving) { selectedTiming = timing }
                  .testTag("timing_option_${timing.name}"),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = timingIcon,
                    contentDescription = null,
                    tint = if (isSelected) DarkRoyalBlue else TextSecondary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = timing.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontSize = 13.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isSelected) DarkRoyalBlue else TextPrimary,
                    maxLines = 1,
                  )
                  if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = DarkRoyalBlue,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
              }
            }
          }
        }

        // 4. Subject Name
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FieldLabel(text = "Subject Name")
          CompactFormTextField(
            value = subjectName,
            onValueChange = { subjectName = it },
            placeholder = "e.g. Data Structures & Algorithms",
            leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
            enabled = !uiState.isSaving,
            modifier = Modifier.testTag("add_class_subject_input"),
          )
        }

        // 5. Total Marks
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FieldLabel(text = "Total Marks")
          CompactFormTextField(
            value = totalMarksText,
            onValueChange = { totalMarksText = it },
            placeholder = "e.g. 100",
            leadingIcon = Icons.Default.Grade,
            keyboardType = KeyboardType.Number,
            enabled = !uiState.isSaving,
            modifier = Modifier.testTag("add_class_marks_input"),
          )
        }

        // 6. Optional Question Paper Attachment
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FieldLabel(text = "Question Paper (Optional)")
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !uiState.isSaving) { launchCamera() }
                .testTag("add_class_camera_button"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.PhotoCamera,
                  contentDescription = null,
                  tint = RoyalBlue,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Camera",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                  ),
                  color = TextPrimary,
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.White,
              border = BorderStroke(1.dp, BorderGray),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !uiState.isSaving) {
                  galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                }
                .testTag("add_class_gallery_button"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.PhotoLibrary,
                  contentDescription = null,
                  tint = RoyalBlue,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Gallery",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                  ),
                  color = TextPrimary,
                )
              }
            }
          }

          if (selectedFileName != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = LightBlueHighlight,
              border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("add_class_attachment_preview"),
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
                    contentDescription = null,
                    tint = RoyalBlue,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = selectedFileName!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                    ),
                    color = DarkRoyalBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                  onClick = {
                    selectedImageBytes = null
                    selectedFileName = null
                  },
                  enabled = !uiState.isSaving,
                  modifier = Modifier
                    .size(24.dp)
                    .testTag("add_class_remove_attachment_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove attachment",
                    tint = StatusWarning,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }

        // 7. Error message if any
        val saveError = uiState.saveErrorMessage
        if (saveError != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = StatusWarningContainer),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_class_error_card"),
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
                text = saveError,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = StatusWarning,
                ),
              )
            }
          }
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
            .clickable(enabled = !uiState.isSaving) {
              coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
              }
            }
            .testTag("cancel_add_class_button"),
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
            viewModel.createClass(
              examId = examId,
              session = session,
              semester = semester,
              timing = selectedTiming,
              subjectName = subjectName,
              totalMarksText = totalMarksText,
              questionPaperBytes = selectedImageBytes,
              fileExtension = if (selectedImageBytes != null) "jpg" else null,
              onSuccess = { createdClass ->
                coroutineScope.launch {
                  sheetState.hide()
                  onClassCreated(createdClass)
                }
              },
            )
          },
          enabled = !uiState.isSaving,
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
          ),
          modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(
              brush = if (!uiState.isSaving) {
                saveButtonBrush
              } else {
                Brush.horizontalGradient(listOf(DisabledBackground, DisabledBackground))
              }
            )
            .testTag("save_class_button"),
        ) {
          if (uiState.isSaving) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp),
            )
          } else {
            Text(
              text = "Add Class",
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

@Composable
private fun FieldLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium.copy(
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
    ),
    color = TextPrimary,
    modifier = modifier,
  )
}

@Composable
private fun CompactFormTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  leadingIcon: ImageVector,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val borderColor = if (isFocused) RoyalBlue else BorderGray
  val iconTint = if (isFocused) RoyalBlue else TextSecondary

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = true,
    interactionSource = interactionSource,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    textStyle = TextStyle(
      fontSize = 14.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    cursorBrush = SolidColor(RoyalBlue),
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(Color.White)
      .border(
        width = if (isFocused) 1.5.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(12.dp),
      ),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
              ),
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
