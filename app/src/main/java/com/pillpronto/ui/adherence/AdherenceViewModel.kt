package com.pillpronto.ui.adherence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.usecase.ComputeAdherenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdherenceViewModel @Inject constructor(
    private val computeAdherence: ComputeAdherenceUseCase
) : ViewModel() {

    private val _stats = MutableStateFlow(AdherenceStats.EMPTY)
    val stats = _stats.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { _stats.value = computeAdherence(windowDays = 30) }
}
