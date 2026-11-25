// Presenta el intercambio manual y por QR de las claves públicas de los contactos.
package com.securesms.app.ui

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securesms.app.identity.EstadoClave
import com.securesms.app.identity.leerClaveQr
import com.securesms.app.integration.Perfil
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/** Etiqueta visible de cada estado; el vocabulario sigue el glosario del proyecto. */
fun etiquetaDe(estado: EstadoClave): String = when (estado) {
    EstadoClave.SIN_IMPORTAR -> "No key imported"
    EstadoClave.IMPORTADA_SIN_VERIFICAR -> "Imported, unverified"
    EstadoClave.VERIFICADA -> "Verified"
}

/** Agrupa la huella en bloques de ocho caracteres para compararla de un vistazo. */
fun huellaLegible(huella: String): String =
    huella.chunked(8).joinToString(" ")

/**
 * Pantalla de contactos y claves públicas.
 *
 * El botón de envío solo se habilita con una clave verificada.
 */
@Composable
fun PantallaContactos(viewModel: ContactosViewModel, onIniciarConversacion: (String) -> Unit) {
    val estado by viewModel.uiState.collectAsState()
    val alcance = rememberCoroutineScope()

    var numero by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var perfil by rememberSaveable { mutableStateOf(Perfil.RSA_2048) }
    var material by rememberSaveable { mutableStateOf("") }
    var confirmada by rememberSaveable { mutableStateOf("") }

    var mostrarQr by rememberSaveable { mutableStateOf(false) }
    var contactoEscaneo by rememberSaveable { mutableStateOf<String?>(null) }
    var codigoEscaneado by rememberSaveable { mutableStateOf<String?>(null) }
    var errorQr by rememberSaveable { mutableStateOf(false) }
    val lector = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val codigo = resultado.contents
        val clave = codigo?.let(::leerClaveQr)
        errorQr = codigo != null && clave == null
        codigoEscaneado = codigo?.takeIf { clave != null }
    }

    estado.miClave?.takeIf { mostrarQr }?.let { clave ->
        MostrarClaveQr(clave, onCerrar = { mostrarQr = false })
    }
    val claveEscaneada = remember(codigoEscaneado) { codigoEscaneado?.let(::leerClaveQr) }
    val contacto = estado.contactoSeleccionado
    if (claveEscaneada != null && contacto != null && contacto.numero == contactoEscaneo) {
        RevisarClaveQr(
            clave = claveEscaneada,
            contacto = contacto,
            onImportar = {
                viewModel.importar(contacto.numero, claveEscaneada.perfil, claveEscaneada.materialBase64)
                perfil = claveEscaneada.perfil
                material = claveEscaneada.materialBase64
                confirmada = ""
                codigoEscaneado = null
            },
            onCerrar = { codigoEscaneado = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Contacts", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Share your public key, add the other person as a contact, and verify their fingerprint before sending an encrypted SMS.",
            style = MaterialTheme.typography.bodyMedium,
        )

        MiClaveCard(
            miClave = estado.miClave,
            onGenerar = { alcance.launch { viewModel.generarMiClave() } },
            onMostrarQr = { mostrarQr = true },
        )

        Text("Add contact", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = numero,
                onValueChange = { numero = it },
                label = { Text("Phone number") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Button(
            enabled = numero.isNotBlank() && nombre.isNotBlank(),
            onClick = {
                viewModel.agregar(numero, nombre)
                perfil = viewModel.uiState.value.contactoSeleccionado?.perfil ?: Perfil.RSA_2048
                material = ""
                confirmada = ""
                errorQr = false
                codigoEscaneado = null
                numero = ""
                nombre = ""
            },
        ) {
            Text("Add contact")
        }

        if (estado.aviso.isNotBlank()) {
            Text(estado.aviso)
        }

        if (estado.filas.isNotEmpty()) {
            Text("Choose a contact", style = MaterialTheme.typography.titleMedium)
        }
        estado.filas.forEach { fila ->
            FilaDeContacto(
                fila = fila,
                seleccionado = fila.numero == estado.seleccionado,
                onSeleccionar = {
                    if (estado.seleccionado != fila.numero) {
                        perfil = fila.perfil ?: Perfil.RSA_2048
                        material = ""
                        confirmada = ""
                        errorQr = false
                        codigoEscaneado = null
                    }
                    viewModel.seleccionar(fila.numero)
                },
            )
        }

        estado.contactoSeleccionado?.let { fila ->
            DetalleDeClave(
                fila = fila,
                perfil = perfil,
                onPerfil = { perfil = it },
                material = material,
                onMaterial = { material = it },
                confirmada = confirmada,
                onConfirmada = { confirmada = it },
                exportado = estado.exportado,
                onImportar = {
                    viewModel.importar(fila.numero, perfil, material)
                    confirmada = ""
                },
                errorQr = errorQr,
                onEscanear = {
                    contactoEscaneo = fila.numero
                    errorQr = false
                    codigoEscaneado = null
                    lector.launch(ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan the public key QR code")
                        setBeepEnabled(false)
                    })
                },
                onVerificar = { viewModel.verificar(fila.numero, confirmada) },
                onExportar = { viewModel.exportar(fila.numero) },
                puedeEnviar = viewModel.puedeEnviar(fila.numero),
                onIniciarConversacion = { onIniciarConversacion(fila.numero) },
            )
        }
    }
}

/** Tarjeta con la clave pública propia: se genera solo cuando el usuario lo pide (D-027). */
@Composable
private fun MiClaveCard(
    miClave: MiClaveUi?,
    onGenerar: () -> Unit,
    onMostrarQr: () -> Unit,
) {
    val portapapeles = LocalClipboard.current
    val alcance = rememberCoroutineScope()
    val copiar: (String) -> Unit = { valor ->
        alcance.launch {
            portapapeles.setClipEntry(ClipEntry(ClipData.newPlainText("SecureSMS", valor)))
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("My public key (RSA-2048)", style = MaterialTheme.typography.titleMedium)

            if (miClave == null) {
                Text("Generate your key to receive encrypted SMS on this phone.")
                Button(onClick = onGenerar) {
                    Text("Generate my key")
                }
            } else {
                Button(onClick = onMostrarQr) { Text("Show my QR code") }
                BloqueCopiable(
                    etiqueta = "SHA-256 fingerprint",
                    valor = huellaLegible(miClave.huella),
                    onCopiar = { copiar(miClave.huella) },
                )
                BloqueCopiable(
                    etiqueta = "Public key (Base64)",
                    valor = miClave.base64,
                    onCopiar = { copiar(miClave.base64) },
                )
            }
        }
    }
}

@Composable
private fun FilaDeContacto(
    fila: FilaContacto,
    seleccionado: Boolean,
    onSeleccionar: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeleccionar),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(fila.nombre, style = MaterialTheme.typography.titleMedium)
            Text(fila.numero)
            Text(etiquetaDe(fila.estado))
        }
    }
}

@Composable
private fun DetalleDeClave(
    fila: FilaContacto,
    perfil: Perfil,
    onPerfil: (Perfil) -> Unit,
    material: String,
    onMaterial: (String) -> Unit,
    confirmada: String,
    onConfirmada: (String) -> Unit,
    exportado: String?,
    onImportar: () -> Unit,
    errorQr: Boolean,
    onEscanear: () -> Unit,
    onVerificar: () -> Unit,
    onExportar: () -> Unit,
    puedeEnviar: Boolean,
    onIniciarConversacion: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        val portapapeles = LocalClipboard.current
        val alcance = rememberCoroutineScope()
        val copiar: (String) -> Unit = { valor ->
            alcance.launch {
                portapapeles.setClipEntry(ClipEntry(ClipData.newPlainText("SecureSMS", valor)))
            }
        }

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Public key for ${fila.nombre}", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${etiquetaDe(fila.estado)}")
            Text("Profile: ${fila.perfil?.name ?: "No key"}")

            Text("Import public key", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onEscanear) { Text("Scan QR code") }
            if (errorQr) {
                Text("This QR code does not contain a valid SecureSMS public key.", color = MaterialTheme.colorScheme.error)
            }
            Perfil.entries.chunked(2).forEach { grupo ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    grupo.forEach { opcion ->
                        FilterChip(
                            selected = opcion == perfil,
                            onClick = { onPerfil(opcion) },
                            label = { Text(opcion.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = material,
                onValueChange = onMaterial,
                label = { Text("Public key (Base64)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(onClick = onImportar, enabled = material.isNotBlank()) {
                Text("Import")
            }

            fila.huella?.let { huella ->
                Text("Stored SHA-256 fingerprint", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(huellaLegible(huella), fontFamily = FontFamily.Monospace) }
            }

            if (fila.estado == EstadoClave.IMPORTADA_SIN_VERIFICAR) {
                Text(
                    "Enter the fingerprint the contact confirms through a separate channel. " +
                        "It should match the stored fingerprint above.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = confirmada,
                    onValueChange = onConfirmada,
                    label = { Text("Independently confirmed fingerprint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = onVerificar, enabled = confirmada.isNotBlank()) {
                    Text("Verify fingerprint")
                }
            }

            Button(onClick = onExportar, enabled = fila.huella != null) {
                Text("Export")
            }
            exportado?.let {
                BloqueCopiable(
                    etiqueta = "Public key (Base64)",
                    valor = it,
                    onCopiar = { copiar(it) },
                )
            }

            Button(
                onClick = onIniciarConversacion,
                enabled = puedeEnviar,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Compose encrypted SMS")
            }
            if (!puedeEnviar) {
                Text(if (fila.huella == null) {
                    "Import this contact’s public key to continue."
                } else {
                    "Verify this contact’s fingerprint before sending."
                })
            }
        }
    }
}

/** Texto monoespaciado, seleccionable y con botón para copiarlo al portapapeles. */
@Composable
private fun BloqueCopiable(
    etiqueta: String,
    valor: String,
    onCopiar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(valor, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onCopiar) {
                Text("Copy")
            }
        }
    }
}
