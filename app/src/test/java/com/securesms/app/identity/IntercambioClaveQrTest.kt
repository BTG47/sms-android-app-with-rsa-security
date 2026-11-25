// Verifica que el intercambio por QR preserve los datos públicos y rechace perfiles incompatibles.
package com.securesms.app.identity

import com.securesms.app.integration.Perfil
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IntercambioClaveQrTest {
    private val material = Base64.getEncoder().encodeToString(
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public.encoded,
    )

    @Test
    fun conservaMaterialYHuellaDeLaClavePropia() {
        val codigo = codificarClaveQr(Perfil.RSA_2048, material)
        val clave = leerClaveQr(codigo)
        assertNotNull(clave)
        assertEquals(Perfil.RSA_2048, clave?.perfil)
        assertEquals(material, clave?.materialBase64)
        assertEquals(Huella.calcular(material).getOrThrow(), clave?.huella)
    }

    @Test
    fun rechazaClavesConPerfilIncorrectoYTextoAjeno() {
        assertNull(leerClaveQr(codificarClaveQr(Perfil.RSA_3072, material)))
        assertNull(leerClaveQr("SSPK1:RSA_2048:AA"))
        assertNull(leerClaveQr("texto ajeno al intercambio SecureSMS"))
    }
}
