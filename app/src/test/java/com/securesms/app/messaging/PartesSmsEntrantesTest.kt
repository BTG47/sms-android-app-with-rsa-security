// Cubre la recepción de segmentos físicos y fragmentos SS1 independientes.
package com.securesms.app.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartesSmsEntrantesTest {
    private val fragmentos = encode(ByteArray(220) { it.toByte() })

    @Test
    fun uneSegmentosDeAndroidAntesDeEntregarUnFragmentoSs1() {
        val fragmento = fragmentos.first()
        assertEquals(listOf(fragmento), fragmentosSmsEntrantes(fragmento.chunked(80)))
    }

    @Test
    fun conservaFragmentosSs1Independientes() {
        assertEquals(fragmentos, fragmentosSmsEntrantes(fragmentos))
    }

    @Test
    fun ignoraSmsComunesYFragmentosFueraDelLimite() {
        assertTrue(fragmentosSmsEntrantes(listOf("ordinary SMS")).isEmpty())
        assertTrue(fragmentosSmsEntrantes(listOf(fragmentos.first() + "A")).isEmpty())
    }
}
