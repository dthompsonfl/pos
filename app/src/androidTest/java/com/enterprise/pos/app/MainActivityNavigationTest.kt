package com.enterprise.pos.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enterprise.pos.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MainActivityNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchingMainActivityShowsBottomNavigation() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Orders").assertExists()
        composeRule.onNodeWithContentDescription("Menu").assertExists()
        composeRule.onNodeWithContentDescription("Checkout").assertExists()
        composeRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun clickingMenuNavigatesToCatalogScreen() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Categories").assertExists()
    }

    @Test
    fun clickingOrdersNavigatesToOrdersScreen() {
        composeRule.onNodeWithContentDescription("Orders").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open Orders").assertExists()
    }

    @Test
    fun clickingCheckoutNavigatesToCheckoutScreen() {
        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Total").assertExists()
    }

    @Test
    fun clickingSettingsNavigatesToSettingsScreen() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("General").assertExists()
    }

    @Test
    fun backButtonFromOrdersDoesNotExit() {
        composeRule.onNodeWithContentDescription("Orders").performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Orders").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CheckoutFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addItemToCartAndProceedToPayment() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beverages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coffee").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coffee").assertExists()

        composeRule.onNodeWithText("Pay").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select Payment Method").assertExists()
    }

    @Test
    fun applyDiscountInCheckout() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beverages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coffee").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discount").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("10% Off").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discount Applied").assertExists()
    }

    @Test
    fun splitTenderInCheckout() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beverages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coffee").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pay").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Split").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Amount").assertExists()
    }

    @Test
    fun voidOrderFromCheckout() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beverages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coffee").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Void").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Confirm Void").assertExists()
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cart is empty").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SettingsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openSettingsAndChangeStoreName() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun openSettingsAndNavigateToHardware() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hardware").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Receipt Printer").assertExists()
    }

    @Test
    fun openSettingsAndNavigateToPayment() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Payment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stripe Terminal").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class OnboardingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingWelcomeScreenShows() {
        composeRule.onNodeWithText("Welcome to EnterprisePOS").assertExists()
        composeRule.onNodeWithText("Get Started").assertExists()
    }

    @Test
    fun clickingGetStartedAdvancesToStoreSetup() {
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").assertExists()
    }

    @Test
    fun completingStoreSetupAdvancesToEmployeeSetup() {
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").performTextInput("Test Store")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Employee Name").assertExists()
    }

    @Test
    fun completingEmployeeSetupAdvancesToHardwareSetup() {
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").performTextInput("Test Store")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Employee Name").performTextInput("Admin")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hardware Setup").assertExists()
    }
}
