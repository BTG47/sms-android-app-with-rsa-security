package com.securesms.crypto.bench

import com.securesms.crypto.Envelope
import com.securesms.crypto.Profile
import com.securesms.crypto.PublicKeys
import com.securesms.crypto.SecureSmsCrypto
import java.util.Locale
import kotlin.math.sqrt

/**
 * Reproducible benchmark runner with no Android or file-system dependencies, so the exact same code
 * runs on a PC JVM (`:benchmark` module) and on Bruno's phone (instrumented test or debug button).
 */
data class BenchmarkConfig(
    val profiles: List<Profile> = Profile.values().toList(),
    /** Plaintext sizes in characters (ASCII, so also bytes). 160 = one plain GSM-7 SMS. */
    val messageSizes: List<Int> = listOf(0, 16, 70, 140, 160, 320, 1000),
    val keygenWarmup: Int = 5,
    val keygenIterations: Int = 50,
    val opWarmup: Int = 200,
    val opIterations: Int = 500,
    /**
     * Rounds of every operation for every profile run *before* any measurement, so the JIT has
     * compiled the BigInteger / AES-GCM paths for all profiles. Without it, the first profile
     * measured looks slower than the rest just because it ran first.
     */
    val globalWarmup: Int = 1500,
    /** Fixed seed for the message text so every run encrypts the same plaintexts. */
    val messageSeed: Long = 20261119L,
) {
    companion object {
        /** Lighter settings for a phone (a few minutes in total). */
        @JvmStatic
        fun quick() = BenchmarkConfig(keygenWarmup = 2, keygenIterations = 15, opWarmup = 50, opIterations = 150, globalWarmup = 300)

        /** Tiny settings used by the unit tests. */
        @JvmStatic
        fun smoke() = BenchmarkConfig(messageSizes = listOf(0, 160), keygenWarmup = 0, keygenIterations = 2, opWarmup = 2, opIterations = 5, globalWarmup = 2)
    }
}

/** One measured operation. Times in microseconds. */
data class TimingRow(
    val profile: Profile,
    val operation: String,
    val messageChars: Int?,
    val n: Int,
    val meanUs: Double,
    val medianUs: Double,
    val stdDevUs: Double,
    val minUs: Double,
    val maxUs: Double,
    val p95Us: Double,
)

/** Size / SMS cost of one message size under one profile. */
data class SizeRow(
    val profile: Profile,
    val messageChars: Int,
    val plaintextBytes: Int,
    val envelopeBytes: Int,
    val overheadBytes: Int,
    val expansion: Double,
    val base64Chars: Int,
    val smsPlain: Int,
    val smsEnvelopeBase64: Int,
    val smsEnvelopeBinary: Int,
)

data class BenchmarkReport(
    val environment: Map<String, String>,
    val timings: List<TimingRow>,
    val sizes: List<SizeRow>,
) {
    fun timingsCsv(): String = buildString {
        appendLine("platform,profile,security_bits,operation,message_chars,n,mean_us,median_us,stddev_us,min_us,max_us,p95_us")
        val platform = environment["platform"] ?: "unknown"
        for (r in timings) {
            appendLine(
                listOf(
                    platform, r.profile.displayName, r.profile.securityBits, r.operation, r.messageChars ?: "",
                    r.n, f(r.meanUs), f(r.medianUs), f(r.stdDevUs), f(r.minUs), f(r.maxUs), f(r.p95Us),
                ).joinToString(","),
            )
        }
    }

    fun sizesCsv(): String = buildString {
        appendLine("profile,message_chars,plaintext_bytes,envelope_bytes,overhead_bytes,expansion,base64_chars,sms_plain,sms_envelope_base64,sms_envelope_binary")
        for (r in sizes) {
            appendLine(
                listOf(
                    r.profile.displayName, r.messageChars, r.plaintextBytes, r.envelopeBytes, r.overheadBytes,
                    f(r.expansion), r.base64Chars, r.smsPlain, r.smsEnvelopeBase64, r.smsEnvelopeBinary,
                ).joinToString(","),
            )
        }
    }

    fun environmentText(): String = environment.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n"

    private fun f(v: Double) = String.format(Locale.US, "%.3f", v)
}

object BenchmarkRunner {
    const val OP_KEYGEN = "keygen"
    const val OP_ENCAPSULATE = "asym_encrypt" // RSA-OAEP encrypt of the seed  | ephemeral keygen + ECDH
    const val OP_DECAPSULATE = "asym_decrypt" // RSA-OAEP decrypt of the seed  | point decompression + ECDH
    const val OP_ENCRYPT = "encrypt_full"
    const val OP_DECRYPT = "decrypt_full"

    /**
     * Runs every measurement. [extraEnvironment] lets the caller add device info
     * (e.g. Build.MODEL, Build.VERSION.SDK_INT on Android). [progress] receives one line per step.
     */
    @JvmStatic
    @JvmOverloads
    fun run(
        config: BenchmarkConfig = BenchmarkConfig(),
        extraEnvironment: Map<String, String> = emptyMap(),
        progress: (String) -> Unit = {},
    ): BenchmarkReport {
        val env = LinkedHashMap<String, String>()
        env.putAll(environmentInfo())
        env.putAll(extraEnvironment)
        env["config"] = config.toString()
        env["started_at_epoch_ms"] = System.currentTimeMillis().toString()

        val timings = ArrayList<TimingRow>()
        val sizes = ArrayList<SizeRow>()
        val messages = config.messageSizes.associateWith { message(it, config.messageSeed) }

        progress("Global warm-up (${config.globalWarmup} rounds per profile)")
        val warmKeys = config.profiles.associateWith { SecureSmsCrypto.generateKeyPair(it) }
        val warmText = message(160, config.messageSeed).toByteArray(Charsets.UTF_8)
        repeat(config.globalWarmup) {
            for ((p, kp) in warmKeys) {
                sink = SecureSmsCrypto.decrypt(SecureSmsCrypto.encrypt(warmText, kp.public, p), kp)
            }
        }

        for (profile in config.profiles) {
            progress("${profile.displayName}: key generation")
            timings += measure(profile, OP_KEYGEN, null, config.keygenWarmup, config.keygenIterations) {
                SecureSmsCrypto.generateKeyPair(profile)
            }

            val kp = SecureSmsCrypto.generateKeyPair(profile)
            val header = byteArrayOf(0x53, 0x53, 1, profile.id.toByte()) + PublicKeys.fingerprint(kp.public).keyId
            progress("${profile.displayName}: asymmetric operations")
            timings += measure(profile, OP_ENCAPSULATE, null, config.opWarmup, config.opIterations) {
                SecureSmsCrypto.encapsulate(profile, kp.public, header)
            }
            val sample = SecureSmsCrypto.encapsulate(profile, kp.public, header)
            timings += measure(profile, OP_DECAPSULATE, null, config.opWarmup, config.opIterations) {
                SecureSmsCrypto.decapsulate(profile, kp.public, kp.private, header, sample.block)
            }

            for ((chars, text) in messages) {
                progress("${profile.displayName}: full encrypt/decrypt, $chars chars")
                val plain = text.toByteArray(Charsets.UTF_8)
                timings += measure(profile, OP_ENCRYPT, chars, config.opWarmup, config.opIterations) {
                    SecureSmsCrypto.encrypt(plain, kp.public, profile)
                }
                val env1 = SecureSmsCrypto.encrypt(plain, kp.public, profile)
                timings += measure(profile, OP_DECRYPT, chars, config.opWarmup, config.opIterations) {
                    SecureSmsCrypto.decrypt(env1, kp)
                }
                check(SecureSmsCrypto.decrypt(env1, kp).contentEquals(plain)) { "Round trip failed during benchmark" }
                sizes += sizeRow(profile, chars, text, env1.size)
            }
        }
        env["finished_at_epoch_ms"] = System.currentTimeMillis().toString()
        return BenchmarkReport(env, timings, sizes)
    }

    /** Size analysis for any text (used for the Unicode examples in the report). */
    @JvmStatic
    fun sizeRow(profile: Profile, chars: Int, text: String, envelopeBytes: Int = Envelope.sizeFor(profile, text.toByteArray(Charsets.UTF_8).size)): SizeRow {
        val plainBytes = text.toByteArray(Charsets.UTF_8).size
        return SizeRow(
            profile = profile,
            messageChars = chars,
            plaintextBytes = plainBytes,
            envelopeBytes = envelopeBytes,
            overheadBytes = envelopeBytes - plainBytes,
            expansion = if (plainBytes == 0) Double.NaN else envelopeBytes.toDouble() / plainBytes,
            base64Chars = SmsEstimator.base64Length(envelopeBytes),
            smsPlain = SmsEstimator.segmentsForPlainText(text),
            smsEnvelopeBase64 = SmsEstimator.segmentsForEnvelopeBase64(envelopeBytes),
            smsEnvelopeBinary = SmsEstimator.segmentsBinary(envelopeBytes),
        )
    }

    /** Deterministic printable ASCII text of [chars] characters (letters, digits, spaces). */
    @JvmStatic
    fun message(chars: Int, seed: Long): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,"
        val rnd = java.util.Random(seed + chars)
        return String(CharArray(chars) { alphabet[rnd.nextInt(alphabet.length)] })
    }

    @Volatile
    private var sink: Any? = null

    private inline fun measure(profile: Profile, op: String, chars: Int?, warmup: Int, n: Int, block: () -> Any): TimingRow {
        repeat(warmup) { sink = block() }
        val samples = DoubleArray(n)
        for (i in 0 until n) {
            val t0 = System.nanoTime()
            val r = block()
            val t1 = System.nanoTime()
            sink = r
            samples[i] = (t1 - t0) / 1000.0
        }
        return stats(profile, op, chars, samples)
    }

    internal fun stats(profile: Profile, op: String, chars: Int?, samples: DoubleArray): TimingRow {
        val sorted = samples.sortedArray()
        val n = sorted.size
        val mean = sorted.average()
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        val sd = if (n > 1) sqrt(sorted.sumOf { (it - mean) * (it - mean) } / (n - 1)) else 0.0
        val p95 = sorted[minOf(n - 1, kotlin.math.ceil(0.95 * n).toInt() - 1).coerceAtLeast(0)]
        return TimingRow(profile, op, chars, n, mean, median, sd, sorted.first(), sorted.last(), p95)
    }

    @JvmStatic
    fun environmentInfo(): Map<String, String> {
        val p = System.getProperties()
        val m = LinkedHashMap<String, String>()
        val vmName = p.getProperty("java.vm.name", "")
        m["platform"] = if (vmName.contains("Dalvik", true) || vmName.contains("ART", false)) "android" else "jvm"
        m["os.name"] = p.getProperty("os.name", "")
        m["os.version"] = p.getProperty("os.version", "")
        m["os.arch"] = p.getProperty("os.arch", "")
        m["java.vm.name"] = vmName
        m["java.vm.version"] = p.getProperty("java.vm.version", "")
        m["java.version"] = p.getProperty("java.version", "")
        m["java.vendor"] = p.getProperty("java.vendor", "")
        m["cpu.cores"] = Runtime.getRuntime().availableProcessors().toString()
        m["max.heap.mb"] = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toString()
        cpuModel()?.let { m["cpu.model"] = it }
        m["providers"] = java.security.Security.getProviders().joinToString(" ") { it.name }
        return m
    }

    private fun cpuModel(): String? = try {
        java.io.File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("model name") || it.startsWith("Hardware") || it.startsWith("Processor") }
            ?.substringAfter(":")?.trim()
    } catch (e: Exception) {
        null
    }
}
