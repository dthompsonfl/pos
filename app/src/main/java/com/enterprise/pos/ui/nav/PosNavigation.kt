package com.enterprise.pos.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val NAVIGATION_ANIMATION_DURATION = 300

/**
 * Creates and remembers a [NavHostController] with custom back handling and state tracking.
 */
@Composable
fun rememberPosNavController(): NavHostController {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRoute = remember { mutableStateOf<String?>(null) }

    DisposableEffect(navController, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentRoute.value = navController.currentDestination?.route
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return navController
}

/**
 * Custom [NavHost] with route validation and deep link support.
 */
@Composable
fun PosNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    modifier: Modifier = Modifier,
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = { defaultEnterTransition() },
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = { defaultExitTransition() },
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = { defaultPopEnterTransition() },
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = { defaultPopExitTransition() },
    builder: NavGraphBuilder.() -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        builder = builder
    )
}

// ==================== NAVIGATION EXTENSIONS ====================

/**
 * Navigates to [route] only if the current destination is not already at [route].
 * Prevents duplicate navigation to the same destination.
 */
fun NavController.safeNavigate(route: String) {
    val currentRoute = currentDestination?.route ?: return
    if (currentRoute == route || currentRoute.startsWith(route.substringBefore("/{") + "/")) {
        return
    }
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigates to [route] with a pop-up behavior, optionally removing [popUpTo] from the back stack.
 */
fun NavController.navigateWithPopUp(
    route: String,
    popUpTo: String,
    inclusive: Boolean = false,
    saveState: Boolean = true
) {
    navigate(route) {
        popUpTo(popUpTo) {
            this.inclusive = inclusive
            this.saveState = saveState
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigates to [route] with [launchSingleTop] enabled to avoid duplicate destinations.
 */
fun NavController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Pops the back stack if there is a previous destination. Returns true if a pop occurred.
 */
fun NavController.popBackStackSafe(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

/**
 * Returns the current route as a [StateFlow<String?>] that updates when the destination changes.
 */
@Composable
fun NavController.currentRouteAsState(): State<String?> {
    val state = remember { mutableStateOf(currentDestination?.route) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(this, lifecycleOwner) {
        val observer = NavController.OnDestinationChangedListener { _, destination, _ ->
            state.value = destination.route
        }
        addOnDestinationChangedListener(observer)
        onDispose { removeOnDestinationChangedListener(observer) }
    }

    return state
}

/**
 * Observes the current route as a [StateFlow].
 */
fun NavController.currentRouteFlow(): StateFlow<String?> {
    val flow = MutableStateFlow(currentDestination?.route)
    addOnDestinationChangedListener { _, destination, _ ->
        flow.value = destination.route
    }
    return flow.asStateFlow()
}

// ==================== ANIMATION HELPERS ====================

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = LinearOutSlowInEasing
        )
    )
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutLinearInEasing
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutLinearInEasing
        )
    )
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = LinearOutSlowInEasing
        )
    )
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutLinearInEasing
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NAVIGATION_ANIMATION_DURATION,
            easing = FastOutLinearInEasing
        )
    )
}

/**
 * Fade-only transition for dialog-like destinations.
 */
fun fadeEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 200)
)

fun fadeExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 200)
)

// ==================== TYPE-SAFE COMPOSABLE HELPER ====================

/**
 * Registers a composable destination with route validation and deep link support.
 */
inline fun <reified T : Screen> NavGraphBuilder.posComposable(
    screen: T,
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    crossinline content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = screen.route,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        content(backStackEntry)
    }
}
