package com.securesms.crypto

import com.google.gson.JsonParser
import java.security.KeyPair
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks the committed integration vectors (the same file Bruno uses on Android). */
class TestVectorsTest {
    @Test
    fun `all published vectors give their expected result`() {
        val text = javaClass.getResourceAsStream("/vectors.json")!!.reader().readText()
        val root = JsonParser.parseString(text).asJsonObject
        val keys = root.getAsJsonArray("keys").associate { el ->
            val o = el.asJsonObject
            val pub = PublicKeys.importCompact(o["public_key_compact"].asString)
            val priv = PrivateKeys.importPkcs8(Base64.getDecoder().decode(o["private_key_pkcs8_base64"].asString), pub.profile)
            assertEquals(o["fingerprint"].asString, PublicKeys.fingerprint(pub.key).hex)
            assertEquals(pub.key.encoded.toList(), PublicKeys.importPem(o["public_key_pem"].asString).key.encoded.toList())
            o["key_name"].asString to KeyPair(pub.key, priv)
        }
        val vectors = root.getAsJsonArray("vectors")
        assertTrue(vectors.size() >= 40)
        for (el in vectors) {
            val v = el.asJsonObject
            val env = Base64.getDecoder().decode(v["envelope_base64"].asString)
            val kp = keys.getValue(v["key_name"].asString)
            val result = try {
                val plain = SecureSmsCrypto.decryptText(env, kp)
                assertEquals(v["expected_plaintext"].asString, plain, v["id"].asString)
                "OK"
            } catch (e: SecureSmsException) {
                e.code.name
            }
            assertEquals(v["expected_result"].asString, result, v["id"].asString)
        }
    }
}
