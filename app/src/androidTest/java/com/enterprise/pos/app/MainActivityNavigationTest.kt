package com.enterprise.pos.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MainActivityNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launching MainActivity shows bottom navigation`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Orders").assertExists()
        composeRule.onNodeWithContentDescription("Menu").assertExists()
        composeRule.onNodeWithContentDescription("Checkout").assertExists()
        composeRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun `clicking menu navigates to catalog screen`() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Categories").assertExists()
    }

    @Test
    fun `clicking orders navigates to orders screen`() {
        composeRule.onNodeWithContentDescription("Orders").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open Orders").assertExists()
    }

    @Test
    fun `clicking checkout navigates to checkout screen`() {
        composeRule.onNodeWithContentDescription("Checkout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Total").assertExists()
    }

    @Test
    fun `clicking settings navigates to settings screen`() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("General").assertExists()
    }

    @Test
    fun `back button from orders does not exit`() {
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

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `add item to cart and proceed to payment`() {
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
    fun `apply discount in checkout`() {
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
    fun `split tender in checkout`() {
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
    fun `void order from checkout`() {
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

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `open settings and change store name`() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun `open settings and navigate to hardware`() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hardware").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Receipt Printer").assertExists()
    }

    @Test
    fun `open settings and navigate to payment`() {
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

    @get:Rule
    val composeRule = createAndroidComposeRule<OnboardingActivity>()

    @Test
    fun `onboarding welcome screen shows`() {
        composeRule.onNodeWithText("Welcome to EnterprisePOS").assertExists()
        composeRule.onNodeWithText("Get Started").assertExists()
    }

    @Test
    fun `clicking get started advances to store setup`() {
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").assertExists()
    }

    @Test
    fun `completing store setup advances to employee setup`() {
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Store Name").performTextInput("Test Store")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Employee Name").assertExists()
    }

    @Test
    fun `completing employee setup advances to hardware setup`() {
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
