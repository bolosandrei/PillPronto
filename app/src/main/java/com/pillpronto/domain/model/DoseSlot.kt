package com.pillpronto.domain.model

import java.time.LocalTime

/** O ora de administrare din orarul unui tratament, cu propria cantitate opțională — permite
 * modelarea unor regimuri precum "Nolpaza dimineața 1 comprimat, seara 2 comprimate": doua
 * sloturi, cantitati diferite. `cantitate` goala = mostenit din `Treatment.cantitate` (cantitatea
 * generala a tratamentului) — vezi GenerateDosesUseCase. */
data class DoseSlot(val time: LocalTime, val cantitate: String = "")
