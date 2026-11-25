package com.securesms.app.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos de aceptación de la normalización previa al envío por SMS. */
class NumeroSmsTest {

    @Test
    fun `conserva un mas inicial y descarta formato`() {
        assertEquals("+525512345678", normalizarNumeroSms("+52 (55) 1234-5678"))
        assertEquals("5512345678", normalizarNumeroSms(" 55 1234 5678 "))
        assertEquals("551234", normalizarNumeroSms("55.12.34"))
    }

    @Test
    fun `rechaza lo que no es un numero utilizable`() {
        assertNull(normalizarNumeroSms(""))
        assertNull(normalizarNumeroSms("   "))
        assertNull(normalizarNumeroSms("abc"))
        assertNull(normalizarNumeroSms("+"))
        assertNull(normalizarNumeroSms("12+34"))
        assertNull(normalizarNumeroSms("12/34"))
    }
}
