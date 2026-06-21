package com.enterprise.pos.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

private const val DRAWER_WIDTH_DP = 280
private const val RAIL_WIDTH_COLLAPSED_DP = 72
private const val RAIL_WIDTH_EXPANDED_DP = 240
private const val COMPACT_MAX_WIDTH = 600
private const val MEDIUM_MAX_WIDTH = 840
private const val ANIMATION_DURATION_MS = 300

/**
 * Enum class representing the navigation layout type based on window size.
 */
enum class NavigationType {
    BOTTOM_NAVIGATION, NAVIGATION_RAIL, PERMANENT_DRAWER
}

/**
 * Determines the [NavigationType] based on the current window size.
 */
fun WindowSizeClass.toNavigationType(): NavigationType = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> NavigationType.BOTTOM_NAVIGATION
    WindowWidthSizeClass.Medium -> NavigationType.NAVIGATION_RAIL
    WindowWidthSizeClass.Expanded -> NavigationType.PERMANENT_DRAWER
    else -> NavigationType.BOTTOM_NAVIGATION
}

/**
 * Root adaptive navigation layout that switches between bottom navigation, navigation rail,
 * and permanent drawer based on the current window size class.
 *
 * @param windowSizeClass The current window size class.
 * @param navController The navigation controller.
 * @param currentRoute The current navigation route.
 * @param onNavigate Callback invoked when a navigation item is selected.
 * @param onMenuClick Callback invoked when the hamburger menu is clicked (opens drawer).
 * @param badgeCounts Optional map of route to badge count for bottom nav items.
 * @param content The main content area.
 */
@Composable
fun AdaptiveNavigationLayout(
    windowSizeClass: WindowSizeClass,
    navController: NavController,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onMenuClick: () -> Unit = {},
    badgeCounts: Map<String, Int> = emptyMap(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    topBarActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val navigationType = windowSizeClass.toNavigationType()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val isDrawerOpen = drawerState.isOpen

    when (navigationType) {
        NavigationType.BOTTOM_NAVIGATION -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(DRAWER_WIDTH_DP.dp)
                    ) {
                        NavigationDrawerContent(
                            navItems = drawerNavItems,
                            currentRoute = currentRoute,
                            onItemClick = { screen ->
                                scope.launch { drawerState.close() }
                                onNavigate(screen)
                            },
                            onHeaderAction = { scope.launch { drawerState.close() } }
                        )
                    }
                },
                gesturesEnabled = drawerState.isOpen
            ) {
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        PosTopAppBar(
                            title = currentRoute?.let { Screen.fromRoute(it)?.label } ?: "",
                            onMenuClick = { scope.launch { drawerState.open() } },
                            actions = topBarActions,
                            scrollBehavior = scrollBehavior
                        )
                    },
                    bottomBar = {
                        BottomNavigationBar(
                            items = bottomNavItems,
                            currentRoute = currentRoute,
                            badgeCounts = badgeCounts,
                            onItemSelected = { screen ->
                                if (screen == MoreTab) {
                                    scope.launch { drawerState.open() }
                                } else {
                                    onNavigate(screen)
                                }
                            }
                        )
                    },
                    floatingActionButton = floatingActionButton,
                    content = content
                )
            }
        }

        NavigationType.NAVIGATION_RAIL -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(DRAWER_WIDTH_DP.dp)
                    ) {
                        NavigationDrawerContent(
                            navItems = drawerNavItems,
                            currentRoute = currentRoute,
                            onItemClick = { screen ->
                                scope.launch { drawerState.close() }
                                onNavigate(screen)
                            },
                            onHeaderAction = { scope.launch { drawerState.close() } }
                        )
                    }
                },
                gesturesEnabled = drawerState.isOpen
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PosNavigationRail(
                        items = railNavItems,
                        currentRoute = currentRoute,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onItemSelected = onNavigate
                    )
                    VerticalDivider()
                    Scaffold(
                        modifier = Modifier
                            .weight(1f)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            PosTopAppBar(
                                title = currentRoute?.let { Screen.fromRoute(it)?.label } ?: "",
                                onMenuClick = null,
                                actions = topBarActions,
                                scrollBehavior = scrollBehavior
                            )
                        },
                        floatingActionButton = floatingActionButton,
                        content = content
                    )
                }
            }
        }

        NavigationType.PERMANENT_DRAWER -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(DRAWER_WIDTH_DP.dp)
                    ) {
                        NavigationDrawerContent(
                            navItems = drawerNavItems,
                            currentRoute = currentRoute,
                            onItemClick = onNavigate,
                            onHeaderAction = null
                        )
                    }
                }
            ) {
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        PosTopAppBar(
                            title = currentRoute?.let { Screen.fromRoute(it)?.label } ?: "",
                            onMenuClick = null,
                            actions = topBarActions,
                            scrollBehavior = scrollBehavior
                        )
                    },
                    floatingActionButton = floatingActionButton,
                    content = content
                )
            }
        }
    }
}

// ==================== BOTTOM NAVIGATION ====================

@Composable
fun BottomNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    badgeCounts: Map<String, Int> = emptyMap(),
    onItemSelected: (Screen) -> Unit
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute?.let {
                it == screen.route || it.startsWith(screen.baseRoute + "/")
            } ?: false
            val badgeCount = badgeCounts[screen.baseRoute]

            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(screen) },
                icon = {
                    if (badgeCount != null && badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label
                        )
                    }
                },
                label = {
                    Text(
                        text = screen.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// ==================== NAVIGATION RAIL ====================

@Composable
fun PosNavigationRail(
    items: List<Screen>,
    currentRoute: String?,
    onMenuClick: () -> Unit,
    onItemSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (isExpanded) RAIL_WIDTH_EXPANDED_DP.dp else RAIL_WIDTH_COLLAPSED_DP.dp,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "rail_width"
    )

    NavigationRail(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        header = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute?.let {
                it == screen.route || it.startsWith(screen.baseRoute + "/")
            } ?: false

            NavigationRailItem(
                selected = isSelected,
                onClick = { onItemSelected(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = if (isExpanded) {
                    {
                        Text(
                            text = screen.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null,
                alwaysShowLabel = isExpanded,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = { isExpanded = !isExpanded }) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }
    }
}

// ==================== NAVIGATION DRAWER ====================

@Composable
private fun NavigationDrawerContent(
    navItems: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    onHeaderAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EnterprisePOS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (onHeaderAction != null) {
                IconButton(onClick = onHeaderAction) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close drawer"
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Group items by ScreenGroup
        val groupedItems = navItems.groupBy { screen ->
            ScreenGroup.groupFor(screen)?.label ?: "Other"
        }

        groupedItems.forEach { (groupLabel, screens) ->
            Text(
                text = groupLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            screens.forEach { screen ->
                val isSelected = currentRoute?.let {
                    it == screen.route || it.startsWith(screen.baseRoute + "/")
                } ?: false

                NavigationDrawerItem(
                    selected = isSelected,
                    onClick = { onItemClick(screen) },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label
                        )
                    },
                    label = {
                        Text(
                            text = screen.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    badge = {
                        if (screen == Screen.Kds) {
                            Badge { Text("3") } // Example KDS badge
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Footer
        Text(
            text = "v2.4.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ==================== TOP APP BAR ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosTopAppBar(
    title: String,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    androidx.compose.material3.TopAppBar(
        modifier = modifier,
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open menu"
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search"
                )
            }
            actions()
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}
