package com.example.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Exam
import com.example.ui.exam.ExamViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.SecondaryTealContainer
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TertiaryAmberContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// Type-specific palette tokens matching approved Exams and Classes screens
private val MidTermTint = Color(0xFFDDE5FF)
private val MidTermText = Color(0xFF0A1D66)
private val FinalTint = Color(0xFFEEDFFF)
private val FinalText = Color(0xFF4A1F8F)

private val ResultsExamFilterOptions = listOf("All", "Mid Term", "Final", "Supply")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
  viewModel: ExamViewModel,
  onNavigateBack: () -> Unit,
  onExamClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedFilter by rememberSaveable { mutableStateOf("All") }

  LaunchedEffect(Unit) {
    viewModel.loadExams()
  }

  LaunchedEffect(uiState.feedbackMessage) {
    uiState.feedbackMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.dismissFeedbackMessage()
    }
  }

  val filteredExams = remember(uiState.exams, searchQuery, selectedFilter) {
    val trimmedQuery = searchQuery.trim()
    uiState.exams.filter { exam ->
      val matchesFilter = selectedFilter == "All" ||
        exam.type.equals(selectedFilter, ignoreCase = true)
      val matchesQuery = trimmedQuery.isEmpty() ||
        exam.name.contains(trimmedQuery, ignoreCase = true) ||
        exam.type.contains(trimmedQuery, ignoreCase = true) ||
        exam.date.contains(trimmedQuery, ignoreCase = true)
      matchesFilter && matchesQuery
    }
  }

  val topHeaderBlendGradient = remember {
    Brush.verticalGradient(
      colorStops = arrayOf(
        0.00f to DarkRoyalBlue,
        0.36f to PrimaryBlue,
        0.58f to Color(0xFF3358DF),
        0.76f to Color(0xFF6E8CEF),
        0.90f to Color(0xFFC2D3FA),
        1.00f to BackgroundLight,
      )
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("results_screen"),
  ) {
    // Six-stop backdrop gradient layer extending behind topBar down into search and filter area
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(HeaderBlueGradient)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)
            .testTag("top_app_bar"),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clickable(
                    role = Role.Button,
                    onClick = onNavigateBack,
                  )
                  .testTag("results_back_button"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(
                      width = 1.dp,
                      color = Color.White.copy(alpha = 0.30f),
                      shape = CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = "Results",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                  color = Color.White,
                  letterSpacing = (-0.2).sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "View approved student results and class reports",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.5.sp,
                  color = Color.White.copy(alpha = 0.92f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clickable(
                    role = Role.Button,
                    onClick = viewModel::loadExams,
                  )
                  .testTag("refresh_results_button"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(
                      width = 1.dp,
                      color = Color.White.copy(alpha = 0.30f),
                      shape = CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
      ) {
        when {
          uiState.isLoading && uiState.exams.isEmpty() -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("results_loading_indicator"),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                CircularProgressIndicator(
                  color = RoyalBlue,
                  strokeWidth = 3.dp,
                  modifier = Modifier.size(34.dp),
                )
                Text(
                  text = "Loading exams for results…",
                  fontFamily = FigtreeFontFamily,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextSecondary,
                )
              }
            }
          }

          uiState.errorMessage != null && uiState.exams.isEmpty() -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("results_error_state"),
              contentAlignment = Alignment.Center,
            ) {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StatusWarningContainer,
                    border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.18f)),
                    modifier = Modifier.size(48.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier.size(22.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = "Unable to load exams",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = uiState.errorMessage ?: "",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(18.dp))
                  Button(
                    onClick = viewModel::loadExams,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = RoyalBlue,
                      contentColor = Color.White,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("results_retry_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Retry",
                      fontFamily = FigtreeFontFamily,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }

          uiState.exams.isEmpty() -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .testTag("results_empty_state"),
              contentAlignment = Alignment.Center,
            ) {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidTermTint,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.16f)),
                    modifier = Modifier.size(52.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = "No Exams",
                        tint = RoyalBlue,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = "No exams found",
                    fontFamily = BricolageGrotesqueFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Create an exam and approve student evaluations to view results and class reports.",
                    fontFamily = FigtreeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          else -> {
            LazyColumn(
              contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxSize()
                .testTag("results_exams_list"),
            ) {
              item(key = "results_search_and_filters") {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  ResultsSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClearQuery = { searchQuery = "" },
                  )

                  ResultsFilterChipRow(
                    exams = uiState.exams,
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it },
                  )

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Select an exam to view approved results:",
                      fontFamily = BricolageGrotesqueFontFamily,
                      fontSize = 14.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = SoftBlueTint,
                      border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.14f)),
                    ) {
                      Text(
                        text = "${filteredExams.size} ${if (filteredExams.size == 1) "exam" else "exams"}",
                        fontFamily = FigtreeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                  }
                }
              }

              if (filteredExams.isEmpty()) {
                item(key = "results_filtered_empty_state") {
                  FilteredResultsEmptyCard(
                    searchQuery = searchQuery,
                    selectedFilter = selectedFilter,
                    onResetFilters = {
                      searchQuery = ""
                      selectedFilter = "All"
                    },
                  )
                }
              } else {
                items(
                  items = filteredExams,
                  key = { it.id ?: "${it.name}_${it.date}" },
                ) { exam ->
                  ResultExamCard(
                    exam = exam,
                    onClick = { exam.id?.let { onExamClick(it) } },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ResultsSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val shape = RoundedCornerShape(14.dp)
  val borderColor = if (isFocused) PrimaryBlue else BorderGray
  val iconTint = if (isFocused || query.isNotBlank()) PrimaryBlue else TextSecondary

  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    interactionSource = interactionSource,
    textStyle = TextStyle(
      fontFamily = FigtreeFontFamily,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Normal,
      color = TextPrimary,
    ),
    cursorBrush = SolidColor(PrimaryBlue),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 46.dp)
      .testTag("results_search_input"),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(shape)
          .background(Color.White)
          .border(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = borderColor,
            shape = shape,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search exams for results",
          tint = iconTint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (query.isEmpty()) {
            Text(
              text = "Search exams by name, type, or date…",
              fontFamily = FigtreeFontFamily,
              fontSize = 13.sp,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
        if (query.isNotEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(SoftBlueTint)
              .clickable(
                role = Role.Button,
                onClick = onClearQuery,
              )
              .testTag("results_search_clear_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search",
              tint = TextSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    },
  )
}

@Composable
private fun ResultsFilterChipRow(
  exams: List<Exam>,
  selectedFilter: String,
  onSelectFilter: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ResultsExamFilterOptions.forEach { filter ->
      val isSelected = selectedFilter.equals(filter, ignoreCase = true)
      val count = if (filter == "All") {
        exams.size
      } else {
        exams.count { it.type.equals(filter, ignoreCase = true) }
      }

      val (selectedBg, selectedFg, selectedBorder) = when (filter.lowercase()) {
        "mid term" -> Triple(MidTermTint, MidTermText, MidTermText.copy(alpha = 0.40f))
        "final" -> Triple(FinalTint, FinalText, FinalText.copy(alpha = 0.40f))
        "supply" -> Triple(TertiaryAmberContainer, StatusAmber, StatusAmber.copy(alpha = 0.40f))
        else -> Triple(SoftBlueTint, PrimaryBlue, PrimaryBlue.copy(alpha = 0.40f))
      }

      val chipBg = if (isSelected) selectedBg else Color(0xFFEEF2F9)
      val chipFg = if (isSelected) selectedFg else TextSecondary
      val chipBorder = if (isSelected) selectedBorder else BorderGray
      val tagSuffix = filter.lowercase().replace(" ", "_")

      Surface(
        shape = CircleShape,
        color = chipBg,
        border = BorderStroke(
          width = if (isSelected) 1.5.dp else 1.dp,
          color = chipBorder,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 36.dp)
          .clip(CircleShape)
          .clickable(
            role = Role.Button,
            onClick = { onSelectFilter(filter) },
          )
          .testTag("results_filter_$tagSuffix"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = filter,
            fontFamily = FigtreeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = chipFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.width(3.dp))
          Surface(
            shape = CircleShape,
            color = if (isSelected) {
              selectedFg.copy(alpha = 0.15f)
            } else {
              Color.White
            },
          ) {
            Text(
              text = count.toString(),
              fontFamily = FigtreeFontFamily,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) selectedFg else TextSecondary,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FilteredResultsEmptyCard(
  searchQuery: String,
  selectedFilter: String,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = Color.White,
    border = BorderStroke(1.dp, BorderGray),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("results_filtered_empty_state"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = SoftBlueTint,
        border = BorderStroke(1.dp, LightBlueHighlight),
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "No matching exams",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = if (searchQuery.isNotBlank()) {
          "No exams match \"$searchQuery\" in $selectedFilter."
        } else {
          "No $selectedFilter exams scheduled yet."
        },
        fontFamily = FigtreeFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      TextButton(onClick = onResetFilters) {
        Text(
          text = "Reset filters",
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = PrimaryBlue,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultExamCard(
  exam: Exam,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (badgeBg, badgeFg, iconBg, iconTint) = when (exam.type.lowercase()) {
    "mid term" -> listOf(MidTermTint, MidTermText, MidTermTint, PrimaryBlue)
    "final" -> listOf(FinalTint, FinalText, FinalTint, FinalText)
    "supply" -> listOf(TertiaryAmberContainer, StatusAmber, TertiaryAmberContainer, StatusAmber)
    else -> listOf(SecondaryTealContainer, SecondaryTeal, SecondaryTealContainer, SecondaryTeal)
  }

  val shape = RoundedCornerShape(18.dp)
  Card(
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(
        role = Role.Button,
        onClick = onClick,
      )
      .testTag("result_exam_card_${exam.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = iconBg,
        border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.14f)),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Assessment,
            contentDescription = "Results for Exam",
            tint = iconTint,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = exam.name,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(5.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeBg,
            border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.12f)),
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Text(
              text = exam.type,
              fontFamily = FigtreeFontFamily,
              color = badgeFg,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            )
          }

          if (exam.date.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = BackgroundLight,
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.CalendarToday,
                  contentDescription = "Date",
                  tint = TextSecondary,
                  modifier = Modifier.size(11.dp),
                )
                Text(
                  text = exam.date,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 11.sp,
                  color = TextSecondary,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = SoftBlueTint,
        modifier = Modifier.size(30.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Results",
            tint = PrimaryBlue,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

