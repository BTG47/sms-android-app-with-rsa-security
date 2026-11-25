// Presenta el transporte SMS, los permisos y el historial de la sesión.
package com.securesms.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.securesms.app.messaging.EstadoSim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nombre visible de cada estado del transporte. */
fun etiquetaDe(estado: EstadoMensaje): String = when (estado) {
    EstadoMensaje.PREPARANDO -> "Preparando"
    EstadoMensaje.CIFRADO -> "Cifrado"
    EstadoMensaje.ENVIANDO -> "Enviando"
    EstadoMensaje.ACEPTADO_PARA_ENVIO -> "Aceptado para envio"
    EstadoMensaje.INCOMPLETO -> "Incompleto"
    EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO -> "Recibido y reconstruido"
    EstadoMensaje.DESCIFRADO -> "Descifrado"
    EstadoMensaje.EXPIRADO -> "Expirado"
    EstadoMensaje.ERROR -> "Error"
}

/** Formatea el progreso, los diagnósticos y los errores de una entrada. */
fun detalleDe(mensaje: MensajeUi): String {
    if (mensaje.detalle.isNotEmpty()) return mensaje.detalle
    if (mensaje.estado in listOf(EstadoMensaje.INCOMPLETO, EstadoMensaje.EXPIRADO)) {
        return "${mensaje.recibidos} de ${mensaje.total} fragmentos"
    }
    return listOfNotNull(
        mensaje.perfil?.name,
        mensaje.bytesEnvelope?.let { "$it B" },
        mensaje.caracteresBase64?.let { "$it Base64" },
        mensaje.cantidadSms?.let { "$it SMS" },
    ).joinToString(" | ")
}

/** Permite elegir un contacto verificado y consultar los mensajes de la sesión. */
@Composable
fun PantallaMensajes(viewModel: MensajesViewModel) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var texto by rememberSaveable { mutableStateOf("") }
    var pidioPermisos by rememberSaveable { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    val permisos = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)

    fun sincronizarPermisos(): List<String> {
        val faltantes = permisos.filter { contexto.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        viewModel.actualizarPermisos(
            Manifest.permission.SEND_SMS !in faltantes,
            Manifest.permission.RECEIVE_SMS !in faltantes,
        )
        return faltantes
    }

    val solicitar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        sincronizarPermisos()
    }
    val ajustes = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        sincronizarPermisos()
        viewModel.refrescar()
    }

    LaunchedEffect(Unit) {
        val faltantes = sincronizarPermisos()
        if (!pidioPermisos && faltantes.isNotEmpty()) {
            pidioPermisos = true
            solicitar.launch(faltantes.toTypedArray())
        }
        while (true) {
            viewModel.refrescar()
            delay(10_000)
            sincronizarPermisos()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mensajes")
                if (estado.esDemo) Text("modo demo: el cifrado no es real")
                if (!estado.permisoEnviar || !estado.permisoRecibir) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Otorga los permisos SMS para enviar y recibir mensajes.")
                            TextButton(onClick = {
                                val faltantes = sincronizarPermisos()
                                if (faltantes.isNotEmpty()) solicitar.launch(faltantes.toTypedArray())
                            }) { Text("Solicitar permisos") }
                            TextButton(onClick = {
                                ajustes.launch(Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", contexto.packageName, null),
                                ))
                            }) { Text("Abrir ajustes") }
                        }
                    }
                }
                when (estado.estadoSim) {
                    EstadoSim.AUSENTE -> Text("No se detecta una SIM.")
                    EstadoSim.NO_DISPONIBLE -> Text("La SIM no esta disponible.")
                    EstadoSim.LISTA -> Unit
                }
                if (estado.contactos.isEmpty()) Text("Verifica una clave en Contactos para enviar.")
            }
        }
        items(estado.contactos, key = { "contacto|${it.numero}" }) { contacto ->
            FilterChip(
                selected = estado.seleccionado == contacto.numero,
                onClick = { viewModel.seleccionar(contacto.numero) },
                label = { Text("${contacto.nombre} · ${contacto.numero}") },
            )
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !enviando,
                    label = { Text("Mensaje") },
                )
                Button(
                    enabled = !enviando,
                    onClick = {
                        enviando = true
                        alcance.launch {
                            try {
                                if (viewModel.enviar(texto)) texto = ""
                            } finally {
                                enviando = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Enviar") }
                if (estado.aviso.isNotBlank()) Text(estado.aviso)
            }
        }
        items(estado.mensajes, key = { it.clave }) { mensaje ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val direccion = if (mensaje.direccion == DireccionMensaje.ENVIADO) "Para" else "De"
                    Text("$direccion ${mensaje.contacto ?: mensaje.numero}")
                    mensaje.texto?.let { Text(it) }
                    Text(etiquetaDe(mensaje.estado))
                    val detalle = detalleDe(mensaje)
                    if (detalle.isNotBlank()) Text(detalle)
                }
            }
        }
    }
}
