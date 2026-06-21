package com.enterprise.pos.ui.nav

import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import androidx.navigation.NavController

private const val TAG = "RouteValidator"

/**
 * Validates routes before navigation to ensure safety and correctness.
 */
object RouteValidator {

    private val knownRoutes: Set<String> by lazy {
        Screen.all.map { it.route }.toSet()
    }

    private val knownBaseRoutes: Set<String> by lazy {
        Screen.all.map { it.baseRoute }.toSet()
    }

    /**
     * Validates whether a route is known and safe to navigate to.
     *
     * @param route The route string to validate.
     * @return True if the route is valid, false otherwise.
     */
    fun isValid(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        if (route in knownRoutes) return true
        if (route.substringBefore("/") in knownBaseRoutes) return true
        if (route.substringBefore("?") in knownRoutes) return true

        // Check parameterized routes with arguments filled in
        val baseRoute = route.substringBefore("/")
        return knownBaseRoutes.any { it == baseRoute }
    }

    /**
     * Validates that a route is safe to navigate to. Logs a warning if invalid.
     */
    fun validateOrLog(route: String?): Boolean {
        val logger = NoopLogger
        return if (isValid(route)) {
            true
        } else {
            logger.w(TAG, "Attempted to navigate to invalid route")
            false
        }
    }
}

/**
 * Security checks for routes, including authentication and permission validation.
 */
object RouteSecurity {

    private val authenticatedRoutes = setOf(
        Screen.Dashboard.route, Screen.Floor.route, Screen.Catalog.route,
        Screen.Kds.route, Screen.Cart.route, Screen.Checkout.route,
        Screen.Customers.route, Screen.Employees.route, Screen.Reports.route,
        Screen.Inventory.route, Screen.Shifts.route, Screen.Settings.route,
        Screen.Reservations.route, Screen.Diagnostics.route, Screen.Migration.route
    )

    private val adminOnlyRoutes = setOf(
        Screen.Employees.route, Screen.EmployeeDetail.route, Screen.EmployeeEdit.route,
        Screen.RoleEditor.route, Screen.Reports.route, Screen.Migration.route,
        Screen.Diagnostics.route, Screen.SettingsAdvanced.route
    )

    /**
     * Checks whether the route requires authentication.
     */
    fun requiresAuth(route: String): Boolean {
        val base = route.substringBefore("/{")
        return authenticatedRoutes.any { it.startsWith(base) || base == it.substringBefore("/{") }
    }

    /**
     * Checks whether the route is restricted to admin users.
     */
    fun requiresAdmin(route: String): Boolean {
        val base = route.substringBefore("/{")
        return adminOnlyRoutes.any { it.startsWith(base) || base == it.substringBefore("/{") }
    }

    /**
     * Validates that a user can access the given route based on their role.
     *
     * @param route The route to check.
     * @param isAuthenticated Whether the user is authenticated.
     * @param userRole The user's role (e.g., "admin", "manager", "cashier").
     * @return True if access is allowed, false otherwise.
     */
    fun canAccess(route: String, isAuthenticated: Boolean, userRole: String): Boolean {
        if (!isAuthenticated) {
            return !requiresAuth(route) || route == Screen.Login.route
        }

        if (requiresAdmin(route)) {
            return userRole == "admin" || userRole == "manager"
        }

        return true
    }

    /**
     * Redirects to the login screen if the user is not authenticated.
     */
    fun enforceAuth(navController: NavController, route: String, isAuthenticated: Boolean): Boolean {
        if (!isAuthenticated && requiresAuth(route)) {
            navController.navigateSingleTop(Screen.Login.route)
            return false
        }
        return true
    }
}

/**
 * Validates required arguments for parameterized routes.
 */
object RouteArgumentValidator {

    /**
     * Validates that all required arguments are present for a route.
     *
     * @param route The route definition (e.g., "product/{id}").
     * @param args The provided arguments map.
     * @return True if all required arguments are present and non-blank.
     */
    fun validateArgs(route: String, args: Map<String, String?>): Boolean {
        val required = extractPlaceholders(route)
        return required.all { key ->
            args[key]?.isNotBlank() == true
        }
    }

    /**
     * Validates a single argument value (e.g., UUID, numeric ID).
     */
    fun validateArgValue(key: String, value: String?): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult.Invalid("Argument '$key' is null or blank")
        }
        return when (key) {
            "id", "orderId" -> validateId(value)
            else -> ValidationResult.Valid
        }
    }

    private fun validateId(value: String): ValidationResult {
        // Accept UUIDs or numeric IDs
        if (value.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
            return ValidationResult.Valid
        }
        if (value.matches(Regex("^[0-9]+$"))) {
            return ValidationResult.Valid
        }
        if (value.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return ValidationResult.Valid
        }
        return ValidationResult.Invalid("Invalid ID format: $value")
    }

    private fun extractPlaceholders(route: String): List<String> {
        return Regex("\\{([^}]+)}").findAll(route).map { it.groupValues[1] }.toList()
    }
}

/**
 * Intercepts navigation for analytics, logging, or blocking purposes.
 */
object RouteInterceptor {

    private val logger: Logger = NoopLogger
    interface NavigationInterceptor {
        fun intercept(route: String, currentRoute: String?): InterceptionResult
    }

    data class InterceptionResult(
        val allow: Boolean,
        val redirectTo: String? = null,
        val logEvent: String? = null
    )

    private val interceptors = mutableListOf<NavigationInterceptor>()

    fun register(interceptor: NavigationInterceptor) {
        interceptors.add(interceptor)
    }

    fun unregister(interceptor: NavigationInterceptor) {
        interceptors.remove(interceptor)
    }

    /**
     * Processes all registered interceptors and returns the final result.
     */
    fun intercept(route: String, currentRoute: String?): InterceptionResult {
        for (interceptor in interceptors) {
            val result = interceptor.intercept(route, currentRoute)
            if (!result.allow) {
                logger.i(TAG, "Navigation blocked by interceptor")
                return result
            }
            if (result.redirectTo != null) {
                logger.i(TAG, "Navigation redirected by interceptor")
                return result
            }
        }
        return InterceptionResult(allow = true)
    }

    /**
     * Default analytics interceptor that logs all navigation events.
     */
    class AnalyticsInterceptor : NavigationInterceptor {
        override fun intercept(route: String, currentRoute: String?): InterceptionResult {
            logger.d(TAG, "Navigation event")
            return InterceptionResult(allow = true, logEvent = "navigate:$route")
        }
    }
}

/**
 * Guards routes by blocking navigation to unauthorized destinations.
 */
object RouteGuard {

    /**
     * Checks whether navigation to [route] is allowed. If not, redirects to the appropriate screen.
     *
     * @param navController The navigation controller.
     * @param route The target route.
     * @param isAuthenticated Whether the user is authenticated.
     * @param userRole The user's role.
     * @return True if navigation is allowed, false if redirected.
     */
    fun check(
        navController: NavController,
        route: String,
        isAuthenticated: Boolean,
        userRole: String
    ): Boolean {
        val logger = NoopLogger
        // Run through interceptors first
        val currentRoute = navController.currentDestination?.route
        val interception = RouteInterceptor.intercept(route, currentRoute)
        if (!interception.allow) {
            interception.redirectTo?.let { navController.navigateSingleTop(it) }
            return false
        }
        if (interception.redirectTo != null) {
            navController.navigateSingleTop(interception.redirectTo)
            return false
        }

        // Validate route
        if (!RouteValidator.isValid(route)) {
            logger.w(TAG, "Blocked invalid route")
            return false
        }

        // Security checks
        if (!RouteSecurity.canAccess(route, isAuthenticated, userRole)) {
            if (!isAuthenticated) {
                navController.navigateSingleTop(Screen.Login.route)
            } else {
                logger.w(TAG, "Access denied for route")
            }
            return false
        }

        return true
    }

    /**
     * Safe navigation that applies all guards before navigating.
     */
    fun navigateSafe(
        navController: NavController,
        route: String,
        isAuthenticated: Boolean,
        userRole: String
    ) {
        if (check(navController, route, isAuthenticated, userRole)) {
            navController.safeNavigate(route)
        }
    }
}
