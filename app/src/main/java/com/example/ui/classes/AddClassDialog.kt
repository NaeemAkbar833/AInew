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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.data.repository.deleteAppOwnedTempCameraFile
import com.example.data.repository.readHighFidelityImageBytesInternal
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  val maxSheetHeight = (configuration.screenHeightDp * 0.80f).dp

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

  DisposableEffect(Unit) {
    onDispose {
      val leftoverUri = tempCameraUri
      tempCameraUri = null
      if (leftoverUri != null) {
        deleteAppOwnedTempCameraFile(context, leftoverUri)
      }
    }
  }

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
      coroutineScope.launch {
        val bytes = withContext(Dispatchers.IO) {
          try {
            readHighFidelityImageBytesInternal(context, uri)
              ?: context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
              }
          } catch (e: Exception) {
            null
          }
        }
        if (bytes != null) {
          selectedImageBytes = bytes
          selectedFileName = "question_paper_gallery.jpg"
        }
      }
    }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success: Boolean ->
    val capturedUri = tempCameraUri
    tempCameraUri = null
    if (capturedUri != null) {
      coroutineScope.launch {
        val bytes = withContext(Dispatchers.IO) {
          try {
            if (success) {
              readHighFidelityImageBytesInternal(context, capturedUri)
                ?: context.contentResolver.openInputStream(capturedUri)?.use { stream ->
                  stream.readBytes()
                }
            } else {
              null
            }
          } catch (e: Exception) {
            null
          } finally {
            deleteAppOwnedTempCameraFile(context, capturedUri)
          }
        }
        if (bytes != null) {
          selectedImageBytes = bytes
          selectedFileName = "question_paper_camera.jpg"
        }
      }
    }
  }

  fun launchCamera() {
    try {
      val previousUri = tempCameraUri
      if (previousUri != null) {
        tempCameraUri = null
        coroutineScope.launch(Dispatchers.IO) {
          deleteAppOwnedTempCameraFile(context, previousUri)
        }
      }
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
    modifier = modifier.testTag("add_class_dialog"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .navigationBarsPadding()
        .imePadding(),
    ) {
      // Compact Header
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
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = "Configure class details and question paper.",
              fontFamily = FigtreeFontFamily,
              fontSize = 12.sp,
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

      // Scrollable Form Content
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(scrollState)
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // 1. Class Session
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          FieldLabel(text = "Class Session")
          CompactFormTextField(
            value = session,
            onValueChange = { session = it },
            placeholder = "e.g. 2022-2026",
            leadingIcon = Icons.Default.CalendarMonth,
            enabled = !uiState.isSaving,
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.testTag("add_class_session_input"),
          )
        }

        // 2. Semester
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          FieldLabel(text = "Semester")
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
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) LightBlueHighlight else SoftBlueTint,
                    border = BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) RoyalBlue else BorderGray,
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .height(36.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .clickable(enabled = !uiState.isSaving) { semester = option }
                      .testTag("semester_option_$option"),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = option,
                        fontFamily = FigtreeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          FieldLabel(text = "Timing")
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_class_timing_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ClassTiming.entries.forEach { timing ->
              val isSelected = selectedTiming == timing
              val timingIcon = if (timing == ClassTiming.MORNING) {
                Icons.Default.WbSunny
              } else {
                Icons.Default.WbTwilight
              }
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) LightBlueHighlight else SoftBlueTint,
                border = BorderStroke(
                  width = 1.dp,
                  color = if (isSelected) RoyalBlue else BorderGray,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .clickable(enabled = !uiState.isSaving) { selectedTiming = timing }
                  .testTag("timing_option_${timing.name}"),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = timingIcon,
                    contentDescription = null,
                    tint = if (isSelected) DarkRoyalBlue else TextSecondary,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = timing.displayName,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) DarkRoyalBlue else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = DarkRoyalBlue,
                      modifier = Modifier.size(13.dp),
                    )
                  }
                }
              }
            }
          }
        }

        // 4. Subject Name (supports 2-line wrapping for long course names)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          FieldLabel(text = "Subject Name")
          CompactFormTextField(
            value = subjectName,
            onValueChange = { subjectName = it },
            placeholder = "e.g. Data Structures & Algorithms",
            leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
            enabled = !uiState.isSaving,
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.testTag("add_class_subject_input"),
          )
        }

        // 5. Total Marks
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          FieldLabel(text = "Total Marks")
          CompactFormTextField(
            value = totalMarksText,
            onValueChange = { totalMarksText = it },
            placeholder = "e.g. 100",
            leadingIcon = Icons.Default.Grade,
            keyboardType = KeyboardType.Number,
            enabled = !uiState.isSaving,
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.testTag("add_class_marks_input"),
          )
        }

        // 6. Optional Question Paper Attachment
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FieldLabel(text = "Question Paper (Optional)")
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = SoftBlueTint,
              border = BorderStroke(1.dp, BorderGray),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
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
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary,
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = SoftBlueTint,
              border = BorderStroke(1.dp, BorderGray),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
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
                  fontFamily = FigtreeFontFamily,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold,
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
                  .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
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
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = selectedFileName!!,
                    fontFamily = FigtreeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkRoyalBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = {
                    selectedImageBytes = null
                    selectedFileName = null
                  },
                  enabled = !uiState.isSaving,
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("add_class_remove_attachment_button"),
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color.White)
                      .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Remove attachment",
                      tint = TextSecondary,
                      modifier = Modifier.size(14.dp),
                    )
                  }
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

      // Pinned Bottom Action Row
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
              fontFamily = FigtreeFontFamily,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
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
private fun FieldLabel(
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
private fun CompactFormTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  leadingIcon: ImageVector,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
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
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
