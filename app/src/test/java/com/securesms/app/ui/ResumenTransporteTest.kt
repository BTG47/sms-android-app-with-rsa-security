/*
 * Pruebas unitarias del resumen de diagnóstico.
 * Responsabilidad: verificar los tres casos de aceptación del bloque 1.
 */

package com.securesms.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Casos de aceptación del formato de resumen. */
class ResumenTransporteTest {

    @Test
    fun `muestra huella y cantidad de sms conocidas`() {
        val datos = DatosDeEnvio("+59170000000", "ECC_P256", "A1B2")
        assertEquals(
            "+59170000000 | ECC_P256 | 640 bytes | SMS: 5 | huella: A1B2",
            resumen(datos, 640, 5)
        )
    }

    @Test
    fun `marca la huella como sin verificar cuando falta`() {
        val datos = DatosDeEnvio("+59170000000", "RSA_2048", null)
        assertEquals(
            "+59170000000 | RSA_2048 | 384 bytes | SMS: 3 | huella: sin verificar",
            resumen(datos, 384, 3)
        )
    }

    @Test
    fun `marca el numero de sms como pendiente cuando no se calculo`() {
        val datos = DatosDeEnvio("+59170000000", "RSA_3072", "C3D4")
        assertEquals(
            "+59170000000 | RSA_3072 | 900 bytes | SMS: pendiente | huella: C3D4",
            resumen(datos, 900, null)
        )
    }
}
