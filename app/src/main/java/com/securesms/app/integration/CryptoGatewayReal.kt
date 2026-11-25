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
        Result.failure(FalloDeCifrado("Selecciona un destinatario con clave verificada"))

    override fun cifrar(texto: String, clave: ClavePublicaImportada): Result<ByteArray> = ejecutar {
        if (!clave.habilitaEnvio) throw FalloDeCifrado("Verifica la clave del destinatario")
        val perfil = Profile.valueOf(clave.perfil.name)
        val der = try {
            Base64.getDecoder().decode(clave.materialBase64)
        } catch (_: IllegalArgumentException) {
            throw FalloDeCifrado("La clave pública del destinatario es inválida")
        }
        SecureSmsCrypto.encryptText(texto, PublicKeys.importDer(der, perfil), perfil)
    }

    override fun descifrar(envelope: ByteArray): Result<MensajeDescifrado> = ejecutar {
        val perfil = Perfil.valueOf(SecureSmsCrypto.inspect(envelope).profile.name)
        val par = parPropio(perfil) ?: throw FalloDeCifrado("Falta tu clave para ${perfil.name}")
        MensajeDescifrado(SecureSmsCrypto.decryptText(envelope, par), perfil)
    }

    private inline fun <T> ejecutar(operacion: () -> T): Result<T> = try {
        Result.success(operacion())
    } catch (error: SecureSmsException) {
        Result.failure(error.code.aFalloDeCifrado())
    } catch (error: FalloDeCifrado) {
        Result.failure(error)
    } catch (_: java.security.GeneralSecurityException) {
        Result.failure(FalloDeCifrado("No se pudo acceder a la clave del dispositivo"))
    } catch (_: java.security.ProviderException) {
        Result.failure(FalloDeCifrado("El proveedor criptográfico no pudo completar la operación"))
    }
}

private fun ErrorCode.aFalloDeCifrado(): FalloDeCifrado = when (this) {
    ErrorCode.TRUNCATED_ENVELOPE -> FalloDeCifrado("El mensaje está incompleto", incompleto = true)
    ErrorCode.WRONG_RECIPIENT -> FalloDeCifrado("El mensaje fue cifrado para otra clave")
    ErrorCode.AUTHENTICATION_FAILED -> FalloDeCifrado("Mensaje alterado o clave incorrecta")
    ErrorCode.KEY_PROFILE_MISMATCH -> FalloDeCifrado("La clave no coincide con el perfil del mensaje")
    ErrorCode.MALFORMED_ENVELOPE, ErrorCode.UNSUPPORTED_VERSION, ErrorCode.UNKNOWN_PROFILE,
    ErrorCode.INVALID_EPHEMERAL_KEY -> FalloDeCifrado("El formato del mensaje es inválido o incompatible")
    ErrorCode.INVALID_KEY -> FalloDeCifrado("La clave pública es inválida")
    ErrorCode.PLAINTEXT_TOO_LARGE -> FalloDeCifrado("El mensaje supera el tamaño admitido")
    ErrorCode.INVALID_TEXT -> FalloDeCifrado("El texto del mensaje no es válido")
    ErrorCode.PLATFORM_ERROR -> FalloDeCifrado("No se pudo completar la operación criptográfica")
}
