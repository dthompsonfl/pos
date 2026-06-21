package com.enterprise.pos.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class representing all navigable screens in the EnterprisePOS application.
 *
 * @property route The navigation route string. May contain path parameters (e.g., "product/{id}").
 * @property label The human-readable label for the screen, used in navigation UI.
 * @property icon The icon to display in navigation elements.
 * @property baseRoute The route without path parameters, used for matching and grouping.
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {

    /** The base route without path parameters. */
    val baseRoute: String
        get() = route.replace(Regex("/\\{[^}]+}"), "")

    // ==================== CORE SCREENS ====================

    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Dashboard)
    data object Floor : Screen("floor", "Tables", Icons.Filled.TableRestaurant)
    data object Catalog : Screen("catalog", "Menu", Icons.Filled.RestaurantMenu)
    data object Kds : Screen("kds", "Kitchen", Icons.Filled.Restaurant)

    data object Cart : Screen("cart/{orderId}", "Cart", Icons.Filled.ShoppingCart) {
        fun build(orderId: String): String = "cart/$orderId"
    }

    data object Checkout : Screen("checkout/{orderId}", "Checkout", Icons.Filled.Payments) {
        fun build(orderId: String): String = "checkout/$orderId"
    }

    data object Customers : Screen("customers", "Customers", Icons.Filled.People)

    data object CustomerDetail : Screen("customer/{id}", "Customer", Icons.Filled.Person) {
        fun build(id: String): String = "customer/$id"
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

    // ==================== CATALOG SCREENS (feature-catalog) ====================

    data object ProductDetail : Screen("product/{id}", "Product", Icons.Filled.Fastfood) {
        fun build(id: String): String = "product/$id"
    }

    data object CategoryDetail : Screen("category/{id}", "Category", Icons.Filled.Category) {
        fun build(id: String): String = "category/$id"
    }

    data object ModifierEditor : Screen("modifier/{id}", "Modifier", Icons.Filled.Edit) {
        fun build(id: String): String = "modifier/$id"
    }

    data object ProductEdit : Screen("product/edit/{id}", "Edit Product", Icons.Filled.Edit) {
        fun build(id: String): String = "product/edit/$id"
    }

    // ==================== SALES SCREENS (feature-sales) ====================

    data object OrderDetail : Screen("order/{id}", "Order", Icons.Filled.Receipt) {
        fun build(id: String): String = "order/$id"
    }

    data object OrderHistory : Screen("orders", "Orders", Icons.Filled.History)

    data object RefundScreen : Screen("refund/{orderId}", "Refund", Icons.Filled.MoneyOff) {
        fun build(orderId: String): String = "refund/$orderId"
    }

    // ==================== SHIFT SCREENS (feature-shifts) ====================

    data object ShiftDetail : Screen("shift/{id}", "Shift", Icons.Filled.LockClock) {
        fun build(id: String): String = "shift/$id"
    }

    data object ShiftOpen : Screen("shift/open", "Open Shift", Icons.Filled.LockOpen)
    data object ShiftClose : Screen("shift/close", "Close Shift", Icons.Filled.Lock)

    // ==================== INVENTORY SCREENS (feature-inventory) ====================

    data object InventoryDetail : Screen("inventory/{id}", "Inventory", Icons.Filled.Inventory2) {
        fun build(id: String): String = "inventory/$id"
    }

    data object StockAdjustment : Screen("inventory/adjust/{id}", "Adjust Stock", Icons.Filled.Tune) {
        fun build(id: String): String = "inventory/adjust/$id"
    }

    data object PurchaseOrder : Screen("purchase/{id}", "Purchase Order", Icons.Filled.ShoppingBag) {
        fun build(id: String): String = "purchase/$id"
    }

    data object SupplierDetail : Screen("supplier/{id}", "Supplier", Icons.Filled.LocalShipping) {
        fun build(id: String): String = "supplier/$id"
    }

    // ==================== CUSTOMER SCREENS (feature-customers) ====================

    data object CustomerEdit : Screen("customer/edit/{id}", "Edit Customer", Icons.Filled.Edit) {
        fun build(id: String): String = "customer/edit/$id"
    }

    data object CustomerAdd : Screen("customer/add", "Add Customer", Icons.Filled.PersonAdd)

    // ==================== RESTAURANT SCREENS (feature-restaurant) ====================

    data object ReservationDetail : Screen("reservation/{id}", "Reservation", Icons.Filled.EventNote) {
        fun build(id: String): String = "reservation/$id"
    }

    data object TableDetail : Screen("table/{id}", "Table", Icons.Filled.TableRestaurant) {
        fun build(id: String): String = "table/$id"
    }

    // ==================== EMPLOYEE SCREENS (feature-employees) ====================

    data object EmployeeDetail : Screen("employee/{id}", "Employee", Icons.Filled.Badge) {
        fun build(id: String): String = "employee/$id"
    }

    data object EmployeeEdit : Screen("employee/edit/{id}", "Edit Employee", Icons.Filled.Edit) {
        fun build(id: String): String = "employee/edit/$id"
    }

    data object RoleEditor : Screen("role/{id}", "Role", Icons.Filled.AdminPanelSettings) {
        fun build(id: String): String = "role/$id"
    }

    // ==================== REPORT SCREENS (feature-reports) ====================

    data object ReportDetail : Screen("report/{id}", "Report", Icons.Filled.Assessment) {
        fun build(id: String): String = "report/$id"
    }

    data object ReportExport : Screen("report/export/{id}", "Export Report", Icons.Filled.FileDownload) {
        fun build(id: String): String = "report/export/$id"
    }

    // ==================== GIFT CARD SCREENS (feature-giftcards) ====================

    data object GiftCardDetail : Screen("giftcard/{id}", "Gift Card", Icons.Filled.CardGiftcard) {
        fun build(id: String): String = "giftcard/$id"
    }

    data object GiftCardAdd : Screen("giftcard/add", "Add Gift Card", Icons.Filled.AddCard)

    // ==================== PROMOTION SCREENS (feature-promotions) ====================

    data object PromotionDetail : Screen("promotion/{id}", "Promotion", Icons.Filled.LocalOffer) {
        fun build(id: String): String = "promotion/$id"
    }

    data object PromotionAdd : Screen("promotion/add", "Add Promotion", Icons.Filled.AddCircle)

    // ==================== RETURN SCREENS (feature-returns) ====================

    data object ReturnDetail : Screen("return/{id}", "Return", Icons.Filled.AssignmentReturn) {
        fun build(id: String): String = "return/$id"
    }

    data object ReturnCreate : Screen("return/create/{orderId}", "Create Return", Icons.Filled.NoteAdd) {
        fun build(orderId: String): String = "return/create/$orderId"
    }

    // ==================== SETTINGS SCREENS (feature-settings) ====================

    data object SettingsStore : Screen("settings/store", "Store Settings", Icons.Filled.Store)
    data object SettingsRegister : Screen("settings/register", "Register Settings", Icons.Filled.PointOfSale)
    data object SettingsPayment : Screen("settings/payment", "Payment Settings", Icons.Filled.CreditCard)
    data object SettingsTax : Screen("settings/tax", "Tax Settings", Icons.Filled.AccountBalance)
    data object SettingsReceipt : Screen("settings/receipt", "Receipt Settings", Icons.Filled.Receipt)
    data object SettingsHardware : Screen("settings/hardware", "Hardware Settings", Icons.Filled.Devices)
    data object SettingsBackup : Screen("settings/backup", "Backup Settings", Icons.Filled.Backup)
    data object SettingsAdvanced : Screen("settings/advanced", "Advanced Settings", Icons.Filled.Tune)

    // ==================== ONBOARDING SCREENS (feature-settings) ====================

    data object OnboardingStore : Screen("onboarding/store", "Store Setup", Icons.Filled.Store)
    data object OnboardingRegister : Screen("onboarding/register", "Register Setup", Icons.Filled.PointOfSale)
    data object OnboardingEmployee : Screen("onboarding/employee", "Employee Setup", Icons.Filled.People)
    data object OnboardingProduct : Screen("onboarding/product", "Product Setup", Icons.Filled.Fastfood)
    data object OnboardingPayment : Screen("onboarding/payment", "Payment Setup", Icons.Filled.CreditCard)
    data object OnboardingComplete : Screen("onboarding/complete", "Setup Complete", Icons.Filled.CheckCircle)

    companion object {
        /** All registered screens. */
        val all: List<Screen> by lazy {
            listOf(
                Dashboard, Floor, Catalog, Kds, Cart, Checkout, Customers, CustomerDetail,
                Employees, Reports, Inventory, Shifts, Settings, Migration, Reservations,
                Diagnostics, Onboarding, Login, ProductDetail, CategoryDetail, ModifierEditor,
                ProductEdit, InventoryDetail, StockAdjustment, PurchaseOrder, SupplierDetail,
                CustomerEdit, CustomerAdd, ReservationDetail, TableDetail, EmployeeDetail,
                EmployeeEdit, RoleEditor, SettingsStore, SettingsRegister, SettingsPayment,
                SettingsTax, SettingsReceipt, SettingsHardware, SettingsBackup, SettingsAdvanced
            )
        }

        /** Find a Screen by its exact route or base route. */
        fun fromRoute(route: String?): Screen? {
            if (route == null) return null
            val routeWithoutQuery = route.substringBefore("?")
            return all.find {
                it.route == route ||
                it.route == routeWithoutQuery ||
                it.baseRoute == route ||
                it.baseRoute == routeWithoutQuery ||
                routeWithoutQuery.startsWith(it.baseRoute + "/") ||
                (it.route.contains("/{") && routeWithoutQuery.startsWith(it.baseRoute + "/"))
            }
        }
    }
}

/** The "More" tab opens the navigation drawer for additional features. */
data object MoreTab : Screen("more", "More", Icons.Filled.Menu)

// ==================== NAVIGATION ITEM SETS ====================

/** Items displayed in the bottom navigation bar. */
val bottomNavItems: List<Screen> = listOf(
    Screen.Dashboard, Screen.Floor, Screen.Catalog, Screen.Reports, MoreTab
)

/** Items displayed in the navigation rail (medium screens). */
val railNavItems: List<Screen> = listOf(
    Screen.Dashboard, Screen.Floor, Screen.Catalog, Screen.Kds, Screen.Reports, Screen.Customers
)

/** Items displayed in the permanent navigation drawer (expanded screens). */
val drawerNavItems: List<Screen> = listOf(
    Screen.Dashboard, Screen.Floor, Screen.Catalog, Screen.Kds,
    Screen.Reports, Screen.Customers, Screen.Employees, Screen.Inventory,
    Screen.Shifts, Screen.Reservations, Screen.Settings, Screen.Diagnostics
)

// ==================== SCREEN GROUPS ====================

/**
 * Organizes screens into logical groups for permissions, analytics, and UI sections.
 */
sealed class ScreenGroup(val label: String, val screens: List<Screen>) {
    data object Sales : ScreenGroup(
        "Sales",
        listOf(Screen.Dashboard, Screen.Floor, Screen.Catalog, Screen.Cart, Screen.Checkout, Screen.Kds)
    )

    data object Operations : ScreenGroup(
        "Operations",
        listOf(
            Screen.Reservations, Screen.Customers, Screen.CustomerDetail,
            Screen.CustomerAdd, Screen.CustomerEdit
        )
    )

    data object Inventory : ScreenGroup(
        "Inventory",
        listOf(
            Screen.Inventory, Screen.InventoryDetail, Screen.StockAdjustment,
            Screen.PurchaseOrder, Screen.SupplierDetail
        )
    )

    data object Admin : ScreenGroup(
        "Administration",
        listOf(
            Screen.Employees, Screen.EmployeeDetail, Screen.EmployeeEdit, Screen.RoleEditor,
            Screen.Reports, Screen.Shifts
        )
    )

    data object Settings : ScreenGroup(
        "Settings",
        listOf(
            Screen.Settings, Screen.SettingsStore, Screen.SettingsRegister, Screen.SettingsPayment,
            Screen.SettingsTax, Screen.SettingsReceipt, Screen.SettingsHardware, Screen.SettingsBackup,
            Screen.SettingsAdvanced, Screen.Onboarding, Screen.Migration, Screen.Diagnostics
        )
    )

    data object Catalog : ScreenGroup(
        "Catalog",
        listOf(
            Screen.ProductDetail, Screen.CategoryDetail, Screen.ModifierEditor, Screen.ProductEdit
        )
    )

    data object Restaurant : ScreenGroup(
        "Restaurant",
        listOf(Screen.ReservationDetail, Screen.TableDetail)
    )

    companion object {
        val all: List<ScreenGroup> by lazy {
            listOf(Sales, Operations, Inventory, Admin, Settings, Catalog, Restaurant)
        }

        /** Find which group a screen belongs to. */
        fun groupFor(screen: Screen): ScreenGroup? {
            return all.find { it.screens.contains(screen) }
        }
    }
}

// ==================== SCREEN ARGUMENTS ====================

/**
 * Helper class for type-safe extraction of navigation arguments from a NavBackStackEntry.
 */
object ScreenArguments {

    fun orderId(entry: androidx.navigation.NavBackStackEntry): String =
        entry.arguments?.getString("orderId") ?: throw IllegalArgumentException("Missing orderId")

    fun id(entry: androidx.navigation.NavBackStackEntry): String =
        entry.arguments?.getString("id") ?: throw IllegalArgumentException("Missing id")

    fun idOrNull(entry: androidx.navigation.NavBackStackEntry): String? =
        entry.arguments?.getString("id")

    fun orderIdOrNull(entry: androidx.navigation.NavBackStackEntry): String? =
        entry.arguments?.getString("orderId")

    fun stringArg(entry: androidx.navigation.NavBackStackEntry, key: String): String? =
        entry.arguments?.getString(key)

    fun stringArgRequired(entry: androidx.navigation.NavBackStackEntry, key: String): String =
        entry.arguments?.getString(key) ?: throw IllegalArgumentException("Missing required argument: $key")

    fun intArg(entry: androidx.navigation.NavBackStackEntry, key: String, default: Int = 0): Int =
        entry.arguments?.getInt(key, default) ?: default

    fun booleanArg(entry: androidx.navigation.NavBackStackEntry, key: String, default: Boolean = false): Boolean =
        entry.arguments?.getBoolean(key, default) ?: default
}
