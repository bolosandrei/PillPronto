package com.pillpronto.domain.gs1

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GS = '\u001D'

class Gs1ParserTest {

    @Test
    fun `payload gol intoarce null`() {
        assertNull(Gs1Parser.parse(""))
        assertNull(Gs1Parser.parse("   "))
    }

    @Test
    fun `gtin plus expirare plus lot cu separator GS pe ultimul camp variabil`() {
        // 01 + GTIN(14) + 17 + YYMMDD(6) + 10 + LOT + GS + 21 + SERIAL (ultimul camp, fara GS dupa)
        val raw = "01" + "05901234123457" + "17" + "251231" + "10" + "LOT42A" + GS + "21" + "SN00123"

        val decoded = Gs1Parser.parse(raw)

        assertEquals("05901234123457", decoded?.gtin)
        assertEquals(LocalDate.of(2025, 12, 31), decoded?.expiry)
        assertEquals("LOT42A", decoded?.batch)
        assertEquals("SN00123", decoded?.serial)
    }

    @Test
    fun `camp variabil fara separator GS se opreste la lungimea maxima standard`() {
        // Doar GTIN + lot de exact 20 caractere, fara AI dupa si fara separator GS.
        val lot20 = "A".repeat(20)
        val raw = "01" + "05901234123457" + "10" + lot20

        val decoded = Gs1Parser.parse(raw)

        assertEquals(lot20, decoded?.batch)
        assertNull(decoded?.serial)
    }

    @Test
    fun `gtin trunchiat opreste parsarea fara sa arunce`() {
        val raw = "01" + "0590123" // mai putin de 14 cifre ramase

        val decoded = Gs1Parser.parse(raw)

        assertNull(decoded?.gtin)
    }

    @Test
    fun `ai necunoscut la mijloc opreste parsarea dar pastreaza campurile de dinainte`() {
        val raw = "01" + "05901234123457" + "99" + "orice altceva aici"

        val decoded = Gs1Parser.parse(raw)

        assertEquals("05901234123457", decoded?.gtin)
        assertNull(decoded?.batch)
        assertNull(decoded?.serial)
        assertNull(decoded?.expiry)
    }

    @Test
    fun `doar gtin fara celelalte AI-uri nu esueaza total`() {
        val raw = "01" + "05901234123457"

        val decoded = Gs1Parser.parse(raw)

        assertEquals("05901234123457", decoded?.gtin)
        assertNull(decoded?.batch)
        assertNull(decoded?.expiry)
        assertNull(decoded?.serial)
    }

    @Test
    fun `data expirare cu ziua 00 foloseste ultima zi a lunii`() {
        val raw = "17" + "250200" // februarie 2025, DD=00

        val decoded = Gs1Parser.parse(raw)

        assertEquals(LocalDate.of(2025, 2, 28), decoded?.expiry)
    }

    @Test
    fun `pivotul de secol GS1 trateaza yy mai mare sau egal cu 51 ca 1900 plus yy`() {
        val raw = "17" + "511231"

        val decoded = Gs1Parser.parse(raw)

        assertEquals(LocalDate.of(1951, 12, 31), decoded?.expiry)
    }
}
