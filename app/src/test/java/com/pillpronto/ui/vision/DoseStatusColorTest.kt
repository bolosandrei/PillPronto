package com.pillpronto.ui.vision

import com.pillpronto.core.ui.theme.DoseDueNow
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.core.ui.theme.DoseUnknown
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.DoseColorState
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseStatusColorTest {

    @Test
    fun `null intoarce gri (neidentificat)`() {
        assertEquals(DoseUnknown, null.toContourColor())
    }

    @Test
    fun `TAKEN intoarce verde`() {
        assertEquals(DoseTaken, DoseColorState(DoseStatus.TAKEN, isActionable = false).toContourColor())
    }

    @Test
    fun `MISSED intoarce rosu`() {
        assertEquals(DoseMissed, DoseColorState(DoseStatus.MISSED, isActionable = false).toContourColor())
    }

    @Test
    fun `SKIPPED intoarce tot rosu, ca MISSED`() {
        assertEquals(DoseMissed, DoseColorState(DoseStatus.SKIPPED, isActionable = false).toContourColor())
    }

    @Test
    fun `PENDING actionabil intoarce portocaliu`() {
        assertEquals(DoseDueNow, DoseColorState(DoseStatus.PENDING, isActionable = true).toContourColor())
    }

    @Test
    fun `PENDING neactionabil inca intoarce gri`() {
        assertEquals(DoseUnknown, DoseColorState(DoseStatus.PENDING, isActionable = false).toContourColor())
    }
}
