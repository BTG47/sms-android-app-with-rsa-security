package com.securesms.app.identity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.securesms.app.integration.Perfil
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Custodia de claves en Android Keystore, con un alias estable por perfil.
 *
 * Los parámetros quedan congelados al generar la clave: RSA usa OAEP con SHA-256 y, desde API 35,
 * MGF1-SHA1 explícito (D-022); las curvas usan el propósito de acuerdo para ECDH. StrongBox se
 * intenta solo si el dispositivo lo anuncia y se degrada por clave si lo rechaza (D-018).
 *
 * La clave privada nunca sale del Keystore: no hay operación que la devuelva, solo su pública.
 */
class AlmacenDeClavesAndroid(private val context: Context) : AlmacenDeClaves {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun tieneClave(perfil: Perfil): Boolean = keyStore.containsAlias(alias(perfil))

    override fun generarSiFalta(perfil: Perfil): Result<Unit> {
        if (tieneClave(perfil)) return Result.success(Unit)
        return runCatching { generar(perfil) }
    }

    override fun clavePublicaBase64(perfil: Perfil): Result<String> = runCatching {
        val certificado = keyStore.getCertificate(alias(perfil))
            ?: error("no hay clave para ${perfil.name}")
        Base64.getEncoder().encodeToString(certificado.publicKey.encoded)
    }

    private fun generar(perfil: Perfil) {
        val anunciaStrongBox = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (!anunciaStrongBox) {
            generarCon(perfil, strongBox = false)
            return
        }

        // D-018: StrongBox es una optimización opcional. El hardware puede anunciarla y aun
        // así rechazar un perfil concreto (se observó con P-384 en el Samsung); se degrada a
        // TEE. Un intento fallido puede dejar un alias a medias, que se limpia antes de repetir.
        val enStrongBox = runCatching { generarCon(perfil, strongBox = true) }
        if (enStrongBox.isSuccess) return

        runCatching {
            if (keyStore.containsAlias(alias(perfil))) keyStore.deleteEntry(alias(perfil))
        }
        generarCon(perfil, strongBox = false)
    }

    private fun generarCon(perfil: Perfil, strongBox: Boolean) {
        val constructor = KeyGenParameterSpec.Builder(alias(perfil), proposito(perfil))

        when (perfil) {
            Perfil.RSA_2048, Perfil.RSA_3072 -> {
                constructor
                    .setKeySize(if (perfil == Perfil.RSA_2048) 2048 else 3072)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                if (Build.VERSION.SDK_INT >= 35) {
                    constructor.setMgf1Digests(KeyProperties.DIGEST_SHA1)
                }
            }

            Perfil.ECC_P256, Perfil.ECC_P384 -> {
                val curva = if (perfil == Perfil.ECC_P256) "secp256r1" else "secp384r1"
                constructor.setAlgorithmParameterSpec(ECGenParameterSpec(curva))
            }
        }

        if (strongBox) constructor.setIsStrongBoxBacked(true)

        KeyPairGenerator.getInstance(algoritmo(perfil), "AndroidKeyStore")
            .apply { initialize(constructor.build()) }
            .generateKeyPair()
    }

    private fun alias(perfil: Perfil): String = "securesms.${perfil.name.lowercase()}.v1"

    private fun proposito(perfil: Perfil): Int = when (perfil) {
        Perfil.RSA_2048, Perfil.RSA_3072 ->
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT

        Perfil.ECC_P256, Perfil.ECC_P384 -> KeyProperties.PURPOSE_AGREE_KEY
    }

    private fun algoritmo(perfil: Perfil): String = when (perfil) {
        Perfil.RSA_2048, Perfil.RSA_3072 -> KeyProperties.KEY_ALGORITHM_RSA
        Perfil.ECC_P256, Perfil.ECC_P384 -> KeyProperties.KEY_ALGORITHM_EC
    }
}
