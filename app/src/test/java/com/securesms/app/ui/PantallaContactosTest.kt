package com.securesms.app.ui

import com.securesms.app.identity.EstadoClave
import org.junit.Assert.assertEquals
import org.junit.Test

/** Casos de las funciones puras que usa la pantalla de contactos. */
class PantallaContactosTest {

    @Test
    fun `cada estado tiene su etiqueta del glosario`() {
        assertEquals("No key imported", etiquetaDe(EstadoClave.SIN_IMPORTAR))
        assertEquals(
            "Imported, unverified",
            etiquetaDe(EstadoClave.IMPORTADA_SIN_VERIFICAR),
        )
        assertEquals("Verified", etiquetaDe(EstadoClave.VERIFICADA))
    }

    @Test
    fun `la huella se agrupa en bloques de ocho`() {
        val huella = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertEquals(
            "ba7816bf 8f01cfea 414140de 5dae2223 " +
                "b00361a3 96177a9c b410ff61 f20015ad",
            huellaLegible(huella),
        )
    }

    @Test
    fun `la huella vacia no produce separadores`() {
        assertEquals("", huellaLegible(""))
    }
}
