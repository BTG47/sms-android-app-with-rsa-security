package com.securesms.app.integration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba si [aguja] aparece como secuencia contigua dentro de [bytes]. */
private fun contiene(bytes: ByteArray, aguja: ByteArray): Boolean {
    if (aguja.isEmpty() || aguja.size > bytes.size) return false
    return (0..bytes.size - aguja.size).any { inicio ->
        aguja.indices.all { aguja[it] == bytes[inicio + it] }
    }
}

/** Casos de aceptación del implante demo de la fachada de cifrado. */
class FakeCryptoGatewayTest {

    private val gateway = FakeCryptoGateway()

    @Test
    fun `esDemo es verdadero`() {
        assertTrue(gateway.esDemo)
    }

    @Test
    fun `cifrar devuelve bytes no vacios y deterministas`() {
        val primero = gateway.cifrar("Hola SecureSMS", Perfil.RSA_2048).getOrThrow()
        val segundo = gateway.cifrar("Hola SecureSMS", Perfil.RSA_2048).getOrThrow()

        assertTrue(primero.isNotEmpty())
        assertArrayEquals(primero, segundo)
    }

    @Test
    fun `el envelope no contiene el texto plano`() {
        val bytes = gateway.cifrar("Hola SecureSMS", Perfil.RSA_2048).getOrThrow()

        assertFalse(contiene(bytes, "Hola SecureSMS".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `texto vacio tambien produce bytes no vacios`() {
        assertTrue(gateway.cifrar("", Perfil.ECC_P256).getOrThrow().isNotEmpty())
    }

    @Test
    fun `el tamano esta acotado`() {
        val bytes = gateway.cifrar("x".repeat(10_000), Perfil.ECC_P384).getOrThrow()

        assertTrue(bytes.size <= 36)
    }
}
