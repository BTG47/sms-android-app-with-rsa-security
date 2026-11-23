/*
 * Pruebas unitarias del codec SS1.
 * Responsabilidad: verificar la ida y vuelta del Envelope opaco y la integridad de los Fragmentos.
 */

package com.securesms.app.messaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos de aceptación de `encode` y `decode` con bytes opacos. */
class SmsFragmentCodecTest {

    private val envelope = ByteArray(300) { (it * 37).toByte() }

    @Test
    fun `fragmentos desordenados reconstruyen el envelope`() {
        val fragmentos = encode(envelope)
        assertEquals(4, fragmentos.size)
        assertArrayEquals(envelope, decode(fragmentos.shuffled()))
    }

    @Test
    fun `duplicados identicos no alteran el resultado`() {
        val fragmentos = encode(envelope)
        assertArrayEquals(envelope, decode(fragmentos + fragmentos.first()))
    }

    @Test
    fun `contradicciones se rechazan`() {
        val fragmentos = encode(envelope)
        val campos = fragmentos[0].split(':', limit = 5).toMutableList()
        campos[4] = "QUJD"
        val contradictorio = campos.joinToString(":")
        assertNull(decode(fragmentos + contradictorio))
    }

    @Test
    fun `fragmento faltante se rechaza`() {
        assertNull(decode(encode(envelope).drop(1)))
    }

    @Test
    fun `texto malformado se rechaza`() {
        assertNull(decode(listOf("SS1:ABC:01:03:QUJD")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `envelope vacio se rechaza`() {
        encode(ByteArray(0))
    }

    @Test
    fun `id hexadecimal se valida sin distinguir mayusculas`() {
        assertNotNull(parsear("SS1:abcdef123456:01:01:QUJD"))
        assertNull(parsear("SS1:ZZZZZZZZZZZZ:01:01:QUJD"))
    }
}
