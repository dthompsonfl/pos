package com.enterprise.pos.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Dashboard)
    data object Floor : Screen("floor", "Tables", Icons.Filled.TableRestaurant)
    data object Catalog : Screen("catalog", "Menu", Icons.Filled.RestaurantMenu)
    data object Kds : Screen("kds", "Kitchen", Icons.Filled.Restaurant)
    data object Cart : Screen("cart/{orderId}", "Cart", Icons.Filled.ShoppingCart) {
        fun build(orderId: String) = "cart/$orderId"
    }
    data object Checkout : Screen("checkout/{orderId}", "Checkout", Icons.Filled.Payments) {
        fun build(orderId: String) = "checkout/$orderId"
    }
    data object Customers : Screen("customers", "Customers", Icons.Filled.People)
    data object CustomerDetail : Screen("customer/{id}", "Customer", Icons.Filled.Person) {
        fun build(id: String) = "customer/$id"
    }
    data object Employees : Screen("employees", "Staff", Icons.Filled.Badge)
    data object Reports : Screen("reports", "Reports", Icons.Filled.Insights)
    data object Inventory : Screen("inventory", "Stock", Icons.Filled.Inventory2)
    data object Shifts : Screen("shifts", "Shift", Icons.Filled.LockClock)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Migration : Screen("migration", "Migrate", Icons.Filled.CloudDownload)
    data object Reservations : Screen("reservations", "Reservations", Icons.Filled.EventAvailable)
    data object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Filled.BugReport)
    data object Onboarding : Screen("onboarding", "Setup", Icons.Filled.RocketLaunch)
    data object Login : Screen("login", "Login", Icons.Filled.Login)
}

/** Bottom navigation items. "More" opens the navigation drawer for full feature access. */
data object MoreTab : Screen("more", "More", Icons.Filled.Menu)

val bottomNavItems: List<Screen> = listOf(
    Screen.Dashboard,
    Screen.Floor,
    Screen.Catalog,
    Screen.Reports,
    MoreTab
)
