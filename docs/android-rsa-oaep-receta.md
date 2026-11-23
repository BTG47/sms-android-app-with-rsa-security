# Receta RSA-OAEP acordada para el frente Android

Estado: borrador de implementación derivado de ADR-0005 y D-022.

Este documento fija el patrón que deben consumir los tickets 04, 06 y 08 en cuanto exista código de producción. Hoy las operaciones RSA-OAEP de este repositorio viven solo en el arnés instrumentado (`app/src/androidTest/java/com/securesms/app/keystore/`); no hay código RSA-OAEP en `app/src/main`. La fuente ejecutable del patrón es `OaepMgf1Sha1.kt` y su prueba es `RsaMgf1Sha1Test.kt`.

## Parámetros acordados

| Parámetro | Valor | Dónde se fija |
|---|---|---|
| Digest del mensaje | SHA-256 | `OAEPParameterSpec` y `setDigests` |
| Función de enmascaramiento | MGF1 | `OAEPParameterSpec` |
| Digest de MGF1 | SHA-1 | `MGF1ParameterSpec.SHA1` y `setMgf1Digests` (API 35+) |
| PSource | `PSource.PSpecified.DEFAULT` | `OAEPParameterSpec` |
| Cifrado de contenido | AES-256-GCM | Sin cambios, fuera de esta receta |
| Tamaño de clave RSA | 2048 o 3072 bits | `setKeySize` |

## Fragmento 1: parámetros del `Cipher`

```kotlin
internal const val TRANSFORMACION_RSA_ACORDADA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

internal fun parametrosOaepAcordados(): OAEPParameterSpec = OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA1,
    PSource.PSpecified.DEFAULT,
)
```

La transformación declara SHA-256 para el mensaje. El `OAEPParameterSpec` es obligatorio en ambos sentidos: sin él, JCE aplicaría el valor por defecto del proveedor y podría no coincidir con `:crypto`.

## Fragmento 2: generación de clave en Android Keystore

```kotlin
internal fun generadorRsa(alias: String, bits: Int, strongBox: Boolean): KeyPairGenerator {
    val especificacion = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(bits)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .apply {
            if (Build.VERSION.SDK_INT >= 35) {
                setMgf1Digests(KeyProperties.DIGEST_SHA1)
            }
            if (strongBox) {
                setIsStrongBoxBacked(true)
            }
        }
        .build()

    return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        .apply { initialize(especificacion) }
}
```

### Reparto por API

- **API 35+:** se declara el tag MGF1 con `setMgf1Digests(KeyProperties.DIGEST_SHA1)`.
- **API 31–34:** `setMgf1Digests` no existe. No se declara el tag y Keystore aplica su valor por defecto, MGF1-SHA1, verificado en AOSP y confirmado por el spike en los dos dispositivos. La rama es deliberada: omitir la llamada aquí no es una omisión del acuerdo.

### StrongBox

StrongBox sigue siendo opcional (D-018). Se intenta solo si `PackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)` devuelve `true`; si el dispositivo no lo anuncia se registra `NOT_TESTED` y se continúa con TEE. Si lo anuncia pero rechaza la configuración —por ejemplo RSA-3072— se registra `NO_DISPONIBLE` y se usa Android Keystore convencional. Nunca se degrada a una clave privada sin protección de hardware. Se registra `KeyInfo.securityLevel` para dejar constancia del proveedor real.

## Regla de registro de proveedor

`Cipher.provider` no es fiable cuando `init` falla. Solo se lee y se escribe en la evidencia después de que `init` y `doFinal` terminen correctamente:

```kotlin
private fun descifrar(privada: PrivateKey, parametros: OAEPParameterSpec, cifrado: ByteArray): Descifrado =
    Cipher.getInstance(TRANSFORMACION_RSA_ACORDADA).run {
        init(Cipher.DECRYPT_MODE, privada, parametros)
        val proveedor = provider.name
        Descifrado(String(doFinal(cifrado), Charsets.UTF_8), proveedor)
    }
```

La clave privada se recupera con `keyStore.getKey(alias, null)` y la operación se repite tras esa recuperación para comprobar que Keystore sigue pudiendo usarla. No se imprimen claves, secretos ni material derivado.

## Incompatibilidad con el contrato v0.2

Un envelope generado con MGF1-SHA256 no es descifrable con estos parámetros. El formato del envelope no incluye los parámetros OAEP, de modo que no existe una detección automática de la configuración de origen. No se mezclan mensajes de ambas configuraciones y no se implementa un fallback silencioso: cualquier compatibilidad con v0.2 requeriría un acuerdo nuevo y un cambio explícito del formato, vedado por D-019.

## Pendiente en el contrato

`docs/contract.md` todavía describe MGF1-SHA256. Por decisión de Bruno (D-023) el PDF v0.2 sigue siendo el artefacto canónico y no se emite uno nuevo; el acuerdo directo entre Bruno y Persona 2 prevalece para este cambio y la copia se actualizará cuando Bruno lo indique.

## Consumo por ticket

- **04:** usar ambos fragmentos al introducir la primera operación RSA-OAEP de producción.
- **06:** conservar los parámetros al añadir RSA-3072; no duplicar la lógica de `setMgf1Digests`.
- **08:** verificar los vectores de `docs/interop-vectores.md` antes de dar por buena la integración con `:crypto`.
