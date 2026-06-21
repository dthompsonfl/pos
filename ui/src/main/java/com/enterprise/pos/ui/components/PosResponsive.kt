package com.enterprise.pos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// AdaptiveLayout — Switches between single-pane and two-pane based on window size
// ============================================================================
@Composable
fun AdaptiveLayout(
    singlePane: @Composable () -> Unit,
    twoPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    twoPaneBreakpoint: androidx.compose.ui.unit.Dp = 600.dp
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Box(modifier = modifier.fillMaxSize()) {
        if (screenWidth >= twoPaneBreakpoint) {
            twoPane()
        } else {
            singlePane()
        }
    }
}

// ============================================================================
// ResponsiveGrid — Grid that adjusts columns based on available width
// ============================================================================
@Composable
fun ResponsiveGrid(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    minColumnWidth: androidx.compose.ui.unit.Dp = 180.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp)
) {
    val configuration = LocalConfiguration.current
    val containerWidth = configuration.screenWidthDp.dp
    val columns = (containerWidth / minColumnWidth).toInt().coerceAtLeast(1)

    val rows = items.chunked(columns)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = verticalArrangement
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = horizontalArrangement
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        item()
                    }
                }
                // Fill remaining slots if row is incomplete
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ============================================================================
// NavigationRailLayout — Layout with navigation rail for large screens
// ============================================================================
@Composable
fun NavigationRailLayout(
    destinations: List<NavDestination>,
    selectedDestination: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.semantics { contentDescription = "Navigation rail" },
            header = header
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedDestination
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = selected,
                    onClick = { onDestinationSelected(destination.route) },
                    alwaysShowLabel = true
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

// ============================================================================
// BottomNavLayout — Layout with bottom navigation for small screens
// ============================================================================
@Composable
fun BottomNavLayout(
    destinations: List<NavDestination>,
    selectedDestination: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.semantics { contentDescription = "Bottom navigation" }
            ) {
                destinations.forEach { destination ->
                    val selected = destination.route == selectedDestination
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = selected,
                        onClick = { onDestinationSelected(destination.route) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            content()
        }
    }
}

// ============================================================================
// NavDestination data class
// ============================================================================
data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BottomNavLayoutPreview() {
    PosTheme {
        Surface {
            val destinations = listOf(
                NavDestination("home", "Home", Icons.Default.Home, Icons.Default.Home),
                NavDestination("orders", "Orders", Icons.Default.Receipt, Icons.Default.Receipt),
                NavDestination("settings", "Settings", Icons.Default.Settings, Icons.Default.Settings)
            )
            BottomNavLayout(
                destinations = destinations,
                selectedDestination = "home",
                onDestinationSelected = {}
            ) {
                Text(
                    text = "Screen content",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 640)
@Composable
private fun NavigationRailLayoutPreview() {
    PosTheme {
        Surface {
            val destinations = listOf(
                NavDestination("home", "Home", Icons.Default.Home, Icons.Default.Home),
                NavDestination("orders", "Orders", Icons.Default.Receipt, Icons.Default.Receipt),
                NavDestination("settings", "Settings", Icons.Default.Settings, Icons.Default.Settings)
            )
            NavigationRailLayout(
                destinations = destinations,
                selectedDestination = "home",
                onDestinationSelected = {}
            ) {
                Text(
                    text = "Screen content",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResponsiveGridPreview() {
    PosTheme {
        Surface {
            ResponsiveGrid(
                items = List(6) { index ->
                    { StatCard(label = "Stat $index", value = "${index * 100}") }
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
