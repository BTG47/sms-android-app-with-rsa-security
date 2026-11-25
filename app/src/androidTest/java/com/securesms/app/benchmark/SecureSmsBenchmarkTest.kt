// Mide el flujo criptográfico real y el transporte SS1 local en Android.
package com.securesms.app.benchmark

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securesms.app.identity.AlmacenDeClavesAndroid
import com.securesms.app.identity.ClavePublicaImportada
import com.securesms.app.identity.Contacto
import com.securesms.app.identity.EstadoClave
import com.securesms.app.integration.CryptoGatewayReal
import com.securesms.app.integration.Perfil
import com.securesms.app.messaging.FragmentAssembler
import com.securesms.app.messaging.encode
import com.securesms.app.messaging.parsear
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MUESTRAS_CLAVE = 5
private const val MUESTRAS_MENSAJE = 30
private const val CALENTAMIENTO = 3

@RunWith(AndroidJUnit4::class)
class SecureSmsBenchmarkTest {
    /** Guarda los tiempos individuales y el estado de cada fase por perfil y carga. */
    @Test
    fun medirPerfiles() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val carpeta = File(checkNotNull(contexto.getExternalFilesDir(null)), "securesms-bench/$id")
        check(carpeta.mkdirs())
        File(carpeta, "entorno.txt").writeText(
            "modelo=${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "android=${Build.VERSION.RELEASE}\napi=${Build.VERSION.SDK_INT}\n" +
                "soc=${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}\n" +
                "claves=$MUESTRAS_CLAVE\nmensajes=$MUESTRAS_MENSAJE\ncalentamiento=$CALENTAMIENTO\n",
        )
        val claves = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var fallos = 0
        File(carpeta, "mediciones.csv").bufferedWriter().use { csv ->
            csv.appendLine("perfil,carga_bytes,operacion,muestra,estado,ns,envelope_bytes,base64_chars,sms_ss1")
            for (perfil in Perfil.entries) {
                val prefijo = "securesms.bench.$id"
                val alias = "$prefijo.${perfil.name.lowercase()}.v1"
                val almacen = AlmacenDeClavesAndroid(contexto, prefijo)
                val gateway = CryptoGatewayReal(almacen::parParaDescifrar)

                fun fila(carga: Int?, operacion: String, muestra: Int?, estado: String,
                         ns: Long? = null, tamano: Tamano? = null) {
                    csv.appendLine(listOf(
                        perfil.name, carga, operacion, muestra, estado, ns,
                        tamano?.bytes, tamano?.base64, tamano?.sms,
                    ).joinToString(",") { it?.toString().orEmpty() })
                    if (estado == "FAIL") fallos++
                }

                try {
                    for (muestra in 1..MUESTRAS_CLAVE) {
                        // Un alias nuevo mide generación real y evita medir una búsqueda en caché.
                        if (claves.containsAlias(alias)) claves.deleteEntry(alias)
                        val inicio = SystemClock.elapsedRealtimeNanos()
                        val resultado = almacen.generarSiFalta(perfil)
                        val duracion = SystemClock.elapsedRealtimeNanos() - inicio
                        fila(null, "generacion", muestra, if (resultado.isSuccess) "PASS" else "FAIL", duracion)
                    }
                    val contacto = Contacto("+525512345678", "Benchmark")
                    val clave = almacen.clavePublicaBase64(perfil).mapCatching { material ->
                        ClavePublicaImportada.crear(contacto, perfil, material).getOrThrow()
                            .copy(estado = EstadoClave.VERIFICADA)
                    }.getOrNull()

                    for (texto in listOf("Hola", "A".repeat(160), "México 🔐")) {
                        val carga = texto.toByteArray(Charsets.UTF_8).size
                        fila(carga, "entrega_red_sms", null, "NOT TESTED")
                        if (clave == null) {
                            for (fase in listOf("cifrado", "descifrado", "fragmentacion", "reensamblado")) {
                                fila(carga, fase, null, "NOT TESTED")
                            }
                            continue
                        }
                        for (muestra in (1 - CALENTAMIENTO)..MUESTRAS_MENSAJE) {
                            val inicio = SystemClock.elapsedRealtimeNanos()
                            val cifrado = gateway.cifrar(texto, clave)
                            val nsCifrado = SystemClock.elapsedRealtimeNanos() - inicio
                            val envelope = cifrado.getOrNull()
                            if (envelope == null) {
                                if (muestra > 0) {
                                    fila(carga, "cifrado", muestra, "FAIL", nsCifrado)
                                    for (fase in listOf("descifrado", "fragmentacion", "reensamblado")) {
                                        fila(carga, fase, muestra, "NOT TESTED")
                                    }
                                }
                                continue
                            }
                            val inicioFragmentacion = SystemClock.elapsedRealtimeNanos()
                            val partes = runCatching { encode(envelope) }.getOrNull()
                            val nsFragmentacion = SystemClock.elapsedRealtimeNanos() - inicioFragmentacion
                            val tamano = Tamano(envelope.size, 4 * ((envelope.size + 2) / 3), partes?.size)
                            val inicioReensamblado = SystemClock.elapsedRealtimeNanos()
                            val reconstruido = partes?.let(::reensamblar)
                            val nsReensamblado = SystemClock.elapsedRealtimeNanos() - inicioReensamblado
                            val coincide = reconstruido?.contentEquals(envelope) == true
                            val inicioDescifrado = SystemClock.elapsedRealtimeNanos()
                            val claro = gateway.descifrar(envelope).getOrNull()
                            val nsDescifrado = SystemClock.elapsedRealtimeNanos() - inicioDescifrado
                            if (muestra > 0) {
                                fila(carga, "cifrado", muestra, "PASS", nsCifrado, tamano)
                                fila(carga, "fragmentacion", muestra, if (partes != null) "PASS" else "FAIL", nsFragmentacion, tamano)
                                fila(carga, "reensamblado", muestra,
                                    if (partes == null) "NOT TESTED" else if (coincide) "PASS" else "FAIL",
                                    nsReensamblado.takeIf { partes != null }, tamano)
                                fila(carga, "descifrado", muestra,
                                    if (claro?.texto == texto && claro.perfil == perfil) "PASS" else "FAIL",
                                    nsDescifrado, tamano)
                            }
                        }
                    }
                } finally {
                    if (claves.containsAlias(alias)) claves.deleteEntry(alias)
                    csv.flush()
                }
            }
        }
        Log.i("SecureSmsBench", "CSV: ${carpeta.absolutePath}/mediciones.csv")
        assertTrue("$fallos mediciones fallaron; revisa el CSV", fallos == 0)
    }

    private fun reensamblar(partes: List<String>): ByteArray? {
        val id = partes.firstOrNull()?.let(::parsear)?.messageId ?: return null
        val ensamblador = FragmentAssembler(SystemClock::elapsedRealtime)
        val remitente = "benchmark"
        partes.forEach { ensamblador.agregar(remitente, it) }
        return ensamblador.envelope(remitente, id)
    }

    private data class Tamano(val bytes: Int, val base64: Int, val sms: Int?)
}
