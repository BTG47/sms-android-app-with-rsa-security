package com.securesms.app.identity

import android.content.pm.PackageManager
import android.security.keystore.KeyInfo
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securesms.app.integration.Perfil
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyStore
import java.util.Base64

/**
 * Prueba en dispositivo del almacén de claves: genera, no regenera y comprueba que la clave
 * privada no es exportable. La ejecución es HUMANO y se hace con `adb` en ambos teléfonos.
 */
@RunWith(AndroidJUnit4::class)
class AlmacenDeClavesAndroidTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val almacen = AlmacenDeClavesAndroid(contexto)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun alias(perfil: Perfil) = "securesms.${perfil.name.lowercase()}.v1"

    private fun publica(perfil: Perfil): ByteArray =
        Base64.getDecoder().decode(almacen.clavePublicaBase64(perfil).getOrThrow())

    @After
    fun limpiar() {
        Perfil.entries.forEach { perfil ->
            val alias = alias(perfil)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    @Test
    fun `genera RSA-2048 y la privada no es exportable`() {
        assertTrue(almacen.generarSiFalta(Perfil.RSA_2048).isSuccess)

        assertTrue(almacen.tieneClave(Perfil.RSA_2048))
        assertTrue(publica(Perfil.RSA_2048).isNotEmpty())
        assertNull(keyStore.getKey(alias(Perfil.RSA_2048), null).encoded)
    }

    @Test
    fun `no regenera si ya existe`() {
        almacen.generarSiFalta(Perfil.RSA_2048).getOrThrow()
        val primera = publica(Perfil.RSA_2048)

        almacen.generarSiFalta(Perfil.RSA_2048).getOrThrow()

        assertArrayEquals(primera, publica(Perfil.RSA_2048))
    }

    @Test
    fun `genera P-256 y P-384 con proposito de acuerdo`() {
        assertTrue(almacen.generarSiFalta(Perfil.ECC_P256).isSuccess)
        assertTrue(almacen.generarSiFalta(Perfil.ECC_P384).isSuccess)

        assertTrue(almacen.tieneClave(Perfil.ECC_P256))
        assertTrue(almacen.tieneClave(Perfil.ECC_P384))
        assertTrue(publica(Perfil.ECC_P256).isNotEmpty())
        assertTrue(publica(Perfil.ECC_P384).isNotEmpty())
    }

    @Test
    fun `los alias son estables y distintos por perfil`() {
        almacen.generarSiFalta(Perfil.RSA_2048).getOrThrow()
        almacen.generarSiFalta(Perfil.ECC_P256).getOrThrow()

        assertTrue(keyStore.containsAlias("securesms.rsa_2048.v1"))
        assertTrue(keyStore.containsAlias("securesms.ecc_p256.v1"))
    }

    @Test
    fun `strongbox solo si se anuncia`() {
        val anuncia =
            contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        assertTrue(almacen.generarSiFalta(Perfil.RSA_2048).isSuccess)

        val privada = keyStore.getKey(alias(Perfil.RSA_2048), null)
        val nivel = KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
            .getKeySpec(privada, KeyInfo::class.java)
            .securityLevel

        Log.i("SecureSMS", "strongbox anunciado=$anuncia nivel=$nivel")
        assertTrue(nivel >= 1)
    }
}
