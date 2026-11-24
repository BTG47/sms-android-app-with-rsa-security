package com.securesms.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securesms.app.identity.EstadoClave
import com.securesms.app.integration.Perfil

/** Etiqueta visible de cada estado; el vocabulario sigue el glosario del proyecto. */
fun etiquetaDe(estado: EstadoClave): String = when (estado) {
    EstadoClave.SIN_IMPORTAR -> "Sin importar"
    EstadoClave.IMPORTADA_SIN_VERIFICAR -> "Importada sin verificar"
    EstadoClave.VERIFICADA -> "Verificada"
}

/** Agrupa la huella en bloques de ocho caracteres para compararla de un vistazo. */
fun huellaLegible(huella: String): String =
    huella.chunked(8).joinToString(" ")

/**
 * Pantalla de contactos y claves públicas.
 *
 * El botón de envío solo se habilita con una clave verificada. El envío real llega en el ticket 07.
 */
@Composable
fun PantallaContactos(viewModel: ContactosViewModel) {
    val estado by viewModel.uiState.collectAsState()

    var numero by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var perfil by remember { mutableStateOf(Perfil.ECC_P256) }
    var material by remember { mutableStateOf("") }
    var confirmada by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Contactos", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = numero,
                onValueChange = { numero = it },
                label = { Text("Numero") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Button(
            onClick = {
                viewModel.agregar(numero, nombre)
                numero = ""
                nombre = ""
            },
        ) {
            Text("Agregar contacto")
        }

        if (estado.aviso.isNotBlank()) {
            Text(estado.aviso)
        }

        estado.filas.forEach { fila ->
            FilaDeContacto(
                fila = fila,
                seleccionado = fila.numero == estado.seleccionado,
                onSeleccionar = { viewModel.seleccionar(fila.numero) },
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
                onImportar = { viewModel.importar(fila.numero, perfil, material) },
                onVerificar = { viewModel.verificar(fila.numero, confirmada) },
                onExportar = { viewModel.exportar(fila.numero) },
                puedeEnviar = viewModel.puedeEnviar(fila.numero),
            )
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
    onVerificar: () -> Unit,
    onExportar: () -> Unit,
    puedeEnviar: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Clave de ${fila.nombre}", style = MaterialTheme.typography.titleMedium)
            Text("Estado: ${etiquetaDe(fila.estado)}")
            Text("Perfil: ${fila.perfil?.name ?: "sin clave"}")

            fila.huella?.let { huella ->
                Text(
                    text = "Huella: ${huellaLegible(huella)}",
                    fontFamily = FontFamily.Monospace,
                )
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
                label = { Text("Clave publica (Base64)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(onClick = onImportar, enabled = material.isNotBlank()) {
                Text("Importar")
            }

            OutlinedTextField(
                value = confirmada,
                onValueChange = onConfirmada,
                label = { Text("Huella confirmada por otro canal") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = onVerificar, enabled = confirmada.isNotBlank()) {
                Text("Verificar")
            }

            Button(onClick = onExportar, enabled = fila.huella != null) {
                Text("Exportar")
            }
            exportado?.let {
                Text(
                    text = "Base64: $it",
                    fontFamily = FontFamily.Monospace,
                )
            }

            Button(onClick = { /* el envio real llega en el ticket 07 */ }, enabled = puedeEnviar) {
                Text("Enviar")
            }
            if (!puedeEnviar) {
                Text("El envio se habilita solo con la clave verificada.")
            }
        }
    }
}
