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

    @Test
    fun `cada fragmento reinicia los cinco minutos`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("5551", fragmentos[0])
        reloj.avanzar(4 * 60 * 1000L)
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
        assembler.agregar("5551", fragmentos[1])
        reloj.avanzar(4 * 60 * 1000L)
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
        reloj.avanzar(60 * 1000L + 1)
        assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
    }

    @Test
    fun `duplicado no reinicia el plazo y el limite expira`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("5551", fragmentos[0])
        reloj.avanzar(4 * 60 * 1000L)
        assembler.agregar("5551", fragmentos[0])
        reloj.avanzar(60 * 1000L - 1)
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
        reloj.avanzar(1)
        assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
    }

    @Test
    fun `grupo completo no expira por inactividad`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        fragmentos.forEach { assembler.agregar("5551", it) }
        reloj.avanzar(10 * 60 * 1000L)
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
        assertArrayEquals(envelope, assembler.envelope("5551", id))
    }

    @Test
    fun `fragmento tardio crea un grupo nuevo`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("5551", fragmentos[0])
        reloj.avanzar(5 * 60 * 1000L)
        assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
        assertNull(assembler.envelope("5551", id))

        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.agregar("5551", fragmentos[1]))
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
    }
}
