package com.securesms.app.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

private const val ETIQUETA_ECDH = "KeystoreSpike"

private data class Curva(val nombre: String, val etiqueta: String)

private val P256 = Curva("secp256r1", "P-256")
private val P384 = Curva("secp384r1", "P-384")

private data class Acuerdo(val bytes: ByteArray, val proveedor: String)

/**
 * Verifica ECDH real con dos pares persistentes por curva, en ambos sentidos y
 * después de recuperar las claves privadas de Android Keystore. Solo registra
 * longitudes, proveedores y niveles de seguridad: nunca imprime el secreto.
 */
@RunWith(AndroidJUnit4::class)
class EcdhTest {

    @Test
    fun ecdhP256DosParesObtienenElMismoSecreto() = verificar(P256)

    @Test
    fun ecdhP384DosParesObtienenElMismoSecreto() = verificar(P384)

    private fun verificar(curva: Curva) {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliasA = "spike_ecdh_${curva.nombre}_a"
        val aliasB = "spike_ecdh_${curva.nombre}_b"
        almacen.deleteEntry(aliasA)
        almacen.deleteEntry(aliasB)

        val parA = generarPar(aliasA, curva)
        val parB = generarPar(aliasB, curva)
        val privadaA = almacen.getKey(aliasA, null) as PrivateKey
        val privadaB = almacen.getKey(aliasB, null) as PrivateKey

        val acuerdoA = acuerdo(privadaA, parB.public)
        val acuerdoB = acuerdo(privadaB, parA.public)
        assertArrayEquals(
            "Los dos participantes deben obtener el mismo secreto con ${curva.etiqueta}",
            acuerdoA.bytes,
            acuerdoB.bytes,
        )

        val recuperadaA = almacen.getKey(aliasA, null) as PrivateKey
        val recuperadaB = almacen.getKey(aliasB, null) as PrivateKey
        val acuerdoRecuperadoA = acuerdo(recuperadaA, parB.public)
        val acuerdoRecuperadoB = acuerdo(recuperadaB, parA.public)
        assertArrayEquals(acuerdoA.bytes, acuerdoRecuperadoA.bytes)
        assertArrayEquals(acuerdoB.bytes, acuerdoRecuperadoB.bytes)

        Log.i(
            ETIQUETA_ECDH,
            "ECDH api=${Build.VERSION.SDK_INT} curva=${curva.etiqueta} " +
                "proveedorA=${acuerdoA.proveedor} proveedorB=${acuerdoB.proveedor} " +
                "nivelA=${nivel(privadaA)} nivelB=${nivel(privadaB)} " +
                "longitudSecreto=${acuerdoA.bytes.size} resultado=PASS",
        )
    }

    private fun generarPar(alias: String, curva: Curva): KeyPair {
        val especificacion = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
            .setAlgorithmParameterSpec(ECGenParameterSpec(curva.nombre))
            .build()

        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply { initialize(especificacion) }
            .generateKeyPair()
    }

    private fun acuerdo(privada: PrivateKey, publica: PublicKey): Acuerdo {
        val keyAgreement = KeyAgreement.getInstance("ECDH", "AndroidKeyStore")
        keyAgreement.init(privada)
        val proveedor = keyAgreement.provider.name
        keyAgreement.doPhase(publica, true)
        return Acuerdo(keyAgreement.generateSecret(), proveedor)
    }

    private fun nivel(privada: PrivateKey): Int =
        KeyFactory.getInstance(privada.algorithm, "AndroidKeyStore")
            .getKeySpec(privada, KeyInfo::class.java)
            .securityLevel
}
