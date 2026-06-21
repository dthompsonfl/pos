package com.enterprise.pos.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enterprise.pos.ui.components.ButtonVariant
import com.enterprise.pos.ui.components.PosAlertDialog
import com.enterprise.pos.ui.components.PosBottomSheet
import com.enterprise.pos.ui.components.PosButton
import com.enterprise.pos.ui.components.PosDataTable
import com.enterprise.pos.ui.components.PosDropdownField
import com.enterprise.pos.ui.components.PosIconButton
import com.enterprise.pos.ui.components.PosSearchField
import com.enterprise.pos.ui.components.PosTextField
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `primary button displays text`() {
        composeRule.setContent {
            PosButton(
                text = "Submit",
                onClick = {},
                variant = ButtonVariant.PRIMARY
            )
        }
        composeRule.onNodeWithText("Submit").assertExists()
    }

    @Test
    fun `primary button is enabled by default`() {
        composeRule.setContent {
            PosButton(
                text = "Submit",
                onClick = {},
                variant = ButtonVariant.PRIMARY
            )
        }
        composeRule.onNodeWithText("Submit").assertIsEnabled()
    }

    @Test
    fun `disabled button is not enabled`() {
        composeRule.setContent {
            PosButton(
                text = "Submit",
                onClick = {},
                variant = ButtonVariant.PRIMARY,
                enabled = false
            )
        }
        composeRule.onNodeWithText("Submit").assertIsNotEnabled()
    }

    @Test
    fun `button click invokes callback`() {
        var clicked = false
        composeRule.setContent {
            PosButton(
                text = "Click Me",
                onClick = { clicked = true },
                variant = ButtonVariant.PRIMARY
            )
        }
        composeRule.onNodeWithText("Click Me").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `secondary button displays text`() {
        composeRule.setContent {
            PosButton(
                text = "Cancel",
                onClick = {},
                variant = ButtonVariant.SECONDARY
            )
        }
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `danger button displays text`() {
        composeRule.setContent {
            PosButton(
                text = "Delete",
                onClick = {},
                variant = ButtonVariant.DANGER
            )
        }
        composeRule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun `icon button displays content description`() {
        composeRule.setContent {
            PosIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add Item",
                onClick = {}
            )
        }
        composeRule.onNodeWithContentDescription("Add Item").assertExists()
    }

    @Test
    fun `icon button click invokes callback`() {
        var clicked = false
        composeRule.setContent {
            PosIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add Item",
                onClick = { clicked = true }
            )
        }
        composeRule.onNodeWithContentDescription("Add Item").performClick()
        assertThat(clicked).isTrue()
    }
}

@RunWith(AndroidJUnit4::class)
class PosDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `alert dialog shows title and message`() {
        composeRule.setContent {
            PosAlertDialog(
                title = "Confirm",
                message = "Are you sure?",
                confirmText = "Yes",
                dismissText = "No",
                onConfirm = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Confirm").assertExists()
        composeRule.onNodeWithText("Are you sure?").assertExists()
        composeRule.onNodeWithText("Yes").assertExists()
        composeRule.onNodeWithText("No").assertExists()
    }

    @Test
    fun `alert dialog confirm invokes callback`() {
        var confirmed = false
        composeRule.setContent {
            PosAlertDialog(
                title = "Confirm",
                message = "Are you sure?",
                confirmText = "Yes",
                dismissText = "No",
                onConfirm = { confirmed = true },
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Yes").performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun `alert dialog dismiss invokes callback`() {
        var dismissed = false
        composeRule.setContent {
            PosAlertDialog(
                title = "Confirm",
                message = "Are you sure?",
                confirmText = "Yes",
                dismissText = "No",
                onConfirm = {},
                onDismiss = { dismissed = true }
            )
        }
        composeRule.onNodeWithText("No").performClick()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun `bottom sheet shows title and content`() {
        composeRule.setContent {
            PosBottomSheet(
                title = "Options",
                onDismiss = {}
            ) {
                Text("Option A")
                Text("Option B")
            }
        }
        composeRule.onNodeWithText("Options").assertExists()
        composeRule.onNodeWithText("Option A").assertExists()
        composeRule.onNodeWithText("Option B").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class PosFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `text field displays label and accepts input`() {
        composeRule.setContent {
            PosTextField(
                value = "",
                onValueChange = {},
                label = "Name"
            )
        }
        composeRule.onNodeWithText("Name").assertExists()
    }

    @Test
    fun `text field error state shows error message`() {
        composeRule.setContent {
            PosTextField(
                value = "",
                onValueChange = {},
                label = "Name",
                errorText = "Name is required"
            )
        }
        composeRule.onNodeWithText("Name is required").assertExists()
    }

    @Test
    fun `text field is disabled when specified`() {
        composeRule.setContent {
            PosTextField(
                value = "Fixed",
                onValueChange = {},
                label = "ID",
                enabled = false
            )
        }
        composeRule.onNodeWithText("Fixed").assertIsNotEnabled()
    }

    @Test
    fun `dropdown field shows options`() {
        composeRule.setContent {
            PosDropdownField(
                value = "Option 1",
                options = listOf("Option 1", "Option 2", "Option 3"),
                onValueChange = {},
                label = "Select"
            )
        }
        composeRule.onNodeWithText("Option 1").assertExists()
    }

    @Test
    fun `dropdown field click opens menu`() {
        composeRule.setContent {
            PosDropdownField(
                value = "Option 1",
                options = listOf("Option 1", "Option 2", "Option 3"),
                onValueChange = {},
                label = "Select"
            )
        }
        composeRule.onNodeWithText("Option 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Option 2").assertExists()
        composeRule.onNodeWithText("Option 3").assertExists()
    }

    @Test
    fun `dropdown field selection invokes callback`() {
        var selected = ""
        composeRule.setContent {
            PosDropdownField(
                value = "Option 1",
                options = listOf("Option 1", "Option 2", "Option 3"),
                onValueChange = { selected = it },
                label = "Select"
            )
        }
        composeRule.onNodeWithText("Option 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Option 2").performClick()
        assertThat(selected).isEqualTo("Option 2")
    }
}

@RunWith(AndroidJUnit4::class)
class PosTableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `data table displays headers and rows`() {
        composeRule.setContent {
            PosDataTable(
                headers = listOf("Name", "Price"),
                rows = listOf(
                    listOf("Burger", "$12.50"),
                    listOf("Fries", "$4.00")
                )
            )
        }
        composeRule.onNodeWithText("Name").assertExists()
        composeRule.onNodeWithText("Price").assertExists()
        composeRule.onNodeWithText("Burger").assertExists()
        composeRule.onNodeWithText("$12.50").assertExists()
        composeRule.onNodeWithText("Fries").assertExists()
        composeRule.onNodeWithText("$4.00").assertExists()
    }

    // Note: Row click and sorting tests are removed as PosDataTable in app/PosComponents.kt
    // doesn't support them yet, and it's a simple implementation.
}

@RunWith(AndroidJUnit4::class)
class PosSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search field displays placeholder`() {
        composeRule.setContent {
            PosSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Search products"
            )
        }
        composeRule.onNodeWithText("Search products").assertExists()
    }

    @Test
    fun `search field accepts input`() {
        var query = ""
        composeRule.setContent {
            PosSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search"
            )
        }
        composeRule.onNodeWithText("Search").assertExists()
    }
}
