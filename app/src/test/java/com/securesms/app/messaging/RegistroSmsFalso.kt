package com.securesms.app.messaging

/** Doble de prueba que permite simular la entrega de SMS al receptor activo. */
class RegistroSmsFalso : RegistroSms {
    private var receptor: ((String, String) -> Unit)? = null

    override fun registrar(alRecibir: (remitente: String, cuerpo: String) -> Unit) {
        if (receptor == null) receptor = alRecibir
    }

    override fun cancelar() {
        receptor = null
    }

    fun simular(remitente: String, cuerpo: String) {
        receptor?.invoke(remitente, cuerpo)
    }
}
