package com.securesms.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HKDF (RFC 5869) built on the platform HMAC, so no extra dependency is needed on Android. */
internal object Hkdf {
    fun derive(macAlgorithm: String, ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(macAlgorithm)
        val hashLen = mac.macLength
        require(length in 1..255 * hashLen) { "Invalid HKDF output length" }
        // Extract
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(hashLen) else salt, macAlgorithm))
        val prk = mac.doFinal(ikm)
        // Expand
        mac.init(SecretKeySpec(prk, macAlgorithm))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - offset)
            t.copyInto(out, offset, 0, n)
            offset += n
            counter++
        }
        prk.fill(0)
        return out
    }
}
