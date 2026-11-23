package com.securesms.app.messaging

/** Reloj controlado para probar la expiración sin esperar tiempo real. */
class RelojFalso(private var valor: Long = 0L) {

    fun ahora(): Long = valor

    fun avanzar(ms: Long) {
        valor += ms
    }
}
