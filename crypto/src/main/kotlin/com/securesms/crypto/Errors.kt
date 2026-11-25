package com.securesms.crypto

/**
 * Every failure of the library is reported as a [SecureSmsException] carrying one of these codes,
 * so the Android app can map each one to a clear status without parsing messages.
 */
enum class ErrorCode {
    /** Input is not a SecureSMS envelope (wrong magic bytes, empty input, oversized body). */
    MALFORMED_ENVELOPE,

    /** Envelope is shorter than its header + key block + authentication tag. Usually a missing SMS fragment. */
    TRUNCATED_ENVELOPE,

    /** Version byte is not one this library understands. */
    UNSUPPORTED_VERSION,

    /** Profile byte does not name RSA-2048, RSA-3072, P-256 or P-384. */
    UNKNOWN_PROFILE,

    /** The key given does not belong to the algorithm / size / curve of the chosen profile. */
    KEY_PROFILE_MISMATCH,

    /** The envelope was encrypted for a different public key (key id in the header does not match). */
    WRONG_RECIPIENT,

    /** The ephemeral EC point inside the envelope is not a valid point on the curve. */
    INVALID_EPHEMERAL_KEY,

    /**
     * Integrity check failed: ciphertext, header or key block was modified, or the private key is wrong.
     * RSA-OAEP failures are deliberately reported with this same code (no padding oracle).
     */
    AUTHENTICATION_FAILED,

    /** A public or private key could not be decoded or fails validation. */
    INVALID_KEY,

    /** Plaintext is larger than [SecureSmsCrypto.MAX_PLAINTEXT_BYTES]. */
    PLAINTEXT_TOO_LARGE,

    /** Decrypted bytes are not valid UTF-8 (only from decryptText). */
    INVALID_TEXT,

    /** The platform is missing a required algorithm (should not happen on JDK 11+ / Android 8+). */
    PLATFORM_ERROR,
}

class SecureSmsException(
    val code: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception("[$code] $message", cause)

internal fun fail(code: ErrorCode, message: String, cause: Throwable? = null): Nothing =
    throw SecureSmsException(code, message, cause)
