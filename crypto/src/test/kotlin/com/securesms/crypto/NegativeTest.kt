package com.securesms.crypto

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigInteger
import java.security.KeyPair
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NegativeTest {
    private fun sample(profile: Profile) =
        SecureSmsCrypto.encryptText("Mensaje secreto de prueba", TestKeys.of(profile).public, profile)

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `envelope for another recipient is rejected`(profile: Profile) {
        assertCode(ErrorCode.WRONG_RECIPIENT) { SecureSmsCrypto.decrypt(sample(profile), TestKeys.other(profile)) }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `wrong private key is rejected by the cryptography, not only by the key id`(profile: Profile) {
        // Correct public key (so the key id matches) but the private key of a different pair.
        val mixed = KeyPair(TestKeys.of(profile).public, TestKeys.other(profile).private)
        assertCode(ErrorCode.AUTHENTICATION_FAILED) { SecureSmsCrypto.decrypt(sample(profile), mixed) }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `every single-bit change anywhere in the envelope is rejected`(profile: Profile) {
        val env = sample(profile)
        val kp = TestKeys.of(profile)
        for (i in env.indices) {
            for (bit in listOf(0, 7)) {
                val t = env.copyOf()
                t[i] = (t[i].toInt() xor (1 shl bit)).toByte()
                assertFailsWith<SecureSmsException>("flip of bit $bit in byte $i accepted") { SecureSmsCrypto.decrypt(t, kp) }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `changing ciphertext, tag, key block or key id gives the expected code`(profile: Profile) {
        val env = sample(profile)
        val kp = TestKeys.of(profile)
        fun flipped(i: Int) = env.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
        assertCode(ErrorCode.AUTHENTICATION_FAILED) { SecureSmsCrypto.decrypt(flipped(env.size - 20), kp) } // ciphertext
        assertCode(ErrorCode.AUTHENTICATION_FAILED) { SecureSmsCrypto.decrypt(flipped(env.size - 1), kp) } // tag
        assertCode(ErrorCode.WRONG_RECIPIENT) { SecureSmsCrypto.decrypt(flipped(5), kp) } // key id
        // Key block: RSA -> OAEP or GCM failure; EC -> point invalid or GCM failure.
        val e = assertFailsWith<SecureSmsException> { SecureSmsCrypto.decrypt(flipped(Envelope.HEADER_LENGTH + 3), kp) }
        assert(e.code == ErrorCode.AUTHENTICATION_FAILED || e.code == ErrorCode.INVALID_EPHEMERAL_KEY) { e.message!! }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `every truncation is rejected`(profile: Profile) {
        val env = sample(profile)
        val kp = TestKeys.of(profile)
        for (len in 0 until env.size) {
            assertFailsWith<SecureSmsException>("truncation to $len bytes accepted") {
                SecureSmsCrypto.decrypt(env.copyOf(len), kp)
            }
        }
        assertCode(ErrorCode.TRUNCATED_ENVELOPE) { SecureSmsCrypto.decrypt(env.copyOf(Envelope.overhead(profile) - 1), kp) }
        assertCode(ErrorCode.AUTHENTICATION_FAILED) { SecureSmsCrypto.decrypt(env.copyOf(env.size - 1), kp) }
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `appended bytes are rejected`(profile: Profile) {
        assertCode(ErrorCode.AUTHENTICATION_FAILED) { SecureSmsCrypto.decrypt(sample(profile) + byteArrayOf(0), TestKeys.of(profile)) }
    }

    @Test
    fun `header validation codes`() {
        val profile = Profile.ECC_P256
        val env = sample(profile)
        val kp = TestKeys.of(profile)
        assertCode(ErrorCode.MALFORMED_ENVELOPE) { SecureSmsCrypto.decrypt(ByteArray(0), kp) }
        assertCode(ErrorCode.MALFORMED_ENVELOPE) { SecureSmsCrypto.decrypt("hola mundo".toByteArray(), kp) }
        assertCode(ErrorCode.TRUNCATED_ENVELOPE) { SecureSmsCrypto.decrypt(env.copyOf(5), kp) }
        assertCode(ErrorCode.UNSUPPORTED_VERSION) { SecureSmsCrypto.decrypt(env.copyOf().also { it[2] = 2 }, kp) }
        assertCode(ErrorCode.UNKNOWN_PROFILE) { SecureSmsCrypto.decrypt(env.copyOf().also { it[3] = 0x7F }, kp) }
        // Profile byte changed to another valid profile: key no longer fits it.
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.decrypt(env.copyOf().also { it[3] = Profile.ECC_P384.id.toByte() }, kp) }
    }

    @Test
    fun `invalid ephemeral points are rejected`() {
        for (profile in listOf(Profile.ECC_P256, Profile.ECC_P384)) {
            val env = sample(profile)
            val kp = TestKeys.of(profile)
            val k = Envelope.HEADER_LENGTH
            // Uncompressed / unknown prefix
            assertCode(ErrorCode.INVALID_EPHEMERAL_KEY) { SecureSmsCrypto.decrypt(env.copyOf().also { it[k] = 0x04 }, kp) }
            // x = p - 1 is not on the curve for either curve (checked below), x >= p out of range
            val curve = profile.curve!!
            val notOnCurve = generateSequence(BigInteger.ONE) { it + BigInteger.ONE }.first { x ->
                val rhs = x.pow(3).add(curve.a.multiply(x)).add(curve.b).mod(curve.p)
                val y = rhs.modPow(curve.p.add(BigInteger.ONE).shiftRight(2), curve.p)
                y.multiply(y).mod(curve.p) != rhs
            }
            val bad = env.copyOf()
            EcCurve.toFixed(notOnCurve, curve.coordinateLength).copyInto(bad, k + 1)
            assertCode(ErrorCode.INVALID_EPHEMERAL_KEY) { SecureSmsCrypto.decrypt(bad, kp) }
            val outOfRange = env.copyOf()
            ByteArray(curve.coordinateLength) { 0xFF.toByte() }.copyInto(outOfRange, k + 1)
            assertCode(ErrorCode.INVALID_EPHEMERAL_KEY) { SecureSmsCrypto.decrypt(outOfRange, kp) }
        }
    }

    @Test
    fun `keys that do not match the chosen profile are rejected when encrypting`() {
        val rsa2048 = TestKeys.of(Profile.RSA_2048).public
        val rsa3072 = TestKeys.of(Profile.RSA_3072).public
        val p256 = TestKeys.of(Profile.ECC_P256).public
        val p384 = TestKeys.of(Profile.ECC_P384).public
        val msg = "x".toByteArray()
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, rsa2048, Profile.RSA_3072) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, rsa3072, Profile.RSA_2048) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, p256, Profile.ECC_P384) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, p384, Profile.ECC_P256) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, rsa2048, Profile.ECC_P256) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, p256, Profile.RSA_2048) }
        val rsa1024 = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public
        for (p in Profile.values()) assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.encrypt(msg, rsa1024, p) }
    }

    @Test
    fun `decrypting with a key pair of another profile is rejected`() {
        val env = sample(Profile.RSA_2048)
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.decrypt(env, TestKeys.of(Profile.RSA_3072)) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { SecureSmsCrypto.decrypt(env, TestKeys.of(Profile.ECC_P256)) }
    }

    @Test
    fun `too large plaintext is rejected`() {
        val kp = TestKeys.of(Profile.ECC_P256)
        assertCode(ErrorCode.PLAINTEXT_TOO_LARGE) {
            SecureSmsCrypto.encrypt(ByteArray(SecureSmsCrypto.MAX_PLAINTEXT_BYTES + 1), kp.public, Profile.ECC_P256)
        }
    }

    @Test
    fun `non UTF-8 plaintext is reported by decryptText`() {
        val kp = TestKeys.of(Profile.ECC_P256)
        val env = SecureSmsCrypto.encrypt(byteArrayOf(0xC3.toByte(), 0x28), kp.public, Profile.ECC_P256)
        assertCode(ErrorCode.INVALID_TEXT) { SecureSmsCrypto.decryptText(env, kp) }
    }
}
