package com.enterprise.pos.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// ElevatedCard — Standard elevated card with optional header, content, actions
// ============================================================================
@Composable
fun ElevatedPosCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = title ?: "Card" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null || subtitle != null || icon != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            content()
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    content = actions
                )
            }
        }
    }
}

// ============================================================================
// OutlinedCard — Bordered card for less emphasis
// ============================================================================
@Composable
fun OutlinedPosCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = title ?: "Outlined card" },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

// ============================================================================
// InfoCard — Card with colored left border (info, success, warning, error)
// ============================================================================
enum class InfoCardType {
    INFO, SUCCESS, WARNING, ERROR
}

@Composable
fun InfoCard(
    type: InfoCardType,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val borderColor = when (type) {
        InfoCardType.INFO -> MaterialTheme.colorScheme.primary
        InfoCardType.SUCCESS -> MaterialTheme.colorScheme.tertiary
        InfoCardType.WARNING -> MaterialTheme.colorScheme.secondary
        InfoCardType.ERROR -> MaterialTheme.colorScheme.error
    }

    val containerColor = when (type) {
        InfoCardType.INFO -> MaterialTheme.colorScheme.primaryContainer
        InfoCardType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        InfoCardType.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        InfoCardType.ERROR -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when (type) {
        InfoCardType.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        InfoCardType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        InfoCardType.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        InfoCardType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title: $message" },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ============================================================================
// StatCard — Card displaying a metric (label, value, trend, icon)
// ============================================================================
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trend: String? = null,
    trendPositive: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "$label: $value" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (trend != null && trendPositive != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val trendColor = if (trendPositive) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                }
                val trendIcon = if (trendPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = if (trendPositive) "Positive trend" else "Negative trend",
                        tint = trendColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.bodySmall,
                        color = trendColor
                    )
                }
            }
        }
    }
}

// ============================================================================
// SelectableCard — Card that can be selected/checked
// ============================================================================
@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect
            )
            .semantics {
                this.selected = selected
                contentDescription = title ?: "Selectable card"
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Checkbox(
                checked = selected,
                onCheckedChange = null
            )
        }
    }
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun CardVariantsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedPosCard(
                    title = "Sales Today",
                    subtitle = "Overview of daily transactions",
                    icon = Icons.Default.ShoppingCart
                ) {
                    Text(
                        text = "\$1,245.00",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                OutlinedPosCard(title = "Settings") {
                    Text(
                        text = "Configure your POS preferences",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                InfoCard(
                    type = InfoCardType.SUCCESS,
                    title = "Transaction Complete",
                    message = "Payment processed successfully.",
                    icon = Icons.Default.CheckCircle
                )

                StatCard(
                    label = "Revenue",
                    value = "\$12,450",
                    icon = Icons.Default.AttachMoney,
                    trend = "+12.5%",
                    trendPositive = true
                )

                var selected by remember { mutableStateOf(false) }
                SelectableCard(
                    selected = selected,
                    onSelect = { selected = !selected },
                    title = "Dine-in",
                    subtitle = "Table service option",
                    icon = Icons.Default.TableRestaurant
                )
            }
        }
    }
}
