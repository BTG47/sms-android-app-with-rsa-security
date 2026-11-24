package com.securesms.app.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos de la huella SHA-256 del material público. */
class HuellaTest {

    @Test
    fun `material conocido produce su sha-256 hexadecimal`() {
        // "YWJj" es el Base64 de los tres bytes "abc", con SHA-256 conocido.
        val resultado = Huella.calcular("YWJj")

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            resultado.getOrThrow(),
        )
    }

    @Test
    fun `material que no es base64 falla sin lanzar`() {
        val resultado = Huella.calcular("no-es-base64!!")

        assertTrue(resultado.isFailure)
    }

    @Test
    fun `normalizar ignora espacios separadores y mayusculas`() {
        assertEquals("ba7816bf", Huella.normalizar(" BA78 16:BF "))
    }
}
