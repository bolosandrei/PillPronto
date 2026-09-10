package com.pillpronto.data.local.gtinmapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GtinMappingSeedImporterTest {

    @Test
    fun `linie valida se parseaza corect`() {
        val entity = parseSeedLine("05901234123457\tW43451001")

        assertEquals("05901234123457", entity?.gtin)
        assertEquals("W43451001", entity?.codCim)
        assertEquals(0L, entity?.confirmedAt)
    }

    @Test
    fun `linie cu prea putine coloane intoarce null`() {
        assertNull(parseSeedLine("05901234123457"))
        assertNull(parseSeedLine(""))
    }

    @Test
    fun `gtin gol intoarce null`() {
        assertNull(parseSeedLine("\tW43451001"))
    }

    @Test
    fun `codCim gol intoarce null`() {
        assertNull(parseSeedLine("05901234123457\t"))
    }
}
