// Conecta el receptor del manifiesto con la sesión de mensajes en el hilo principal.
package com.securesms.app.messaging

import android.os.SystemClock

/** Entrega al ViewModel los fragmentos recibidos antes y durante el inicio de la sesión. */
class RegistroSmsAndroid : RegistroSms {
    override fun registrar(alRecibir: (remitente: String, cuerpo: String) -> Unit) {
        RegistroSmsEntrantes.suscribir(alRecibir)
    }

    override fun cancelar() {
        RegistroSmsEntrantes.cancelar()
    }
}

/** Conserva temporalmente fragmentos cifrados mientras no hay ViewModel suscrito. */
internal object RegistroSmsEntrantes {
    private const val MAX_PENDIENTES = 16 * 99
    private const val VIGENCIA_MS = 5 * 60 * 1000L

    private data class Pendiente(val remitente: String, val cuerpo: String, val recibidoEn: Long)

    private val pendientes = ArrayDeque<Pendiente>()
    private var listener: ((String, String) -> Unit)? = null

    fun suscribir(alRecibir: (String, String) -> Unit) {
        if (listener != null) return
        listener = alRecibir
        purgarVencidos(SystemClock.elapsedRealtime())
        while (pendientes.isNotEmpty()) {
            val sms = pendientes.removeFirst()
            alRecibir(sms.remitente, sms.cuerpo)
        }
    }

    fun cancelar() {
        listener = null
    }

    fun entregar(remitente: String, cuerpo: String) {
        val activo = listener
        if (activo != null) {
            activo(remitente, cuerpo)
            return
        }

        val ahora = SystemClock.elapsedRealtime()
        purgarVencidos(ahora)
        if (pendientes.size == MAX_PENDIENTES) pendientes.removeFirst()
        pendientes.addLast(Pendiente(remitente, cuerpo, ahora))
    }

    private fun purgarVencidos(ahora: Long) {
        while (pendientes.firstOrNull()?.let { ahora - it.recibidoEn >= VIGENCIA_MS } == true) {
            pendientes.removeFirst()
        }
    }
}
