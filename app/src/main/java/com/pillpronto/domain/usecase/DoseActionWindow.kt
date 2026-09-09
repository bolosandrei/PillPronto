package com.pillpronto.domain.usecase

import java.time.Duration
import java.time.LocalDateTime

/** Fereastra in care o doza mai poate fi confirmata/omisa manual — inainte, e prea devreme
 * (doza nu e inca "a acum"); dupa, raspunsul nu mai reflecta realitatea sigur, iar dozele
 * depasite trec oricum in MISSED (vezi MarkOverdueDosesUseCase). Functie pura, testabila, folosita
 * atat de LogDoseUseCase (aplicat si notificarilor, vezi DoseActionReceiver) cat si de
 * ui/today/TodayScreen.kt (afisarea butoanelor Confirma/Omite). */
const val ACTION_WINDOW_MINUTES = 60L

fun isDoseActionable(scheduledAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): Boolean =
    Duration.between(scheduledAt, now).abs().toMinutes() <= ACTION_WINDOW_MINUTES
