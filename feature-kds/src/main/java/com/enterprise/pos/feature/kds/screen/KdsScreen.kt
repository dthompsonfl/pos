package com.enterprise.pos.feature.kds.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.feature.kds.state.KdsTicket
import com.enterprise.pos.feature.kds.state.KdsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Kitchen Display System — large-format cards per order, grouped by station. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    storeId: StoreId,
    viewModel: KdsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId) { viewModel.load(storeId) }

    val allStations = state.tickets.flatMap { it.stationItems.keys }.distinct().sorted()
    val filtered = if (state.stationFilter != null) state.tickets.filter { state.stationFilter in it.stationItems }
        else state.tickets

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitchen Display", fontWeight = FontWeight.Bold) },
                actions = {
                    LazyRow {
                        item {
                            FilterChip(
                                selected = state.stationFilter == null,
                                onClick = { viewModel.filterStation(null) },
                                label = { Text("All") }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        items(allStations) { s ->
                            FilterChip(
                                selected = state.stationFilter == s,
                                onClick = { viewModel.filterStation(s) },
                                label = { Text(s.replaceFirstChar { it.titlecase() }) }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (filtered.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(8.dp))
                    Text("All caught up!", style = MaterialTheme.typography.headlineSmall)
                    Text("No active kitchen tickets.", color = MaterialTheme.colorScheme.outline)
                }
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.order.id.value }) { ticket ->
                KdsTicketCard(
                    ticket = ticket,
                    stationFilter = state.stationFilter,
                    onMarkReady = { viewModel.markReady(ticket.order.id) },
                    onMarkServed = { viewModel.markServed(ticket.order.id) },
                    onRecall = { viewModel.recall(ticket.order.id) }
                )
            }
        }
    }
}

@Composable
private fun KdsTicketCard(
    ticket: KdsTicket,
    stationFilter: String?,
    onMarkReady: () -> Unit,
    onMarkServed: () -> Unit,
    onRecall: () -> Unit
) {
    val borderColor = when {
        ticket.isCritical -> Color(0xFFD32F2F)
        ticket.isUrgent -> Color(0xFFFFA000)
        ticket.order.status == OrderStatus.READY -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = when (ticket.order.status) {
        OrderStatus.READY -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        else -> if (ticket.isCritical) Color(0xFFD32F2F).copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header: order # + table + elapsed time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${ticket.order.id.value.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ticket.order.tableName?.let {
                    Surface(color = borderColor, shape = RoundedCornerShape(8.dp)) {
                        Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    formatElapsed(ticket.elapsedMs),
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${ticket.order.diningMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }} · ${ticket.order.guestCount} guests",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            ticket.order.notes?.takeIf { it.isNotBlank() }?.let {
                Text("📝 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Items grouped by station
            val stationsToShow = stationFilter?.let { mapOf(it to (ticket.stationItems[it] ?: emptyList())) } ?: ticket.stationItems
            stationsToShow.forEach { (station, lines) ->
                if (stationFilter == null) {
                    Text(station.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                lines.forEach { line -> KdsLineItem(line) }
                Spacer(Modifier.height(8.dp))
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ticket.order.status == OrderStatus.READY) {
                    Button(onClick = onMarkServed, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Filled.DeliveryDining, null); Spacer(Modifier.width(4.dp)); Text("Served")
                    }
                    OutlinedButton(onClick = onRecall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(4.dp)); Text("Recall")
                    }
                } else {
                    Button(onClick = onMarkReady, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Check, null); Spacer(Modifier.width(4.dp)); Text("Mark Ready")
                    }
                }
            }
        }
    }
}

@Composable
private fun KdsLineItem(line: OrderLine) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("${line.quantity.asInt}×", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(line.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            line.notes?.takeIf { it.isNotBlank() }?.let {
                Text("↳ $it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            // Modifier lines
            line.modifiers.forEach { mod ->
                Text("  + ${mod.name}", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val mins = ms / 60_000
    val secs = (ms % 60_000) / 1000
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m"
    else "%d:%02d".format(mins, secs)
}
