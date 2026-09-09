package com.pillpronto.data.local.nomenclature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NomenclatureImporterTest {

    private fun validLine(codCim: String = "W43451001") =
        listOf(
            codCim, "5 - FLUOROURACIL EBEWE 50mg/ml", "FLUOROURACILUM", "CONC. PT. SOL. INJ./PERF.",
            "50mg/ml", "EBEWE PHARMA GES.M.B.H. NFG. KG - AUSTRIA", "EBEWE PHARMA GES.M.B.H. NFG. KG - AUSTRIA",
            "L01BC02", "ANTIMETABOLITI ANALOGI AI BAZELOR PIRIMIDINICE", "PR", "7321/2015/01",
            "Cutie cu 1 flac. din sticla bruna x 5 ml conc. pt. sol. inj./perf.", "5 ml", "2 ani",
            "", "", "", "", "", "09.09.26"
        ).joinToString("\t")

    @Test
    fun `linie valida se parseaza corect`() {
        val entity = parseNomenclatureLine(validLine())

        assertEquals("W43451001", entity?.codCim)
        assertEquals("5 - FLUOROURACIL EBEWE 50mg/ml", entity?.denumireComerciala)
        assertEquals("FLUOROURACILUM", entity?.dci)
        assertEquals("50mg/ml", entity?.concentratie)
        assertEquals("09.09.26", entity?.dataActualizare)
    }

    @Test
    fun `linie cu prea putine coloane intoarce null`() {
        val entity = parseNomenclatureLine("W43451001\tNume\tDCI")

        assertNull(entity)
    }

    @Test
    fun `linie cu codCim gol intoarce null`() {
        val entity = parseNomenclatureLine(validLine(codCim = ""))

        assertNull(entity)
    }

    @Test
    fun `linie cu campuri finale goale (bulina, diez etc) se parseaza fara eroare`() {
        val entity = parseNomenclatureLine(validLine())

        assertEquals("", entity?.bulina)
        assertEquals("", entity?.triunghi)
    }
}
