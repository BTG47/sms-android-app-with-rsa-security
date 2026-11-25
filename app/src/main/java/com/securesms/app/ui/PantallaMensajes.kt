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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.securesms.app.messaging.EstadoSim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nombre visible de cada estado del transporte. */
fun etiquetaDe(estado: EstadoMensaje): String = when (estado) {
    EstadoMensaje.PREPARANDO -> "Preparing"
    EstadoMensaje.CIFRADO -> "Encrypted"
    EstadoMensaje.ENVIANDO -> "Sending"
    EstadoMensaje.ACEPTADO_PARA_ENVIO -> "Accepted for sending"
    EstadoMensaje.INCOMPLETO -> "Incomplete"
    EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO -> "Received and reassembled"
    EstadoMensaje.DESCIFRADO -> "Decrypted"
    EstadoMensaje.EXPIRADO -> "Expired"
    EstadoMensaje.ERROR -> "Error"
}

/** Formatea el progreso, los diagnósticos y los errores de una entrada. */
fun detalleDe(mensaje: MensajeUi): String {
    if (mensaje.detalle.isNotEmpty()) return mensaje.detalle
    if (mensaje.estado in listOf(EstadoMensaje.INCOMPLETO, EstadoMensaje.EXPIRADO)) {
        return "${mensaje.recibidos} of ${mensaje.total} fragments"
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
fun PantallaMensajes(viewModel: MensajesViewModel, onAbrirContactos: () -> Unit) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var texto by rememberSaveable { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var verRecibidos by rememberSaveable { mutableStateOf(false) }
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
        sincronizarPermisos()
        while (true) {
            viewModel.refrescar()
            delay(10_000)
            sincronizarPermisos()
        }
    }

    val direccionVisible = if (verRecibidos) DireccionMensaje.RECIBIDO else DireccionMensaje.ENVIADO
    val historial = estado.mensajes.filter { it.direccion == direccionVisible }
    val faltaPermiso = if (verRecibidos) !estado.permisoRecibir else !estado.permisoEnviar
    val permisoActual = if (verRecibidos) Manifest.permission.RECEIVE_SMS else Manifest.permission.SEND_SMS

    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Messages", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !verRecibidos, onClick = { verRecibidos = false }, label = { Text("Send") })
                    FilterChip(selected = verRecibidos, onClick = { verRecibidos = true }, label = { Text("Received") })
                }
                if (estado.esDemo) Text("Demo mode: simulated encryption")
                if (faltaPermiso) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (verRecibidos) "Allow SMS access to receive encrypted messages."
                                else "Allow SMS access to send encrypted messages.")
                            TextButton(onClick = {
                                val faltantes = sincronizarPermisos()
                                if (permisoActual in faltantes) solicitar.launch(arrayOf(permisoActual))
                            }) { Text("Grant SMS permission") }
                            TextButton(onClick = {
                                ajustes.launch(Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", contexto.packageName, null),
                                ))
                            }) { Text("Open app settings") }
                        }
                    }
                }
                if (!verRecibidos) {
                    when (estado.estadoSim) {
                        EstadoSim.AUSENTE -> Text("No SIM detected.")
                        EstadoSim.NO_DISPONIBLE -> Text("The SIM is unavailable.")
                        EstadoSim.LISTA -> Unit
                    }
                    if (estado.contactos.isEmpty()) {
                        Text("Add a contact and verify their public key to send them an encrypted SMS.")
                        TextButton(onClick = onAbrirContactos) { Text("Open Contacts") }
                    } else {
                        SelectorDestinatario(
                            contactos = estado.contactos,
                            seleccionado = estado.contactoSeleccionado,
                            habilitado = !enviando,
                            onSeleccionar = viewModel::seleccionar,
                        )
                    }
                    estado.contactoSeleccionado?.let { contacto ->
                        Text("Encryption profile: ${contacto.perfil.name}")
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !enviando,
                            label = { Text("Message") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        Button(
                            enabled = !enviando && texto.isNotBlank() &&
                                estado.permisoEnviar && estado.estadoSim == EstadoSim.LISTA,
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
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (enviando) "Sending…" else "Send encrypted SMS") }
                    }
                    if (estado.aviso.isNotBlank()) Text(estado.aviso)
                }
            }
        }
        item {
            Text(
                if (verRecibidos) "Received this session" else "Sent this session",
                style = MaterialTheme.typography.titleMedium,
            )
            if (historial.isEmpty()) {
                Text(if (verRecibidos) "Incoming encrypted SMS will appear here."
                    else "Outgoing SMS attempts will appear here.")
            }
        }
        items(historial, key = { it.clave }) { mensaje ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val direccion = if (mensaje.direccion == DireccionMensaje.ENVIADO) "To" else "From"
                    val numeroConNombre = mensaje.contacto?.let { "$it · ${mensaje.numero}" } ?: mensaje.numero
                    Text("$direccion $numeroConNombre", style = MaterialTheme.typography.titleSmall)
                    if (mensaje.direccion == DireccionMensaje.RECIBIDO && mensaje.contacto == null) {
                        Text("Number not saved in Contacts", style = MaterialTheme.typography.bodySmall)
                    }
                    mensaje.texto?.let { Text(it) }
                    Text(etiquetaDe(mensaje.estado), style = MaterialTheme.typography.labelLarge)
                    val total = mensaje.total
                    val recibidos = mensaje.recibidos
                    if (mensaje.estado == EstadoMensaje.INCOMPLETO && total != null && total > 0 &&
                        recibidos != null && recibidos < total
                    ) {
                        LinearProgressIndicator(
                            progress = { recibidos.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                    if (mensaje.estado == EstadoMensaje.ACEPTADO_PARA_ENVIO) {
                        Text("Android accepted the send request. Delivery is unconfirmed.")
                    }
                    val detalle = detalleDe(mensaje)
                    if (detalle.isNotBlank()) Text(detalle)
                }
            }
        }
    }
}

/** Mantiene visible el número de destino y limita la selección a contactos verificados. */
@Composable
private fun SelectorDestinatario(
    contactos: List<ContactoEnvioUi>,
    seleccionado: ContactoEnvioUi?,
    habilitado: Boolean,
    onSeleccionar: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(seleccionado?.let { "To ${it.nombre} · ${it.numero}" } ?: "Choose recipient")
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            contactos.forEach { contacto ->
                DropdownMenuItem(
                    text = { Text("${contacto.nombre} · ${contacto.numero}") },
                    onClick = {
                        abierto = false
                        onSeleccionar(contacto.numero)
                    },
                )
            }
        }
    }
}
