package com.enterprise.pos.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// DataColumn — Column definition for tables
// ============================================================================
data class DataColumn<T>(
    val title: String,
    val width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    val sortable: Boolean = false,
    val comparator: ((T, T) -> Int)? = null,
    val content: @Composable (T) -> Unit
)

// ============================================================================
// DataTable — Column headers, sortable, selectable rows
// ============================================================================
@Composable
fun <T> DataTable(
    columns: List<DataColumn<T>>,
    items: List<T>,
    modifier: Modifier = Modifier,
    selectedItems: Set<T> = emptySet(),
    onSelectionChange: ((Set<T>) -> Unit)? = null,
    keyExtractor: (T) -> String = { it.hashCode().toString() },
    emptyContent: @Composable () -> Unit = {}
) {
    var sortColumn by remember { mutableStateOf(-1) }
    var sortAscending by remember { mutableStateOf(true) }

    val sortedItems = remember(items, sortColumn, sortAscending, columns) {
        if (sortColumn >= 0 && columns[sortColumn].comparator != null) {
            val sorted = items.sortedWith(columns[sortColumn].comparator!!)
            if (sortAscending) sorted else sorted.reversed()
        } else {
            items
        }
    }

    if (items.isEmpty()) {
        emptyContent()
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (onSelectionChange != null) {
                    Checkbox(
                        checked = selectedItems.size == items.size && items.isNotEmpty(),
                        onCheckedChange = { checked ->
                            if (checked) onSelectionChange(items.toSet())
                            else onSelectionChange(emptySet())
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                columns.forEachIndexed { index, column ->
                    val isSorted = sortColumn == index
                    Row(
                        modifier = Modifier
                            .then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                            .clickable(enabled = column.sortable) {
                                if (sortColumn == index) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortColumn = index
                                    sortAscending = true
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = column.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (column.sortable) {
                            Icon(
                                imageVector = if (isSorted) {
                                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                } else Icons.Default.UnfoldMore,
                                contentDescription = if (isSorted) "Sorted" else "Sort",
                                modifier = Modifier.size(16.dp),
                                tint = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Rows
        sortedItems.forEach { item ->
            val isSelected = selectedItems.contains(item)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSelectionChange != null) {
                            Modifier.selectable(
                                selected = isSelected,
                                onClick = {
                                    val newSelection = if (isSelected) {
                                        selectedItems - item
                                    } else {
                                        selectedItems + item
                                    }
                                    onSelectionChange(newSelection)
                                }
                            )
                        } else Modifier
                    )
                    .semantics {
                        selected = isSelected
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSelectionChange != null) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                columns.forEachIndexed { index, column ->
                    Box(
                        modifier = Modifier
                            .then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                    ) {
                        column.content(item)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ============================================================================
// PaginatedTable — DataTable with pagination controls
// ============================================================================
@Composable
fun <T> PaginatedTable(
    columns: List<DataColumn<T>>,
    items: List<T>,
    modifier: Modifier = Modifier,
    pageSize: Int = 10,
    selectedItems: Set<T> = emptySet(),
    onSelectionChange: ((Set<T>) -> Unit)? = null,
    keyExtractor: (T) -> String = { it.hashCode().toString() },
    emptyContent: @Composable () -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = (items.size + pageSize - 1) / pageSize.coerceAtLeast(1)
    val pageItems = items.drop(currentPage * pageSize).take(pageSize)

    Column(modifier = modifier.fillMaxWidth()) {
        DataTable(
            columns = columns,
            items = pageItems,
            selectedItems = selectedItems,
            onSelectionChange = onSelectionChange,
            keyExtractor = keyExtractor,
            emptyContent = emptyContent
        )

        if (totalPages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
                    }
                    IconButton(
                        onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}

// ============================================================================
// ExpandableTable — Rows that can expand to show details
// ============================================================================
@Composable
fun <T> ExpandableTable(
    columns: List<DataColumn<T>>,
    items: List<T>,
    modifier: Modifier = Modifier,
    keyExtractor: (T) -> String = { it.hashCode().toString() },
    emptyContent: @Composable () -> Unit = {},
    expandedContent: @Composable (T) -> Unit
) {
    var expandedRows by remember { mutableStateOf<Set<String>>(emptySet()) }

    if (items.isEmpty()) {
        emptyContent()
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.width(40.dp)) {}
                columns.forEach { column ->
                    Text(
                        text = column.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        items.forEach { item ->
            val key = keyExtractor(item)
            val isExpanded = expandedRows.contains(key)
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedRows = if (isExpanded) expandedRows - key else expandedRows + key }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { expandedRows = if (isExpanded) expandedRows - key else expandedRows + key }
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                    columns.forEach { column ->
                        Box(
                            modifier = Modifier.then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                        ) {
                            column.content(item)
                        }
                    }
                }
                AnimatedVisibility(visible = isExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            expandedContent(item)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ============================================================================
// ActionTable — Table with action buttons per row
// ============================================================================
data class RowAction<T>(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: (T) -> Unit
)

@Composable
fun <T> ActionTable(
    columns: List<DataColumn<T>>,
    items: List<T>,
    actions: List<RowAction<T>>,
    modifier: Modifier = Modifier,
    keyExtractor: (T) -> String = { it.hashCode().toString() },
    emptyContent: @Composable () -> Unit = {}
) {
    if (items.isEmpty()) {
        emptyContent()
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                columns.forEach { column ->
                    Text(
                        text = column.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                    )
                }
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEach { column ->
                    Box(
                        modifier = Modifier.then(if (column.width != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(column.width) else Modifier.weight(1f))
                    ) {
                        column.content(item)
                    }
                }
                Row(
                    modifier = Modifier.width(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actions.forEach { action ->
                        IconButton(
                            onClick = { action.onClick(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun TablePreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                val columns = listOf(
                    DataColumn<String>("Name", width = 120.dp) { Text(it) },
                    DataColumn<String>("Category", width = 100.dp) { Text(it) },
                    DataColumn<String>("Price", width = 80.dp) { Text(it) }
                )
                DataTable(
                    columns = columns,
                    items = listOf("Burger", "Pizza", "Salad")
                )
            }
        }
    }
}
