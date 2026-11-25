package com.securesms.app.identity

import com.securesms.app.integration.Perfil

/**
 * Repositorio de contactos y sus claves públicas.
 *
 * El contenido vive en memoria y se delega en un [AlmacenContactos] tras cada cambio, de modo
 * que sobreviva a la recreación del proceso. Si la carga falla, arranca vacío y lo reporta en
 * [cargaFallida]; si un guardado falla, conserva el estado en memoria y lo reporta en
 * [ultimoFalloDeGuardado] en vez de fingir que persistió.
 */
class RepositorioContactos(private val almacen: AlmacenContactos) {

    private val contactos = mutableMapOf<String, Contacto>()
    private val claves = mutableMapOf<String, ClavePublicaImportada>()

    /** Indica que el contenido guardado no se pudo leer y se arrancó vacío. */
    var cargaFallida: Boolean = false
        private set

    /** Indica que el último cambio no se pudo escribir en el almacén. */
    var ultimoFalloDeGuardado: Boolean = false
        private set

    init {
        runCatching { almacen.cargar() }
            .onSuccess { estado ->
                estado.contactos.forEach { contactos[it.numero] = it }
                estado.claves.forEach { clave ->
                    val contacto = contactos[clave.contacto.numero] ?: return@forEach
                    claves[contacto.numero] = clave.copy(contacto = contacto)
                }
            }
            .onFailure { cargaFallida = true }
    }

    /** Registra o actualiza un contacto sin modificar su clave. */
    fun agregar(contacto: Contacto) {
        contactos[contacto.numero] = contacto
        claves[contacto.numero]?.let { claves[contacto.numero] = it.copy(contacto = contacto) }
        guardarEstado()
    }

    /** Devuelve el contacto registrado con ese número, o `null` si no existe. */
    fun buscar(numero: String): Contacto? = contactos[numero]

    /** Devuelve los contactos registrados en el orden en que se agregaron. */
    fun todos(): List<Contacto> = contactos.values.toList()

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
            .onSuccess { clave ->
                claves[numero] = clave
                guardarEstado()
            }
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
        guardarEstado()
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

    private fun guardarEstado() {
        val estado = EstadoContactos(
            contactos = contactos.values.toList(),
            claves = claves.values.toList(),
        )
        ultimoFalloDeGuardado = almacen.guardar(estado).isFailure
    }
}
