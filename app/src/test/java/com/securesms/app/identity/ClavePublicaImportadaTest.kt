package com.securesms.app.identity

import com.securesms.app.integration.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos del modelo de clave pública y de su regla de confianza. */
class ClavePublicaImportadaTest {

    private val contacto = Contacto("5512345678", "Ana")

    @Test
    fun `crear deja la clave importada sin verificar`() {
        val clave = ClavePublicaImportada
            .crear(contacto, Perfil.RSA_2048, "YWJj")
            .getOrThrow()

        assertEquals(EstadoClave.IMPORTADA_SIN_VERIFICAR, clave.estado)
        assertFalse(clave.habilitaEnvio)
    }

    @Test
    fun `solo el estado verificado habilita el envio`() {
        val importada = ClavePublicaImportada
            .crear(contacto, Perfil.RSA_2048, "YWJj")
            .getOrThrow()

        assertFalse(importada.habilitaEnvio)
        assertTrue(importada.copy(estado = EstadoClave.VERIFICADA).habilitaEnvio)
    }

    @Test
    fun `material invalido no crea la clave`() {
        val resultado = ClavePublicaImportada.crear(contacto, Perfil.RSA_2048, "%%%")

        assertTrue(resultado.isFailure)
    }
}
