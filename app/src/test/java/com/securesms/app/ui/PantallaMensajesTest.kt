// Fija las etiquetas que distinguen la aceptación, recepción y lectura de un mensaje.
package com.securesms.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PantallaMensajesTest {
    @Test
    fun `cada estado usa la etiqueta del glosario`() {
        val etiquetas = mapOf(
            EstadoMensaje.PREPARANDO to "Preparando",
            EstadoMensaje.CIFRADO to "Cifrado",
            EstadoMensaje.ENVIANDO to "Enviando",
            EstadoMensaje.ACEPTADO_PARA_ENVIO to "Aceptado para envio",
            EstadoMensaje.INCOMPLETO to "Incompleto",
            EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO to "Recibido y reconstruido",
            EstadoMensaje.DESCIFRADO to "Descifrado",
            EstadoMensaje.EXPIRADO to "Expirado",
            EstadoMensaje.ERROR to "Error",
        )
        assertEquals(EstadoMensaje.entries.toSet(), etiquetas.keys)
        etiquetas.forEach { (estado, etiqueta) -> assertEquals(etiqueta, etiquetaDe(estado)) }
    }
}
