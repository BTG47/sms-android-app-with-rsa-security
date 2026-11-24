package com.securesms.app.identity

import com.securesms.app.integration.Perfil

/**
 * Clave pública de un [Contacto] para un [Perfil] concreto, con su huella y su nivel de confianza.
 *
 * Importar solo produce el estado [EstadoClave.IMPORTADA_SIN_VERIFICAR]. La verificación exige
 * comparar la huella por un canal independiente (ADR-0001); nunca se asume por haber importado.
 */
data class ClavePublicaImportada(
    val contacto: Contacto,
    val perfil: Perfil,
    val materialBase64: String,
    val huella: String,
    val estado: EstadoClave,
) {

    /** Solo una clave verificada habilita el envío. */
    val habilitaEnvio: Boolean get() = estado == EstadoClave.VERIFICADA

    companion object {

        /** Construye la clave desde el material público y la deja sin verificar. */
        fun crear(
            contacto: Contacto,
            perfil: Perfil,
            materialBase64: String,
        ): Result<ClavePublicaImportada> =
            Huella.calcular(materialBase64).map { huella ->
                ClavePublicaImportada(
                    contacto = contacto,
                    perfil = perfil,
                    materialBase64 = materialBase64.filterNot { it.isWhitespace() },
                    huella = huella,
                    estado = EstadoClave.IMPORTADA_SIN_VERIFICAR,
                )
            }
    }
}
