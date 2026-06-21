package com.enterprise.pos.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.enterprise.pos.ui.theme.PosTheme

@Composable
fun PosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String = "",
    errorText: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val resolvedError = isError || errorText.isNotBlank()
    val resolvedSupportingText = when {
        resolvedError && errorText.isNotBlank() -> errorText
        !supportingText.isNullOrBlank() -> supportingText
        helperText.isNotBlank() -> helperText
        else -> null
    }
    val resolvedKeyboardOptions = keyboardOptions ?: KeyboardOptions(
        keyboardType = keyboardType,
        imeAction = imeAction
    )
    val resolvedKeyboardActions = keyboardActions ?: KeyboardActions(
        onDone = { onImeAction?.invoke() },
        onGo = { onImeAction?.invoke() },
        onNext = { onImeAction?.invoke() },
        onPrevious = { onImeAction?.invoke() },
        onSearch = { onImeAction?.invoke() },
        onSend = { onImeAction?.invoke() }
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (resolvedError && errorText.isNotBlank()) {
                    errorText
                } else {
                    label.orEmpty()
                }
            },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingIcon = trailingIcon?.let {
            {
                IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                    Icon(imageVector = it, contentDescription = null)
                }
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        readOnly = readOnly,
        enabled = enabled,
        isError = resolvedError,
        supportingText = resolvedSupportingText?.let { { Text(it) } },
        keyboardOptions = resolvedKeyboardOptions,
        keyboardActions = resolvedKeyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        shape = MaterialTheme.shapes.medium
    )
}

@Preview(showBackground = true)
@Composable
private fun PosTextFieldPreview() {
    PosTheme {
        PosTextField(
            value = "Sample text",
            onValueChange = {},
            label = "Label",
            placeholder = "Enter text"
        )
    }
}
