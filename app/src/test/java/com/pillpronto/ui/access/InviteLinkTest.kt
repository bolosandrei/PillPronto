package com.pillpronto.ui.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {

    @Test
    fun `round-trip pastreaza codul`() {
        val uri = buildInviteUri("ABC123")

        assertEquals("ABC123", extractInviteCode(uri))
    }

    @Test
    fun `URI null intoarce null`() {
        assertNull(extractInviteCode(null))
    }

    @Test
    fun `schema gresita intoarce null`() {
        assertNull(extractInviteCode("https://invite?code=ABC123"))
    }

    @Test
    fun `host gresit intoarce null`() {
        assertNull(extractInviteCode("pillpronto://altceva?code=ABC123"))
    }

    @Test
    fun `fara parametrul code intoarce null`() {
        assertNull(extractInviteCode("pillpronto://invite"))
        assertNull(extractInviteCode("pillpronto://invite?altparam=x"))
    }

    @Test
    fun `cod gol intoarce null`() {
        assertNull(extractInviteCode("pillpronto://invite?code="))
    }
}
