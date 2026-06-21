package com.enterprise.pos.ui.nav

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink

private const val HTTPS_SCHEME = "https"
private const val APP_SCHEME = "enterprise-pos"
private const val WEB_HOST = "pos.enterprise.com"
private const val URI_PATTERN_DASHBOARD = "dashboard"
private const val URI_PATTERN_CATALOG = "catalog"
private const val URI_PATTERN_FLOOR = "floor"
private const val URI_PATTERN_KDS = "kds"
private const val URI_PATTERN_EMPLOYEES = "employees"
private const val URI_PATTERN_INVENTORY = "inventory"
private const val URI_PATTERN_SHIFTS = "shifts"
private const val URI_PATTERN_RESERVATIONS = "reservations"
private const val URI_PATTERN_DIAGNOSTICS = "diagnostics"
private const val URI_PATTERN_MIGRATION = "migration"
private const val URI_PATTERN_LOGIN = "login"

/**
 * Builds a deep link [Uri] for the given screen with optional parameters.
 *
 * @param screen The target screen.
 * @param scheme The URI scheme ("https" or "enterprise-pos").
 * @param params Path parameters to fill into the route.
 * @return A [Uri] that can be used to navigate to the screen.
 */
fun deepLinkUri(
    screen: Screen,
    scheme: String = APP_SCHEME,
    vararg params: Pair<String, String>
): Uri {
    val baseUri = when (scheme) {
        HTTPS_SCHEME -> "https://$WEB_HOST/${screen.route}"
        else -> "$scheme://${screen.route}"
    }

    var uri = baseUri
    params.forEach { (key, value) ->
        uri = uri.replace("{$key}", Uri.encode(value))
    }

    return Uri.parse(uri)
}

/**
 * Creates a [NavDeepLink] for the given screen with both web and app scheme support.
 *
 * @param screen The target screen.
 * @param appScheme Whether to include the app scheme (`enterprise-pos://`).
 * @param webScheme Whether to include the web scheme (`https://pos.enterprise.com/`).
 */
fun deepLinkRoute(
    screen: Screen,
    appScheme: Boolean = true,
    webScheme: Boolean = true
): List<NavDeepLink> {
    val deepLinks = mutableListOf<NavDeepLink>()

    if (appScheme) {
        deepLinks.add(
            navDeepLink {
                uriPattern = "$APP_SCHEME://${screen.route}"
            }
        )
    }

    if (webScheme) {
        deepLinks.add(
            navDeepLink {
                uriPattern = "$HTTPS_SCHEME://$WEB_HOST/${screen.route}"
            }
        )
    }

    return deepLinks
}

/**
 * Handles an incoming deep link [Uri] by navigating to the appropriate screen.
 *
 * @param navController The navigation controller to use for navigation.
 * @param uri The incoming deep link URI.
 * @return True if the URI was handled, false otherwise.
 */
fun handleDeepLink(navController: NavController, uri: Uri): Boolean {
    val scheme = uri.scheme ?: return false
    val host = uri.host ?: ""

    val path = uri.path ?: return false
    val pathSegments = uri.pathSegments

    // Validate scheme and host
    when (scheme) {
        APP_SCHEME -> { /* valid */ }
        HTTPS_SCHEME -> if (host != WEB_HOST) return false
        else -> return false
    }

    // Map path to route
    val route = when {
        path == "/$URI_PATTERN_DASHBOARD" || pathSegments.firstOrNull() == URI_PATTERN_DASHBOARD -> Screen.Dashboard.route
        path == "/$URI_PATTERN_CATALOG" || pathSegments.firstOrNull() == URI_PATTERN_CATALOG -> Screen.Catalog.route
        path == "/$URI_PATTERN_FLOOR" || pathSegments.firstOrNull() == URI_PATTERN_FLOOR -> Screen.Floor.route
        path == "/$URI_PATTERN_KDS" || pathSegments.firstOrNull() == URI_PATTERN_KDS -> Screen.Kds.route
        path == "/$URI_PATTERN_EMPLOYEES" || pathSegments.firstOrNull() == URI_PATTERN_EMPLOYEES -> Screen.Employees.route
        path == "/$URI_PATTERN_INVENTORY" || pathSegments.firstOrNull() == URI_PATTERN_INVENTORY -> Screen.Inventory.route
        path == "/$URI_PATTERN_SHIFTS" || pathSegments.firstOrNull() == URI_PATTERN_SHIFTS -> Screen.Shifts.route
        path == "/$URI_PATTERN_RESERVATIONS" || pathSegments.firstOrNull() == URI_PATTERN_RESERVATIONS -> Screen.Reservations.route
        path == "/$URI_PATTERN_DIAGNOSTICS" || pathSegments.firstOrNull() == URI_PATTERN_DIAGNOSTICS -> Screen.Diagnostics.route
        path == "/$URI_PATTERN_MIGRATION" || pathSegments.firstOrNull() == URI_PATTERN_MIGRATION -> Screen.Migration.route
        path == "/$URI_PATTERN_LOGIN" || pathSegments.firstOrNull() == URI_PATTERN_LOGIN -> Screen.Login.route

        // Product detail
        pathSegments.firstOrNull() == "product" && pathSegments.size == 2 -> {
            Screen.ProductDetail.build(pathSegments[1])
        }

        // Customer detail
        pathSegments.firstOrNull() == "customer" && pathSegments.size == 2 -> {
            Screen.CustomerDetail.build(pathSegments[1])
        }

        // Reservation detail
        pathSegments.firstOrNull() == "reservation" && pathSegments.size == 2 -> {
            Screen.ReservationDetail.build(pathSegments[1])
        }

        // Settings sub-pages
        pathSegments.firstOrNull() == "settings" && pathSegments.size == 2 -> {
            when (pathSegments[1]) {
                "store" -> Screen.SettingsStore.route
                "register" -> Screen.SettingsRegister.route
                "payment" -> Screen.SettingsPayment.route
                "tax" -> Screen.SettingsTax.route
                "receipt" -> Screen.SettingsReceipt.route
                "hardware" -> Screen.SettingsHardware.route
                "backup" -> Screen.SettingsBackup.route
                "advanced" -> Screen.SettingsAdvanced.route
                else -> Screen.Settings.route
            }
        }

        // Onboarding steps
        pathSegments.firstOrNull() == "onboarding" && pathSegments.size == 2 -> {
            Screen.Onboarding.route
        }

        // Cart with orderId
        pathSegments.firstOrNull() == "cart" && pathSegments.size == 2 -> {
            Screen.Cart.build(pathSegments[1])
        }

        // Checkout with orderId
        pathSegments.firstOrNull() == "checkout" && pathSegments.size == 2 -> {
            Screen.Checkout.build(pathSegments[1])
        }

        else -> return false
    }

    // Validate route before navigating
    if (RouteValidator.isValid(route)) {
        navController.safeNavigate(route)
        return true
    }

    return false
}

/**
 * Gets all deep links for a specific screen, to be used in composable registration.
 */
fun getDeepLinksForScreen(screen: Screen): List<NavDeepLink> {
    return deepLinkRoute(screen, appScheme = true, webScheme = true)
}
