package com.securesms.app.identity

import com.securesms.app.integration.Perfil

/**
 * Almacén de claves en memoria para pruebas.
 *
 * No genera criptografía real: solo registra qué perfiles existen y devuelve un Base64 fijo,
 * para probar el ViewModel sin dispositivo.
 */
class AlmacenDeClavesFalso(
    perfilInicial: Perfil? = null,
    private val publicaBase64: String = "YWJj",
    private val fallaAlGenerar: Boolean = false,
    private val fallaPeroQuedaGenerada: Boolean = false,
) : AlmacenDeClaves {

    private val generadas = mutableSetOf<Perfil>().apply { perfilInicial?.let(::add) }

    override fun tieneClave(perfil: Perfil): Boolean = perfil in generadas

    override fun generarSiFalta(perfil: Perfil): Result<Unit> {
        if (fallaPeroQuedaGenerada) {
            generadas.add(perfil)
            return Result.failure(IllegalStateException("alias ya existente"))
        }
        if (fallaAlGenerar) return Result.failure(IllegalStateException("fallo simulado"))

        generadas.add(perfil)
        return Result.success(Unit)
    }

    override fun clavePublicaBase64(perfil: Perfil): Result<String> =
        if (perfil in generadas) {
            Result.success(publicaBase64)
        } else {
            Result.failure(IllegalStateException("no hay clave para ${perfil.name}"))
        }
}
