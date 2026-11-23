/*
 * Pruebas unitarias del ensamblado de fragmentos.
 * Responsabilidad: verificar estados, expiración por inactividad y límites del contrato.
 */

package com.securesms.app.messaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos de aceptación de `FragmentAssembler` con bytes opacos. */
class FragmentAssemblerTest {

    private val envelope = ByteArray(300) { (it * 37).toByte() }

    @Test
    fun `incompleto hasta el ultimo fragmento`() {
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]
        val assembler = FragmentAssembler({ 0L })

        fragmentos.dropLast(1).forEach {
            assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.agregar("5551", it))
        }
        assertNull(assembler.envelope("5551", id))
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.agregar("5551", fragmentos.last()))
        assertArrayEquals(envelope, assembler.envelope("5551", id))
    }
}
