package com.pillpronto.ui.treatments

import com.google.mlkit.vision.barcode.common.Barcode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanBarcodeTest {

    @Test
    fun `format DataMatrix delegheaza la parserul GS1 pt gtin`() {
        val raw = "01" + "05901234123457"

        val scanned = extractScannedBarcode(Barcode.FORMAT_DATA_MATRIX, raw)

        assertEquals("05901234123457", scanned.gtin)
        assertNull(scanned.expiryDate)
    }

    @Test
    fun `format DataMatrix cu AI 17 extrage si data expirarii`() {
        val raw = "01" + "05901234123457" + "17" + "261031"

        val scanned = extractScannedBarcode(Barcode.FORMAT_DATA_MATRIX, raw)

        assertEquals("05901234123457", scanned.gtin)
        assertEquals(LocalDate.of(2026, 10, 31), scanned.expiryDate)
    }

    @Test
    fun `format DataMatrix fara GTIN in payload intoarce gtin null`() {
        val scanned = extractScannedBarcode(Barcode.FORMAT_DATA_MATRIX, "17251231")

        assertNull(scanned.gtin)
    }

    @Test
    fun `EAN-13 se padeaza la GTIN-14 si nu are data expirarii`() {
        val scanned = extractScannedBarcode(Barcode.FORMAT_EAN_13, "5901234123457")

        assertEquals("05901234123457", scanned.gtin)
        assertNull(scanned.expiryDate)
    }

    @Test
    fun `EAN-8 se padeaza la GTIN-14`() {
        val scanned = extractScannedBarcode(Barcode.FORMAT_EAN_8, "12345670")

        assertEquals("00000012345670", scanned.gtin)
    }

    @Test
    fun `UPC-A se padeaza la GTIN-14`() {
        val scanned = extractScannedBarcode(Barcode.FORMAT_UPC_A, "036000291452")

        assertEquals("00036000291452", scanned.gtin)
    }

    @Test
    fun `lungime gresita pt formatul liniar intoarce gtin null`() {
        assertNull(extractScannedBarcode(Barcode.FORMAT_EAN_13, "123").gtin)
    }

    @Test
    fun `valoare non-numerica intoarce gtin null`() {
        assertNull(extractScannedBarcode(Barcode.FORMAT_EAN_13, "590123412345X").gtin)
    }

    @Test
    fun `format nesuportat intoarce gtin null`() {
        assertNull(extractScannedBarcode(Barcode.FORMAT_QR_CODE, "orice").gtin)
    }

    @Test
    fun `rawValue null sau gol intoarce gtin null`() {
        assertNull(extractScannedBarcode(Barcode.FORMAT_DATA_MATRIX, null).gtin)
        assertNull(extractScannedBarcode(Barcode.FORMAT_DATA_MATRIX, "").gtin)
    }
}
