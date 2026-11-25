package com.securesms.app.identity

import com.securesms.app.integration.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Casos del archivo JSON: ida y vuelta, versión, escapado y recuperación de corrupción. */
class ArchivoContactosTest {

    private val carpeta = Files.createTempDirectory("archivo-contactos").toFile()
    private val archivo = File(carpeta, "contactos.json")
    private val almacen = ArchivoContactos(archivo)

    private val ana = Contacto("5512345678", "Ana")
    private val luis = Contacto("5598765432", "Luis")

    private fun claveVerificada(): ClavePublicaImportada =
        ClavePublicaImportada.crear(ana, Perfil.ECC_P256, "YWJj").getOrThrow()
            .copy(estado = EstadoClave.VERIFICADA)

    @Test
    fun `ida y vuelta conserva todo`() {
        val estado = EstadoContactos(listOf(ana, luis), listOf(claveVerificada()))

        almacen.guardar(estado).getOrThrow()
        val leido = almacen.cargar()

        assertEquals(estado.contactos, leido.contactos)
        assertEquals(estado.claves, leido.claves)
    }

    @Test
    fun `un archivo inexistente devuelve estado vacio`() {
        assertEquals(EstadoContactos(), almacen.cargar())
    }

    @Test
    fun `el archivo declara la version 1`() {
        almacen.guardar(EstadoContactos(listOf(ana), emptyList())).getOrThrow()

        assertTrue(archivo.readText().contains("\"version\":1"))
    }

    @Test
    fun `escapa nombres con comillas, barras y saltos`() {
        val raro = Contacto("5500000000", "Ana \"la jefa\" \\ linea\nnueva")
        almacen.guardar(EstadoContactos(listOf(raro), emptyList())).getOrThrow()

        val leido = almacen.cargar()

        assertEquals(raro, leido.contactos.single())
    }

    @Test
    fun `un archivo corrupto falla y conserva el original`() {
        archivo.writeText("{\"version\":1,")

        assertThrows(IllegalStateException::class.java) { almacen.cargar() }

        val corrupto = File(carpeta, "contactos.json.corrupto")
        assertTrue(corrupto.exists())
        assertEquals("{\"version\":1,", corrupto.readText())
    }

    @Test
    fun `una version desconocida falla`() {
        archivo.writeText("{\"version\":99}")

        val error = assertThrows(IllegalStateException::class.java) { almacen.cargar() }

        assertTrue(error.message!!.contains("99"))
    }

    @Test
    fun `guardar no deja archivo temporal`() {
        almacen.guardar(EstadoContactos(listOf(ana), emptyList())).getOrThrow()

        assertFalse(File(carpeta, "contactos.json.tmp").exists())
        assertEquals(listOf(ana), almacen.cargar().contactos)
    }

    @Test
    fun `guardar reemplaza por completo`() {
        almacen.guardar(EstadoContactos(listOf(ana, luis), emptyList())).getOrThrow()
        almacen.guardar(EstadoContactos(listOf(luis), emptyList())).getOrThrow()

        assertEquals(listOf(luis), almacen.cargar().contactos)
    }

    @Test
    fun `un error de lectura no aparta el archivo`() {
        val directorio = File(carpeta, "contactos.json")
        directorio.mkdirs()

        assertThrows(IOException::class.java) { almacen.cargar() }

        assertFalse(File(carpeta, "contactos.json.corrupto").exists())
    }

    @Test
    fun `una huella que no corresponde a la clave se descarta`() {
        archivo.writeText(
            """
            {"version":1,
             "contactos":[{"numero":"5512345678","nombre":"Ana"}],
             "claves":[{"numero":"5512345678","perfil":"ECC_P256",
                        "materialBase64":"YWJj","huella":"00","estado":"VERIFICADA"}]}
            """.trimIndent(),
        )

        assertTrue(almacen.cargar().claves.isEmpty())
    }
}
