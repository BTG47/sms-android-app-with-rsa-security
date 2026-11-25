package com.securesms.app.integration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun `el envelope demo se identifica por su prefijo`() {
        val bytes = gateway.cifrar("Hola", Perfil.ECC_P256).getOrThrow()

        assertArrayEquals("DEMO".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 4))
    }

    @Test
    fun `descifrar recupera el texto y el perfil`() {
        for (perfil in Perfil.entries) {
            val bytes = gateway.cifrar("Hola ñ 你好 😀", perfil).getOrThrow()
            val claro = gateway.descifrar(bytes).getOrThrow()

            assertEquals("Hola ñ 你好 😀", claro.texto)
            assertEquals(perfil, claro.perfil)
        }
    }

    @Test
    fun `texto vacio ida y vuelta`() {
        val claro = gateway.descifrar(gateway.cifrar("", Perfil.RSA_3072).getOrThrow()).getOrThrow()

        assertEquals("", claro.texto)
    }

    @Test
    fun `descifrar rechaza bytes que no son demo`() {
        assertTrue(gateway.descifrar("SSMS".toByteArray()).isFailure)
        assertTrue(gateway.descifrar(ByteArray(0)).isFailure)
    }

    @Test
    fun `un texto largo se recupera intacto`() {
        val texto = "x".repeat(10_000)
        val bytes = gateway.cifrar(texto, Perfil.ECC_P384).getOrThrow()

        assertEquals(texto, gateway.descifrar(bytes).getOrThrow().texto)
    }
}
