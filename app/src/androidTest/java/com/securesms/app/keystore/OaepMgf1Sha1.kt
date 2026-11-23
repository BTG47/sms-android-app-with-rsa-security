package com.securesms.app.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/** Transformación RSA-OAEP con SHA-256 como digest del mensaje. */
internal const val TRANSFORMACION_RSA_ACORDADA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

/**
 * Parámetros OAEP acordados: SHA-256 para el mensaje, MGF1 con SHA-1 y PSource vacío.
 * Solo se fija el digest de la máscara; el digest del mensaje no cambia.
 */
internal fun parametrosOaepAcordados(): OAEPParameterSpec = OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA1,
    PSource.PSpecified.DEFAULT,
)

/**
 * Generador de claves RSA en Android Keystore con la configuración acordada.
 *
 * `setMgf1Digests` solo existe desde API 35. En API 31–34 no se declara el tag y se usa
 * el valor por defecto de Keystore para MGF1, que es SHA-1; esta rama es deliberada y
 * documentada, no una omisión. La clave privada permanece dentro de Keystore y nunca
 * se exporta. `strongBox` solo solicita la mejora opcional cuando el dispositivo la anuncia.
 */
internal fun generadorRsa(alias: String, bits: Int, strongBox: Boolean): KeyPairGenerator {
    val especificacion = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(bits)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .apply {
            if (Build.VERSION.SDK_INT >= 35) {
                setMgf1Digests(KeyProperties.DIGEST_SHA1)
            }
            if (strongBox) {
                setIsStrongBoxBacked(true)
            }
        }
        .build()

    return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        .apply { initialize(especificacion) }
}
