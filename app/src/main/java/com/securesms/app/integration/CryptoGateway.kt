/*
 * Frontera de cifrado del frente Android. No depende de la biblioteca de Persona 2.
 */

package com.securesms.app.integration

import com.securesms.app.identity.ClavePublicaImportada

/**
 * Configuraciones asimétricas que la aplicación puede seleccionar.
 *
 * Cada valor nombra una combinación completa de algoritmos, no un algoritmo aislado.
 * Los nombres se muestran tal cual en el resumen de diagnóstico.
 */
enum class Perfil {
    RSA_2048,
    RSA_3072,
    ECC_P256,
    ECC_P384,
}

/**
 * Texto recuperado de un Envelope junto con el perfil que lo produjo.
 *
 * Solo lo devuelve una implementación que puede invertir el cifrado; el perfil permite
 * mostrar el diagnóstico sin volver a interpretar el contenido.
 */
data class MensajeDescifrado(
    val texto: String,
    val perfil: Perfil,
)

/**
 * Fachada de cifrado usada por la interfaz de usuario.
 *
 * Es una frontera nuestra: ningún tipo de la biblioteca criptográfica cruza esta interfaz.
 * El diagnóstico conserva su implementación demo y Mensajes usa las claves del contacto.
 */
interface CryptoGateway {

    /** Indica si la implementación es una demostración y no contiene criptografía real. */
    val esDemo: Boolean

    /**
     * Cifra [texto] con la configuración [perfil] y devuelve los bytes opacos del Envelope.
     *
     * El texto plano no se conserva en el resultado ni se expone en caso de fallo.
     */
    fun cifrar(texto: String, perfil: Perfil): Result<ByteArray>

    /** Cifra usando la clave pública del contacto seleccionado. */
    fun cifrar(texto: String, clave: ClavePublicaImportada): Result<ByteArray> =
        cifrar(texto, clave.perfil)

    /**
     * Recupera el texto y el perfil de un Envelope producido por esta misma implementación.
     *
     * Devuelve un fallo, sin exponer el contenido ni la causa técnica, si el Envelope es ajeno,
     * está truncado o no se puede interpretar como UTF-8.
     */
    fun descifrar(envelope: ByteArray): Result<MensajeDescifrado>
}
