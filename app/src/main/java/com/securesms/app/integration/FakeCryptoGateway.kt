package com.securesms.app.integration

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Implante demo de [CryptoGateway]: ofusca el texto de forma reversible sin cifrarlo.
 *
 * Sirve para probar el transporte completo, incluido el estado «Descifrado», mientras no se
 * integra `:crypto`. El envelope empieza por `DEMO`, sigue el ordinal del [Perfil] y termina
 * con el texto UTF-8 aplicado un XOR con una clave fija. **No es criptografía**: cualquiera
 * que conozca este código puede recuperar el texto. La interfaz muestra el modo demo siempre.
 */
class FakeCryptoGateway : CryptoGateway {

    override val esDemo: Boolean = true

    override fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> = runCatching {
        val ordinal = Perfil.entries.indexOf(perfil)
        require(ordinal >= 0) { "Perfil desconocido" }
        MAGIC + byteArrayOf(ordinal.toByte()) + xor(texto.toByteArray(Charsets.UTF_8))
    }

    override fun descifrar(envelope: ByteArray): Result<MensajeDescifrado> = runCatching {
        require(envelope.size >= MAGIC.size + 1) { "Envelope truncado" }
        require(envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Envelope ajeno" }
        val ordinal = envelope[MAGIC.size].toInt() and 0xff
        require(ordinal in Perfil.entries.indices) { "Perfil desconocido" }

        val claro = xor(envelope.copyOfRange(MAGIC.size + 1, envelope.size))
        val texto = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(claro))
            .toString()
        MensajeDescifrado(texto, Perfil.entries[ordinal])
    }

    /** Aplica el XOR con [CLAVE]; la operación es su propia inversa. */
    private fun xor(datos: ByteArray): ByteArray = ByteArray(datos.size) { indice ->
        (datos[indice].toInt() xor CLAVE[indice % CLAVE.size].toInt()).toByte()
    }

    private companion object {
        /** Marca los bytes como producidos por este implante y no por `:crypto`. */
        val MAGIC = "DEMO".toByteArray(Charsets.US_ASCII)

        /** Clave fija y pública: no aporta confidencialidad, solo evita el texto en claro. */
        val CLAVE = "SecureSMS-demo".toByteArray(Charsets.US_ASCII)
    }
}
