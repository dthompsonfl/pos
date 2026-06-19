package com.enterprise.pos.feature.dashboard.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.DashboardAlert
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.model.AlertSeverity
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.feature.dashboard.state.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** The home screen — real-time executive dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    storeId: StoreId,
    onNavigateToFloor: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToShifts: () -> Unit,
    onNavigateToKds: () -> Unit,
    onNavigateToMigration: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId) { viewModel.load(storeId) }
    val snap = state.snapshot

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard", fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("EEEE, MMM d · h:mm a", Locale.US).format(Date()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNow(storeId) }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                    IconButton(onClick = { viewModel.toggleAutoRefresh() }) {
                        Icon(if (state.autoRefresh) Icons.Filled.Autorenew else Icons.Filled.Pause, "Auto-refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (snap == null && state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (snap == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "No data")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero row — today's sales + change vs yesterday
            item { Spacer(Modifier.height(8.dp)) }
            item { HeroSalesCard(snap) }

            // Quick actions
            item { QuickActionsRow(onNavigateToFloor, onNavigateToKds, onNavigateToShifts, onNavigateToInventory, onNavigateToMigration) }

            // KPI grid
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile("Transactions", snap.todayTransactions.toString(), Icons.Filled.Receipt, Modifier.weight(1f))
                    KpiTile("Avg Order", snap.todayAverageOrder.format(), Icons.Filled.TrendingUp, Modifier.weight(1f))
                    KpiTile("Tips", snap.todayTips.format(), Icons.Filled.TipsAndUpdates, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile("Active Orders", snap.activeOrders.toString(), Icons.Filled.ShoppingCart, Modifier.weight(1f))
                    KpiTile("Low Stock", snap.lowStockItems.toString(), Icons.Filled.Warning, Modifier.weight(1f))
                    KpiTile("Pending Sync", snap.pendingSyncCount.toString(), Icons.Filled.CloudSync, Modifier.weight(1f))
                }
            }

            // Alerts
            if (snap.alerts.isNotEmpty()) {
                item { Text("Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(snap.alerts) { alert -> AlertCard(alert) }
            }

            // Hourly sales chart
            item { Text("Hourly Sales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item { HourlySalesChart(snap.hourlySales) }

            // Top items
            item { Text("Top Items Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(snap.topProducts) { (name, count) ->
                ListItem(
                    headlineContent = { Text(name, maxLines = 1) },
                    trailingContent = { Text("$count sold", style = MaterialTheme.typography.labelLarge) }
                )
                HorizontalDivider()
            }

            // Top employees
            item { Text("Top Employees Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(snap.topEmployees) { emp -> EmployeeRow(emp) }

            // 30-day comparison
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sales Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        TrendRow("Yesterday", snap.yesterdaySales)
                        TrendRow("Last 7 days", snap.last7DaysSales)
                        TrendRow("Last 30 days", snap.last30DaysSales)
                    }
                }
            }

            item {
                Button(onClick = onNavigateToReports, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Analytics, null); Spacer(Modifier.width(8.dp)); Text("View Full Reports")
                }
            }
        }
    }
}

@Composable
private fun HeroSalesCard(snap: DashboardSnapshot) {
    val todayVsY = if (snap.yesterdaySales.isZero()) 0.0
        else (snap.todaySales.minorUnits - snap.yesterdaySales.minorUnits).toDouble() / snap.yesterdaySales.minorUnits * 100
    val trendColor = if (todayVsY >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Today's Sales", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(snap.todaySales.format(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (todayVsY >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    null, tint = trendColor
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${if (todayVsY >= 0) "+" else ""}${"%.1f".format(todayVsY)}% vs yesterday",
                    color = trendColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onFloor: () -> Unit, onKds: () -> Unit, onShifts: () -> Unit,
    onInventory: () -> Unit, onMigration: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionButton("New Order", Icons.Filled.PointOfSale, onFloor, Modifier.weight(1f))
        QuickActionButton("Kitchen", Icons.Filled.Restaurant, onKds, Modifier.weight(1f))
        QuickActionButton("Shift", Icons.Filled.LockClock, onShifts, Modifier.weight(1f))
        QuickActionButton("Stock", Icons.Filled.Inventory2, onInventory, Modifier.weight(1f))
        QuickActionButton("Migrate", Icons.Filled.CloudDownload, onMigration, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(88.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AlertCard(alert: DashboardAlert) {
    val color = when (alert.severity) {
        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
        AlertSeverity.WARNING -> Color(0xFFFFA000)
        AlertSeverity.ERROR -> MaterialTheme.colorScheme.error
        AlertSeverity.CRITICAL -> Color(0xFFD32F2F)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.title, fontWeight = FontWeight.Bold, color = color)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HourlySalesChart(hourly: List<SalesByHour>) {
    val maxSales = hourly.maxOfOrNull { it.grossSales.minorUnits } ?: 0L
    if (maxSales == 0L) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text("No sales yet today", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width
                val h = size.height
                val barWidth = w / hourly.size
                hourly.forEachIndexed { i, hour ->
                    val barH = (hour.grossSales.minorUnits.toFloat() / maxSales.toFloat()) * h * 0.85f
                    val startX = i * barWidth
                    val startY = h - barH
                    drawRect(
                        color = Color(0xFF1976D2),
                        topLeft = Offset(startX + 2, startY),
                        size = Size(barWidth - 4, barH)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12a", "6a", "12p", "6p", "12a").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun EmployeeRow(emp: SalesByEmployee) {
    ListItem(
        leadingContent = {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emp.employeeName.take(1).ifEmpty { "?" }, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        headlineContent = { Text(emp.employeeName) },
        supportingContent = { Text("${emp.transactionCount} orders · ${emp.itemsSold} items") },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(emp.grossSales.format(), fontWeight = FontWeight.SemiBold)
                Text("+${emp.tipsCollected.format()} tips", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun TrendRow(label: String, value: Money) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value.format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
