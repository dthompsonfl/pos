package com.enterprise.pos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// PrimaryButton — Filled, high emphasis
// ============================================================================
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = if (isLoading) "$text loading" else text },
        enabled = enabled && !isLoading,
        shape = shape
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// SecondaryButton — Tonal, medium emphasis
// ============================================================================
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = text },
        enabled = enabled,
        shape = shape
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// TertiaryButton — Outlined, low emphasis
// ============================================================================
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = text },
        enabled = enabled,
        shape = shape
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// DangerButton — Error color, for destructive actions
// ============================================================================
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = text },
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// IconButton — Circular icon button with tooltip
// ============================================================================
@Composable
fun PosIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = contentDescription,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            enabled = enabled
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ============================================================================
// LoadingButton — Shows circular progress when isLoading = true
// ============================================================================
@Composable
fun LoadingButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = if (isLoading) "$text loading" else text },
        enabled = enabled && !isLoading,
        shape = shape
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun ButtonVariantsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(text = "Primary", onClick = {})
                PrimaryButton(text = "Primary Loading", onClick = {}, isLoading = true)
                SecondaryButton(text = "Secondary", onClick = {})
                TertiaryButton(text = "Tertiary", onClick = {})
                DangerButton(text = "Danger", onClick = {})
                LoadingButton(text = "Loading", onClick = {}, isLoading = true)
                PosIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = {},
                    contentDescription = "Refresh"
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonVariantsDarkPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(text = "Primary", onClick = {})
                PrimaryButton(text = "Primary Loading", onClick = {}, isLoading = true)
                SecondaryButton(text = "Secondary", onClick = {})
                TertiaryButton(text = "Tertiary", onClick = {})
                DangerButton(text = "Danger", onClick = {})
                LoadingButton(text = "Loading", onClick = {}, isLoading = true)
                PosIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = {},
                    contentDescription = "Refresh"
                )
            }
        }
    }
}
