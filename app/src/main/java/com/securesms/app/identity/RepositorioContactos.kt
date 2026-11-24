package com.securesms.app.identity

import com.securesms.app.integration.Perfil

/**
 * Repositorio en memoria de contactos y sus claves públicas.
 *
 * No persiste: al terminar el proceso se pierde todo. La persistencia llega en el ticket 06.
 */
class RepositorioContactos {

    private val contactos = mutableMapOf<String, Contacto>()
    private val claves = mutableMapOf<String, ClavePublicaImportada>()

    /** Registra o actualiza un contacto sin modificar su clave. */
    fun agregar(contacto: Contacto) {
        contactos[contacto.numero] = contacto
    }

    /** Devuelve el contacto registrado con ese número, o `null` si no existe. */
    fun buscar(numero: String): Contacto? = contactos[numero]

    /** Devuelve la clave pública guardada para ese número, o `null` si no hay. */
    fun claveDe(numero: String): ClavePublicaImportada? = claves[numero]

    /**
     * Importa el material público de [numero] y lo deja sin verificar.
     *
     * Volver a importar descarta cualquier verificación anterior: el material es otro.
     */
    fun importarClave(
        numero: String,
        perfil: Perfil,
        materialBase64: String,
    ): Result<ClavePublicaImportada> {
        val contacto = contactos[numero]
            ?: return Result.failure(IllegalArgumentException("contacto no registrado"))

        return ClavePublicaImportada.crear(contacto, perfil, materialBase64)
            .onSuccess { clave -> claves[numero] = clave }
    }

    /**
     * Marca la clave como verificada si [huellaConfirmada] coincide con la guardada.
     *
     * Devuelve `true` solo cuando la comparación tiene éxito; una huella distinta deja el estado
     * intacto. La confirmación debe llegar por un canal independiente de la importación.
     */
    fun verificarClave(numero: String, huellaConfirmada: String): Boolean {
        val clave = claves[numero] ?: return false
        if (Huella.normalizar(clave.huella) != Huella.normalizar(huellaConfirmada)) return false

        claves[numero] = clave.copy(estado = EstadoClave.VERIFICADA)
        return true
    }

    /** Devuelve el material público en Base64 para exportarlo, o `null` si no hay clave. */
    fun exportarClave(numero: String): String? = claves[numero]?.materialBase64

    /** Indica si el contacto tiene una clave verificada y, por tanto, puede recibir envíos. */
    fun puedeEnviar(numero: String): Boolean = claves[numero]?.habilitaEnvio == true

    /**
     * Estado de confianza de la clave del contacto. Indica si no se ha importado una clave,
     * o si en caso de tener una, da su estado.
     */
    fun estadoDe(numero: String): EstadoClave = claves[numero]?.estado ?: EstadoClave.SIN_IMPORTAR
}
