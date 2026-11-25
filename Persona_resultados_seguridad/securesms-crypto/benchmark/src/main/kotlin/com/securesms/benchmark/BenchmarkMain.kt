package com.securesms.benchmark

import com.securesms.crypto.bench.BenchmarkConfig
import com.securesms.crypto.bench.BenchmarkRunner
import java.io.File

/**
 * PC/JVM benchmark entry point.
 *   ./gradlew :benchmark:run --args="--out results/jvm"            (full run)
 *   ./gradlew :benchmark:run --args="--out results/jvm-quick --quick"
 *   ./gradlew :benchmark:run --args="--out results/x --label 'Laptop de Jose'"
 */
fun main(args: Array<String>) {
    var out = "results/jvm"
    var quick = false
    var label: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--out" -> out = args[++i]
            "--quick" -> quick = true
            "--label" -> label = args[++i]
            else -> error("Unknown argument ${args[i]}")
        }
        i++
    }
    val config = if (quick) BenchmarkConfig.quick() else BenchmarkConfig()
    val extra = buildMap { label?.let { put("device.label", it) } }
    val t0 = System.currentTimeMillis()
    val report = BenchmarkRunner.run(config, extra) { println("[bench] $it") }
    val dir = File(out).apply { mkdirs() }
    File(dir, "timings.csv").writeText(report.timingsCsv())
    File(dir, "sizes.csv").writeText(report.sizesCsv())
    File(dir, "environment.txt").writeText(report.environmentText())
    println("Done in ${(System.currentTimeMillis() - t0) / 1000}s -> ${dir.absolutePath}")
}
