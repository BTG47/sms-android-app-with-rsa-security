package com.securesms.app.ui

import androidx.lifecycle.ViewModel
import com.securesms.app.identity.Contacto
import com.securesms.app.identity.EstadoClave
import com.securesms.app.identity.RepositorioContactos
import com.securesms.app.integration.Perfil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resumen de un contacto para la lista de la pantalla. */
data class FilaContacto(
    val numero: String,
    val nombre: String,
    val estado: EstadoClave,
    val perfil: Perfil?,
    val huella: String?,
)

/**
 * Estado que consume la pantalla de contactos.
 *
 * [exportado] solo contiene material público. Nunca se guardan claves privadas ni texto plano.
 */
data class ContactosUiState(
    val filas: List<FilaContacto> = emptyList(),
    val seleccionado: String? = null,
    val exportado: String? = null,
    val aviso: String = "",
) {

    /** Contacto marcado en la lista, si todavía existe. */
    val contactoSeleccionado: FilaContacto?
        get() = filas.firstOrNull { it.numero == seleccionado }
}

/**
 * ViewModel de contactos y claves públicas en memoria.
 *
 * Importar nunca verifica: solo [verificar] cambia el estado, y únicamente cuando la huella
 * confirmada por otro canal coincide con la guardada (ADR-0001).
 */
class ContactosViewModel(
    private val repositorio: RepositorioContactos = RepositorioContactos(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactosUiState())

    /** Estado actual de la pantalla. */
    val uiState: StateFlow<ContactosUiState> = _uiState.asStateFlow()

    /** Agrega un contacto y lo deja seleccionado. */
    fun agregar(numero: String, nombre: String) {
        val limpio = numero.trim()
        if (limpio.isBlank()) {
            publicar(aviso = "escribe un numero")
            return
        }

        repositorio.agregar(Contacto(limpio, nombre.trim().ifBlank { limpio }))
        publicar(seleccionado = limpio)
    }

    /** Selecciona el contacto cuyo detalle se muestra. */
    fun seleccionar(numero: String) {
        publicar(seleccionado = numero)
    }

    /** Importa el material público y lo deja sin verificar. */
    fun importar(numero: String, perfil: Perfil, materialBase64: String) {
        repositorio.importarClave(numero, perfil, materialBase64).fold(
            onSuccess = {
                publicar(
                    seleccionado = numero,
                    aviso = "clave importada; aun no esta verificada",
                )
            },
            onFailure = {
                publicar(seleccionado = numero, aviso = "no se pudo importar la clave")
            },
        )
    }

    /** Verifica la clave solo si la huella confirmada coincide con la guardada. */
    fun verificar(numero: String, huellaConfirmada: String) {
        if (repositorio.claveDe(numero) == null) {
            publicar(seleccionado = numero, aviso = "no hay clave importada")
            return
        }

        val coincide = repositorio.verificarClave(numero, huellaConfirmada)
        publicar(
            seleccionado = numero,
            aviso = if (coincide) "clave verificada" else "la huella no coincide",
        )
    }

    /** Publica el material público en Base64 para compararlo fuera de la app. */
    fun exportar(numero: String) {
        val material = repositorio.exportarClave(numero)
        publicar(
            seleccionado = numero,
            exportado = material,
            aviso = if (material == null) "no hay clave para exportar" else "clave exportada",
        )
    }

    /** Indica si el contacto seleccionado puede recibir envíos. */
    fun puedeEnviar(numero: String): Boolean = repositorio.puedeEnviar(numero)

    private fun publicar(
        seleccionado: String? = _uiState.value.seleccionado,
        exportado: String? = null,
        aviso: String = "",
    ) {
        _uiState.value = ContactosUiState(
            filas = repositorio.todos().map { contacto ->
                val clave = repositorio.claveDe(contacto.numero)
                FilaContacto(
                    numero = contacto.numero,
                    nombre = contacto.nombre,
                    estado = repositorio.estadoDe(contacto.numero),
                    perfil = clave?.perfil,
                    huella = clave?.huella,
                )
            },
            seleccionado = seleccionado,
            exportado = exportado,
            aviso = aviso,
        )
    }
}
