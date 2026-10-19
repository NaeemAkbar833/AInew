package com.example.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.TextSecondary

sealed class BottomNavItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  data object Home : BottomNavItem(
    route = Screen.Home.route,
    label = "Home",
    selectedIcon = Icons.Filled.Home,
    unselectedIcon = Icons.Outlined.Home,
    testTag = "bottom_nav_home",
  )

  data object Exams : BottomNavItem(
    route = Screen.Exams.route,
    label = "Exams",
    selectedIcon = Icons.Filled.Description,
    unselectedIcon = Icons.Outlined.Description,
    testTag = "bottom_nav_exams",
  )

  data object Classes : BottomNavItem(
    route = Screen.Classes.route,
    label = "Classes",
    selectedIcon = Icons.Filled.Book,
    unselectedIcon = Icons.Outlined.Book,
    testTag = "bottom_nav_classes",
  )

  data object Reports : BottomNavItem(
    route = Screen.Results.route,
    label = "Reports",
    selectedIcon = Icons.Filled.Assessment,
    unselectedIcon = Icons.Outlined.Assessment,
    testTag = "bottom_nav_reports",
  )
}

val bottomNavItems = listOf(
  BottomNavItem.Home,
  BottomNavItem.Exams,
  BottomNavItem.Classes,
  BottomNavItem.Reports,
)

/**
 * Stitch Material 3 Bottom Navigation Bar for GradeScan with 4 items: Home, Exams, Classes, Reports.
 * Pure white surface (#FFFFFF), #D1D9EC top border, #DDE5FF pill indicator, and #1F3FBF active accent.
 */
@Composable
fun GradeScanBottomNavigationBar(
  currentRoute: String?,
  onNavigateToDestination: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White,
    shadowElevation = 8.dp,
    tonalElevation = 0.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("gradescan_bottom_nav_bar"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(color = BorderGray, thickness = 1.dp)
      NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
      ) {
        bottomNavItems.forEach { item ->
          val selected = currentRoute == item.route
          NavigationBarItem(
            modifier = Modifier.testTag(item.testTag),
            selected = selected,
            onClick = { onNavigateToDestination(item.route) },
            icon = {
              Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                modifier = Modifier.size(22.dp),
              )
            },
            label = {
              Text(
                text = item.label,
                fontFamily = FigtreeFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = DarkRoyalBlue,
              selectedTextColor = DarkRoyalBlue,
              indicatorColor = LightBlueHighlight,
              unselectedIconColor = TextSecondary,
              unselectedTextColor = TextSecondary,
            ),
          )
        }
      }
    }
  }
}
