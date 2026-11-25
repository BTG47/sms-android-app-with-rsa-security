// Muestra el QR propio o los datos públicos escaneados antes de importarlos.
package com.securesms.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.securesms.app.identity.ClaveQr
import com.securesms.app.identity.codificarClaveQr
import com.securesms.app.integration.Perfil

/** QR de la clave propia, acompañado de los datos que aparecerán en el otro teléfono. */
@Composable
internal fun MostrarClaveQr(clave: MiClaveUi, onCerrar: () -> Unit) {
    val imagen = remember(clave) {
        val codigo = codificarClaveQr(clave.perfil, clave.base64)
        val matriz = QRCodeWriter().encode(codigo, BarcodeFormat.QR_CODE, 0, 0)
        val pixeles = IntArray(matriz.width * matriz.height) { indice ->
            if (matriz[indice % matriz.width, indice / matriz.width]) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixeles, matriz.width, matriz.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Share my public key") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = imagen,
                    contentDescription = "QR code for my public key",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    filterQuality = FilterQuality.None,
                )
                DatosClaveQr(clave.perfil, clave.huella, clave.base64)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Close") } },
    )
}

/** Revisión del contenido antes de importar la clave del contacto elegido. */
@Composable
internal fun RevisarClaveQr(
    clave: ClaveQr,
    contacto: FilaContacto,
    onImportar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Review scanned key") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Selected contact: ${contacto.nombre} · ${contacto.numero}")
                DatosClaveQr(clave.perfil, clave.huella, clave.materialBase64)
                Text("Import this public key, then confirm the fingerprint through a separate channel.")
            }
        },
        confirmButton = { TextButton(onClick = onImportar) { Text("Import public key") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancel") } },
    )
}

@Composable
private fun DatosClaveQr(perfil: Perfil, huella: String, material: String) {
    Text("Profile: ${perfil.name}")
    Text("SHA-256 fingerprint")
    SelectionContainer { Text(huellaLegible(huella), fontFamily = FontFamily.Monospace) }
    Text("Public key (Base64)")
    SelectionContainer { Text(material, fontFamily = FontFamily.Monospace) }
}
