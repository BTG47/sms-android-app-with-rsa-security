// Fija las etiquetas que distinguen la aceptación, recepción y lectura de un mensaje.
package com.securesms.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PantallaMensajesTest {
    @Test
    fun `cada estado usa la etiqueta del glosario`() {
        val etiquetas = mapOf(
            EstadoMensaje.PREPARANDO to "Preparing",
            EstadoMensaje.CIFRADO to "Encrypted",
            EstadoMensaje.ENVIANDO to "Sending",
            EstadoMensaje.ACEPTADO_PARA_ENVIO to "Accepted for sending",
            EstadoMensaje.INCOMPLETO to "Incomplete",
            EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO to "Received and reassembled",
            EstadoMensaje.DESCIFRADO to "Decrypted",
            EstadoMensaje.EXPIRADO to "Expired",
            EstadoMensaje.ERROR to "Error",
        )
        assertEquals(EstadoMensaje.entries.toSet(), etiquetas.keys)
        etiquetas.forEach { (estado, etiqueta) -> assertEquals(etiqueta, etiquetaDe(estado)) }
    }
}
