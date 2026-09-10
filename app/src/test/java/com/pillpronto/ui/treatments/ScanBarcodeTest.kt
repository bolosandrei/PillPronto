package com.pillpronto.ui.treatments

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanBarcodeTest {

    @Test
    fun `format DataMatrix delegheaza la parserul GS1`() {
        val raw = "01" + "05901234123457"

        val gtin = extractGtin(Barcode.FORMAT_DATA_MATRIX, raw)

        assertEquals("05901234123457", gtin)
    }

    @Test
    fun `format DataMatrix fara GTIN in payload intoarce null`() {
        val gtin = extractGtin(Barcode.FORMAT_DATA_MATRIX, "17251231")

        assertNull(gtin)
    }

    @Test
    fun `EAN-13 se padeaza la GTIN-14`() {
        val gtin = extractGtin(Barcode.FORMAT_EAN_13, "5901234123457")

        assertEquals("05901234123457", gtin)
    }

    @Test
    fun `EAN-8 se padeaza la GTIN-14`() {
        val gtin = extractGtin(Barcode.FORMAT_EAN_8, "12345670")

        assertEquals("00000012345670", gtin)
    }

    @Test
    fun `UPC-A se padeaza la GTIN-14`() {
        val gtin = extractGtin(Barcode.FORMAT_UPC_A, "036000291452")

        assertEquals("00036000291452", gtin)
    }

    @Test
    fun `lungime gresita pt formatul liniar intoarce null`() {
        assertNull(extractGtin(Barcode.FORMAT_EAN_13, "123"))
    }

    @Test
    fun `valoare non-numerica intoarce null`() {
        assertNull(extractGtin(Barcode.FORMAT_EAN_13, "590123412345X"))
    }

    @Test
    fun `format nesuportat intoarce null`() {
        assertNull(extractGtin(Barcode.FORMAT_QR_CODE, "orice"))
    }

    @Test
    fun `rawValue null sau gol intoarce null`() {
        assertNull(extractGtin(Barcode.FORMAT_DATA_MATRIX, null))
        assertNull(extractGtin(Barcode.FORMAT_DATA_MATRIX, ""))
    }
}
