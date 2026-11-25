package com.securesms.app.messaging

/** Puerto para escuchar SMS entrantes mientras esté activa la suscripción. */
interface RegistroSms {
    /** Registra el receptor una sola vez hasta que se cancele. */
    fun registrar(alRecibir: (remitente: String, cuerpo: String) -> Unit)
    fun cancelar()
}
