package com.securesms.crypto

import com.securesms.crypto.bench.BenchmarkConfig
import com.securesms.crypto.bench.BenchmarkRunner
import com.securesms.crypto.bench.SmsEstimator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatAndHkdfTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `HKDF-SHA256 matches RFC 5869 test case 1`() {
        val okm = Hkdf.derive(
            "HmacSHA256",
            ikm = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertContentEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
    }

    @Test
    fun `HKDF-SHA256 matches RFC 5869 test case 3 (empty salt and info)`() {
        val okm = Hkdf.derive("HmacSHA256", ByteArray(22) { 0x0b }, ByteArray(0), ByteArray(0), 42)
        assertContentEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"), okm)
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `header layout and inspect`(profile: Profile) {
        val kp = TestKeys.of(profile)
        val env = SecureSmsCrypto.encryptText("hola", kp.public, profile)
        assertEquals(0x53, env[0].toInt())
        assertEquals(0x53, env[1].toInt())
        assertEquals(1, env[2].toInt())
        assertEquals(profile.id, env[3].toInt() and 0xFF)
        assertContentEquals(PublicKeys.fingerprint(kp.public).keyId, env.copyOfRange(4, 8))
        val info = SecureSmsCrypto.inspect(env)
        assertEquals(profile, info.profile)
        assertEquals(4, info.plaintextLength)
        assertEquals(env.size, info.totalLength)
        assertContentEquals(env, info.toByteArray())
        if (profile.algorithm == Algorithm.EC) assertTrue(env[8] == 0x02.toByte() || env[8] == 0x03.toByte())
    }

    @Test
    fun `SMS segment estimates`() {
        assertEquals(1, SmsEstimator.segmentsGsm7(160))
        assertEquals(2, SmsEstimator.segmentsGsm7(161))
        assertEquals(2, SmsEstimator.segmentsGsm7(306))
        assertEquals(3, SmsEstimator.segmentsGsm7(307))
        assertEquals(1, SmsEstimator.segmentsUcs2(70))
        assertEquals(2, SmsEstimator.segmentsUcs2(71))
        assertEquals(1, SmsEstimator.segmentsBinary(140))
        assertEquals(2, SmsEstimator.segmentsBinary(141))
        assertEquals(1, SmsEstimator.segmentsForPlainText("Hola, ¿qué tal?"))
        assertEquals(1, SmsEstimator.segmentsForPlainText("😀"))
        assertEquals(2, SmsEstimator.segmentsForPlainText("中".repeat(71)))
        assertEquals(96, SmsEstimator.base64Length(70))
    }

    @Test
    fun `benchmark runner smoke test`() {
        val report = BenchmarkRunner.run(BenchmarkConfig.smoke())
        assertEquals(4 * (3 + 2 * 2), report.timings.size)
        assertEquals(4 * 2, report.sizes.size)
        assertTrue(report.timingsCsv().lines().size > 10)
        assertTrue(report.timings.all { it.meanUs > 0 && it.medianUs > 0 })
        val p256 = report.sizes.first { it.profile == Profile.ECC_P256 && it.messageChars == 160 }
        assertEquals(160 + 57, p256.envelopeBytes)
    }
}
