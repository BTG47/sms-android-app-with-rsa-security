package com.securesms.app.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

private const val ETIQUETA_RSA = "KeystoreSpike"
private const val TRANSFORMACION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
private const val TEXTO = "SecureSMS spike RSA-OAEP"

/**
 * Verifica RSA-OAEP con SHA-256 para el mensaje y para MGF1 usando claves persistentes.
 * No imprime texto cifrado, claves privadas ni material derivado.
 */
@RunWith(AndroidJUnit4::class)
class RsaOaepTest {

    @Test
    fun rsa2048CifraYDescifraConMgf1Sha256() = ejecutar(2048)

    @Test
    fun rsa3072CifraYDescifraConMgf1Sha256() = ejecutar(3072)

    private fun ejecutar(bits: Int) {
        val alias = "spike_rsa_$bits"
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        almacen.deleteEntry(alias)

        val par = generarPar(alias, bits)
        val privada = almacen.getKey(alias, null) as PrivateKey
        val parametros = parametrosOaep()
        val proveedor = Cipher.getInstance(TRANSFORMACION).provider.name
        val nivel = nivelDeSeguridad(privada)

        Log.i(ETIQUETA_RSA, "RSA-$bits proveedor=$proveedor nivel=$nivel mgf1=SHA-256")

        val cifrado = cifrar(par.public, parametros)
        assertEquals(TEXTO, descifrar(privada, parametros, cifrado))

        val recuperada = almacen.getKey(alias, null) as PrivateKey
        assertEquals(TEXTO, descifrar(recuperada, parametros, cifrado))
    }

    private fun generarPar(alias: String, bits: Int): KeyPair {
        val especificacion = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(bits)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256)

        // setMgf1Digests existe desde API 35; ambos dispositivos están por encima.
        if (Build.VERSION.SDK_INT >= 35) {
            especificacion.setMgf1Digests(KeyProperties.DIGEST_SHA256)
        }

        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            .apply { initialize(especificacion.build()) }
            .generateKeyPair()
    }

    private fun parametrosOaep() = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT,
    )

    private fun cifrar(publica: PublicKey, parametros: OAEPParameterSpec): ByteArray =
        Cipher.getInstance(TRANSFORMACION).run {
            init(Cipher.ENCRYPT_MODE, publica, parametros)
            doFinal(TEXTO.toByteArray(Charsets.UTF_8))
        }

    private fun descifrar(privada: PrivateKey, parametros: OAEPParameterSpec, cifrado: ByteArray): String =
        Cipher.getInstance(TRANSFORMACION).run {
            init(Cipher.DECRYPT_MODE, privada, parametros)
            String(doFinal(cifrado), Charsets.UTF_8)
        }

    private fun nivelDeSeguridad(privada: PrivateKey): String {
        val fabrica = KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
        val info = fabrica.getKeySpec(privada, KeyInfo::class.java)
        return info.securityLevel.toString()
    }
}
