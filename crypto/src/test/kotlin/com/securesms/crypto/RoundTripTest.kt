package com.securesms.crypto

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoundTripTest {
    private val messages = linkedMapOf(
        "empty" to "",
        "short" to "Hola",
        "sms-160" to "x".repeat(160),
        "long-10k" to buildString { repeat(1000) { append("Mensaje $it. ") } },
        "unicode" to "Ñandú, café, niño, 你好, مرحبا, Привет, emoji 😀🔐🇲🇽 and combining é",
    )

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `text round trip for empty, short, long and unicode messages`(profile: Profile) {
        val kp = TestKeys.of(profile)
        for ((name, text) in messages) {
            val env = SecureSmsCrypto.encryptText(text, kp.public, profile)
            assertEquals(Envelope.sizeFor(profile, text.toByteArray(Charsets.UTF_8).size), env.size, "size of $name")
            assertEquals(text, SecureSmsCrypto.decryptText(env, kp), "round trip of $name")
        }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `binary round trip including all byte values`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val data = ByteArray(1024).also { SecureRandom().nextBytes(it) } + ByteArray(256) { it.toByte() }
        assertContentEquals(data, SecureSmsCrypto.decrypt(SecureSmsCrypto.encrypt(data, kp.public, profile), kp))
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `maximum size message round trips`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val data = ByteArray(SecureSmsCrypto.MAX_PLAINTEXT_BYTES) { (it * 31).toByte() }
        assertContentEquals(data, SecureSmsCrypto.decrypt(SecureSmsCrypto.encrypt(data, kp.public, profile), kp))
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `encryption is randomized`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val a = SecureSmsCrypto.encryptText("same text", kp.public, profile)
        val b = SecureSmsCrypto.encryptText("same text", kp.public, profile)
        assertFalse(a.contentEquals(b), "two encryptions of the same text must differ")
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `decrypt works with separate public and private key objects`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val pub = PublicKeys.importCompact(PublicKeys.exportCompact(kp.public)).key
        val priv = PrivateKeys.importPkcs8(PrivateKeys.exportPkcs8(kp.private), profile)
        val env = SecureSmsCrypto.encryptText("imported keys", pub, profile)
        assertEquals("imported keys", SecureSmsCrypto.decryptText(env, kp.public, priv))
    }

    @Test
    fun `envelope overhead per profile`() {
        assertEquals(8 + 256 + 16, Envelope.overhead(Profile.RSA_2048))
        assertEquals(8 + 384 + 16, Envelope.overhead(Profile.RSA_3072))
        assertEquals(8 + 33 + 16, Envelope.overhead(Profile.ECC_P256))
        assertEquals(8 + 49 + 16, Envelope.overhead(Profile.ECC_P384))
    }
}
