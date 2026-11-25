// Copy into Bruno's Android project:  app/src/androidTest/java/com/securesms/app/CryptoBenchmarkInstrumentedTest.kt
// Run on the phone (USB debugging on):
//   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.securesms.app.CryptoBenchmarkInstrumentedTest
// Then pull the results:
//   adb pull /sdcard/Android/data/<applicationId>/files/securesms-bench/ ./results/android-<modelo>/
// and send the folder (timings.csv, sizes.csv, environment.txt) to José.
//
// Needs in app/build.gradle(.kts):
//   androidTestImplementation("androidx.test.ext:junit:1.2.1")
//   androidTestImplementation("androidx.test:runner:1.6.2")
//   defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
// Tip: build a *release-like* variant or at least keep the phone plugged in, screen on, no other apps,
// and do not touch it while it runs (about 3-8 minutes with the quick config).
package com.securesms.app

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securesms.crypto.bench.BenchmarkConfig
import com.securesms.crypto.bench.BenchmarkRunner
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CryptoBenchmarkInstrumentedTest {
    @Test
    fun runCryptoBenchmarks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = mapOf(
            "device.label" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android.sdk" to Build.VERSION.SDK_INT.toString(),
            "android.release" to Build.VERSION.RELEASE,
            "android.soc" to (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE),
            "android.abi" to Build.SUPPORTED_ABIS.joinToString(" "),
            "android.debuggable" to ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0).toString(),
        )
        val report = BenchmarkRunner.run(BenchmarkConfig.quick(), device) { Log.i("SecureSmsBench", it) }

        val dir = File(context.getExternalFilesDir(null), "securesms-bench").apply { mkdirs() }
        File(dir, "timings.csv").writeText(report.timingsCsv())
        File(dir, "sizes.csv").writeText(report.sizesCsv())
        File(dir, "environment.txt").writeText(report.environmentText())
        Log.i("SecureSmsBench", "Results written to ${dir.absolutePath}")
        // Also dump to logcat in case adb pull is not possible:  adb logcat -s SecureSmsBenchCsv
        report.timingsCsv().lines().forEach { Log.i("SecureSmsBenchCsv", it) }
    }
}
