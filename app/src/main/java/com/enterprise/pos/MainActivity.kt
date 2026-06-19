package com.enterprise.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enterprise.pos.feature.catalog.screen.CatalogScreen
import com.enterprise.pos.feature.customers.screen.CustomerDetailScreen
import com.enterprise.pos.feature.customers.screen.CustomersScreen
import com.enterprise.pos.feature.dashboard.screen.DashboardScreen
import com.enterprise.pos.feature.employees.screen.EmployeesManagementScreen
import com.enterprise.pos.feature.employees.screen.LoginScreen
import com.enterprise.pos.feature.employees.state.EmployeesViewModel
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
import com.enterprise.pos.ui.nav.MoreTab
import com.enterprise.pos.ui.nav.Screen
import com.enterprise.pos.ui.nav.bottomNavItems
import com.enterprise.pos.ui.theme.PosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                val employeesVm: EmployeesViewModel = hiltViewModel()
                val empState by employeesVm.state.collectAsStateWithLifecycle()

                val navController = rememberNavController()
                val currentEmployee = empState.currentEmployee
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                if (currentEmployee == null) {
                    LoginScreen(onLoginSuccess = { /* state updates via VM */ })
                    return@PosTheme
                }

                // Demo IDs — in production these come from a ConfigRepository
                val storeId = com.enterprise.pos.core.StoreId("store-demo-001")
                val registerId = com.enterprise.pos.core.RegisterId("register-001")

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Enterprise POS", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            listOf(
                                Screen.Dashboard, Screen.Floor, Screen.Catalog, Screen.Kds,
                                Screen.Reservations, Screen.Customers, Screen.Inventory,
                                Screen.Employees, Screen.Shifts, Screen.Reports,
                                Screen.Migration, Screen.Settings
                            ).forEach { screen ->
                                NavigationDrawerItem(
                                    label = { Text(screen.label) },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(screen.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(screen.icon, null) }
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        bottomBar = {
                            val currentRoute = navController.currentBackStackEntry?.destination?.route
                            val showBar = currentRoute in bottomNavItems.map { it.route } || currentRoute == MoreTab.route
                            if (showBar) {
                                NavigationBar {
                                    bottomNavItems.forEach { screen ->
                                        NavigationBarItem(
                                            selected = currentRoute == screen.route,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(screen.icon, screen.label) },
                                            label = { Text(screen.label) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Dashboard.route,
                            modifier = Modifier.fillMaxSize().padding(padding)
                        ) {
                            composable(Screen.Dashboard.route) {
                                DashboardScreen(
                                    storeId = storeId,
                                    onNavigateToFloor = { navController.navigate(Screen.Floor.route) },
                                    onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                                    onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                                    onNavigateToShifts = { navController.navigate(Screen.Shifts.route) },
                                    onNavigateToKds = { navController.navigate(Screen.Kds.route) },
                                    onNavigateToMigration = { navController.navigate(Screen.Migration.route) }
                                )
                            }
                            composable(Screen.Floor.route) {
                                FloorScreen(
                                    storeId = storeId,
                                    registerId = registerId,
                                    employeeId = currentEmployee.id,
                                    onOrderCreated = { orderId, _ -> navController.navigate(Screen.Cart.build(orderId)) }
                                )
                            }
                            composable(Screen.Catalog.route) {
                                CatalogScreen(onProductClick = { })
                            }
                            composable(Screen.Kds.route) {
                                KdsScreen(storeId = storeId)
                            }
                            composable(Screen.Reservations.route) {
                                ReservationsScreen(storeId = storeId)
                            }
                            composable(
                                route = Screen.Cart.route,
                                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                            ) { entry ->
                                val orderId = com.enterprise.pos.core.OrderId(entry.arguments?.getString("orderId") ?: return@composable)
                                CartScreen(
                                    orderId = orderId,
                                    requestingEmployee = currentEmployee.id,
                                    onCheckout = { id -> navController.navigate(Screen.Checkout.build(id.value)) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = Screen.Checkout.route,
                                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                            ) { entry ->
                                val orderId = com.enterprise.pos.core.OrderId(entry.arguments?.getString("orderId") ?: return@composable)
                                CheckoutScreen(
                                    orderId = orderId,
                                    amountDue = com.enterprise.pos.core.Money.of(0.0),
                                    employeeId = currentEmployee.id,
                                    onComplete = { navController.popBackStack(Screen.Dashboard.route, inclusive = false) },
                                    onCancel = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Customers.route) {
                                CustomersScreen(onCustomerSelected = { id -> navController.navigate(Screen.CustomerDetail.build(id.value)) })
                            }
                            composable(
                                route = Screen.CustomerDetail.route,
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { entry ->
                                val customerId = com.enterprise.pos.core.CustomerId(entry.arguments?.getString("id") ?: return@composable)
                                CustomerDetailScreen(
                                    customerId = customerId,
                                    storeId = storeId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Employees.route) { EmployeesManagementScreen() }
                            composable(Screen.Reports.route) { ReportsScreen(storeId = storeId) }
                            composable(Screen.Inventory.route) {
                                InventoryScreen(storeId = storeId, employeeId = currentEmployee.id)
                            }
                            composable(Screen.Shifts.route) {
                                ShiftsScreen(storeId = storeId, registerId = registerId, employeeId = currentEmployee.id)
                            }
                            composable(Screen.Settings.route) { SettingsScreen() }
                            composable(Screen.Migration.route) { MigrationScreen(employeeId = currentEmployee.id) }
                            composable(MoreTab.route) {
                                MoreTabContent { route -> navController.navigate(route) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreTabContent(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val items = listOf(
            Screen.Reservations, Screen.Kds, Screen.Inventory, Screen.Employees,
            Screen.Shifts, Screen.Migration, Screen.Settings
        )
        items.forEach { screen ->
            ListItem(
                headlineContent = { Text(screen.label) },
                leadingContent = { Icon(screen.icon, null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
            HorizontalDivider()
        }
    }
}
