package com.securesms.crypto

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * SHA-256 fingerprint of a public key, computed over a canonical, provider-independent encoding:
 *
 *   SHA-256( "SSPK1" || profileId || canonicalKey )
 *   canonicalKey = RSA: modulus (k bytes, big-endian) || public exponent (big-endian)
 *                  EC : uncompressed point 0x04 || X || Y
 *
 * so the same key gives the same fingerprint on a PC JVM and on Android, regardless of how each
 * provider happens to DER-encode it. The first 4 bytes ([keyId]) are written in every envelope.
 */
class KeyFingerprint internal constructor(private val digest: ByteArray) {
    fun bytes(): ByteArray = digest.copyOf()

    /** Full fingerprint, upper-case hex in groups of 4: "3F2A 91C0 ..." (for manual verification). */
    val hex: String get() = digest.toHex().chunked(4).joinToString(" ")

    /** First 8 bytes in groups of 4, convenient to read aloud or show next to a contact. */
    val short: String get() = digest.copyOf(8).toHex().chunked(4).joinToString(" ")

    /** First 4 bytes; the "recipient key id" stored in byte 4..7 of the envelope header. */
    val keyId: ByteArray get() = digest.copyOf(KEY_ID_LENGTH)

    override fun equals(other: Any?) = other is KeyFingerprint && MessageDigest.isEqual(digest, other.digest)
    override fun hashCode() = digest.contentHashCode()
    override fun toString() = hex

    companion object {
        const val KEY_ID_LENGTH = 4
    }
}

/** Result of importing a public key that carries its own profile (compact format). */
data class ImportedPublicKey(val profile: Profile, val key: PublicKey)

/** Export / import / validation of recipients' public keys. */
object PublicKeys {
    private const val COMPACT_PREFIX = "SSPK1"

    /**
     * Checks that [key] can be used with [profile]: correct family, exact modulus size (RSA) or
     * exact named curve and on-curve point (EC). Throws [ErrorCode.KEY_PROFILE_MISMATCH] otherwise.
     */
    @JvmStatic
    fun requireProfile(key: PublicKey, profile: Profile) {
        when (profile.algorithm) {
            Algorithm.RSA -> {
                val rsa = key as? RSAPublicKey
                    ?: fail(ErrorCode.KEY_PROFILE_MISMATCH, "${profile.displayName} needs an RSA public key, got ${key.algorithm}")
                val bits = rsa.modulus.bitLength()
                if (bits != profile.keySizeBits) {
                    fail(ErrorCode.KEY_PROFILE_MISMATCH, "${profile.displayName} needs a ${profile.keySizeBits}-bit modulus, got $bits bits")
                }
                val e = rsa.publicExponent
                if (!e.testBit(0) || e.bitLength() < 2 || e.bitLength() > 256) {
                    fail(ErrorCode.KEY_PROFILE_MISMATCH, "Unsupported RSA public exponent")
                }
            }
            Algorithm.EC -> {
                val ec = key as? ECPublicKey
                    ?: fail(ErrorCode.KEY_PROFILE_MISMATCH, "${profile.displayName} needs an EC public key, got ${key.algorithm}")
                val curve = profile.curve!!
                if (!curve.matches(ec.params)) {
                    fail(ErrorCode.KEY_PROFILE_MISMATCH, "${profile.displayName} needs a key on ${curve.jcaName}")
                }
                if (!curve.isOnCurve(ec.w)) fail(ErrorCode.KEY_PROFILE_MISMATCH, "Public point is not on ${curve.jcaName}")
            }
        }
    }

    /** Profile that matches [key], or [ErrorCode.KEY_PROFILE_MISMATCH] if it fits none of the four. */
    @JvmStatic
    fun detectProfile(key: PublicKey): Profile =
        Profile.values().firstOrNull { p -> runCatching { requireProfile(key, p) }.isSuccess }
            ?: fail(ErrorCode.KEY_PROFILE_MISMATCH, "Key is not RSA-2048, RSA-3072, P-256 or P-384")

    @JvmStatic
    fun fingerprint(key: PublicKey): KeyFingerprint {
        val profile = detectProfile(key)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(COMPACT_PREFIX.toByteArray(Charsets.US_ASCII))
        md.update(profile.id.toByte())
        when (key) {
            is RSAPublicKey -> {
                md.update(EcCurve.toFixed(key.modulus, profile.keySizeBits / 8))
                md.update(key.publicExponent.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
            }
            is ECPublicKey -> md.update(profile.curve!!.uncompressed(key.w))
        }
        return KeyFingerprint(md.digest())
    }

    /** Standard X.509 SubjectPublicKeyInfo (DER). Interoperable with OpenSSL, Java, Android, etc. */
    @JvmStatic
    fun exportDer(key: PublicKey): ByteArray {
        detectProfile(key)
        return key.encoded ?: fail(ErrorCode.INVALID_KEY, "Public key has no X.509 encoding")
    }

    /** Imports a SubjectPublicKeyInfo and checks it matches [expected]. */
    @JvmStatic
    fun importDer(der: ByteArray, expected: Profile): PublicKey {
        val algorithm = if (expected.algorithm == Algorithm.RSA) "RSA" else "EC"
        val key = try {
            KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(der))
        } catch (e: GeneralSecurityException) {
            fail(ErrorCode.INVALID_KEY, "Not a valid ${expected.displayName} public key", e)
        } catch (e: RuntimeException) {
            fail(ErrorCode.INVALID_KEY, "Not a valid ${expected.displayName} public key", e)
        }
        requireProfile(key, expected)
        return key
    }

    /** Imports a SubjectPublicKeyInfo of any supported profile and reports which one it is. */
    @JvmStatic
    fun importDer(der: ByteArray): ImportedPublicKey {
        for (p in Profile.values()) {
            val key = runCatching { importDer(der, p) }.getOrNull()
            if (key != null) return ImportedPublicKey(p, key)
        }
        fail(ErrorCode.INVALID_KEY, "Not an RSA-2048, RSA-3072, P-256 or P-384 public key")
    }

    @JvmStatic
    fun exportPem(key: PublicKey): String {
        val b64 = Base64.getEncoder().encodeToString(exportDer(key))
        return "-----BEGIN PUBLIC KEY-----\n" + b64.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----\n"
    }

    @JvmStatic
    fun importPem(pem: String, expected: Profile): PublicKey = importDer(pemBody(pem), expected)

    @JvmStatic
    fun importPem(pem: String): ImportedPublicKey = importDer(pemBody(pem))

    /**
     * One-line text form for manual exchange (QR, copy/paste, SMS):
     * `SSPK1:<PROFILE>:<base64url(SPKI DER), no padding>`, e.g. `SSPK1:ECC_P256:MFkwEwYH...`.
     */
    @JvmStatic
    fun exportCompact(key: PublicKey): String {
        val profile = detectProfile(key)
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(exportDer(key))
        return "$COMPACT_PREFIX:${profile.name}:$b64"
    }

    @JvmStatic
    fun importCompact(text: String): ImportedPublicKey {
        val parts = text.trim().split(":")
        if (parts.size != 3 || parts[0] != COMPACT_PREFIX) fail(ErrorCode.INVALID_KEY, "Not a SSPK1 public key string")
        val profile = Profile.fromName(parts[1]) ?: fail(ErrorCode.INVALID_KEY, "Unknown profile '${parts[1]}'")
        val der = try {
            Base64.getUrlDecoder().decode(parts[2])
        } catch (e: IllegalArgumentException) {
            fail(ErrorCode.INVALID_KEY, "Public key is not valid base64url", e)
        }
        return ImportedPublicKey(profile, importDer(der, profile))
    }

    private fun pemBody(pem: String): ByteArray {
        val body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
            .filterNot { it.isWhitespace() }
        return try {
            Base64.getDecoder().decode(body)
        } catch (e: IllegalArgumentException) {
            fail(ErrorCode.INVALID_KEY, "PEM body is not valid base64", e)
        }
    }
}

/**
 * PKCS#8 export/import of private keys, for tests and for apps that keep the private key wrapped
 * by an Android Keystore AES key. Private keys must never be sent by SMS.
 */
object PrivateKeys {
    @JvmStatic
    fun exportPkcs8(key: PrivateKey): ByteArray =
        key.encoded ?: fail(ErrorCode.INVALID_KEY, "Private key is not exportable (e.g. it lives inside Android Keystore)")

    @JvmStatic
    fun importPkcs8(pkcs8: ByteArray, profile: Profile): PrivateKey {
        val algorithm = if (profile.algorithm == Algorithm.RSA) "RSA" else "EC"
        val key = try {
            KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        } catch (e: GeneralSecurityException) {
            fail(ErrorCode.INVALID_KEY, "Not a valid ${profile.displayName} private key", e)
        } catch (e: RuntimeException) {
            fail(ErrorCode.INVALID_KEY, "Not a valid ${profile.displayName} private key", e)
        }
        val ok = when (key) {
            is RSAPrivateKey -> profile.algorithm == Algorithm.RSA && key.modulus.bitLength() == profile.keySizeBits
            is ECPrivateKey -> profile.algorithm == Algorithm.EC && profile.curve!!.matches(key.params)
            else -> false
        }
        if (!ok) fail(ErrorCode.KEY_PROFILE_MISMATCH, "Private key does not match ${profile.displayName}")
        return key
    }
}

internal fun ByteArray.toHex(): String {
    val chars = "0123456789ABCDEF"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(chars[v ushr 4]).append(chars[v and 0x0F])
    }
    return sb.toString()
}
