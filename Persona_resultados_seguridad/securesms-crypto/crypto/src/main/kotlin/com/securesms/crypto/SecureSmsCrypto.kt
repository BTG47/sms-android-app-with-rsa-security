package com.securesms.crypto

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Public entry point of the library: key generation, encryption and decryption for the four profiles.
 *
 * Hybrid schemes (both end in AES-256-GCM over the message):
 *  - RSA  : a random 32-byte seed is encrypted with RSA-OAEP (SHA-256, MGF1-SHA-1); the AES key and
 *           GCM nonce are derived from the seed with HKDF-SHA-256.
 *  - ECC  : ECIES-style. A fresh ephemeral EC key pair is generated per message, ECDH with the
 *           recipient key gives a shared secret, HKDF (SHA-256 for P-256, SHA-384 for P-384) derives
 *           the AES key and nonce. Only the compressed ephemeral public point is sent.
 *
 * Each message uses a brand-new AES key, so deriving the 96-bit nonce from the same HKDF output is
 * safe and saves 12 bytes per SMS compared to sending a random nonce.
 *
 * Thread-safe: every call creates its own Cipher / KeyAgreement instances.
 */
object SecureSmsCrypto {
    /** Upper bound for a single message (far above any realistic SMS conversation). */
    const val MAX_PLAINTEXT_BYTES = 64 * 1024

    private const val SEED_LENGTH = 32
    private const val AES_KEY_LENGTH = 32
    private const val NONCE_LENGTH = 12
    private val LABEL = "SecureSMS-v1".toByteArray(Charsets.US_ASCII)

    /**
     * RSA-OAEP parameters are fixed explicitly so the PC JVM, Android's Conscrypt and AndroidKeyStore
     * all use exactly the same padding (their defaults for "OAEPWithSHA-256" differ). MGF1-SHA-1 is
     * the MGF digest Android Keystore supports on every API level; it does not weaken OAEP.
     */
    private val OAEP_SPEC = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    private val random: SecureRandom by lazy { SecureRandom() }

    // ------------------------------------------------------------------ keys

    /** Generates a fresh key pair for [profile] (RSA e = 65537; EC named curve secp256r1 / secp384r1). */
    @JvmStatic
    fun generateKeyPair(profile: Profile): KeyPair = guard {
        val kpg = when (profile.algorithm) {
            Algorithm.RSA -> KeyPairGenerator.getInstance("RSA").apply {
                initialize(RSAKeyGenParameterSpec(profile.keySizeBits, RSAKeyGenParameterSpec.F4), random)
            }
            Algorithm.EC -> KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec(profile.curve!!.jcaName), random)
            }
        }
        kpg.generateKeyPair()
    }

    // ------------------------------------------------------------- encrypt

    /**
     * Encrypts [plaintext] for the holder of [recipient] using [profile].
     *
     * @return the binary envelope (see [Envelope]); its size is exactly [Envelope.sizeFor].
     * @throws SecureSmsException KEY_PROFILE_MISMATCH if [recipient] is not a key of [profile];
     *         PLAINTEXT_TOO_LARGE above [MAX_PLAINTEXT_BYTES].
     */
    @JvmStatic
    fun encrypt(plaintext: ByteArray, recipient: PublicKey, profile: Profile): ByteArray {
        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            fail(ErrorCode.PLAINTEXT_TOO_LARGE, "Plaintext is ${plaintext.size} bytes, maximum is $MAX_PLAINTEXT_BYTES")
        }
        PublicKeys.requireProfile(recipient, profile)
        val header = Envelope.header(profile, PublicKeys.fingerprint(recipient).keyId)
        val kem = encapsulate(profile, recipient, header)
        val aad = header + kem.block
        val ct = try {
            aesGcm(Cipher.ENCRYPT_MODE, kem.material, aad, plaintext)
        } finally {
            kem.material.fill(0)
        }
        return aad + ct
    }

    /** UTF-8 convenience wrapper around [encrypt]. */
    @JvmStatic
    fun encryptText(text: String, recipient: PublicKey, profile: Profile): ByteArray =
        encrypt(text.toByteArray(Charsets.UTF_8), recipient, profile)

    // ------------------------------------------------------------- decrypt

    /**
     * Decrypts an envelope with the recipient's key pair. The public key is needed to check the
     * key id and (for ECC) for the key derivation. [privateKey] may be a non-exportable Android
     * Keystore key; it is only used through Cipher / KeyAgreement.
     *
     * @throws SecureSmsException with MALFORMED_ENVELOPE, TRUNCATED_ENVELOPE, UNSUPPORTED_VERSION,
     *         UNKNOWN_PROFILE, KEY_PROFILE_MISMATCH, WRONG_RECIPIENT, INVALID_EPHEMERAL_KEY or
     *         AUTHENTICATION_FAILED. No plaintext is ever returned if any check fails.
     */
    @JvmStatic
    fun decrypt(envelope: ByteArray, publicKey: PublicKey, privateKey: PrivateKey): ByteArray {
        val env = Envelope.parse(envelope)
        PublicKeys.requireProfile(publicKey, env.profile)
        val expectedId = PublicKeys.fingerprint(publicKey).keyId
        if (!expectedId.contentEquals(env.recipientKeyId)) {
            fail(ErrorCode.WRONG_RECIPIENT, "Message was encrypted for key id ${env.recipientKeyId.toHex()}, not ${expectedId.toHex()}")
        }
        val material = decapsulate(env.profile, publicKey, privateKey, env.header(), env.kem())
        try {
            return aesGcm(Cipher.DECRYPT_MODE, material, env.associatedData(), env.ciphertext)
        } finally {
            material.fill(0)
        }
    }

    @JvmStatic
    fun decrypt(envelope: ByteArray, recipient: KeyPair): ByteArray = decrypt(envelope, recipient.public, recipient.private)

    /** UTF-8 convenience wrapper around [decrypt]; rejects bytes that are not valid UTF-8. */
    @JvmStatic
    fun decryptText(envelope: ByteArray, recipient: KeyPair): String = decodeUtf8(decrypt(envelope, recipient))

    @JvmStatic
    fun decryptText(envelope: ByteArray, publicKey: PublicKey, privateKey: PrivateKey): String =
        decodeUtf8(decrypt(envelope, publicKey, privateKey))

    /** Reads the public header without decrypting (e.g. to pick the right key or show the profile). */
    @JvmStatic
    fun inspect(envelope: ByteArray): Envelope = Envelope.parse(envelope)

    // ------------------------------------------------------- internals (KEM)

    internal class Encapsulated(val block: ByteArray, val material: ByteArray)

    /** Asymmetric half of encryption. Exposed internally so the benchmark can time it on its own. */
    internal fun encapsulate(profile: Profile, recipient: PublicKey, header: ByteArray): Encapsulated = guard {
        when (profile.algorithm) {
            Algorithm.RSA -> {
                val seed = ByteArray(SEED_LENGTH).also { random.nextBytes(it) }
                val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, recipient, OAEP_SPEC, random)
                val wrapped = cipher.doFinal(seed)
                val material = Hkdf.derive(profile.hkdfMac, seed, ByteArray(0), LABEL + header, AES_KEY_LENGTH + NONCE_LENGTH)
                seed.fill(0)
                Encapsulated(wrapped, material)
            }
            Algorithm.EC -> {
                val curve = profile.curve!!
                val recipientEc = recipient as ECPublicKey
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(ECGenParameterSpec(curve.jcaName), random)
                val eph = kpg.generateKeyPair()
                val shared = ecdh(eph.private, recipient)
                val ephBytes = curve.compress((eph.public as ECPublicKey).w)
                val info = LABEL + header + curve.compress(recipientEc.w)
                val material = Hkdf.derive(profile.hkdfMac, shared, ephBytes, info, AES_KEY_LENGTH + NONCE_LENGTH)
                shared.fill(0)
                Encapsulated(ephBytes, material)
            }
        }
    }

    /** Asymmetric half of decryption: returns the 44 bytes of key + nonce material. */
    internal fun decapsulate(
        profile: Profile,
        publicKey: PublicKey,
        privateKey: PrivateKey,
        header: ByteArray,
        block: ByteArray,
    ): ByteArray = when (profile.algorithm) {
        Algorithm.RSA -> {
            val seed = try {
                val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
                cipher.doFinal(block)
            } catch (e: GeneralSecurityException) {
                // Same code as a GCM failure: never tell an attacker *which* check failed.
                fail(ErrorCode.AUTHENTICATION_FAILED, "Message could not be authenticated")
            }
            if (seed.size != SEED_LENGTH) fail(ErrorCode.AUTHENTICATION_FAILED, "Message could not be authenticated")
            val material = Hkdf.derive(profile.hkdfMac, seed, ByteArray(0), LABEL + header, AES_KEY_LENGTH + NONCE_LENGTH)
            seed.fill(0)
            material
        }
        Algorithm.EC -> {
            val curve = profile.curve!!
            val point = curve.decompress(block)
            val params = (publicKey as ECPublicKey).params
            val ephPub = guard { KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params)) }
            val shared = try {
                ecdh(privateKey, ephPub)
            } catch (e: GeneralSecurityException) {
                fail(ErrorCode.INVALID_EPHEMERAL_KEY, "Key agreement with the ephemeral key failed", e)
            }
            val info = LABEL + header + curve.compress(publicKey.w)
            val material = Hkdf.derive(profile.hkdfMac, shared, block, info, AES_KEY_LENGTH + NONCE_LENGTH)
            shared.fill(0)
            material
        }
    }

    private fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    private fun aesGcm(mode: Int, material: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val key = SecretKeySpec(material, 0, AES_KEY_LENGTH, "AES")
            val nonce = GCMParameterSpec(Envelope.TAG_LENGTH * 8, material, AES_KEY_LENGTH, NONCE_LENGTH)
            cipher.init(mode, key, nonce)
            cipher.updateAAD(aad)
            return cipher.doFinal(input)
        } catch (e: AEADBadTagException) {
            fail(ErrorCode.AUTHENTICATION_FAILED, "Message could not be authenticated")
        } catch (e: GeneralSecurityException) {
            if (mode == Cipher.DECRYPT_MODE) fail(ErrorCode.AUTHENTICATION_FAILED, "Message could not be authenticated")
            fail(ErrorCode.PLATFORM_ERROR, "AES-GCM unavailable: ${e.message}", e)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        fail(ErrorCode.INVALID_TEXT, "Decrypted message is not valid UTF-8", e)
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: SecureSmsException) {
        throw e
    } catch (e: GeneralSecurityException) {
        fail(ErrorCode.PLATFORM_ERROR, "Crypto provider error: ${e.message}", e)
    }
}
