package com.enterprise.pos.ui.nav

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.navigationDataStore: DataStore<Preferences> by preferencesDataStore(name = "navigation_state")

private val KEY_NAVIGATION_STATE = stringPreferencesKey("navigation_state")
private val KEY_LAST_ROUTE = stringPreferencesKey("last_route")

/**
 * Immutable data class representing the current navigation state.
 *
 * @property currentRoute The current visible route.
 * @property previousRoute The previous route before navigation, if any.
 * @property isDrawerOpen Whether the navigation drawer is open.
 * @property isRailExpanded Whether the navigation rail is expanded (medium screens).
 * @property canPopBackStack Whether the back stack can be popped.
 * @property backStackDepth Current depth of the back stack.
 * @property sessionId Unique identifier for the current navigation session.
 */
@Serializable
data class NavigationState(
    val currentRoute: String? = null,
    val previousRoute: String? = null,
    val isDrawerOpen: Boolean = false,
    val isRailExpanded: Boolean = false,
    val canPopBackStack: Boolean = false,
    val backStackDepth: Int = 0,
    val sessionId: String = UUID.randomUUID().toString()
) {
    companion object {
        val INITIAL = NavigationState()
    }
}

/**
 * Manages navigation state persistence and restoration using DataStore.
 */
class NavigationStateManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Saves the navigation state to DataStore.
     */
    suspend fun saveState(state: NavigationState) {
        context.navigationDataStore.edit { prefs ->
            prefs[KEY_NAVIGATION_STATE] = json.encodeToString(state)
            prefs[KEY_LAST_ROUTE] = state.currentRoute ?: ""
        }
    }

    /**
     * Restores the navigation state from DataStore.
     */
    fun restoreState(): Flow<NavigationState> {
        return context.navigationDataStore.data.map { prefs ->
            prefs[KEY_NAVIGATION_STATE]?.let { json.decodeFromString(it) } ?: NavigationState.INITIAL
        }
    }

    /**
     * Gets the last known route from DataStore.
     */
    fun lastRoute(): Flow<String?> {
        return context.navigationDataStore.data.map { prefs ->
            prefs[KEY_LAST_ROUTE]?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Clears all saved navigation state.
     */
    suspend fun clearState() {
        context.navigationDataStore.edit { prefs ->
            prefs.remove(KEY_NAVIGATION_STATE)
            prefs.remove(KEY_LAST_ROUTE)
        }
    }
}

/**
 * Remembers and returns a [NavigationState] that updates as the navigation destination changes.
 */
@Composable
fun rememberNavigationState(
    navController: NavController,
    manager: NavigationStateManager = rememberNavigationStateManager()
): State<NavigationState> {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NavigationState.INITIAL) }
    val rememberedState = remember { mutableStateOf(state) }

    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val currentRoute = destination.route
            val previousRoute = state.currentRoute
            val backStack = navController.currentBackStack.value
            val canPop = navController.previousBackStackEntry != null

            val newState = NavigationState(
                currentRoute = currentRoute,
                previousRoute = previousRoute,
                canPopBackStack = canPop,
                backStackDepth = backStack.size,
                sessionId = state.sessionId
            )

            state = newState
            rememberedState.value = newState
            scope.launch { manager.saveState(newState) }
        }
    }

    return rememberedState
}

/**
 * Remembers a [NavigationStateManager] tied to the current context.
 */
@Composable
fun rememberNavigationStateManager(): NavigationStateManager {
    val context = LocalContext.current
    return remember { NavigationStateManager(context) }
}

// ==================== NAVIGATION STATE SAVER ====================

/**
 * Saves and restores navigation state across process death using
 * SavedStateHandle and DataStore.
 */
class NavigationStateSaver(
    private val manager: NavigationStateManager,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(NavigationState.INITIAL)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /**
     * Initializes the saver by restoring state from DataStore.
     */
    fun initialize() {
        scope.launch {
            manager.restoreState().collect { restored ->
                _state.value = restored
            }
        }
    }

    /**
     * Saves the current state.
     */
    fun save(state: NavigationState) {
        scope.launch { manager.saveState(state) }
    }

    /**
     * Restores the last saved state.
     */
    fun restore(): NavigationState {
        return _state.value
    }
}

// ==================== NAVIGATION STATE VALIDATOR ====================

/**
 * Validates navigation state, routes, and arguments before allowing navigation.
 */
object NavigationStateValidator {

    /**
     * Validates a route and its arguments before navigation.
     *
     * @param route The route to validate.
     * @param arguments Optional arguments map.
     * @return True if the route is valid and can be navigated to.
     */
    fun validate(route: String?, arguments: Map<String, String>? = null): ValidationResult {
        if (route.isNullOrBlank()) {
            return ValidationResult.Invalid("Route is null or blank")
        }

        // Check if route is known
        val screen = Screen.fromRoute(route)
        if (screen == null) {
            return ValidationResult.Invalid("Unknown route: $route")
        }

        // Validate arguments if route has placeholders
        val requiredArgs = extractRequiredArgs(screen.route)
        if (requiredArgs.isNotEmpty()) {
            if (arguments == null) {
                return ValidationResult.Invalid("Missing required arguments for $route: $requiredArgs")
            }
            val missingArgs = requiredArgs.filter { it !in arguments || arguments[it].isNullOrBlank() }
            if (missingArgs.isNotEmpty()) {
                return ValidationResult.Invalid("Missing required arguments: $missingArgs")
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Validates that the current user has permission to navigate to the given route.
     */
    fun validatePermission(route: String, userRole: String): ValidationResult {
        // Map routes to required minimum roles
        val restrictedRoutes = mapOf(
            Screen.Diagnostics.route to listOf("admin", "manager"),
            Screen.Employees.route to listOf("admin", "manager"),
            Screen.EmployeeDetail.route to listOf("admin", "manager"),
            Screen.EmployeeEdit.route to listOf("admin", "manager"),
            Screen.RoleEditor.route to listOf("admin"),
            Screen.Reports.route to listOf("admin", "manager"),
            Screen.Settings.route to listOf("admin", "manager"),
            Screen.Migration.route to listOf("admin"),
            Screen.SettingsAdvanced.route to listOf("admin")
        )

        val baseRoute = route.substringBefore("/{")
        val requiredRoles = restrictedRoutes.entries.find {
            it.key == route || it.key.startsWith(baseRoute)
        }?.value

        if (requiredRoles != null && userRole !in requiredRoles) {
            return ValidationResult.Invalid("Insufficient permissions for route: $route")
        }

        return ValidationResult.Valid
    }

    private fun extractRequiredArgs(route: String): List<String> {
        val regex = "\\{([^}]+)}".toRegex()
        return regex.findAll(route).map { it.groupValues[1] }.toList()
    }
}

/**
 * Result of a validation check.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}
