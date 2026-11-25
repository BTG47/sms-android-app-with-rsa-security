package com.securesms.benchmark

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.securesms.crypto.Envelope
import com.securesms.crypto.PrivateKeys
import com.securesms.crypto.Profile
import com.securesms.crypto.PublicKeys
import com.securesms.crypto.SecureSmsCrypto
import com.securesms.crypto.SecureSmsException
import java.io.File
import java.util.Base64

/**
 * Writes integration vectors for the Android app: for every profile, a TEST-ONLY key pair, several
 * valid envelopes with their expected plaintext, and invalid envelopes with their expected error code.
 * Encryption is randomized, so these vectors check *decryption* and *error handling* on the phone.
 */
fun main(args: Array<String>) {
    val outputs = args.ifEmpty { arrayOf("test-vectors/vectors.json") }
    val b64 = Base64.getEncoder()
    val root = JsonObject()
    root.addProperty("format", "SecureSMS envelope v1")
    root.addProperty("warning", "TEST KEYS ONLY. Private keys are published here on purpose; never use them for real messages.")
    root.addProperty("generated_at", java.time.Instant.now().toString())
    val keys = JsonArray()
    val vectors = JsonArray()

    val texts = linkedMapOf(
        "empty" to "",
        "short" to "Hola Bruno",
        "sms160" to "Este mensaje tiene exactamente ciento sesenta caracteres para medir el tamano de un SMS normal sin acentos ni simbolos raros!!. Relleno relleno relleno relleno.",
        "unicode" to "¡Hola! Ñandú, café y 😀🔐 — 你好",
    )
    check(texts["sms160"]!!.length == 160) { "sms160 is ${texts["sms160"]!!.length}" }

    for (profile in Profile.values()) {
        val kp = SecureSmsCrypto.generateKeyPair(profile)
        val other = SecureSmsCrypto.generateKeyPair(profile)
        val fp = PublicKeys.fingerprint(kp.public)
        keys.add(JsonObject().apply {
            addProperty("key_name", "test-${profile.name}")
            addProperty("profile", profile.name)
            addProperty("public_key_compact", PublicKeys.exportCompact(kp.public))
            addProperty("public_key_pem", PublicKeys.exportPem(kp.public))
            addProperty("private_key_pkcs8_base64", b64.encodeToString(PrivateKeys.exportPkcs8(kp.private)))
            addProperty("fingerprint", fp.hex)
            addProperty("key_id_hex", fp.keyId.joinToString("") { "%02X".format(it) })
        })

        fun add(id: String, description: String, envelope: ByteArray, plaintext: String?, expected: String) {
            // Self-check: every vector must behave as documented before it is written.
            val actual = try {
                val got = SecureSmsCrypto.decryptText(envelope, kp)
                check(got == plaintext) { "vector $id decrypted to the wrong text" }
                "OK"
            } catch (e: SecureSmsException) {
                e.code.name
            }
            check(actual == expected) { "vector $id: expected $expected, got $actual" }
            vectors.add(JsonObject().apply {
                addProperty("id", id)
                addProperty("profile", profile.name)
                addProperty("key_name", "test-${profile.name}")
                addProperty("description", description)
                addProperty("envelope_base64", b64.encodeToString(envelope))
                addProperty("envelope_length", envelope.size)
                addProperty("expected_result", expected)
                if (plaintext != null) addProperty("expected_plaintext", plaintext)
            })
        }

        val p = profile.name.lowercase()
        for ((name, text) in texts) {
            add("$p-ok-$name", "Valid ${profile.displayName} envelope, ${text.length}-char '$name' message", SecureSmsCrypto.encryptText(text, kp.public, profile), text, "OK")
        }
        val base = SecureSmsCrypto.encryptText("Mensaje de prueba", kp.public, profile)
        fun mutate(f: (ByteArray) -> Unit) = base.copyOf().also(f)
        add("$p-bad-ciphertext", "Last ciphertext byte before the tag flipped", mutate { it[it.size - 17] = (it[it.size - 17].toInt() xor 1).toByte() }, null, "AUTHENTICATION_FAILED")
        add("$p-bad-tag", "Last tag byte flipped", mutate { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x80).toByte() }, null, "AUTHENTICATION_FAILED")
        add("$p-bad-keyid", "Recipient key id changed", mutate { it[4] = (it[4].toInt() xor 1).toByte() }, null, "WRONG_RECIPIENT")
        add("$p-bad-version", "Version byte set to 2", mutate { it[2] = 2 }, null, "UNSUPPORTED_VERSION")
        add("$p-bad-profile", "Profile byte set to 0x7F", mutate { it[3] = 0x7F }, null, "UNKNOWN_PROFILE")
        add("$p-bad-magic", "Magic bytes changed", mutate { it[0] = 0x00 }, null, "MALFORMED_ENVELOPE")
        add("$p-truncated-fragment", "Only the first SMS fragment arrived (shorter than header + key block + tag)", base.copyOf(Envelope.overhead(profile) - 1), null, "TRUNCATED_ENVELOPE")
        add("$p-truncated-1", "Last byte missing", base.copyOf(base.size - 1), null, "AUTHENTICATION_FAILED")
        add("$p-wrong-recipient", "Encrypted for a different key of the same profile", SecureSmsCrypto.encryptText("No es para ti", other.public, profile), null, "WRONG_RECIPIENT")
        if (profile.name.startsWith("ECC")) {
            add("$p-bad-ephemeral", "Ephemeral point prefix changed to 0x04", mutate { it[Envelope.HEADER_LENGTH] = 0x04 }, null, "INVALID_EPHEMERAL_KEY")
        } else {
            add("$p-bad-wrapped-key", "RSA-OAEP block modified", mutate { it[Envelope.HEADER_LENGTH + 10] = (it[Envelope.HEADER_LENGTH + 10].toInt() xor 1).toByte() }, null, "AUTHENTICATION_FAILED")
        }
    }
    root.add("keys", keys)
    root.add("vectors", vectors)
    val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
    for (out in outputs) {
        File(out).apply { parentFile?.mkdirs() }.writeText(json + "\n")
        println("Wrote ${vectors.size()} vectors to $out")
    }
}
