package com.securesms.app.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

private const val ETIQUETA = "KeystoreSpike"
private const val TRANSFORMACION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
private const val TEXTO = "SecureSMS diagnostico MGF1"

/**
 * Escenario controlado: el digest del mensaje se mantiene en SHA-256 y varía solo
 * la declaración de MGF1 en la clave y en la operación. `mgf1DeclaradoEnClave`
 * equivale a null cuando no se llama a setMgf1Digests.
 */
private data class Escenario(
    val nombre: String,
    val mgf1DeclaradoEnClave: String?,
    val mgf1Solicitado: String,
    val especificacionMgf1: MGF1ParameterSpec,
)

private val CON_TAG_SHA256 = Escenario(
    "con_tag_sha256",
    KeyProperties.DIGEST_SHA256,
    "SHA-256",
    MGF1ParameterSpec.SHA256,
)

private val CON_TAG_SHA1 = Escenario(
    "con_tag_sha1",
    KeyProperties.DIGEST_SHA1,
    "SHA-1",
    MGF1ParameterSpec.SHA1,
)

private val SIN_TAG_SHA256 = Escenario(
    "sin_tag_sha256",
    null,
    "SHA-256",
    MGF1ParameterSpec.SHA256,
)

private val SIN_TAG_SHA1 = Escenario(
    "sin_tag_sha1",
    null,
    "SHA-1",
    MGF1ParameterSpec.SHA1,
)

private data class Resultado(val exito: Boolean, val proveedor: String, val detalle: String)

/**
 * Diagnóstico observacional de MGF1 en RSA-OAEP. Las pruebas registran resultados
 * y excepciones en lugar de fallar cuando la operación es rechazada: el objetivo es
 * obtener la matriz completa en una sola corrida. No hay cambios en producción ni
 * en el contrato v0.2. KeyInfo no expone el digest de MGF1, por lo que el parámetro
 * efectivo se infiere comparando escenarios, nunca se asume.
 */
@RunWith(AndroidJUnit4::class)
class RsaMgf1DiagnosticoTest {

    @Test
    fun rsa2048ConTagSha256() = verificar(2048, CON_TAG_SHA256)

    @Test
    fun rsa3072ConTagSha256() = verificar(3072, CON_TAG_SHA256)

    @Test
    fun rsa2048ConTagSha1() = verificar(2048, CON_TAG_SHA1)

    @Test
    fun rsa3072ConTagSha1() = verificar(3072, CON_TAG_SHA1)

    @Test
    fun rsa2048SinTagSha256() = verificar(2048, SIN_TAG_SHA256)

    @Test
    fun rsa3072SinTagSha256() = verificar(3072, SIN_TAG_SHA256)

    @Test
    fun rsa2048SinTagSha1() = verificar(2048, SIN_TAG_SHA1)

    @Test
    fun rsa3072SinTagSha1() = verificar(3072, SIN_TAG_SHA1)

    private fun verificar(bits: Int, escenario: Escenario) {
        val resultado = diagnosticar(bits, escenario)
        assertTrue("El diagnóstico debe registrar un resultado", resultado.detalle.isNotBlank())
    }

    private fun diagnosticar(bits: Int, escenario: Escenario): Resultado {
        val alias = "spike_diag_${bits}_${escenario.nombre}"
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        almacen.deleteEntry(alias)

        val par = generarPar(alias, bits, escenario)
        val privada = almacen.getKey(alias, null) as PrivateKey
        val (nivel, digestsClave) = informacion(privada)
        val parametros = parametros(escenario)

        val cifrado = cifrar(par.public, parametros)
        val descifrado = if (cifrado.ciphertext != null) {
            descifrar(privada, parametros, cifrado.ciphertext)
        } else {
            Resultado(false, "-", "sin ciphertext")
        }

        val recuperada = almacen.getKey(alias, null) as PrivateKey
        val redescifrado = if (cifrado.ciphertext != null) {
            descifrar(recuperada, parametros, cifrado.ciphertext)
        } else {
            Resultado(false, "-", "sin ciphertext")
        }

        Log.i(
            ETIQUETA,
            "DIAG api=${Build.VERSION.SDK_INT} rsa=$bits escenario=${escenario.nombre} " +
                "mgf1Clave=${escenario.mgf1DeclaradoEnClave ?: "ausente"} " +
                "mgf1Op=${escenario.mgf1Solicitado} digestsClave=$digestsClave nivel=$nivel " +
                "cifrar=${cifrado.resultado.exito} proveedorCifrar=${cifrado.resultado.proveedor} " +
                "descifrar=${descifrado.exito} proveedorDescifrar=${descifrado.proveedor} " +
                "recuperada=${redescifrado.exito}",
        )
        Log.i(
            ETIQUETA,
            "DIAG_EX rsa=$bits escenario=${escenario.nombre} " +
                "cifrar=${cifrado.resultado.detalle} descifrar=${descifrado.detalle} " +
                "recuperada=${redescifrado.detalle}",
        )

        return descifrado
    }

    private fun generarPar(alias: String, bits: Int, escenario: Escenario): KeyPair {
        val especificacion = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(bits)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256)

        escenario.mgf1DeclaradoEnClave?.let { declarado ->
            check(Build.VERSION.SDK_INT >= 35) { "setMgf1Digests requiere API 35" }
            especificacion.setMgf1Digests(declarado)
        }

        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            .apply { initialize(especificacion.build()) }
            .generateKeyPair()
    }

    private fun parametros(escenario: Escenario) = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        escenario.especificacionMgf1,
        PSource.PSpecified.DEFAULT,
    )

    private data class Cifrado(val resultado: Resultado, val ciphertext: ByteArray?)

    private fun cifrar(publica: PublicKey, parametros: OAEPParameterSpec): Cifrado {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, publica, parametros)
            val proveedor = cipher.provider.name
            val ciphertext = cipher.doFinal(TEXTO.toByteArray(Charsets.UTF_8))
            Cifrado(Resultado(true, proveedor, "ok"), ciphertext)
        } catch (e: Exception) {
            Cifrado(Resultado(false, cipher.provider.name, describir(e)), null)
        }
    }

    private fun descifrar(privada: PrivateKey, parametros: OAEPParameterSpec, ciphertext: ByteArray): Resultado {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        return try {
            cipher.init(Cipher.DECRYPT_MODE, privada, parametros)
            val proveedor = cipher.provider.name
            val texto = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            if (texto == TEXTO) Resultado(true, proveedor, "ok")
            else Resultado(false, proveedor, "plaintext distinto")
        } catch (e: Exception) {
            Resultado(false, cipher.provider.name, describir(e))
        }
    }

    private fun informacion(privada: PrivateKey): Pair<Int, String> {
        val info = KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
            .getKeySpec(privada, KeyInfo::class.java)
        return info.securityLevel to info.digests.joinToString(",")
    }

    /** Describe la cadena completa de excepciones y redacta el identificador interno de clave. */
    private fun describir(error: Throwable): String {
        val partes = mutableListOf<String>()
        var actual: Throwable? = error
        while (actual != null && partes.size < 8) {
            partes += "${actual.javaClass.name}: ${actual.message}"
            actual = actual.cause
        }
        return partes.joinToString(" | ").replace(Regex("KeyIdGuard\\(\\d+\\)"), "KeyIdGuard(REDACTADO)")
    }
}
