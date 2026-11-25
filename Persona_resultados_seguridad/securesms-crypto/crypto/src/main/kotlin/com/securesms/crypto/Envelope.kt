package com.securesms.crypto

/**
 * Binary envelope, version 1. All lengths are fixed by the profile, so there are no length fields
 * that an attacker could tamper with; the ciphertext is "the rest" of the message.
 *
 * ```
 * offset  size        field
 * 0       2           magic 0x53 0x53 ("SS")
 * 2       1           version = 0x01
 * 3       1           profile id (0x01 RSA-2048, 0x02 RSA-3072, 0x11 P-256, 0x12 P-384)
 * 4       4           recipient key id = first 4 bytes of the recipient key fingerprint
 * 8       K           key block: RSA-OAEP(seed) (K = 256 / 384) or compressed ephemeral EC point (K = 33 / 49)
 * 8+K     n + 16      AES-256-GCM ciphertext of the n-byte plaintext, followed by the 16-byte tag
 * ```
 * Bytes 0 .. 8+K-1 (header + key block) are authenticated as GCM associated data and are also
 * bound into the HKDF "info", so changing any metadata byte makes decryption fail.
 */
class Envelope internal constructor(
    val version: Int,
    val profile: Profile,
    private val keyId: ByteArray,
    private val kemBlock: ByteArray,
    private val ciphertextAndTag: ByteArray,
) {
    val recipientKeyId: ByteArray get() = keyId.copyOf()
    val keyBlock: ByteArray get() = kemBlock.copyOf()
    val ciphertext: ByteArray get() = ciphertextAndTag.copyOf()

    /** Length of the protected plaintext. */
    val plaintextLength: Int get() = ciphertextAndTag.size - TAG_LENGTH

    val totalLength: Int get() = HEADER_LENGTH + kemBlock.size + ciphertextAndTag.size

    internal fun header(): ByteArray = header(profile, keyId)
    internal fun associatedData(): ByteArray = header() + kemBlock
    internal fun kem(): ByteArray = kemBlock

    fun toByteArray(): ByteArray = associatedData() + ciphertextAndTag

    override fun toString(): String =
        "Envelope(v$version, ${profile.displayName}, keyId=${keyId.toHex()}, total=$totalLength B, plaintext=$plaintextLength B)"

    companion object {
        const val MAGIC_0: Byte = 0x53
        const val MAGIC_1: Byte = 0x53
        const val VERSION_1 = 1
        const val HEADER_LENGTH = 8
        const val TAG_LENGTH = 16

        /** Fixed bytes added to every message by [profile]: header + key block + GCM tag. */
        @JvmStatic
        fun overhead(profile: Profile): Int = HEADER_LENGTH + profile.kemBlockLength + TAG_LENGTH

        /** Exact envelope size for a plaintext of [plaintextLength] bytes. */
        @JvmStatic
        fun sizeFor(profile: Profile, plaintextLength: Int): Int = overhead(profile) + plaintextLength

        internal fun header(profile: Profile, keyId: ByteArray): ByteArray {
            require(keyId.size == KeyFingerprint.KEY_ID_LENGTH)
            return byteArrayOf(MAGIC_0, MAGIC_1, VERSION_1.toByte(), profile.id.toByte()) + keyId
        }

        /**
         * Validates structure only (magic, version, profile, lengths). It does NOT authenticate;
         * integrity is verified by [SecureSmsCrypto.decrypt]. Never trust fields before decrypting.
         */
        @JvmStatic
        fun parse(bytes: ByteArray): Envelope {
            if (bytes.isEmpty()) fail(ErrorCode.MALFORMED_ENVELOPE, "Empty input")
            if (bytes[0] != MAGIC_0 || (bytes.size > 1 && bytes[1] != MAGIC_1)) {
                fail(ErrorCode.MALFORMED_ENVELOPE, "Not a SecureSMS envelope (bad magic)")
            }
            if (bytes.size < HEADER_LENGTH) {
                fail(ErrorCode.TRUNCATED_ENVELOPE, "Envelope has ${bytes.size} bytes, header alone needs $HEADER_LENGTH")
            }
            val version = bytes[2].toInt() and 0xFF
            if (version != VERSION_1) fail(ErrorCode.UNSUPPORTED_VERSION, "Envelope version $version is not supported")
            val profileId = bytes[3].toInt() and 0xFF
            val profile = Profile.fromId(profileId)
                ?: fail(ErrorCode.UNKNOWN_PROFILE, "Unknown profile id 0x%02X".format(profileId))
            val minLength = overhead(profile)
            if (bytes.size < minLength) {
                fail(ErrorCode.TRUNCATED_ENVELOPE, "${profile.displayName} envelope needs at least $minLength bytes, got ${bytes.size}")
            }
            if (bytes.size - minLength > SecureSmsCrypto.MAX_PLAINTEXT_BYTES) {
                fail(ErrorCode.MALFORMED_ENVELOPE, "Envelope body is larger than the maximum message size")
            }
            val kemEnd = HEADER_LENGTH + profile.kemBlockLength
            return Envelope(
                version = version,
                profile = profile,
                keyId = bytes.copyOfRange(4, HEADER_LENGTH),
                kemBlock = bytes.copyOfRange(HEADER_LENGTH, kemEnd),
                ciphertextAndTag = bytes.copyOfRange(kemEnd, bytes.size),
            )
        }
    }
}
