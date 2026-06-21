package com.enterprise.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// StatusBadge — Colored badge with status text (active, inactive, pending, error)
// ============================================================================
enum class StatusType {
    ACTIVE, INACTIVE, PENDING, ERROR
}

@Composable
fun StatusBadge(
    status: StatusType,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val displayText = label ?: when (status) {
        StatusType.ACTIVE -> "Active"
        StatusType.INACTIVE -> "Inactive"
        StatusType.PENDING -> "Pending"
        StatusType.ERROR -> "Error"
    }

    val containerColor = when (status) {
        StatusType.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
        StatusType.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
        StatusType.PENDING -> MaterialTheme.colorScheme.secondaryContainer
        StatusType.ERROR -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when (status) {
        StatusType.ACTIVE -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusType.INACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusType.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
        StatusType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    val dotColor = when (status) {
        StatusType.ACTIVE -> MaterialTheme.colorScheme.tertiary
        StatusType.INACTIVE -> MaterialTheme.colorScheme.outline
        StatusType.PENDING -> MaterialTheme.colorScheme.secondary
        StatusType.ERROR -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "Status: $displayText" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = displayText,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// CountBadge — Badge with count number
// ============================================================================
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    maxCount: Int = 99
) {
    if (count <= 0) return

    val displayCount = if (count > maxCount) "$maxCount+" else count.toString()

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = "$count notifications" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayCount,
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// FilterChip — Chip for filtering with selection state
// ============================================================================
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = "Filter: $label" },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}

// ============================================================================
// ActionChip — Chip with icon and action
// ============================================================================
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label }
    )
}

// ============================================================================
// DeletableChip — Chip with delete action
// ============================================================================
@Composable
fun DeletableChip(
    label: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    InputChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        selected = false,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = "$label, press to remove" },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = enabled) { onDelete() }
            )
        }
    )
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun BadgeVariantsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(StatusType.ACTIVE)
                    StatusBadge(StatusType.INACTIVE)
                    StatusBadge(StatusType.PENDING)
                    StatusBadge(StatusType.ERROR)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountBadge(count = 5)
                    CountBadge(count = 100)
                    CountBadge(count = 0)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("All", selected = true, onClick = {})
                    FilterChip("Pending", selected = false, onClick = {})
                    FilterChip("Completed", selected = false, onClick = {})
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip("Add Item", onClick = {}, icon = Icons.Default.Add)
                    ActionChip("Export", onClick = {}, icon = Icons.Default.FileDownload)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeletableChip("Category: Food", onDelete = {})
                    DeletableChip("Status: Active", onDelete = {})
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BadgeDarkPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(StatusType.ACTIVE)
                    StatusBadge(StatusType.INACTIVE)
                    StatusBadge(StatusType.PENDING)
                    StatusBadge(StatusType.ERROR)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountBadge(count = 5)
                    FilterChip("All", selected = true, onClick = {})
                    ActionChip("Add", onClick = {}, icon = Icons.Default.Add)
                }
            }
        }
    }
}
