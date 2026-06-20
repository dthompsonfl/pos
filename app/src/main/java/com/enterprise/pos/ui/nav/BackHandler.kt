package com.enterprise.pos.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Priority-based back handler for the EnterprisePOS application.
 * Allows multiple components to register back handlers with priorities;
 * the highest priority handler is executed first.
 */
@Composable
fun PosBackHandler(
    enabled: Boolean = true,
    priority: Int = 0,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val backHandler = remember { BackHandlerEntry(priority, onBack, enabled) }

    DisposableEffect(lifecycleOwner, enabled, onBack) {
        backHandler.enabled = enabled
        backHandler.onBack = onBack
        BackHandlerRegistry.register(backHandler)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                BackHandlerRegistry.unregister(backHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            BackHandlerRegistry.unregister(backHandler)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Drawer back handler that closes the drawer when back is pressed.
 */
@Composable
fun DrawerBackHandler(
    enabled: Boolean,
    onCloseDrawer: () -> Unit
) {
    PosBackHandler(
        enabled = enabled,
        priority = 100 // High priority: drawer closes before anything else
    ) {
        onCloseDrawer()
    }
}

/**
 * Dialog back handler that dismisses a dialog when back is pressed.
 */
@Composable
fun DialogBackHandler(
    enabled: Boolean,
    onDismiss: () -> Unit
) {
    PosBackHandler(
        enabled = enabled,
        priority = 90 // High priority, but below drawer
    ) {
        onDismiss()
    }
}

/**
 * Exit confirmation handler that shows a confirmation before exiting the app.
 */
@Composable
fun ExitConfirmationHandler(
    enabled: Boolean,
    onConfirmExit: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var backPressCount by remember { mutableStateOf(0) }

    PosBackHandler(
        enabled = enabled,
        priority = 10 // Low priority: only triggers if nothing else handled back
    ) {
        backPressCount++
        if (backPressCount >= 2) {
            onConfirmExit()
        } else {
            showConfirmDialog = true
        }
    }

    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false; backPressCount = 0 },
            title = { androidx.compose.material3.Text("Exit Application") },
            text = { androidx.compose.material3.Text("Press back again to exit.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onConfirmExit()
                    }
                ) {
                    androidx.compose.material3.Text("Exit")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        backPressCount = 0
                    }
                ) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
}

/**
 * Navigation back handler that pops the back stack when back is pressed.
 */
@Composable
fun NavigationBackHandler(
    enabled: Boolean,
    navController: NavController
) {
    PosBackHandler(
        enabled = enabled,
        priority = 20 // Medium priority: pop after dialogs/drawers
    ) {
        navController.popBackStackSafe()
    }
}

// ==================== BACK HANDLER REGISTRY ====================

/**
 * Registry for managing multiple back handlers with priority ordering.
 */
object BackHandlerRegistry {

    private val sequenceGenerator = AtomicInteger(0)
    private val handlers = MutableStateFlow<List<BackHandlerEntry>>(emptyList())

    /** Observable list of registered handlers. */
    val registeredHandlers: StateFlow<List<BackHandlerEntry>> = handlers.asStateFlow()

    fun register(handler: BackHandlerEntry) {
        handler.sequence = sequenceGenerator.incrementAndGet()
        val current = handlers.value
        handlers.value = (current + handler).sortedDescending()
    }

    fun unregister(handler: BackHandlerEntry) {
        handlers.value = handlers.value.filter { it !== handler }.sortedDescending()
    }

    /**
     * Process the back press. Returns true if handled, false otherwise.
     */
    fun handleBack(): Boolean {
        val activeHandlers = handlers.value.filter { it.enabled }
        for (handler in activeHandlers) {
            if (handler.enabled) {
                handler.onBack()
                return true
            }
        }
        return false
    }

    private fun List<BackHandlerEntry>.sortedDescending(): List<BackHandlerEntry> {
        return sortedWith(compareByDescending<BackHandlerEntry> { it.priority }.thenByDescending { it.sequence })
    }
}

/**
 * Entry representing a registered back handler.
 */
class BackHandlerEntry(
    val priority: Int,
    var onBack: () -> Unit,
    var enabled: Boolean = true
) {
    var sequence: Int = 0
}

/**
 * Composable that installs the system [BackHandler] and delegates to the [BackHandlerRegistry].
 * This should be placed at the root of the app. It automatically disables itself when no handlers
 * are registered, allowing the system default behavior (exit app) to work.
 */
@Composable
fun PosBackHandlerRoot(
    enabled: Boolean = true
) {
    val handlers by BackHandlerRegistry.registeredHandlers.collectAsState()
    val hasActiveHandler = handlers.any { it.enabled }

    BackHandler(enabled = enabled && hasActiveHandler) {
        BackHandlerRegistry.handleBack()
    }
}
