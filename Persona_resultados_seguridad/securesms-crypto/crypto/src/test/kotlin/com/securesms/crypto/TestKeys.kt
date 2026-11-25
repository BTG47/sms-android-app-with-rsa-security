package com.securesms.crypto

import java.security.KeyPair

/** Key pairs are expensive (RSA-3072), so tests share two per profile. */
object TestKeys {
    private val primary = HashMap<Profile, KeyPair>()
    private val secondary = HashMap<Profile, KeyPair>()

    @Synchronized
    fun of(p: Profile): KeyPair = primary.getOrPut(p) { SecureSmsCrypto.generateKeyPair(p) }

    @Synchronized
    fun other(p: Profile): KeyPair = secondary.getOrPut(p) { SecureSmsCrypto.generateKeyPair(p) }
}

fun assertCode(expected: ErrorCode, block: () -> Unit) {
    val e = kotlin.test.assertFailsWith<SecureSmsException> { block() }
    kotlin.test.assertEquals(expected, e.code, "Unexpected error: ${e.message}")
}
