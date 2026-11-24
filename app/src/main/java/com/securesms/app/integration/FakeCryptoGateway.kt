package com.securesms.app.integration

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Implante demo de [CryptoGateway]: genera bytes opacos y deterministas sin cifrar.
 *
 * Sirve para probar el transporte y el resumen mientras no se integra `:crypto`.
 * El prefijo `DEMO` distingue estos bytes de un Envelope real; el resto es un SHA-256
 * del perfil y del texto, por lo que el contenido nunca aparece en claro y el tamaño
 * se mantiene fijo en 36 bytes.
 */
class FakeCryptoGateway : CryptoGateway {

    override val esDemo: Boolean = true

    override fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> = runCatching {
        val prefijo = "DEMO".toByteArray(StandardCharsets.US_ASCII)
        val entrada = "$perfil|$texto".toByteArray(StandardCharsets.UTF_8)
        prefijo + MessageDigest.getInstance("SHA-256").digest(entrada)
    }
}
