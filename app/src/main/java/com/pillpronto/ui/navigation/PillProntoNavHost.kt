package com.pillpronto.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import com.pillpronto.ui.access.ManageAccessScreen
import com.pillpronto.ui.gtinmapping.AssociateGtinScreen
import com.pillpronto.ui.recognition.EnrollMedicationScreen
import com.pillpronto.ui.vision.VisionScanScreen
import com.pillpronto.ui.access.ManageProfessionalAccessScreen
import com.pillpronto.ui.account.AccountScreen
import com.pillpronto.ui.adherence.AdherenceScreen
import com.pillpronto.ui.onboarding.OnboardingScreen
import com.pillpronto.ui.patients.MyPatientsScreen
import com.pillpronto.ui.patients.PatientDetailScreen
import com.pillpronto.ui.today.TodayScreen
import com.pillpronto.ui.treatments.AddTreatmentScreen
import com.pillpronto.ui.treatments.TreatmentDetailScreen
import com.pillpronto.ui.treatments.TreatmentsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun PillProntoNavHost(
    openTodayRequests: Flow<Unit> = emptyFlow(),
    inviteCodeRequests: Flow<String> = emptyFlow()
) {
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

    // Deep link de invitatie (pillpronto://invite?code=...) -> "Pacientii mei" cu codul
    // pre-completat (userul tot confirma apasand "Adauga pacient", vezi MyPatientsViewModel).
    LaunchedEffect(Unit) {
        inviteCodeRequests.collect { code ->
            navController.navigate(Route.MyPatients.create(prefillCode = code))
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
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Route.TreatmentDetail.path,
                arguments = listOf(navArgument(Route.TreatmentDetail.ARG) { type = NavType.LongType })
            ) {
                TreatmentDetailScreen(
                    padding,
                    onEdit = { id -> navController.navigate(Route.AddEditTreatment.create(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.Account.path) {
                AccountScreen(
                    padding,
                    // launchSingleTop: plasa de siguranta — daca AccountScreen ar mai declansa
                    // aceasta navigare de mai multe ori la rand (ex. o viitoare regresie a
                    // fix-ului din AccountScreen.kt), nu se mai stivuiesc mai multe instante de
                    // Onboarding, care ar cere userului sa apese Back de mai multe ori.
                    onNeedsOnboarding = {
                        navController.navigate(Route.Onboarding.path) { launchSingleTop = true }
                    },
                    onManageAccess = { navController.navigate(Route.ManageAccess.path) },
                    onManageProfessionalAccess = { navController.navigate(Route.ManageProfessionalAccess.path) },
                    onMyPatients = { navController.navigate(Route.MyPatients.create()) },
                    onAssociateGtin = { navController.navigate(Route.AssociateGtin.path) },
                    onVisionScan = { navController.navigate(Route.VisionScan.path) },
                    onEnrollMedication = { navController.navigate(Route.EnrollMedication.path) }
                )
            }
            composable(Route.Onboarding.path) {
                OnboardingScreen(
                    padding,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Route.ManageAccess.path) {
                ManageAccessScreen(padding, onBack = { navController.popBackStack() })
            }
            composable(Route.AssociateGtin.path) {
                AssociateGtinScreen(padding, onBack = { navController.popBackStack() })
            }
            composable(Route.VisionScan.path) {
                VisionScanScreen(padding, onBack = { navController.popBackStack() })
            }
            composable(Route.EnrollMedication.path) {
                EnrollMedicationScreen(padding, onBack = { navController.popBackStack() })
            }
            composable(Route.ManageProfessionalAccess.path) {
                ManageProfessionalAccessScreen(padding, onBack = { navController.popBackStack() })
            }
            composable(
                route = Route.MyPatients.path,
                arguments = listOf(navArgument(Route.MyPatients.ARG_PREFILL_CODE) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })
            ) {
                MyPatientsScreen(
                    padding,
                    onBack = { navController.popBackStack() },
                    onOpenPatient = { id -> navController.navigate(Route.PatientDetail.create(id)) }
                )
            }
            composable(
                route = Route.PatientDetail.path,
                arguments = listOf(navArgument(Route.PatientDetail.ARG) { type = NavType.StringType })
            ) {
                PatientDetailScreen(padding, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun iconFor(path: String) = when (path) {
    Route.Today.path -> Icons.Filled.CalendarToday
    Route.Treatments.path -> Icons.Filled.Medication
    Route.Account.path -> Icons.Filled.AccountCircle
    else -> Icons.Filled.QueryStats
}

@StringRes
private fun labelResFor(path: String): Int = when (path) {
    Route.Today.path -> R.string.nav_today
    Route.Treatments.path -> R.string.nav_treatments
    Route.Account.path -> R.string.nav_account
    else -> R.string.nav_adherence
}
