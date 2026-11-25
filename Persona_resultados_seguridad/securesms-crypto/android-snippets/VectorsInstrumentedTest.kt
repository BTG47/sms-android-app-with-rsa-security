// Copy into Bruno's Android project:  app/src/androidTest/java/com/securesms/app/VectorsInstrumentedTest.kt
// and copy test-vectors/vectors.json to app/src/androidTest/assets/vectors.json
// Proves that the library gives the same results on the phone as on the PC (same envelopes,
// same expected plaintexts and error codes). Uses org.json, which is built into Android.
package com.securesms.app

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securesms.crypto.PrivateKeys
import com.securesms.crypto.PublicKeys
import com.securesms.crypto.SecureSmsCrypto
import com.securesms.crypto.SecureSmsException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair

@RunWith(AndroidJUnit4::class)
class VectorsInstrumentedTest {
    @Test
    fun vectorsMatchExpectedResults() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val root = JSONObject(ctx.assets.open("vectors.json").bufferedReader().readText())
        val keys = HashMap<String, KeyPair>()
        val ks = root.getJSONArray("keys")
        for (i in 0 until ks.length()) {
            val k = ks.getJSONObject(i)
            val pub = PublicKeys.importCompact(k.getString("public_key_compact"))
            val priv = PrivateKeys.importPkcs8(Base64.decode(k.getString("private_key_pkcs8_base64"), Base64.DEFAULT), pub.profile)
            assertEquals(k.getString("fingerprint"), PublicKeys.fingerprint(pub.key).hex)
            keys[k.getString("key_name")] = KeyPair(pub.key, priv)
        }
        val vs = root.getJSONArray("vectors")
        for (i in 0 until vs.length()) {
            val v = vs.getJSONObject(i)
            val env = Base64.decode(v.getString("envelope_base64"), Base64.DEFAULT)
            val result = try {
                val text = SecureSmsCrypto.decryptText(env, keys.getValue(v.getString("key_name")))
                assertEquals(v.getString("id"), v.getString("expected_plaintext"), text)
                "OK"
            } catch (e: SecureSmsException) {
                e.code.name
            }
            assertEquals(v.getString("id"), v.getString("expected_result"), result)
        }
    }
}
