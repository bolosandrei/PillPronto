package com.pillpronto.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pillpronto.R
import com.pillpronto.ui.adherence.AdherenceScreen
import com.pillpronto.ui.today.TodayScreen
import com.pillpronto.ui.treatments.AddTreatmentScreen
import com.pillpronto.ui.treatments.TreatmentDetailScreen
import com.pillpronto.ui.treatments.TreatmentsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun PillProntoNavHost(openTodayRequests: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // Tap pe notificarea de reminder -> readu utilizatorul pe tab-ul "Azi" (ziua curenta),
    // indiferent unde era navigat in aplicatie cand a fost deschisa (vezi MainActivity).
    LaunchedEffect(Unit) {
        openTodayRequests.collect {
            navController.navigate(Route.Today.path) {
                popUpTo(Route.Today.path) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Route.bottomBar.forEach { route ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == route.path } == true,
                        onClick = {
                            navController.navigate(route.path) {
                                popUpTo(Route.Today.path) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(route.path), contentDescription = stringResource(labelResFor(route.path))) },
                        label = { Text(stringResource(labelResFor(route.path))) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Route.Today.path, modifier = Modifier) {
            composable(Route.Today.path) { TodayScreen(padding) }
            composable(Route.Treatments.path) {
                TreatmentsScreen(
                    padding,
                    onAdd = { navController.navigate(Route.AddEditTreatment.create()) },
                    onOpenDetail = { id -> navController.navigate(Route.TreatmentDetail.create(id)) }
                )
            }
            composable(Route.Adherence.path) { AdherenceScreen(padding) }
            composable(
                route = Route.AddEditTreatment.path,
                arguments = listOf(navArgument(Route.AddEditTreatment.ARG) {
                    type = NavType.LongType; defaultValue = -1L
                })
            ) {
                AddTreatmentScreen(
                    padding,
                    onDone = { deleted ->
                        if (deleted) {
                            // Tratamentul nu mai exista — sare peste ecranul de detaliu (daca a fost punctul de intrare).
                            navController.popBackStack(Route.Treatments.path, inclusive = false)
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }
            composable(
                route = Route.TreatmentDetail.path,
                arguments = listOf(navArgument(Route.TreatmentDetail.ARG) { type = NavType.LongType })
            ) {
                TreatmentDetailScreen(
                    padding,
                    onEdit = { id -> navController.navigate(Route.AddEditTreatment.create(id)) }
                )
            }
        }
    }
}

private fun iconFor(path: String) = when (path) {
    Route.Today.path -> Icons.Filled.CalendarToday
    Route.Treatments.path -> Icons.Filled.Medication
    else -> Icons.Filled.QueryStats
}

@StringRes
private fun labelResFor(path: String): Int = when (path) {
    Route.Today.path -> R.string.nav_today
    Route.Treatments.path -> R.string.nav_treatments
    else -> R.string.nav_adherence
}
