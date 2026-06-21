package com.enterprise.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// TextDivider — Divider with text in the middle (or start/end)
// ============================================================================
@Composable
fun TextDivider(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    thickness: Dp = 1.dp,
    position: TextDividerPosition = TextDividerPosition.MIDDLE
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (position) {
            TextDividerPosition.START -> {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = color,
                    thickness = thickness
                )
            }
            TextDividerPosition.MIDDLE -> {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = color,
                    thickness = thickness
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = color,
                    thickness = thickness
                )
            }
            TextDividerPosition.END -> {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = color,
                    thickness = thickness
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

enum class TextDividerPosition {
    START, MIDDLE, END
}

// ============================================================================
// VerticalDivider — Vertical divider for side-by-side layouts
// ============================================================================
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 1.dp
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness)
            .background(color)
            .semantics { contentDescription = "Vertical divider" }
    )
}

// ============================================================================
// DashedDivider — Dashed line divider
// ============================================================================
@Composable
fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 1.dp,
    dashWidth: Dp = 8.dp,
    gapWidth: Dp = 4.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .semantics { contentDescription = "Dashed divider" }
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashWidth.toPx(), gapWidth.toPx()),
                0f
            )
        )
    }
}

// ============================================================================
// ThickDivider — Emphasized divider with optional label
// ============================================================================
@Composable
fun ThickDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    thickness: Dp = 2.dp
) {
    Divider(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Section divider" },
        color = color,
        thickness = thickness
    )
}

// ============================================================================
// SpacedDivider — Divider with padding above and below
// ============================================================================
@Composable
fun SpacedDivider(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Spacer(modifier = Modifier.height(verticalPadding))
    Divider(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Divider" },
        color = color
    )
    Spacer(modifier = Modifier.height(verticalPadding))
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun DividerVariantsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextDivider(
                    text = "Or",
                    position = TextDividerPosition.MIDDLE
                )

                TextDivider(
                    text = "Section Start",
                    position = TextDividerPosition.START
                )

                TextDivider(
                    text = "Section End",
                    position = TextDividerPosition.END
                )

                DashedDivider()

                ThickDivider()

                SpacedDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Left")
                    VerticalDivider(modifier = Modifier.height(40.dp))
                    Text("Right")
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DividerDarkPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextDivider(text = "Or", position = TextDividerPosition.MIDDLE)
                DashedDivider()
                ThickDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Left")
                    VerticalDivider(modifier = Modifier.height(40.dp))
                    Text("Right")
                }
            }
        }
    }
}
