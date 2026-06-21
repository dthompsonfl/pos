package com.enterprise.pos.feature.reports.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AbcClass
import com.enterprise.pos.domain.model.SalesByCategory
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.domain.model.TaxLiabilityReport
import com.enterprise.pos.feature.reports.state.ReportTab
import com.enterprise.pos.feature.reports.state.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(storeId: StoreId, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId, state.dateFrom, state.dateTo) { viewModel.load(storeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* export to PDF */ }) {
                        Icon(Icons.Filled.PictureAsPdf, "Export PDF")
                    }
                    IconButton(onClick = { /* export to CSV */ }) {
                        Icon(Icons.Filled.FileDownload, "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            ScrollableTabRow(selectedTabIndex = state.activeTab.ordinal) {
                ReportTab.entries.forEachIndexed { idx, tab ->
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(tab.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) }
                    )
                }
            }

            when (state.activeTab) {
                ReportTab.OVERVIEW -> OverviewReport(state)
                ReportTab.HOURLY -> HourlyReport(state.hourlySales)
                ReportTab.CATEGORY -> CategoryReport(state.categorySales)
                ReportTab.EMPLOYEE -> EmployeeReport(state.employeeSales)
                ReportTab.TAX -> TaxReport(state.taxReport)
                ReportTab.ABC -> AbcReport(state.abcAnalysis)
                ReportTab.AUDIT -> AuditReportPlaceholder()
                ReportTab.Z_REPORTS -> ZReportsPlaceholder()
            }
        }
    }
}

@Composable
private fun OverviewReport(state: com.enterprise.pos.feature.reports.state.ReportsUiState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val totalRev = state.categorySales.fold(Money.ZERO) { a, c -> a + c.grossSales }
            val totalTx = state.employeeSales.sumOf { it.transactionCount }
            val totalTips = state.employeeSales.fold(Money.ZERO) { a, e -> a + e.tipsCollected }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigKpi("Revenue", totalRev.format(), Modifier.weight(1f))
                BigKpi("Transactions", totalTx.toString(), Modifier.weight(1f))
                BigKpi("Tips", totalTips.format(), Modifier.weight(1f))
            }
        }
        item {
            Text("Revenue by Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        items(state.categorySales.take(10)) { cat -> CategoryRow(cat) }
    }
}

@Composable
private fun HourlyReport(hourly: List<SalesByHour>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Revenue Heatmap (24h)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    HourlyBarChart(hourly)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { Text("Hourly Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(hourly.filter { it.transactionCount > 0 }) { h ->
            ListItem(
                headlineContent = { Text("${"%02d".format(h.hour)}:00 — ${"%02d".format(h.hour)}:59") },
                supportingContent = { Text("${h.transactionCount} orders · ${h.itemsSold} items") },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(h.grossSales.format(), fontWeight = FontWeight.SemiBold)
                        Text("AOV ${h.averageOrderValue.format()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun HourlyBarChart(hourly: List<SalesByHour>) {
    val maxSales = hourly.maxOfOrNull { it.grossSales.minorUnits } ?: 0L
    if (maxSales == 0L) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text("No data", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height
        val barWidth = w / 24f
        hourly.forEachIndexed { i, hour ->
            val barH = (hour.grossSales.minorUnits.toFloat() / maxSales.toFloat()) * h * 0.9f
            drawRect(
                color = Color(0xFF1976D2),
                topLeft = Offset(i * barWidth + 1, h - barH),
                size = Size(barWidth - 2, barH)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12a", "6a", "12p", "6p", "11p").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun CategoryReport(cats: List<SalesByCategory>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Revenue Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    // Stacked bar
                    val total = cats.sumOf { it.grossSales.minorUnits }.coerceAtLeast(1L)
                    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                        var x = 0f
                        val palette = listOf(
                            Color(0xFF1976D2), Color(0xFFFF7043), Color(0xFF4CAF50),
                            Color(0xFFAB47BC), Color(0xFFFFCA28), Color(0xFF26C6DA),
                            Color(0xFFEC407A), Color(0xFF8D6E63), Color(0xFF78909C)
                        )
                        cats.forEachIndexed { i, c ->
                            val width = (c.grossSales.minorUnits.toFloat() / total) * size.width
                            drawRect(
                                color = palette[i % palette.size],
                                topLeft = Offset(x, 0f),
                                size = Size(width, size.height)
                            )
                            x += width
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        items(cats) { cat -> CategoryRow(cat) }
    }
}

@Composable
private fun CategoryRow(cat: SalesByCategory) {
    ListItem(
        headlineContent = { Text(cat.categoryName) },
        supportingContent = { Text("${cat.itemsSold} units · ${cat.transactionCount} txns") },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(cat.grossSales.format(), fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(cat.percentage)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun EmployeeReport(emps: List<SalesByEmployee>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(emps) { emp ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emp.employeeName.take(1).ifEmpty { "?" }, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(emp.employeeName, fontWeight = FontWeight.SemiBold)
                            Text("${emp.transactionCount} orders · ${emp.itemsSold} items sold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(emp.grossSales.format(), fontWeight = FontWeight.Bold)
                            Text("+${emp.tipsCollected.format()} tips", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricChip("AOV", emp.averageOrderValue.format(), Modifier.weight(1f))
                        MetricChip("Hours", "%.1f".format(emp.hoursWorked), Modifier.weight(1f))
                        MetricChip("Sales/h", if (emp.hoursWorked > 0) Money.ofMinor(emp.grossSales.minorUnits / emp.hoursWorked.toLong()).format() else "—", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TaxReport(tax: TaxLiabilityReport?) {
    if (tax == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading...") }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Tax Liability Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Period: ${tax.period}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(12.dp))
                    TaxRow("Gross Sales", tax.grossSales)
                    TaxRow("Taxable Sales", tax.taxableSales)
                    TaxRow("Exempt Sales", tax.exemptSales)
                    TaxRow("Non-Taxable Sales", tax.nonTaxableSales)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    tax.taxCollected.forEach { (name, amt) -> TaxRow(name, amt) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total Tax Collected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.weight(1f))
                        Text(tax.taxCollectedTotal.format(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { /* export for filing */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export for Tax Filing")
            }
        }
    }
}

@Composable
private fun TaxRow(label: String, value: Money) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value.format(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AbcReport(items: List<AbcAnalysis>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("ABC Analysis (Pareto)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AbcLegend("A", "Top 80% revenue", Color(0xFF4CAF50), Modifier.weight(1f))
                        AbcLegend("B", "Next 15%", Color(0xFFFFA000), Modifier.weight(1f))
                        AbcLegend("C", "Bottom 5%", Color(0xFFEF5350), Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        items(items) { item ->
            val color = when (item.classification) {
                AbcClass.A -> Color(0xFF4CAF50)
                AbcClass.B -> Color(0xFFFFA000)
                AbcClass.C -> Color(0xFFEF5350)
            }
            ListItem(
                leadingContent = {
                    Surface(shape = RoundedCornerShape(8.dp), color = color) {
                        Text(item.classification.name, color = Color.White, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                    }
                },
                headlineContent = { Text(item.productName) },
                supportingContent = { Text("${item.unitsSold} units · ${"%.1f".format(item.revenueContribution)}% revenue") },
                trailingContent = { Text(item.revenue.format(), fontWeight = FontWeight.SemiBold) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AbcLegend(cls: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(16.dp)) {}
            Spacer(Modifier.width(8.dp))
            Column {
                Text(cls, fontWeight = FontWeight.Bold, color = color)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun AuditReportPlaceholder() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.History, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("Audit Log Report", style = MaterialTheme.typography.titleLarge)
            Text("Filter by action, employee, entity, or date range. Exportable for compliance.", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ZReportsPlaceholder() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Receipt, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("Z-Report Archive", style = MaterialTheme.typography.titleLarge)
            Text("Every shift's Z-report is stored permanently. Tap to view, reprint, or export.", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BigKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
