package com.securesms.app.keystore

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val ETIQUETA = "KeystoreSpike"

/**
 * Registra la identidad del dispositivo y las funcionalidades Keystore que anuncia.
 * Una funcionalidad anunciada no demuestra que la operación pueda ejecutarse.
 */
@RunWith(AndroidJUnit4::class)
class IdentidadDispositivoTest {

    @Test
    fun registraIdentidadYAnuncioDeKeystore() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val funciones = contexto.packageManager.getSystemAvailableFeatures()
        val keystore = funciones.firstOrNull { it.name == PackageManager.FEATURE_HARDWARE_KEYSTORE }
        val strongbox = funciones.firstOrNull { it.name == PackageManager.FEATURE_STRONGBOX_KEYSTORE }

        Log.i(
            ETIQUETA,
            "dispositivo=${Build.MODEL} fabricante=${Build.MANUFACTURER} api=${Build.VERSION.SDK_INT}",
        )
        Log.i(ETIQUETA, "build=${Build.DISPLAY} parche=${Build.VERSION.SECURITY_PATCH}")
        Log.i(
            ETIQUETA,
            "hardwareKeystore=${keystore?.version ?: "ausente"} strongBox=${strongbox?.version ?: "ausente"}",
        )

        assertTrue("El dispositivo debe anunciar FEATURE_HARDWARE_KEYSTORE", keystore != null)
        assertTrue("La API mínima acordada es 31", Build.VERSION.SDK_INT >= 31)
    }
}
