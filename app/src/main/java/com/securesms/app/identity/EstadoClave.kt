package com.securesms.app.identity

/** Nivel de confianza de la clave pública de un contacto. */
enum class EstadoClave {
    /** El contacto no tiene clave pública registrada */
    SIN_IMPORTAR,
    /** El contacto cuenta con una clave pública, pero no ha sido verificada */
    IMPORTADA_SIN_VERIFICAR,
    /** El contacto tiene una clave pública importada y verificada*/
    VERIFICADA,
}