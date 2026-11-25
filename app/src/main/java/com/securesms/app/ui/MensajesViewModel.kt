// Coordina el transporte SMS y el ensamblado SS1 durante la sesión.
package com.securesms.app.ui

import androidx.lifecycle.ViewModel
import com.securesms.app.identity.RepositorioContactos
import com.securesms.app.integration.CryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.messaging.ClaveEnsamblado
import com.securesms.app.messaging.EstadoEnsamblado
import com.securesms.app.messaging.EstadoSim
import com.securesms.app.messaging.FragmentAssembler
import com.securesms.app.messaging.RegistroSms
import com.securesms.app.messaging.TransporteSms
import com.securesms.app.messaging.encode
import com.securesms.app.messaging.normalizarNumeroSms
import com.securesms.app.messaging.parsear
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class DireccionMensaje { ENVIADO, RECIBIDO }

enum class EstadoMensaje {
    PREPARANDO, CIFRADO, ENVIANDO, ACEPTADO_PARA_ENVIO, INCOMPLETO,
    RECIBIDO_Y_RECONSTRUIDO, DESCIFRADO, EXPIRADO, ERROR,
}

data class ContactoEnvioUi(val numero: String, val nombre: String, val perfil: Perfil)

data class MensajeUi(
    val clave: String,
    val messageId: String?,
    val direccion: DireccionMensaje,
    val numero: String,
    val contacto: String?,
    val texto: String?,
    val perfil: Perfil?,
    val estado: EstadoMensaje,
    val bytesEnvelope: Int? = null,
    val caracteresBase64: Int? = null,
    val cantidadSms: Int? = null,
    val recibidos: Int? = null,
    val total: Int? = null,
    val detalle: String = "",
)

data class MensajesUiState(
    val mensajes: List<MensajeUi> = emptyList(),
    val contactos: List<ContactoEnvioUi> = emptyList(),
    val seleccionado: String? = null,
    val permisoEnviar: Boolean = false,
    val permisoRecibir: Boolean = false,
    val estadoSim: EstadoSim = EstadoSim.LISTA,
    val aviso: String = "",
    val esDemo: Boolean = true,
) {
    val contactoSeleccionado: ContactoEnvioUi?
        get() = contactos.find { it.numero == seleccionado }
}

/** Conserva los mensajes al cambiar de pestaña o recrear la actividad. */
class MensajesViewModel(
    private val repositorio: RepositorioContactos,
    private val gateway: CryptoGateway,
    private val transporte: TransporteSms,
    private val registro: RegistroSms,
    private val ensamblador: FragmentAssembler = FragmentAssembler(System::currentTimeMillis),
) : ViewModel() {
    private val estado = MutableStateFlow(MensajesUiState(esDemo = gateway.esDemo))
    val uiState = estado.asStateFlow()
    private val pendientes = mutableMapOf<ClaveEnsamblado, MensajeUi>()
    private val consumidos = mutableSetOf<ClaveEnsamblado>()

    init {
        registro.registrar(::recibir)
        refrescar()
    }

    override fun onCleared() {
        registro.cancelar()
        super.onCleared()
    }

    fun actualizarPermisos(enviar: Boolean, recibir: Boolean) {
        estado.update { it.copy(permisoEnviar = enviar, permisoRecibir = recibir) }
    }

    fun seleccionar(numero: String) {
        refrescar()
        val seleccion = estado.value.contactos.find { it.numero == numero }
        estado.update {
            it.copy(seleccionado = seleccion?.numero, aviso = if (seleccion == null) {
                "selecciona un contacto con clave verificada"
            } else "")
        }
    }

    /** Actualiza contactos, SIM y vigencia de los ensamblados abiertos. */
    fun refrescar() {
        val contactos = repositorio.todos().mapNotNull { contacto ->
            val clave = repositorio.claveDe(contacto.numero)
                ?.takeIf { it.habilitaEnvio } ?: return@mapNotNull null
            ContactoEnvioUi(contacto.numero, contacto.nombre, clave.perfil)
        }
        val sim = transporte.estadoSim()
        estado.update { actual ->
            actual.copy(
                contactos = contactos,
                seleccionado = actual.seleccionado?.takeIf { numero -> contactos.any { it.numero == numero } },
                estadoSim = sim,
            )
        }
        val grupos = pendientes.iterator()
        while (grupos.hasNext()) {
            val (clave, mensaje) = grupos.next()
            val vigencia = ensamblador.estado(clave.remitente, clave.messageId)
            if (vigencia == null || vigencia == EstadoEnsamblado.EXPIRADO) {
                publicar(mensaje.copy(estado = EstadoMensaje.EXPIRADO))
                ensamblador.descartar(clave.remitente, clave.messageId)
                grupos.remove()
            }
        }
    }

    /** Devuelve true cuando el intento ya quedó representado en la lista. */
    suspend fun enviar(texto: String): Boolean {
        val actual = estado.value
        val contacto = actual.contactoSeleccionado ?: return avisar("selecciona un contacto")
        if (texto.isBlank()) return avisar("escribe un mensaje")
        if (!actual.permisoEnviar) return avisar("falta el permiso para enviar SMS")
        val sim = transporte.estadoSim()
        estado.update { it.copy(estadoSim = sim) }
        if (sim != EstadoSim.LISTA) return avisar("la SIM no esta lista")
        val perfil = repositorio.claveDe(contacto.numero)?.takeIf { it.habilitaEnvio }?.perfil
            ?: return avisar("se requiere una clave verificada")
        val destino = normalizarNumeroSms(contacto.numero) ?: return avisar("numero SMS invalido")
        var mensaje = MensajeUi(
            UUID.randomUUID().toString(), null, DireccionMensaje.ENVIADO, destino,
            contacto.nombre, texto, perfil, EstadoMensaje.PREPARANDO,
        )
        estado.update { it.copy(aviso = "") }
        publicar(mensaje)
        val envelope = gateway.cifrar(texto, perfil).getOrNull()
        val fragmentos = envelope?.let { runCatching { encode(it) }.getOrNull() }
        val primerFragmento = fragmentos?.firstOrNull()?.let(::parsear)
        if (envelope == null || fragmentos == null || primerFragmento == null) {
            publicar(mensaje.copy(estado = EstadoMensaje.ERROR, detalle = "no se pudo preparar el mensaje"))
            return true
        }
        mensaje = mensaje.copy(
            messageId = primerFragmento.messageId,
            estado = EstadoMensaje.CIFRADO,
            bytesEnvelope = envelope.size,
            caracteresBase64 = longitudBase64(envelope),
            cantidadSms = fragmentos.size,
        )
        publicar(mensaje)
        publicar(mensaje.copy(estado = EstadoMensaje.ENVIANDO))
        // Una vez solicitado el envío, registra su resultado aunque se cierre la pantalla.
        withContext(Dispatchers.IO + NonCancellable) {
            val resultado = transporte.enviar(destino, fragmentos)
            publicar(
                if (resultado.isSuccess) mensaje.copy(estado = EstadoMensaje.ACEPTADO_PARA_ENVIO)
                else mensaje.copy(estado = EstadoMensaje.ERROR, detalle = "no se pudo enviar todo el mensaje"),
            )
        }
        return true
    }

    /** Agrupa por remitente e ID y entrega el texto solo tras reconstruir el Envelope. */
    fun recibir(remitente: String, cuerpo: String) {
        if (!cuerpo.startsWith("SS1:")) return
        val fragmento = parsear(cuerpo) ?: return
        if (fragmento.index !in 1..fragmento.count) return
        val clave = ClaveEnsamblado(remitente, fragmento.messageId)
        if (clave in consumidos) return
        val recibido = ensamblador.agregar(remitente, cuerpo)
        val progreso = ensamblador.progreso(remitente, clave.messageId) ?: return
        val numeroNormalizado = normalizarNumeroSms(remitente)
        val contacto = repositorio.todos().find {
            it.numero == remitente || (numeroNormalizado != null && normalizarNumeroSms(it.numero) == numeroNormalizado)
        }
        val mensaje = MensajeUi(
            "recibido|$remitente|${clave.messageId}", clave.messageId, DireccionMensaje.RECIBIDO,
            remitente, contacto?.nombre, null, null, EstadoMensaje.INCOMPLETO,
            cantidadSms = progreso.total, recibidos = progreso.recibidos, total = progreso.total,
        )
        if (recibido != EstadoEnsamblado.COMPLETO) {
            pendientes[clave] = mensaje
            publicar(mensaje)
            return
        }

        val envelope = ensamblador.envelope(remitente, clave.messageId) ?: return
        consumidos.add(clave)
        pendientes.remove(clave)
        ensamblador.descartar(remitente, clave.messageId)
        val reconstruido = mensaje.copy(
            estado = EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO,
            bytesEnvelope = envelope.size,
            caracteresBase64 = longitudBase64(envelope),
        )
        publicar(reconstruido)
        val descifrado = gateway.descifrar(envelope).getOrNull()
        publicar(
            if (descifrado == null) reconstruido.copy(
                estado = EstadoMensaje.ERROR, detalle = "no se pudo descifrar el mensaje",
            ) else reconstruido.copy(
                estado = EstadoMensaje.DESCIFRADO, texto = descifrado.texto, perfil = descifrado.perfil,
            ),
        )
    }

    private fun avisar(aviso: String): Boolean {
        estado.update { it.copy(aviso = aviso) }
        return false
    }

    private fun publicar(mensaje: MensajeUi) {
        estado.update { actual ->
            val mensajes = actual.mensajes
            actual.copy(mensajes = if (mensajes.any { it.clave == mensaje.clave }) {
                mensajes.map { if (it.clave == mensaje.clave) mensaje else it }
            } else listOf(mensaje) + mensajes)
        }
    }

    private fun longitudBase64(envelope: ByteArray): Int = ((envelope.size + 2) / 3) * 4
}
