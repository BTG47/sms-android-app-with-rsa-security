package com.securesms.app.ui

import com.securesms.app.integration.CryptoGateway
import com.securesms.app.integration.FakeCryptoGateway
import com.securesms.app.integration.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class GatewayQueFalla : CryptoGateway {
    override val esDemo: Boolean = false

    override fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> =
        Result.failure(IllegalStateException("secreto-del-gateway"))
}

private class GatewaySinBytes : CryptoGateway {
    override val esDemo: Boolean = true

    override fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> = Result.success(ByteArray(0))
}

/** Comprueba si [aguja] aparece como secuencia contigua dentro de [bytes]. */
private fun contiene(bytes: ByteArray, aguja: ByteArray): Boolean {
    if (aguja.isEmpty() || aguja.size > bytes.size) return false
    return (0..bytes.size - aguja.size).any { inicio ->
        aguja.indices.all { aguja[it] == bytes[inicio + it] }
    }
}

/** Casos de aceptación del estado que alimenta la interfaz. */
class ResumenViewModelTest {

    @Test
    fun `estado inicial sin envio`() {
        val viewModel = ResumenViewModel(FakeCryptoGateway())

        assertEquals("", viewModel.uiState.value.resumen)
        assertNull(viewModel.uiState.value.envelope)
    }

    @Test
    fun `expone esDemo desde la gateway`() {
        assertTrue(ResumenViewModel(FakeCryptoGateway()).esDemo)
        assertFalse(ResumenViewModel(GatewayQueFalla()).esDemo)
    }

    @Test
    fun `cifrar construye el resumen con perfil bytes y sms`() {
        val viewModel = ResumenViewModel(FakeCryptoGateway())

        viewModel.cifrar("Hola", Perfil.RSA_2048)

        assertEquals(
            "sin destinatario | RSA_2048 | 36 bytes | SMS: 1 | huella: sin verificar",
            viewModel.uiState.value.resumen,
        )
    }

    @Test
    fun `el estado no contiene el texto plano`() {
        val viewModel = ResumenViewModel(FakeCryptoGateway())

        viewModel.cifrar("Hola", Perfil.RSA_2048)

        assertFalse(viewModel.uiState.value.resumen.contains("Hola"))
        val envelope = viewModel.uiState.value.envelope!!
        assertFalse(contiene(envelope, "Hola".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `un fallo no fabrica metricas ni filtra secretos`() {
        val viewModel = ResumenViewModel(GatewayQueFalla())

        viewModel.cifrar("Hola", Perfil.RSA_2048)

        assertEquals("error al cifrar", viewModel.uiState.value.resumen)
        assertNull(viewModel.uiState.value.envelope)
        assertFalse(viewModel.uiState.value.resumen.contains("secreto-del-gateway"))
    }

    @Test
    fun `bytes vacios se tratan como fallo`() {
        val viewModel = ResumenViewModel(GatewaySinBytes())

        viewModel.cifrar("Hola", Perfil.RSA_2048)

        assertEquals("error al cifrar", viewModel.uiState.value.resumen)
        assertNull(viewModel.uiState.value.envelope)
    }
}
