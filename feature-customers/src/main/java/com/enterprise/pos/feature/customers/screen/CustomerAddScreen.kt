package com.enterprise.pos.feature.customers.screen

import androidx.compose.runtime.Composable
import com.enterprise.pos.core.CustomerId

@Composable
fun CustomerAddScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    CustomerEditScreen(
        customerId = null,
        onBack = onBack,
        onSaved = onSaved
    )
}
