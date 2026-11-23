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

    @Test
    fun `techo de grupos descarta el mas inactivo`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora, maxGrupos = 2)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("A", fragmentos[0])
        reloj.avanzar(1000)
        assembler.agregar("B", fragmentos[0])
        reloj.avanzar(1000)
        assembler.agregar("C", fragmentos[0])

        assertNull(assembler.estado("A", id))
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("B", id))
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("C", id))
    }

    @Test
    fun `tope de noventa y nueve textos por grupo`() {
        val completo = ByteArray(9560) { (it * 31).toByte() }
        val fragmentos = encode(completo)
        assertEquals(99, fragmentos.size)
        val id = fragmentos.first().split(':')[1]
        val assembler = FragmentAssembler({ 0L })

        fragmentos.forEach { assembler.agregar("5551", it) }
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
        assertArrayEquals(completo, assembler.envelope("5551", id))

        val campos = fragmentos[0].split(':').toMutableList()
        campos[4] = "QUJD"
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.agregar("5551", campos.joinToString(":")))
        assertArrayEquals(completo, assembler.envelope("5551", id))
    }

    @Test
    fun `agregar barre antes de acumular`() {
        val reloj = RelojFalso()
        val assembler = FragmentAssembler(reloj::ahora)
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("5551", fragmentos[0])
        reloj.avanzar(5 * 60 * 1000L)
        assembler.agregar("5551", fragmentos[1])
        assembler.agregar("5551", fragmentos[2])
        assembler.agregar("5551", fragmentos[3])

        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
        assertNull(assembler.envelope("5551", id))
    }

    @Test
    fun `contradiccion no entrega envelope`() {
        val assembler = FragmentAssembler({ 0L })
        val fragmentos = encode(envelope).toMutableList()
        val campos = fragmentos[0].split(':').toMutableList()
        campos[4] = "QUJD"
        fragmentos.add(campos.joinToString(":"))
        val id = fragmentos.first().split(':')[1]

        fragmentos.forEach { assembler.agregar("5551", it) }
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
        assertNull(assembler.envelope("5551", id))
    }

    @Test
    fun `distintos messageId no forman un grupo`() {
        val assembler = FragmentAssembler({ 0L })
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]
        val ajeno = if (id == "000000000000") "111111111111" else "000000000000"
        val campos = fragmentos[1].split(':').toMutableList()
        campos[1] = ajeno

        fragmentos.forEach { assembler.agregar("5551", it) }
        assembler.agregar("5551", campos.joinToString(":"))

        assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
        assertArrayEquals(envelope, assembler.envelope("5551", id))
        assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", ajeno))
        assertNull(assembler.envelope("5551", ajeno))
    }

    @Test
    fun `descartar elimina el grupo`() {
        val assembler = FragmentAssembler({ 0L })
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]

        assembler.agregar("5551", fragmentos[0])
        assembler.descartar("5551", id)
        assertNull(assembler.estado("5551", id))
    }

    @Test
    fun `indice fuera del total se ignora`() {
        val assembler = FragmentAssembler({ 0L })
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]
        fragmentos.forEach { assembler.agregar("5551", it) }
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))

        val campos = fragmentos[0].split(':').toMutableList()
        campos[2] = "99"
        assembler.agregar("5551", campos.joinToString(":"))

        assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
        assertArrayEquals(envelope, assembler.envelope("5551", id))
    }
}
