package com.securesms.crypto

/** Asymmetric family used by a [Profile]. */
enum class Algorithm { RSA, EC }

/**
 * The four configurations required by the project. The numeric [id] is written in byte 3 of every
 * envelope, so it is part of the wire format and must never be renumbered.
 *
 * [securityBits] is the approximate classical security strength from NIST SP 800-57 Part 1 Rev. 5,
 * Table 2. It is what should be compared across families, not the numeric key size:
 * RSA-3072 and P-256 both give ~128 bits even though 3072 is 12x larger than 256.
 */
enum class Profile(
    val id: Int,
    val algorithm: Algorithm,
    val keySizeBits: Int,
    val displayName: String,
    val securityBits: Int,
) {
    RSA_2048(0x01, Algorithm.RSA, 2048, "RSA-2048", 112),
    RSA_3072(0x02, Algorithm.RSA, 3072, "RSA-3072", 128),
    ECC_P256(0x11, Algorithm.EC, 256, "ECC-P256", 128),
    ECC_P384(0x12, Algorithm.EC, 384, "ECC-P384", 192);

    /** Bytes taken by the key-encapsulation block of the envelope (RSA-OAEP output or compressed ephemeral point). */
    val kemBlockLength: Int
        get() = when (algorithm) {
            Algorithm.RSA -> keySizeBits / 8
            Algorithm.EC -> 1 + keySizeBits / 8
        }

    /** Named curve for EC profiles, null for RSA. */
    internal val curve: EcCurve?
        get() = when (this) {
            ECC_P256 -> EcCurve.P256
            ECC_P384 -> EcCurve.P384
            else -> null
        }

    /** HMAC used by HKDF. P-384 uses SHA-384 to match its 192-bit strength; the rest use SHA-256. */
    internal val hkdfMac: String
        get() = if (this == ECC_P384) "HmacSHA384" else "HmacSHA256"

    companion object {
        @JvmStatic
        fun fromId(id: Int): Profile? = values().firstOrNull { it.id == id }

        /** Accepts the enum name ("RSA_2048") or the display name ("RSA-2048"). */
        @JvmStatic
        fun fromName(name: String): Profile? =
            values().firstOrNull { it.name.equals(name, true) || it.displayName.equals(name, true) }
    }
}
