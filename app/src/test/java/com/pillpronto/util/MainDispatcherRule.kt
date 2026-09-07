package com.pillpronto.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Inlocuieste Dispatchers.Main cu un test dispatcher — necesar pentru orice ViewModel care
 * foloseste viewModelScope (implicit Dispatchers.Main.immediate), altfel testul crapa cu
 * "Module with the Main dispatcher had failed to initialize" in JVM pur (fara Robolectric).
 * Unconfined, nu Standard: ruleaza coroutine-le eager/sincron (init { }.launchIn(...) pe un
 * StateFlow, viewModelScope.launch { } fara IO real) fara sa fie nevoie de advanceUntilIdle()
 * manual dupa fiecare actiune din test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
