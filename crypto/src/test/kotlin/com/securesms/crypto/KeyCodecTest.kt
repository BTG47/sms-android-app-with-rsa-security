package com.securesms.crypto

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KeyCodecTest {
    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `DER, PEM and compact exports import back to the same key`(profile: Profile) {
        val pub = TestKeys.of(profile).public
        val der = PublicKeys.exportDer(pub)
        assertContentEquals(der, PublicKeys.importDer(der, profile).encoded)
        assertEquals(profile, PublicKeys.importDer(der).profile)
        assertContentEquals(der, PublicKeys.importPem(PublicKeys.exportPem(pub), profile).encoded)
        val compact = PublicKeys.exportCompact(pub)
        assertTrue(compact.startsWith("SSPK1:${profile.name}:"))
        val imported = PublicKeys.importCompact(compact)
        assertEquals(profile, imported.profile)
        assertContentEquals(der, imported.key.encoded)
        assertEquals(PublicKeys.fingerprint(pub), PublicKeys.fingerprint(imported.key))
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `private key PKCS8 round trip`(profile: Profile) {
        val priv = TestKeys.of(profile).private
        assertContentEquals(priv.encoded, PrivateKeys.importPkcs8(PrivateKeys.exportPkcs8(priv), profile).encoded)
    }

    @ParameterizedTest
    @EnumSource(Profile::class)
    fun `fingerprints are stable and differ between keys`(profile: Profile) {
        val a = PublicKeys.fingerprint(TestKeys.of(profile).public)
        assertEquals(a, PublicKeys.fingerprint(TestKeys.of(profile).public))
        assertNotEquals(a, PublicKeys.fingerprint(TestKeys.other(profile).public))
        assertEquals(32, a.bytes().size)
        assertEquals(4, a.keyId.size)
        assertTrue(Regex("([0-9A-F]{4} ){15}[0-9A-F]{4}").matches(a.hex), a.hex)
        assertEquals(profile, PublicKeys.detectProfile(TestKeys.of(profile).public))
    }

    @Test
    fun `import rejects keys of the wrong profile or garbage`() {
        val rsaDer = PublicKeys.exportDer(TestKeys.of(Profile.RSA_2048).public)
        val ecDer = PublicKeys.exportDer(TestKeys.of(Profile.ECC_P256).public)
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { PublicKeys.importDer(rsaDer, Profile.RSA_3072) }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importDer(rsaDer, Profile.ECC_P256) }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importDer(ecDer, Profile.RSA_2048) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { PublicKeys.importDer(ecDer, Profile.ECC_P384) }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importDer(byteArrayOf(1, 2, 3)) }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importCompact("hola") }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importCompact("SSPK1:ECC_P999:AAAA") }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importCompact("SSPK1:ECC_P256:@@@") }
        // Compact string that lies about its profile
        val lie = PublicKeys.exportCompact(TestKeys.of(Profile.ECC_P256).public).replace("ECC_P256", "ECC_P384")
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { PublicKeys.importCompact(lie) }
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) {
            PrivateKeys.importPkcs8(TestKeys.of(Profile.RSA_2048).private.encoded, Profile.RSA_3072)
        }
    }

    @Test
    fun `unsupported curves are rejected`() {
        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp521r1")) }
        val p521 = kpg.generateKeyPair().public
        assertCode(ErrorCode.KEY_PROFILE_MISMATCH) { PublicKeys.detectProfile(p521) }
        assertCode(ErrorCode.INVALID_KEY) { PublicKeys.importDer(p521.encoded) }
    }

    @Test
    fun `hard-coded curve parameters equal the JCA parameters`() {
        for (p in listOf(Profile.ECC_P256, Profile.ECC_P384)) {
            val jca = (SecureSmsCrypto.generateKeyPair(p).public as ECPublicKey).params
            assertTrue(p.curve!!.matches(jca), "constants of ${p.curve!!.jcaName} differ from JCA")
        }
    }

    @Test
    fun `point compression round trips`() {
        for (p in listOf(Profile.ECC_P256, Profile.ECC_P384)) {
            repeat(50) {
                val w = (SecureSmsCrypto.generateKeyPair(p).public as ECPublicKey).w
                val c = p.curve!!.compress(w)
                assertEquals(p.kemBlockLength, c.size)
                assertEquals(w, p.curve!!.decompress(c))
            }
        }
    }
}
