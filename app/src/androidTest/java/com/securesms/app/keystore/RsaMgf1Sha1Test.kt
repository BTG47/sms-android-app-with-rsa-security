package com.securesms.app.keystore

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec

private const val ETIQUETA_ACORDADO = "KeystoreSpike"
private const val TEXTO_ACORDADO = "SecureSMS MGF1 SHA-1 acordado"

/**
 * Verifica la configuración RSA-OAEP acordada: SHA-256 para el mensaje y MGF1-SHA1.
 * Cada prueba repite el descifrado tras recuperar la privada de Android Keystore.
 * El proveedor solo se registra cuando la operación termina correctamente.
 */
@RunWith(AndroidJUnit4::class)
class RsaMgf1Sha1Test {

    @Test
    fun rsa2048Tee() = verificar(2048, strongBox = false)

    @Test
    fun rsa3072Tee() = verificar(3072, strongBox = false)

    @Test
    fun rsa2048StrongBoxSiAnunciado() = verificar(2048, strongBox = true)

    @Test
    fun rsa3072StrongBoxSiAnunciado() = verificar(3072, strongBox = true)

    @Test
    fun strongBoxRechazadoRegistraNoDisponible() {
        assertNull(
            generarPublica(3072, strongBox = true) {
                throw StrongBoxUnavailableException("rechazo simulado para la prueba")
            },
        )
    }

    private fun verificar(bits: Int, strongBox: Boolean) {
        if (strongBox && !anunciadoStrongBox()) return

        val alias = "spike_acordado_${bits}_${if (strongBox) "strongbox" else "tee"}"
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        almacen.deleteEntry(alias)

        val publica = generarPublica(bits, strongBox) {
            generadorRsa(alias, bits, strongBox).generateKeyPair()
        } ?: return
        val privada = almacen.getKey(alias, null) as PrivateKey
        val parametros: OAEPParameterSpec = parametrosOaepAcordados()

        val cifrado = cifrar(publica, parametros)
        val descifrado = descifrar(privada, parametros, cifrado)

        assertEquals(TEXTO_ACORDADO, descifrado.texto)

        val recuperada = almacen.getKey(alias, null) as PrivateKey
        assertEquals(TEXTO_ACORDADO, descifrar(recuperada, parametros, cifrado).texto)

        Log.i(
            ETIQUETA_ACORDADO,
            "ACORDADO api=${Build.VERSION.SDK_INT} rsa=$bits " +
                "strongBox=$strongBox nivel=${nivel(privada)} proveedor=${descifrado.proveedor} " +
                "recuperada=PASS resultado=PASS",
        )
    }

    /**
     * Genera la clave y absorbe el rechazo de StrongBox cuando el dispositivo lo anuncia
     * pero no admite el tamaño solicitado. Devuelve `null` en ese caso para que la prueba
     * registre `NO_DISPONIBLE` en lugar de fallar por una capacidad opcional ausente.
     */
    private fun generarPublica(
        bits: Int,
        strongBox: Boolean,
        generar: () -> KeyPair,
    ): PublicKey? = try {
        generar().public
    } catch (_: StrongBoxUnavailableException) {
        Log.i(
            ETIQUETA_ACORDADO,
            "ACORDADO api=${Build.VERSION.SDK_INT} rsa=$bits strongBox=$strongBox " +
                "resultado=NO_DISPONIBLE motivo=strongbox_rechazo",
        )
        null
    }

    private fun anunciadoStrongBox(): Boolean {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val valor = contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (!valor) {
            Log.i(
                ETIQUETA_ACORDADO,
                "ACORDADO api=${Build.VERSION.SDK_INT} strongBox=true resultado=NOT_TESTED motivo=funcionalidad_no_anunciada",
            )
        }
        return valor
    }

    private fun cifrar(publica: PublicKey, parametros: OAEPParameterSpec): ByteArray =
        Cipher.getInstance(TRANSFORMACION_RSA_ACORDADA).run {
            init(Cipher.ENCRYPT_MODE, publica, parametros)
            doFinal(TEXTO_ACORDADO.toByteArray(Charsets.UTF_8))
        }

    private fun descifrar(privada: PrivateKey, parametros: OAEPParameterSpec, cifrado: ByteArray): Descifrado =
        Cipher.getInstance(TRANSFORMACION_RSA_ACORDADA).run {
            init(Cipher.DECRYPT_MODE, privada, parametros)
            val proveedor = provider.name
            Descifrado(String(doFinal(cifrado), Charsets.UTF_8), proveedor)
        }

    private data class Descifrado(val texto: String, val proveedor: String)

    private fun nivel(privada: PrivateKey): Int =
        KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
            .getKeySpec(privada, KeyInfo::class.java)
            .securityLevel
}
