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
import com.enterprise.pos.feature.settings.screen.AdvancedSettingsScreen
import com.enterprise.pos.feature.settings.screen.BackupSettingsScreen
import com.enterprise.pos.feature.settings.screen.HardwareSettingsScreen
import com.enterprise.pos.feature.settings.screen.PaymentSettingsScreen
import com.enterprise.pos.feature.settings.screen.ReceiptSettingsScreen
import com.enterprise.pos.feature.settings.screen.RegisterSettingsScreen
import com.enterprise.pos.feature.settings.screen.SettingsScreen
import com.enterprise.pos.feature.settings.screen.StoreSettingsScreen
import com.enterprise.pos.feature.settings.screen.TaxSettingsScreen
import com.enterprise.pos.feature.shifts.screen.ShiftsScreen
import com.enterprise.pos.ui.onboarding.OnboardingScreen

import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.core.Id
import com.enterprise.pos.domain.model.PurchaseOrderTag
import com.enterprise.pos.domain.model.SupplierTag
import com.enterprise.pos.domain.model.ReservationTag
import com.enterprise.pos.domain.model.Employee

import com.enterprise.pos.feature.catalog.screen.ProductDetailScreen
import com.enterprise.pos.feature.catalog.screen.CategoryDetailScreen
import com.enterprise.pos.feature.catalog.screen.ModifierEditorScreen
import com.enterprise.pos.feature.catalog.screen.ProductEditScreen

import com.enterprise.pos.feature.customers.screen.CustomerAddScreen
import com.enterprise.pos.feature.customers.screen.CustomerEditScreen

import com.enterprise.pos.feature.employees.screen.LoginScreen
import com.enterprise.pos.feature.employees.screen.EmployeeDetailScreen
import com.enterprise.pos.feature.employees.screen.EmployeeEditScreen
import com.enterprise.pos.feature.employees.screen.RoleEditorScreen

import com.enterprise.pos.feature.inventory.screen.InventoryDetailScreen
import com.enterprise.pos.feature.inventory.screen.StockAdjustmentScreen
import com.enterprise.pos.feature.inventory.screen.PurchaseOrderScreen
import com.enterprise.pos.feature.inventory.screen.SupplierDetailScreen

import com.enterprise.pos.feature.restaurant.screen.ReservationDetailScreen
import com.enterprise.pos.feature.restaurant.screen.TableDetailScreen

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

    posDestination(Screen.SettingsStore) {
        StoreSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsRegister) {
        RegisterSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsPayment) {
        PaymentSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsTax) {
        TaxSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsReceipt) {
        ReceiptSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsHardware) {
        HardwareSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsBackup) {
        BackupSettingsScreen(onBack = { navController.popBackStackSafe() })
    }
    posDestination(Screen.SettingsAdvanced) {
        AdvancedSettingsScreen(onBack = { navController.popBackStackSafe() })
    }

    posDestination(Screen.Onboarding) {
        OnboardingScreen(
            onComplete = { navController.safeNavigate(Screen.Dashboard.route) },
            onSkipProducts = { navController.safeNavigate(Screen.Catalog.route) },
            onImportProducts = { navController.safeNavigate(Screen.Catalog.route) }
        )
    }

    posDestination(Screen.ProductDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for product detail")
            MissingArgumentScreen(Screen.ProductDetail, "id")
        } else {
            ProductDetailScreen(
                productId = ProductId(id),
                onNavigateBack = { navController.popBackStackSafe() },
                onEditProduct = { productId -> navController.safeNavigate(Screen.ProductEdit.build(productId.value)) }
            )
        }
    }

    posDestination(Screen.CategoryDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for category detail")
            MissingArgumentScreen(Screen.CategoryDetail, "id")
        } else {
            CategoryDetailScreen(
                categoryId = CategoryId(id),
                onNavigateBack = { navController.popBackStackSafe() },
                onEditCategory = { },
                onProductClick = { productId -> navController.safeNavigate(Screen.ProductDetail.build(productId.value)) }
            )
        }
    }

    posDestination(Screen.ModifierEditor) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        ModifierEditorScreen(
            modifierGroupId = id?.let { ModifierGroupId(it) },
            onNavigateBack = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.ProductEdit) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        ProductEditScreen(
            productId = id?.let { ProductId(it) },
            onNavigateBack = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.CustomerAdd) {
        CustomerAddScreen(
            onBack = { navController.popBackStackSafe() },
            onSaved = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.CustomerEdit) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        CustomerEditScreen(
            customerId = id?.let { CustomerId(it) },
            onBack = { navController.popBackStackSafe() },
            onSaved = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.Login) {
        LoginScreen(
            onLoginSuccess = { navController.safeNavigate(Screen.Dashboard.route) }
        )
    }

    posDestination(Screen.EmployeeDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for employee detail")
            MissingArgumentScreen(Screen.EmployeeDetail, "id")
        } else {
            EmployeeDetailScreen(
                employeeId = EmployeeId(id),
                isAdmin = false,
                onBack = { navController.popBackStackSafe() },
                onEdit = { navController.safeNavigate(Screen.EmployeeEdit.build(id)) },
                onDeactivated = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.EmployeeEdit) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        EmployeeEditScreen(
            employeeId = id,
            isAdmin = false,
            onBack = { navController.popBackStackSafe() },
            onSaved = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.RoleEditor) { entry ->
        RoleEditorScreen(
            onBack = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.InventoryDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for inventory detail")
            MissingArgumentScreen(Screen.InventoryDetail, "id")
        } else {
            InventoryDetailScreen(
                variantId = VariantId(id),
                storeId = storeId,
                onNavigateToAdjustment = { variantId -> navController.safeNavigate(Screen.StockAdjustment.build(variantId.value)) },
                onNavigateToPurchaseOrder = { },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.StockAdjustment) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for stock adjustment")
            MissingArgumentScreen(Screen.StockAdjustment, "id")
        } else {
            StockAdjustmentScreen(
                variantId = VariantId(id),
                storeId = storeId,
                employeeId = employeeId,
                onSaved = { navController.popBackStackSafe() },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.PurchaseOrder) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        PurchaseOrderScreen(
            orderId = id?.let { Id<PurchaseOrderTag>(it) },
            storeId = storeId,
            onSaved = { navController.popBackStackSafe() },
            onBack = { navController.popBackStackSafe() }
        )
    }

    posDestination(Screen.SupplierDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for supplier detail")
            MissingArgumentScreen(Screen.SupplierDetail, "id")
        } else {
            SupplierDetailScreen(
                supplierId = Id<SupplierTag>(id),
                storeId = storeId,
                onNavigateToPurchaseOrder = { },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.ReservationDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for reservation detail")
            MissingArgumentScreen(Screen.ReservationDetail, "id")
        } else {
            ReservationDetailScreen(
                reservationId = Id<ReservationTag>(id),
                onNavigateToEdit = { },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }

    posDestination(Screen.TableDetail) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for table detail")
            MissingArgumentScreen(Screen.TableDetail, "id")
        } else {
            TableDetailScreen(
                tableId = TableId(id),
                storeId = storeId,
                onNavigateToOrder = { orderId -> navController.safeNavigate(Screen.Cart.build(orderId.value)) },
                onBack = { navController.popBackStackSafe() }
            )
        }
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
        Screen.Onboarding,
        Screen.SettingsStore,
        Screen.SettingsRegister,
        Screen.SettingsPayment,
        Screen.SettingsTax,
        Screen.SettingsReceipt,
        Screen.SettingsHardware,
        Screen.SettingsBackup,
        Screen.SettingsAdvanced,
        Screen.ProductDetail,
        Screen.CategoryDetail,
        Screen.ModifierEditor,
        Screen.ProductEdit,
        Screen.CustomerAdd,
        Screen.CustomerEdit,
        Screen.Login,
        Screen.EmployeeDetail,
        Screen.EmployeeEdit,
        Screen.RoleEditor,
        Screen.InventoryDetail,
        Screen.StockAdjustment,
        Screen.PurchaseOrder,
        Screen.SupplierDetail,
        Screen.ReservationDetail,
        Screen.TableDetail
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
