package com.securesms.app.keystore

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

private const val ETIQUETA_STRONGBOX = "KeystoreSpike"
private const val TRANSFORMACION_RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
private const val TEXTO = "SecureSMS strongbox"

/**
 * Comprueba StrongBox como optimización opcional (D-018). Si el dispositivo no
 * anuncia la funcionalidad o el hardware la rechaza, registra el motivo sin
 * fallar el ticket. Nunca degrada a una clave sin protección.
 */
@RunWith(AndroidJUnit4::class)
class StrongBoxTest {

    @Test
    fun strongBoxRsaOaepMgf1Sha256() {
        if (!anunciado()) return

        val alias = "spike_strongbox_rsa_2048"
        val almacen = almacen()
        almacen.deleteEntry(alias)

        val resultado = try {
            val especificacion = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .apply { if (Build.VERSION.SDK_INT >= 35) setMgf1Digests(KeyProperties.DIGEST_SHA256) }
                .setIsStrongBoxBacked(true)
                .build()

            val par = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                .apply { initialize(especificacion) }
                .generateKeyPair()
            val privada = almacen.getKey(alias, null) as PrivateKey
            val parametros = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            )

            val cifrador = Cipher.getInstance(TRANSFORMACION_RSA)
            cifrador.init(Cipher.ENCRYPT_MODE, par.public, parametros)
            val cifrado = cifrador.doFinal(TEXTO.toByteArray(Charsets.UTF_8))

            val descifrador = Cipher.getInstance(TRANSFORMACION_RSA)
            descifrador.init(Cipher.DECRYPT_MODE, privada, parametros)
            val proveedor = descifrador.provider.name
            val plano = String(descifrador.doFinal(cifrado), Charsets.UTF_8)

            "PASS nivel=${nivel(privada)} proveedor=$proveedor coincidente=${plano == TEXTO}"
        } catch (e: StrongBoxUnavailableException) {
            "NO_DISPONIBLE ${e.javaClass.name}: ${e.message}"
        } catch (e: Exception) {
            "FAIL ${describir(e)}"
        }

        registrar("RSA-2048-OAEP-MGF1-SHA256", resultado)
    }

    @Test
    fun strongBoxEcP256() {
        if (!anunciado()) return

        val alias = "spike_strongbox_ec_secp256r1"
        val contraparte = "spike_strongbox_ec_normal"
        val almacen = almacen()
        almacen.deleteEntry(alias)
        almacen.deleteEntry(contraparte)

        val resultado = try {
            val especificacion = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setIsStrongBoxBacked(true)
                .build()
            val par = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(especificacion) }
                .generateKeyPair()

            val especificacionNormal = KeyGenParameterSpec.Builder(contraparte, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .build()
            val otra = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(especificacionNormal) }
                .generateKeyPair()

            val privada = almacen.getKey(alias, null) as PrivateKey
            val secretoA = acuerdo(privada, otra.public)
            val secretoB = acuerdo(otra.private, par.public)

            "PASS nivel=${nivel(privada)} coincidente=${secretoA.contentEquals(secretoB)} longitud=${secretoA.size}"
        } catch (e: StrongBoxUnavailableException) {
            "NO_DISPONIBLE ${e.javaClass.name}: ${e.message}"
        } catch (e: Exception) {
            "FAIL ${describir(e)}"
        }

        registrar("EC-P256-AGREE_KEY", resultado)
    }

    private fun anunciado(): Boolean {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val valor = contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        Log.i(ETIQUETA_STRONGBOX, "STRONGBOX api=${Build.VERSION.SDK_INT} anunciado=$valor")
        if (!valor) {
            registrar("general", "NOT_TESTED motivo=funcionalidad_no_anunciada")
        }
        return valor
    }

    private fun almacen(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun acuerdo(privada: PrivateKey, publica: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH", "AndroidKeyStore")
        keyAgreement.init(privada)
        keyAgreement.doPhase(publica, true)
        return keyAgreement.generateSecret()
    }

    private fun nivel(privada: PrivateKey): Int =
        KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
            .getKeySpec(privada, KeyInfo::class.java)
            .securityLevel

    private fun registrar(algoritmo: String, resultado: String) {
        Log.i(ETIQUETA_STRONGBOX, "STRONGBOX api=${Build.VERSION.SDK_INT} algoritmo=$algoritmo resultado=$resultado")
    }

    private fun describir(error: Throwable): String =
        "${error.javaClass.name}: ${error.message}".replace(Regex("KeyIdGuard\\(\\d+\\)"), "KeyIdGuard(REDACTADO)")
}
