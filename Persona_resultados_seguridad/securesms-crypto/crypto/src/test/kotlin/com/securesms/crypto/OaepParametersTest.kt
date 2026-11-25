package com.securesms.crypto

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Locks the RSA-OAEP parameters agreed with the Android side (Samsung + POCO):
 * OAEP digest SHA-256, MGF1 digest SHA-1, empty label. An independent Cipher configured exactly like
 * Android Keystore must open the key block; one configured with MGF1-SHA-256 must not.
 */
class OaepParametersTest {
    private fun spec(mgf: MGF1ParameterSpec) = OAEPParameterSpec("SHA-256", "MGF1", mgf, PSource.PSpecified.DEFAULT)

    @ParameterizedTest
    @EnumSource(value = Profile::class, names = ["RSA_2048", "RSA_3072"])
    fun `key block is OAEP SHA-256 with MGF1 SHA-1`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val env = SecureSmsCrypto.encryptText("interop", kp.public, profile)
        val block = env.copyOfRange(Envelope.HEADER_LENGTH, Envelope.HEADER_LENGTH + profile.kemBlockLength)

        val ok = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE, kp.private, spec(MGF1ParameterSpec.SHA1)) }
        assertEquals(32, ok.doFinal(block).size)

        val wrongMgf = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE, kp.private, spec(MGF1ParameterSpec.SHA256)) }
        assertFails { wrongMgf.doFinal(block) }
    }

    @ParameterizedTest
    @EnumSource(value = Profile::class, names = ["RSA_2048", "RSA_3072"])
    fun `envelope built by an external OAEP SHA-256 MGF1 SHA-1 encryptor is accepted`(profile: Profile) {
        // Build a whole envelope without the library's encrypt(), as another implementation (e.g. Android) would.
        val kp = TestKeys.of(profile)
        val header = byteArrayOf(0x53, 0x53, 1, profile.id.toByte()) + PublicKeys.fingerprint(kp.public).keyId
        val seed = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val block = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.ENCRYPT_MODE, kp.public, spec(MGF1ParameterSpec.SHA1)) }
            .doFinal(seed)
        val material = Hkdf.derive("HmacSHA256", seed, ByteArray(0), "SecureSMS-v1".toByteArray() + header, 44)
        val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(material, 0, 32, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, material, 32, 12))
            updateAAD(header + block)
        }
        val env = header + block + gcm.doFinal("interop inverso".toByteArray())
        assertEquals("interop inverso", SecureSmsCrypto.decryptText(env, kp))
    }
}
