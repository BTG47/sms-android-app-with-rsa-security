// Adapta la biblioteca SecureSMS a los perfiles, contactos y errores de la app.
package com.securesms.app.integration

import com.securesms.app.identity.ClavePublicaImportada
import com.securesms.crypto.ErrorCode
import com.securesms.crypto.Profile
import com.securesms.crypto.PublicKeys
import com.securesms.crypto.SecureSmsCrypto
import com.securesms.crypto.SecureSmsException
import java.security.KeyPair
import java.util.Base64

/** Fallo apto para la UI, sin causas del proveedor ni datos del mensaje. */
internal class FalloDeCifrado(
    mensaje: String,
    val incompleto: Boolean = false,
) : Exception(mensaje)

/** Usa la clave pública verificada del destinatario y recupera la clave propia por perfil. */
internal class CryptoGatewayReal(
    private val parPropio: (Perfil) -> KeyPair?,
) : CryptoGateway {
    override val esDemo = false

    override fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> =
        Result.failure(FalloDeCifrado("Choose a recipient with a verified public key."))

    override fun cifrar(texto: String, clave: ClavePublicaImportada): Result<ByteArray> = ejecutar {
        if (!clave.habilitaEnvio) throw FalloDeCifrado("Verify the recipient’s public key.")
        val perfil = Profile.valueOf(clave.perfil.name)
        val der = try {
            Base64.getDecoder().decode(clave.materialBase64)
        } catch (_: IllegalArgumentException) {
            throw FalloDeCifrado("The recipient’s public key is invalid.")
        }
        SecureSmsCrypto.encryptText(texto, PublicKeys.importDer(der, perfil), perfil)
    }

    override fun descifrar(envelope: ByteArray): Result<MensajeDescifrado> = ejecutar {
        val perfil = Perfil.valueOf(SecureSmsCrypto.inspect(envelope).profile.name)
        val par = parPropio(perfil) ?: throw FalloDeCifrado("Your device is missing the key for ${perfil.name}")
        MensajeDescifrado(SecureSmsCrypto.decryptText(envelope, par), perfil)
    }

    private inline fun <T> ejecutar(operacion: () -> T): Result<T> = try {
        Result.success(operacion())
    } catch (error: SecureSmsException) {
        Result.failure(error.code.aFalloDeCifrado())
    } catch (error: FalloDeCifrado) {
        Result.failure(error)
    } catch (_: java.security.GeneralSecurityException) {
        Result.failure(FalloDeCifrado("Could not access the device key."))
    } catch (_: java.security.ProviderException) {
        Result.failure(FalloDeCifrado("The cryptographic provider could not complete the operation."))
    }
}

private fun ErrorCode.aFalloDeCifrado(): FalloDeCifrado = when (this) {
    ErrorCode.TRUNCATED_ENVELOPE -> FalloDeCifrado("The message is incomplete.", incompleto = true)
    ErrorCode.WRONG_RECIPIENT -> FalloDeCifrado("The message was encrypted for a different key.")
    ErrorCode.AUTHENTICATION_FAILED -> FalloDeCifrado("The message was altered or the key is incorrect.")
    ErrorCode.KEY_PROFILE_MISMATCH -> FalloDeCifrado("The key does not match the message profile.")
    ErrorCode.MALFORMED_ENVELOPE, ErrorCode.UNSUPPORTED_VERSION, ErrorCode.UNKNOWN_PROFILE,
    ErrorCode.INVALID_EPHEMERAL_KEY -> FalloDeCifrado("The message format is invalid or unsupported.")
    ErrorCode.INVALID_KEY -> FalloDeCifrado("The public key is invalid.")
    ErrorCode.PLAINTEXT_TOO_LARGE -> FalloDeCifrado("The message exceeds the size limit.")
    ErrorCode.INVALID_TEXT -> FalloDeCifrado("The message text is invalid.")
    ErrorCode.PLATFORM_ERROR -> FalloDeCifrado("Could not complete the cryptographic operation.")
}
