package com.enterprise.pos.feature.migration.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.MigrationJob
import com.enterprise.pos.domain.model.MigrationSource
import com.enterprise.pos.domain.model.MigrationStatus
import com.enterprise.pos.domain.model.MigrationType
import com.enterprise.pos.feature.migration.state.MigrationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MigrationSource.displayName: String
    get() = when (this) {
        MigrationSource.SHOPIFY -> "Shopify"
        MigrationSource.SQUARE -> "Square"
        MigrationSource.STRIPE -> "Stripe"
        MigrationSource.CSV -> "CSV"
        MigrationSource.OTHER -> "Other"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen(
    employeeId: EmployeeId,
    viewModel: MigrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Migration Center", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Hero card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Bring your existing data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Migrate your catalog, customers, orders, and payments from Shopify, Square, or Stripe. We support one-click imports and side-by-side comparison so you can switch with zero downtime.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Source picker
            item {
                Text("Choose Source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MigrationSourceCard(MigrationSource.SHOPIFY, "Shopify", "Products, customers, orders", Color(0xFF95BF47), Modifier.weight(1f)) {
                        viewModel.openCreateSheet(MigrationSource.SHOPIFY)
                    }
                    MigrationSourceCard(MigrationSource.SQUARE, "Square", "Catalog, payments, customers", Color(0xFF3E4348), Modifier.weight(1f)) {
                        viewModel.openCreateSheet(MigrationSource.SQUARE)
                    }
                    MigrationSourceCard(MigrationSource.STRIPE, "Stripe", "Payments, products, customers", Color(0xFF635BFF), Modifier.weight(1f)) {
                        viewModel.openCreateSheet(MigrationSource.STRIPE)
                    }
                }
            }

            // Job history
            item {
                Text("Migration History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.jobs) { job -> MigrationJobCard(job, onCancel = { viewModel.cancelJob(job.id) }) }
        }

        if (state.showCreateSheet) {
            MigrationConfigSheet(
                source = state.newSource,
                type = state.newType,
                configJson = state.configJson,
                isWorking = state.isWorking,
                onTypeChange = viewModel::setType,
                onConfigChange = viewModel::setConfigJson,
                onDismiss = viewModel::closeCreateSheet,
                onSubmit = { viewModel.startMigration(employeeId) }
            )
        }
    }
}

@Composable
private fun MigrationSourceCard(
    source: MigrationSource,
    name: String,
    description: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(120.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(brandColor),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun MigrationJobCard(job: MigrationJob, onCancel: () -> Unit) {
    val statusColor = when (job.status) {
        MigrationStatus.COMPLETED -> Color(0xFF4CAF50)
        MigrationStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        MigrationStatus.FAILED, MigrationStatus.PARTIAL -> MaterialTheme.colorScheme.error
        MigrationStatus.PENDING -> MaterialTheme.colorScheme.outline
        MigrationStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${job.source.displayName} → ${job.type.name}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        job.status.name,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (job.totalRecords > 0) {
                val progress = if (job.totalRecords == 0) 0f else job.processedRecords.toFloat() / job.totalRecords
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${job.processedRecords}/${job.totalRecords} records · ${job.failedRecords} failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            job.startedAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Started: ${SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(it))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            job.errorMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (job.status == MigrationStatus.IN_PROGRESS || job.status == MigrationStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MigrationConfigSheet(
    source: MigrationSource,
    type: MigrationType,
    configJson: String,
    isWorking: Boolean,
    onTypeChange: (MigrationType) -> Unit,
    onConfigChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Import from ${source.displayName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text("What to import", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            MigrationType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { onTypeChange(t) },
                            label = { Text(t.name.lowercase().replaceFirstChar { it.titlecase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Configuration (JSON)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = configJson,
                onValueChange = onConfigChange,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSubmit,
                enabled = !isWorking && configJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isWorking) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Start Migration")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Your existing POS will continue to run in parallel during migration. No downtime. We sync every 60 seconds until all data is reconciled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
