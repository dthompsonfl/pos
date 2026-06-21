package com.enterprise.pos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// AlertDialog — Confirmation dialog (title, message, confirm/cancel)
// ============================================================================
@Composable
fun PosAlertDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    icon: ImageVector? = Icons.Default.Warning,
    confirmButtonColor: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error
    )
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.semantics { contentDescription = "$title dialog" },
        icon = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = confirmButtonColor
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================================
// InputDialog — Dialog with text input field
// ============================================================================
@Composable
fun PosInputDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    placeholder: String = "",
    label: String = "",
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.semantics { contentDescription = "$title input dialog" },
        title = {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { if (label.isNotEmpty()) Text(label) },
                placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
                singleLine = true,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================================
// SelectionDialog — Dialog with list of selectable items
// ============================================================================
@Composable
fun <T> PosSelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    itemText: (T) -> String = { it.toString() },
    dismissText: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.semantics { contentDescription = "$title selection dialog" },
        title = {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            LazyColumn {
                items(items) { item ->
                    val isSelected = item == selectedItem
                    ListItem(
                        modifier = Modifier
                            .clickable { onItemSelected(item) }
                            .semantics { contentDescription = itemText(item) },
                        headlineContent = {
                            Text(
                                text = itemText(item),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================================
// ProgressDialog — Dialog with indeterminate progress and optional message
// ============================================================================
@Composable
fun PosProgressDialog(
    message: String,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { contentDescription = "Progress dialog: $message" },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================================
// ErrorDialog — Dialog displaying error details with retry option
// ============================================================================
@Composable
fun PosErrorDialog(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = "Retry",
    dismissText: String = "Close",
    errorCode: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.semantics { contentDescription = "Error: $title" },
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (errorCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Code: $errorCode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(retryText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun DialogVariantsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AlertDialog", style = MaterialTheme.typography.titleMedium)
                PosAlertDialog(
                    title = "Delete Item?",
                    message = "This action cannot be undone. The item will be permanently removed from inventory.",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InputDialogPreview() {
    PosTheme {
        Surface {
            PosInputDialog(
                title = "Enter Discount",
                onConfirm = {},
                onDismiss = {},
                label = "Discount %",
                placeholder = "e.g. 10"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressDialogPreview() {
    PosTheme {
        Surface {
            PosProgressDialog(message = "Processing payment...")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorDialogPreview() {
    PosTheme {
        Surface {
            PosErrorDialog(
                title = "Payment Failed",
                message = "Unable to process the transaction. Please check your network connection and try again.",
                onRetry = {},
                onDismiss = {},
                errorCode = "ERR_504"
            )
        }
    }
}
