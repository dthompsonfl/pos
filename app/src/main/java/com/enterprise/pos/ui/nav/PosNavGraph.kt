package com.enterprise.pos.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.feature.catalog.screen.CatalogScreen
import com.enterprise.pos.feature.customers.screen.CustomerDetailScreen
import com.enterprise.pos.feature.customers.screen.CustomersScreen
import com.enterprise.pos.feature.dashboard.screen.DashboardScreen
import com.enterprise.pos.feature.employees.screen.EmployeesManagementScreen
import com.enterprise.pos.feature.inventory.screen.InventoryScreen
import com.enterprise.pos.feature.kds.screen.KdsScreen
import com.enterprise.pos.feature.migration.screen.MigrationScreen
import com.enterprise.pos.feature.reports.screen.ReportsScreen
import com.enterprise.pos.feature.restaurant.screen.FloorScreen
import com.enterprise.pos.feature.restaurant.screen.ReservationsScreen
import com.enterprise.pos.feature.sales.screen.CartScreen
import com.enterprise.pos.feature.sales.screen.CheckoutScreen
import com.enterprise.pos.feature.settings.screen.SettingsScreen
import com.enterprise.pos.feature.shifts.screen.ShiftsScreen
import com.enterprise.pos.ui.onboarding.OnboardingScreen

/**
 * Defines the application navigation graph for the Enterprise POS shell.
 *
 * The graph wires routes only to screens that are present with verified signatures. Routes for
 * in-progress workflows are still registered so menu/deep-link navigation is safe, but they render
 * an honest unavailable state rather than importing missing or mismatched screen implementations.
 */
@Composable
fun PosNavGraph(
    navController: NavHostController,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route,
    onRouteError: (String) -> Unit = {},
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId
) {
    PosNavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.padding(padding)
    ) {
        placeholderDestinations()
        coreDestinations(navController, onRouteError, storeId, registerId, employeeId)
    }
}

private fun NavGraphBuilder.coreDestinations(
    navController: NavHostController,
    onRouteError: (String) -> Unit,
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId
) {
    posDestination(Screen.Dashboard) {
        DashboardScreen(
            storeId = storeId,
            onNavigateToFloor = { navController.safeNavigate(Screen.Floor.route) },
            onNavigateToReports = { navController.safeNavigate(Screen.Reports.route) },
            onNavigateToInventory = { navController.safeNavigate(Screen.Inventory.route) },
            onNavigateToShifts = { navController.safeNavigate(Screen.Shifts.route) },
            onNavigateToKds = { navController.safeNavigate(Screen.Kds.route) },
            onNavigateToMigration = { navController.safeNavigate(Screen.Migration.route) }
        )
    }

    posDestination(Screen.Floor) {
        FloorScreen(
            storeId = storeId,
            registerId = registerId,
            employeeId = employeeId,
            onOrderCreated = { orderId, _ -> navController.safeNavigate(Screen.Cart.build(orderId)) }
        )
    }

    posDestination(Screen.Catalog) {
        CatalogScreen(
            onProductClick = { productId -> navController.safeNavigate(Screen.ProductDetail.build(productId.value)) }
        )
    }

    posDestination(Screen.Kds) { KdsScreen(storeId = storeId) }

    posDestination(Screen.Cart) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) {
            onRouteError("Missing orderId for cart")
            MissingArgumentScreen(Screen.Cart, "orderId")
        } else {
            CartScreen(
                orderId = OrderId(orderId),
                requestingEmployee = employeeId,
                onCheckout = { id -> navController.safeNavigate(Screen.Checkout.build(id.value)) },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.Checkout) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) {
            onRouteError("Missing orderId for checkout")
            MissingArgumentScreen(Screen.Checkout, "orderId")
        } else {
            CheckoutScreen(
                orderId = OrderId(orderId),
                employeeId = employeeId,
                onComplete = { navController.popBackStack(Screen.Dashboard.route, inclusive = false) },
                onCancel = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.Customers) {
        CustomersScreen(
            onCustomerSelected = { id -> navController.safeNavigate(Screen.CustomerDetail.build(id.value)) }
        )
    }

    posDestination(Screen.CustomerDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for customer detail")
            MissingArgumentScreen(Screen.CustomerDetail, "id")
        } else {
            CustomerDetailScreen(
                customerId = CustomerId(id),
                storeId = storeId,
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.Employees) { EmployeesManagementScreen() }
    posDestination(Screen.Reports) { ReportsScreen(storeId = storeId) }
    posDestination(Screen.Inventory) { InventoryScreen(storeId = storeId, employeeId = employeeId) }
    posDestination(Screen.Shifts) { ShiftsScreen(storeId = storeId, registerId = registerId, employeeId = employeeId) }
    posDestination(Screen.Migration) { MigrationScreen(employeeId = employeeId) }
    posDestination(Screen.Reservations) { ReservationsScreen(storeId = storeId) }

    posDestination(Screen.Settings) {
        SettingsScreen(
            onNavigateToStore = { navController.safeNavigate(Screen.SettingsStore.route) },
            onNavigateToRegister = { navController.safeNavigate(Screen.SettingsRegister.route) },
            onNavigateToTax = { navController.safeNavigate(Screen.SettingsTax.route) },
            onNavigateToPayment = { navController.safeNavigate(Screen.SettingsPayment.route) },
            onNavigateToReceipt = { navController.safeNavigate(Screen.SettingsReceipt.route) },
            onNavigateToHardware = { navController.safeNavigate(Screen.SettingsHardware.route) },
            onNavigateToBackup = { navController.safeNavigate(Screen.SettingsBackup.route) },
            onNavigateToAdvanced = { navController.safeNavigate(Screen.SettingsAdvanced.route) }
        )
    }

    posDestination(Screen.Onboarding) {
        OnboardingScreen(onComplete = { navController.safeNavigate(Screen.Dashboard.route) })
    }
}

private fun NavGraphBuilder.placeholderDestinations() {
    val realScreens = setOf(
        Screen.Dashboard,
        Screen.Floor,
        Screen.Catalog,
        Screen.Kds,
        Screen.Cart,
        Screen.Checkout,
        Screen.Customers,
        Screen.CustomerDetail,
        Screen.Employees,
        Screen.Reports,
        Screen.Inventory,
        Screen.Shifts,
        Screen.Migration,
        Screen.Reservations,
        Screen.Settings,
        Screen.Onboarding
    )

    Screen.all
        .filterNot { it in realScreens }
        .sortedWith(compareByDescending<Screen> { routeSpecificity(it.route) }.thenBy { it.route })
        .forEach { screen ->
            posDestination(screen) { entry ->
                RoutePlaceholder(
                    screen = screen,
                    argument = ScreenArguments.idOrNull(entry) ?: ScreenArguments.orderIdOrNull(entry)
                )
            }
        }
}

private fun NavGraphBuilder.posDestination(
    screen: Screen,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = screen.route,
        arguments = navArgumentsFor(screen.route),
        deepLinks = getDeepLinksForScreen(screen)
    ) { entry ->
        content(entry)
    }
}

private fun navArgumentsFor(route: String): List<NamedNavArgument> = buildList {
    if (route.contains("{id}")) {
        add(navArgument("id") { type = NavType.StringType })
    }
    if (route.contains("{orderId}")) {
        add(navArgument("orderId") { type = NavType.StringType })
    }
}

private fun routeSpecificity(route: String): Int = route.split('/').size * 10 + route.count { it == '/' }

@Composable
private fun MissingArgumentScreen(screen: Screen, argument: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(screen.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Missing required route argument: $argument", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RoutePlaceholder(screen: Screen, argument: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(screen.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!argument.isNullOrBlank()) {
            Text("Reference: $argument", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "This workflow is registered in navigation but is not connected to a verified screen yet.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
