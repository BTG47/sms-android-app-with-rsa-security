// Recibe la notificación protegida de telefonía y extrae los fragmentos cifrados.
package com.securesms.app.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/** Android puede crear este receptor aunque aún no exista MainActivity. */
class ReceptorSmsAndroid : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val mensajes = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val porRemitente = mensajes.mapNotNull { mensaje ->
            val remitente = mensaje?.originatingAddress ?: return@mapNotNull null
            val cuerpo = mensaje.messageBody ?: return@mapNotNull null
            remitente to cuerpo
        }.groupBy({ it.first }, { it.second })

        var fragmentos = 0
        porRemitente.forEach { (remitente, partes) ->
            fragmentosSmsEntrantes(partes).forEach { cuerpo ->
                RegistroSmsEntrantes.entregar(remitente, cuerpo)
                fragmentos++
            }
        }
        Log.d("SecureSmsRx", "SMS broadcast: ${mensajes.size} PDU(s), $fragmentos SS1 fragment(s)")
    }
}
