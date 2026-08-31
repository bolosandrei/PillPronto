package com.pillpronto.ui.navigation

sealed class Route(val path: String, val label: String) {
    data object Today : Route("today", "Azi")
    data object Treatments : Route("treatments", "Tratamente")
    data object Adherence : Route("adherence", "Aderență")
    data object AddTreatment : Route("add_treatment", "Adaugă tratament")

    companion object {
        val bottomBar = listOf(Today, Treatments, Adherence)
    }
}
