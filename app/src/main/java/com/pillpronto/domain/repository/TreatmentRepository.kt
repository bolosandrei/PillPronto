package com.pillpronto.domain.repository

import com.pillpronto.domain.model.Treatment
import kotlinx.coroutines.flow.Flow

interface TreatmentRepository {
    fun observeTreatments(): Flow<List<Treatment>>
    suspend fun getActiveTreatments(): List<Treatment>
    suspend fun getTreatment(id: Long): Treatment?
    suspend fun upsertTreatment(treatment: Treatment): Long
    suspend fun deleteTreatment(id: Long)
}
