package com.securesms.app.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony

/** Escucha la acción protegida de telefonía durante la suscripción. */
class RegistroSmsAndroid(context: Context) : RegistroSms {
    private val contexto = context.applicationContext
    private var receptor: BroadcastReceiver? = null

    override fun registrar(alRecibir: (remitente: String, cuerpo: String) -> Unit) {
        if (receptor != null) return
        val accionSms = Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        val nuevo = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != accionSms) return
                Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { mensaje ->
                    val remitente = mensaje?.originatingAddress ?: return@forEach
                    val cuerpo = mensaje.messageBody ?: return@forEach
                    alRecibir(remitente, cuerpo)
                }
            }
        }
        val filtro = IntentFilter(accionSms)
        // Telefonía puede emitir esta acción protegida desde un UID distinto al del sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            contexto.registerReceiver(nuevo, filtro, Context.RECEIVER_EXPORTED)
        } else {
            contexto.registerReceiver(nuevo, filtro)
        }
        receptor = nuevo
    }

    override fun cancelar() {
        val activo = receptor ?: return
        contexto.unregisterReceiver(activo)
        receptor = null
    }
}
