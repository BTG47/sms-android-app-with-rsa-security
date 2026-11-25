// Example of how the app can wrap the library (Bruno's side). Not part of :crypto.
// Shows the intended calls; storage of the private key is Bruno's decision (see docs/INTEGRACION_BRUNO.md).
package com.securesms.app.crypto

import com.securesms.crypto.ErrorCode
import com.securesms.crypto.Profile
import com.securesms.crypto.PublicKeys
import com.securesms.crypto.SecureSmsCrypto
import com.securesms.crypto.SecureSmsException
import java.security.KeyPair

sealed class DecryptResult {
    data class Ok(val text: String, val profile: Profile) : DecryptResult()
    data class Failed(val code: ErrorCode, val userMessage: String) : DecryptResult()
}

class SecureSmsCryptoAdapter(private val myKeys: Map<Profile, KeyPair>) {

    /** Bytes to send. Bruno converts them to Base64 / splits them into SMS parts. */
    fun encrypt(text: String, contactKeyCompact: String): ByteArray {
        val contact = PublicKeys.importCompact(contactKeyCompact) // validates key + profile
        return SecureSmsCrypto.encryptText(text, contact.key, contact.profile)
    }

    /** Call only after all SMS fragments were reassembled. */
    fun decrypt(envelope: ByteArray): DecryptResult = try {
        val profile = SecureSmsCrypto.inspect(envelope).profile
        val kp = myKeys[profile] ?: return DecryptResult.Failed(ErrorCode.KEY_PROFILE_MISMATCH, "No tengo clave ${profile.displayName}")
        DecryptResult.Ok(SecureSmsCrypto.decryptText(envelope, kp), profile)
    } catch (e: SecureSmsException) {
        DecryptResult.Failed(e.code, when (e.code) {
            ErrorCode.TRUNCATED_ENVELOPE -> "Mensaje incompleto"
            ErrorCode.WRONG_RECIPIENT -> "El mensaje fue cifrado para otra clave"
            ErrorCode.AUTHENTICATION_FAILED -> "Mensaje alterado o clave incorrecta"
            ErrorCode.UNSUPPORTED_VERSION, ErrorCode.UNKNOWN_PROFILE -> "Formato no soportado"
            else -> "Mensaje inválido (${e.code})"
        })
    }

    fun myPublicKeyToShare(profile: Profile): String = PublicKeys.exportCompact(myKeys.getValue(profile).public)
    fun fingerprintOf(contactKeyCompact: String): String = PublicKeys.fingerprint(PublicKeys.importCompact(contactKeyCompact).key).hex
}
