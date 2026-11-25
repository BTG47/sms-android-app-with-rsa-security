package com.securesms.app.identity

import com.google.gson.Gson
import com.securesms.app.integration.Perfil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Almacén en un archivo JSON versionado dentro del almacenamiento privado de la app.
 *
 * Guarda solo material público, huellas y estados. La escritura es atómica: primero un archivo
 * temporal y después un movimiento, de modo que un proceso interrumpido no deje el archivo a
 * medias. Si el contenido no es un archivo válido, se aparta como `.corrupto` y se informa del
 * fallo en lugar de sobrescribirlo en silencio; un error de lectura (permisos, E/S) no aparta el
 * archivo, porque podría estar sano y solo ilegible en ese momento.
 *
 * Al cargar se revalidan los campos y se recalcula la huella del material: una entrada
 * manipulada se descarta en lugar de aceptarse.
 */
class ArchivoContactos(private val archivo: File) : AlmacenContactos {

    private val gson = Gson()

    override fun cargar(): EstadoContactos {
        if (!archivo.exists()) return EstadoContactos()

        val texto = archivo.readText()
        val dto = runCatching { gson.fromJson(texto, ArchivoDto::class.java) }
            .getOrNull()
            ?: throw apartar("el archivo no es JSON valido")

        if (dto.version != VERSION) {
            throw apartar("version no soportada: ${dto.version}")
        }

        val contactos = (dto.contactos ?: emptyList()).mapNotNull { contacto ->
            val numero = contacto.numero ?: return@mapNotNull null
            val nombre = contacto.nombre ?: return@mapNotNull null
            runCatching { Contacto(numero, nombre) }.getOrNull()
        }
        val porNumero = contactos.associateBy { it.numero }

        val claves = (dto.claves ?: emptyList()).mapNotNull { clave ->
            val numero = clave.numero ?: return@mapNotNull null
            val contacto = porNumero[numero] ?: return@mapNotNull null
            val perfil = Perfil.entries.firstOrNull { it.name == clave.perfil }
                ?: return@mapNotNull null
            val material = clave.materialBase64 ?: return@mapNotNull null
            val huella = clave.huella ?: return@mapNotNull null
            val estado = EstadoClave.entries.firstOrNull { it.name == clave.estado }
                ?: return@mapNotNull null

            val esperada = Huella.calcular(material).getOrNull() ?: return@mapNotNull null
            if (Huella.normalizar(huella) != esperada) return@mapNotNull null

            ClavePublicaImportada(
                contacto = contacto,
                perfil = perfil,
                materialBase64 = material.filterNot { it.isWhitespace() },
                huella = esperada,
                estado = estado,
            )
        }

        return EstadoContactos(contactos, claves)
    }

    override fun guardar(estado: EstadoContactos): Result<Unit> = runCatching {
        val dto = ArchivoDto(
            version = VERSION,
            contactos = estado.contactos.map { ContactoDto(it.numero, it.nombre) },
            claves = estado.claves.map { clave ->
                ClaveDto(
                    numero = clave.contacto.numero,
                    perfil = clave.perfil.name,
                    materialBase64 = clave.materialBase64,
                    huella = clave.huella,
                    estado = clave.estado.name,
                )
            },
        )

        archivo.absoluteFile.parentFile?.mkdirs()
        val temporal = File(archivo.parentFile, archivo.name + ".tmp")
        temporal.writeText(gson.toJson(dto))
        mover(temporal, archivo)
    }

    private fun mover(origen: File, destino: File) {
        try {
            Files.move(
                origen.toPath(),
                destino.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun apartar(mensaje: String): IllegalStateException {
        val corrupto = File(archivo.parentFile, archivo.name + ".corrupto")
        if (!archivo.renameTo(corrupto)) {
            runCatching { archivo.copyTo(corrupto, overwrite = true) }
        }
        return IllegalStateException(mensaje)
    }

    companion object {
        /** Versión del formato en disco. */
        const val VERSION = 1
    }
}

private data class ArchivoDto(
    val version: Int = 0,
    val contactos: List<ContactoDto>? = null,
    val claves: List<ClaveDto>? = null,
)

private data class ContactoDto(
    val numero: String? = null,
    val nombre: String? = null,
)

private data class ClaveDto(
    val numero: String? = null,
    val perfil: String? = null,
    val materialBase64: String? = null,
    val huella: String? = null,
    val estado: String? = null,
)
