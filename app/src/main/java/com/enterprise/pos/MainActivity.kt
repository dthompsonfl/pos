package com.enterprise.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.enterprise.pos.feature.employees.screen.LoginScreen
import com.enterprise.pos.feature.employees.state.EmployeesViewModel
import com.enterprise.pos.ui.nav.AdaptiveNavigationLayout
import com.enterprise.pos.ui.nav.PosNavGraph
import com.enterprise.pos.ui.nav.rememberPosNavController
import com.enterprise.pos.ui.nav.safeNavigate
import com.enterprise.pos.ui.state.ConfigViewModel
import com.enterprise.pos.ui.theme.PosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                val employeesVm: EmployeesViewModel = hiltViewModel()
                val empState by employeesVm.state.collectAsStateWithLifecycle()

                val configVm: ConfigViewModel = hiltViewModel()
                val config by configVm.state.collectAsStateWithLifecycle()

                val currentEmployee = empState.currentEmployee

                if (currentEmployee == null) {
                    LoginScreen(onLoginSuccess = { })
                    return@PosTheme
                }

                // If no store/register is configured, redirect to onboarding.
                if (!config.isReady) {
                    OnboardingScreen(onComplete = { /* Phase 15 will wire navigation to settings */ })
                    return@PosTheme
                }

                val storeId = config.storeId!!
                val registerId = config.registerId!!
                val employeeId = currentEmployee.id

                val navController = rememberPosNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)

                AdaptiveNavigationLayout(
                    windowSizeClass = windowSizeClass,
                    navController = navController,
                    currentRoute = currentRoute,
                    onNavigate = { screen -> navController.safeNavigate(screen.route) },
                    drawerState = drawerState,
                    content = { padding ->
                        PosNavGraph(
                            navController = navController,
                            padding = padding,
                            storeId = storeId,
                            registerId = registerId,
                            employeeId = employeeId
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Enterprise POS", style = MaterialTheme.typography.headlineLarge)
        Text("Store setup is incomplete. Please finish onboarding in Settings.")
        Button(onClick = onComplete) { Text("Go to Settings") }
    }
}
