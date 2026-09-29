package com.pillpronto.ui.vision

import androidx.compose.ui.graphics.Color
import com.pillpronto.core.ui.theme.DoseDueNow
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.core.ui.theme.DoseUnknown
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.DoseColorState

/** Mapeaza statusul unei doze (recunoscute vizual, vezi ResolveDoseStatusForCodCimUseCase) pe
 * culoarea conturului AR — TAKEN->verde, PENDING+actionabil->portocaliu ("de luat acum"),
 * MISSED/SKIPPED->rosu (ambele = "nu s-a luat conform programului"; SKIPPED n-are o culoare
 * distincta in specificatia originala din CLAUDE.md, care are doar 4 culori). Orice altceva
 * (PENDING neactionabil inca, sau niciun status rezolvat = neidentificat) -> gri. */
fun DoseColorState?.toContourColor(): Color = when {
    this == null -> DoseUnknown
    status == DoseStatus.TAKEN -> DoseTaken
    status == DoseStatus.MISSED || status == DoseStatus.SKIPPED -> DoseMissed
    status == DoseStatus.PENDING && isActionable -> DoseDueNow
    else -> DoseUnknown
}
