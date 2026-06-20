package com.enterprise.pos.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.enterprise.pos.core.EmployeeId
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

private const val ANIMATION_DURATION = 300

/**
 * Defines the complete navigation graph for the EnterprisePOS application.
 *
 * This composable defines all routes and their destinations, including type-safe navigation,
 * argument validation, and shared element transitions where applicable.
 *
 * @param navController The [NavHostController] used for navigation.
 * @param padding The [PaddingValues] provided by the scaffold for insets handling.
 * @param modifier Optional [Modifier] applied to the [PosNavHost].
 * @param startDestination The initial destination route. Defaults to [Screen.Dashboard].
 * @param onRouteError Callback invoked when a route validation error occurs.
 * @param storeId The current store ID for all store-scoped screens.
 * @param registerId The current register ID for register-scoped screens.
 * @param employeeId The current employee ID for audit and permission tracking.
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
        // ==================== CORE SCREENS ====================
        coreDestinations(navController, onRouteError, storeId, registerId, employeeId)

        // ==================== SALES FLOW ====================
        salesGraph(navController, onRouteError, storeId, registerId, employeeId)

        // ==================== CATALOG FLOW ====================
        catalogGraph(navController, onRouteError)

        // ==================== INVENTORY FLOW ====================
        inventoryGraph(navController, onRouteError, storeId, employeeId)

        // ==================== CUSTOMER FLOW ====================
        customerGraph(navController, onRouteError, storeId)

        // ==================== RESTAURANT FLOW ====================
        restaurantGraph(navController, onRouteError, storeId)

        // ==================== EMPLOYEE FLOW ====================
        employeeGraph(navController, onRouteError)

        // ==================== REPORT FLOW ====================
        reportGraph(navController, onRouteError, storeId)

        // ==================== SETTINGS FLOW ====================
        settingsGraph(navController, onRouteError)

        // ==================== ONBOARDING FLOW ====================
        onboardingGraph(navController, onRouteError)

        // ==================== GIFT CARD FLOW ====================
        giftCardGraph(navController, onRouteError)

        // ==================== PROMOTION FLOW ====================
        promotionGraph(navController, onRouteError)

        // ==================== RETURN FLOW ====================
        returnGraph(navController, onRouteError)
    }
}

private fun NavGraphBuilder.coreDestinations(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId
) {
    posComposable(Screen.Dashboard) {
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

    posComposable(Screen.Floor) {
        FloorScreen(
            storeId = storeId,
            registerId = registerId,
            employeeId = employeeId,
            onOrderCreated = { orderId, _ -> navController.safeNavigate(Screen.Cart.build(orderId)) }
        )
    }

    posComposable(Screen.Catalog) {
        CatalogScreen(onProductClick = { productId ->
            // TODO: Phase 2 - Wire product detail navigation
            // navController.safeNavigate(Screen.ProductDetail.build(productId.value))
        })
    }

    posComposable(Screen.Kds) { KdsScreen(storeId = storeId) }

    posComposable(Screen.Cart, deepLinks = getDeepLinksForScreen(Screen.Cart)) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) {
            onRouteError("Missing orderId for cart")
            return@posComposable
        }
        CartScreen(
            orderId = com.enterprise.pos.core.OrderId(orderId),
            requestingEmployee = employeeId,
            onCheckout = { id -> navController.safeNavigate(Screen.Checkout.build(id.value)) },
            onBack = { navController.popBackStackSafe() }
        )
    }

    posComposable(Screen.Checkout, deepLinks = getDeepLinksForScreen(Screen.Checkout)) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) {
            onRouteError("Missing orderId for checkout")
            return@posComposable
        }
        CheckoutScreen(
            orderId = com.enterprise.pos.core.OrderId(orderId),
            employeeId = employeeId,
            onComplete = { navController.popBackStack(Screen.Dashboard.route, inclusive = false) },
            onCancel = { navController.popBackStackSafe() }
        )
    }

    posComposable(Screen.Customers) {
        CustomersScreen(
            onCustomerSelected = { id -> navController.safeNavigate(Screen.CustomerDetail.build(id.value)) }
        )
    }

    posComposable(Screen.CustomerDetail, deepLinks = getDeepLinksForScreen(Screen.CustomerDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) {
            onRouteError("Missing id for customer detail")
            return@posComposable
        }
        CustomerDetailScreen(
            customerId = com.enterprise.pos.core.CustomerId(id),
            storeId = storeId,
            onBack = { navController.popBackStackSafe() }
        )
    }

    posComposable(Screen.Employees) { EmployeesManagementScreen() }
    posComposable(Screen.Reports) { ReportsScreen(storeId = storeId) }
    posComposable(Screen.Inventory) { InventoryScreen(storeId = storeId, employeeId = employeeId) }
    posComposable(Screen.Shifts) { ShiftsScreen(storeId = storeId, registerId = registerId, employeeId = employeeId) }
    posComposable(Screen.Settings) { SettingsScreen() }
    posComposable(Screen.Migration) { MigrationScreen(employeeId = employeeId) }
    posComposable(Screen.Reservations) { ReservationsScreen(storeId = storeId) }
    posComposable(Screen.Diagnostics) { DiagnosticsPlaceholder() }
    posComposable(Screen.Onboarding) { OnboardingPlaceholder() }
    posComposable(Screen.Login) { LoginPlaceholder() }
}

private fun NavGraphBuilder.salesGraph(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId
) {
    posComposable(Screen.OrderDetail, deepLinks = getDeepLinksForScreen(Screen.OrderDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for order detail")
        OrderDetailPlaceholder(id)
    }

    posComposable(Screen.OrderHistory) { OrderHistoryPlaceholder() }

    posComposable(Screen.RefundScreen, deepLinks = getDeepLinksForScreen(Screen.RefundScreen)) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) onRouteError("Missing orderId for refund")
        RefundPlaceholder(orderId)
    }

    posComposable(Screen.ShiftDetail, deepLinks = getDeepLinksForScreen(Screen.ShiftDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        ShiftDetailPlaceholder(id)
    }

    posComposable(Screen.ShiftOpen) { ShiftOpenPlaceholder() }
    posComposable(Screen.ShiftClose) { ShiftClosePlaceholder() }
}

private fun NavGraphBuilder.catalogGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.ProductDetail, deepLinks = getDeepLinksForScreen(Screen.ProductDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for product detail")
        ProductDetailPlaceholder(id)
    }

    posComposable(Screen.CategoryDetail, deepLinks = getDeepLinksForScreen(Screen.CategoryDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for category detail")
        CategoryDetailPlaceholder(id)
    }

    posComposable(Screen.ModifierEditor, deepLinks = getDeepLinksForScreen(Screen.ModifierEditor)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for modifier editor")
        ModifierEditorPlaceholder(id)
    }
}

private fun NavGraphBuilder.inventoryGraph(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId,
    employeeId: EmployeeId
) {
    posComposable(Screen.InventoryDetail, deepLinks = getDeepLinksForScreen(Screen.InventoryDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for inventory detail")
        InventoryDetailPlaceholder(id)
    }

    posComposable(Screen.StockAdjustment, deepLinks = getDeepLinksForScreen(Screen.StockAdjustment)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for stock adjustment")
        StockAdjustmentPlaceholder(id)
    }

    posComposable(Screen.PurchaseOrder, deepLinks = getDeepLinksForScreen(Screen.PurchaseOrder)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for purchase order")
        PurchaseOrderPlaceholder(id)
    }

    posComposable(Screen.SupplierDetail, deepLinks = getDeepLinksForScreen(Screen.SupplierDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for supplier detail")
        SupplierDetailPlaceholder(id)
    }
}

private fun NavGraphBuilder.customerGraph(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId
) {
    posComposable(Screen.CustomerEdit, deepLinks = getDeepLinksForScreen(Screen.CustomerEdit)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for customer edit")
        CustomerEditPlaceholder(id)
    }

    posComposable(Screen.CustomerAdd) { CustomerAddPlaceholder() }
}

private fun NavGraphBuilder.restaurantGraph(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId
) {
    posComposable(Screen.ReservationDetail, deepLinks = getDeepLinksForScreen(Screen.ReservationDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for reservation detail")
        ReservationDetailPlaceholder(id)
    }

    posComposable(Screen.TableDetail, deepLinks = getDeepLinksForScreen(Screen.TableDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for table detail")
        TableDetailPlaceholder(id)
    }
}

private fun NavGraphBuilder.employeeGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.EmployeeDetail, deepLinks = getDeepLinksForScreen(Screen.EmployeeDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for employee detail")
        EmployeeDetailPlaceholder(id)
    }

    posComposable(Screen.EmployeeEdit, deepLinks = getDeepLinksForScreen(Screen.EmployeeEdit)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for employee edit")
        EmployeeEditPlaceholder(id)
    }

    posComposable(Screen.RoleEditor, deepLinks = getDeepLinksForScreen(Screen.RoleEditor)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for role editor")
        RoleEditorPlaceholder(id)
    }
}

private fun NavGraphBuilder.reportGraph(
    navController: NavController,
    onRouteError: (String) -> Unit,
    storeId: StoreId
) {
    posComposable(Screen.ReportDetail, deepLinks = getDeepLinksForScreen(Screen.ReportDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for report detail")
        ReportDetailPlaceholder(id)
    }

    posComposable(Screen.ReportExport, deepLinks = getDeepLinksForScreen(Screen.ReportExport)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for report export")
        ReportExportPlaceholder(id)
    }
}

private fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.SettingsStore) { SettingsStorePlaceholder() }
    posComposable(Screen.SettingsRegister) { SettingsRegisterPlaceholder() }
    posComposable(Screen.SettingsPayment) { SettingsPaymentPlaceholder() }
    posComposable(Screen.SettingsTax) { SettingsTaxPlaceholder() }
    posComposable(Screen.SettingsReceipt) { SettingsReceiptPlaceholder() }
    posComposable(Screen.SettingsHardware) { SettingsHardwarePlaceholder() }
    posComposable(Screen.SettingsBackup) { SettingsBackupPlaceholder() }
    posComposable(Screen.SettingsAdvanced) { SettingsAdvancedPlaceholder() }
}

private fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.OnboardingStore) { OnboardingStorePlaceholder() }
    posComposable(Screen.OnboardingRegister) { OnboardingRegisterPlaceholder() }
    posComposable(Screen.OnboardingEmployee) { OnboardingEmployeePlaceholder() }
    posComposable(Screen.OnboardingProduct) { OnboardingProductPlaceholder() }
    posComposable(Screen.OnboardingPayment) { OnboardingPaymentPlaceholder() }
    posComposable(Screen.OnboardingComplete) { OnboardingCompletePlaceholder() }
}

private fun NavGraphBuilder.giftCardGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.GiftCardDetail, deepLinks = getDeepLinksForScreen(Screen.GiftCardDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for gift card detail")
        GiftCardDetailPlaceholder(id)
    }

    posComposable(Screen.GiftCardAdd) { GiftCardAddPlaceholder() }
}

private fun NavGraphBuilder.promotionGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.PromotionDetail, deepLinks = getDeepLinksForScreen(Screen.PromotionDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for promotion detail")
        PromotionDetailPlaceholder(id)
    }

    posComposable(Screen.PromotionAdd) { PromotionAddPlaceholder() }
}

private fun NavGraphBuilder.returnGraph(
    navController: NavController,
    onRouteError: (String) -> Unit
) {
    posComposable(Screen.ReturnDetail, deepLinks = getDeepLinksForScreen(Screen.ReturnDetail)) { entry ->
        val id = ScreenArguments.idOrNull(entry)
        if (id == null) onRouteError("Missing id for return detail")
        ReturnDetailPlaceholder(id)
    }

    posComposable(Screen.ReturnCreate, deepLinks = getDeepLinksForScreen(Screen.ReturnCreate)) { entry ->
        val orderId = ScreenArguments.orderIdOrNull(entry)
        if (orderId == null) onRouteError("Missing orderId for return create")
        ReturnCreatePlaceholder(orderId)
    }
}

// ==================== PLACEHOLDER COMPOSABLES ====================
// These are stubs for screens that are not yet implemented in feature modules.
// As feature modules are completed, replace these with actual screen composables.

@Composable private fun OrderDetailPlaceholder(id: String?) = ScreenPlaceholder("Order: $id")
@Composable private fun OrderHistoryPlaceholder() = ScreenPlaceholder("Order History")
@Composable private fun RefundPlaceholder(orderId: String?) = ScreenPlaceholder("Refund: $orderId")
@Composable private fun ShiftDetailPlaceholder(id: String?) = ScreenPlaceholder("Shift: $id")
@Composable private fun ShiftOpenPlaceholder() = ScreenPlaceholder("Open Shift")
@Composable private fun ShiftClosePlaceholder() = ScreenPlaceholder("Close Shift")
@Composable private fun ProductDetailPlaceholder(id: String?) = ScreenPlaceholder("Product: $id")
@Composable private fun CategoryDetailPlaceholder(id: String?) = ScreenPlaceholder("Category: $id")
@Composable private fun ModifierEditorPlaceholder(id: String?) = ScreenPlaceholder("Modifier: $id")
@Composable private fun InventoryDetailPlaceholder(id: String?) = ScreenPlaceholder("Inventory: $id")
@Composable private fun StockAdjustmentPlaceholder(id: String?) = ScreenPlaceholder("Stock Adjustment: $id")
@Composable private fun PurchaseOrderPlaceholder(id: String?) = ScreenPlaceholder("Purchase Order: $id")
@Composable private fun SupplierDetailPlaceholder(id: String?) = ScreenPlaceholder("Supplier: $id")
@Composable private fun CustomerEditPlaceholder(id: String?) = ScreenPlaceholder("Edit Customer: $id")
@Composable private fun CustomerAddPlaceholder() = ScreenPlaceholder("Add Customer")
@Composable private fun ReservationDetailPlaceholder(id: String?) = ScreenPlaceholder("Reservation: $id")
@Composable private fun TableDetailPlaceholder(id: String?) = ScreenPlaceholder("Table: $id")
@Composable private fun EmployeeDetailPlaceholder(id: String?) = ScreenPlaceholder("Employee: $id")
@Composable private fun EmployeeEditPlaceholder(id: String?) = ScreenPlaceholder("Edit Employee: $id")
@Composable private fun RoleEditorPlaceholder(id: String?) = ScreenPlaceholder("Role: $id")
@Composable private fun ReportDetailPlaceholder(id: String?) = ScreenPlaceholder("Report: $id")
@Composable private fun ReportExportPlaceholder(id: String?) = ScreenPlaceholder("Export Report: $id")
@Composable private fun GiftCardDetailPlaceholder(id: String?) = ScreenPlaceholder("Gift Card: $id")
@Composable private fun GiftCardAddPlaceholder() = ScreenPlaceholder("Add Gift Card")
@Composable private fun PromotionDetailPlaceholder(id: String?) = ScreenPlaceholder("Promotion: $id")
@Composable private fun PromotionAddPlaceholder() = ScreenPlaceholder("Add Promotion")
@Composable private fun ReturnDetailPlaceholder(id: String?) = ScreenPlaceholder("Return: $id")
@Composable private fun ReturnCreatePlaceholder(orderId: String?) = ScreenPlaceholder("Create Return: $orderId")
@Composable private fun SettingsStorePlaceholder() = ScreenPlaceholder("Store Settings")
@Composable private fun SettingsRegisterPlaceholder() = ScreenPlaceholder("Register Settings")
@Composable private fun SettingsPaymentPlaceholder() = ScreenPlaceholder("Payment Settings")
@Composable private fun SettingsTaxPlaceholder() = ScreenPlaceholder("Tax Settings")
@Composable private fun SettingsReceiptPlaceholder() = ScreenPlaceholder("Receipt Settings")
@Composable private fun SettingsHardwarePlaceholder() = ScreenPlaceholder("Hardware Settings")
@Composable private fun SettingsBackupPlaceholder() = ScreenPlaceholder("Backup Settings")
@Composable private fun SettingsAdvancedPlaceholder() = ScreenPlaceholder("Advanced Settings")
@Composable private fun OnboardingStorePlaceholder() = ScreenPlaceholder("Store Setup")
@Composable private fun OnboardingRegisterPlaceholder() = ScreenPlaceholder("Register Setup")
@Composable private fun OnboardingEmployeePlaceholder() = ScreenPlaceholder("Employee Setup")
@Composable private fun OnboardingProductPlaceholder() = ScreenPlaceholder("Product Setup")
@Composable private fun OnboardingPaymentPlaceholder() = ScreenPlaceholder("Payment Setup")
@Composable private fun OnboardingCompletePlaceholder() = ScreenPlaceholder("Setup Complete")
@Composable private fun DiagnosticsPlaceholder() = ScreenPlaceholder("Diagnostics")
@Composable private fun LoginPlaceholder() = ScreenPlaceholder("Login")

@Composable
private fun ScreenPlaceholder(title: String) {
    androidx.compose.material3.Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(16.dp)
    )
}
