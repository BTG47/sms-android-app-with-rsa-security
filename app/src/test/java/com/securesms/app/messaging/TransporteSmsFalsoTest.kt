package com.securesms.app.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba el envío y el ciclo de vida de la recepción mediante los dobles. */
class TransporteSmsFalsoTest {
    @Test fun `registra todos los fragmentos de un envio en orden`() {
        val transporte = TransporteSmsFalso()
        val fragmentos = listOf("SS1:A1B2C3D4E5F6:01:02:AAAA", "SS1:A1B2C3D4E5F6:02:02:BBBB")
        assertEquals(EstadoSim.LISTA, transporte.estadoSim())
        assertTrue(transporte.enviar("5512345678", fragmentos).isSuccess)
        assertEquals(TransporteSmsFalso.Envio("5512345678", fragmentos), transporte.envios.single())
    }

    @Test fun `sim ausente o fallo de envio impiden registrar la solicitud`() {
        val transporte = TransporteSmsFalso(sim = EstadoSim.AUSENTE)
        assertEquals(EstadoSim.AUSENTE, transporte.estadoSim())
        assertTrue(transporte.enviar("5512345678", listOf("x")).isFailure)
        transporte.sim = EstadoSim.LISTA
        transporte.falla = true
        assertTrue(transporte.enviar("5512345678", listOf("x")).isFailure)
        assertTrue(transporte.envios.isEmpty())
    }

    @Test fun `entrega solo al receptor activo y conserva el primer registro`() {
        val registro = RegistroSmsFalso()
        val recibidos = mutableListOf<Pair<String, String>>()
        val cuerpo = "SS1:A1B2C3D4E5F6:01:01:AAAA"
        registro.registrar { remitente, mensaje -> recibidos.add(remitente to mensaje) }
        registro.registrar { _, _ -> error("Registro duplicado") }
        registro.simular("5551", cuerpo)
        registro.cancelar()
        registro.simular("5552", cuerpo)
        assertEquals(listOf("5551" to cuerpo), recibidos)
    }
}
