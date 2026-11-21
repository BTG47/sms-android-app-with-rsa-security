# SecureSMS — Contrato de integración v0.2

Copia controlada y versión definitiva de `SecureSMS_Contrato_Integracion_v02_Detallado-1.pdf` (18 de noviembre de 2026). Aprobada por Bruno (Android) y Persona 2 (criptografía) el 21 de noviembre de 2026. Ante cualquier divergencia, prevalece el PDF original.

## 1. Alcance y fundamentos

- Requisitos de la consigna: SMS seguros mediante RSA y ECC; pruebas con distintos tamaños de clave; comparación de tiempos de cifrado y descifrado; análisis SSL/TLS con Wireshark; investigación de blockchain/Bitcoin con hashes; reporte y video en inglés.
- Fundamentos: Bodur y Kara (2015), claves públicas por destinatario, Base64 y longitud SMS; Agoyi y Seral (2010), tiempos y expansión del ciphertext; Jintcharadze y Abashidze (2023), RSA/ECC y ECDH.
- Elección del equipo: cifrado híbrido con primitivas modernas. Es una decisión propia del equipo, no un requisito literal de la consigna ni de los artículos.

## 2. Propietarios y dependencias

| Parte | Responsable | Entrega |
|---|---|---|
| `:app` (Android) | Bruno | UI, claves locales, contactos, envío/recepción y reconstrucción SMS; ejecución física de pruebas |
| `:crypto` (Kotlin/JVM) | Persona 2 | API pública, RSA/ECC híbridos, importación/exportación pública, envelopes, pruebas unitarias |
| `:benchmarks` (JVM) | Persona 2 | Experimentos reproducibles, resultados de tiempo y tamaños; instrucciones para Android |
| `docs/` | Compartido | Contrato versionado, vectores de prueba, evidencias y secciones propias del reporte |

Regla de dependencias: `:app` y `:benchmarks` dependen de `:crypto`; `:crypto` no depende de Android, UI, SMS, Room ni de los otros módulos. Un solo repositorio GitHub con Gradle Kotlin DSL.

## 3. Decisiones criptográficas fijadas

| Perfil | Esquema | Parámetros |
|---|---|---|
| `RSA_2048` | Híbrido RSA + AES | RSA-OAEP con SHA-256/MGF1-SHA256; AES-256-GCM; clave RSA de 2048 bits |
| `RSA_3072` | Híbrido RSA + AES | Iguales parámetros; clave RSA de 3072 bits |
| `ECC_P256` | Híbrido ECDH + AES | ECDH efímero secp256r1; HKDF-SHA256; AES-256-GCM |
| `ECC_P384` | Híbrido ECDH + AES | ECDH efímero secp384r1; HKDF-SHA256; AES-256-GCM |

- Se usan proveedores criptográficos establecidos de Java/Android; no se programa RSA, curvas elípticas ni AES desde cero.
- RSA-3072 y ECC P-256 ofrecen aproximadamente 128 bits de seguridad clásica equivalente. No comparar tamaños de clave numéricamente como si fueran equivalentes.
- AES-GCM usa nonce nuevo de 12 bytes y tag de 16 bytes por mensaje. No reutilizar la misma pareja clave AES/nonce. Generar secretos y claves efímeras con un generador seguro.

## 4. API pública de `:crypto`

Paquete `com.securesms.crypto.api`, con KDoc en español en toda API pública.

```kotlin
enum class CryptoProfile { RSA_2048, RSA_3072, ECC_P256, ECC_P384 }

interface MessageCrypto {
    fun generateKeyPair(profile: CryptoProfile): KeyPair
    fun encrypt(plaintext: ByteArray, recipientPublicKey: PublicKey, profile: CryptoProfile): ByteArray
    fun decrypt(envelope: ByteArray, recipientPrivateKey: PrivateKey): ByteArray
}

interface PublicKeyCodec {
    fun encode(publicKey: PublicKey): ByteArray
    fun decode(encoded: ByteArray, profile: CryptoProfile): PublicKey
    fun fingerprint(publicKey: PublicKey): ByteArray
}
```

Reglas:

- `encrypt()` devuelve el envelope binario completo, sin Base64 ni información de SMS. `decrypt()` recibe exactamente ese array y devuelve el mensaje original sin conversión de caracteres.
- La conversión UTF-8 es responsabilidad de `:app`, antes de cifrar y después de descifrar. `:crypto` admite bytes arbitrarios, incluidos mensajes vacíos y Unicode.
- `generateKeyPair()` sirve para pruebas JVM; en Android se pueden pasar claves de Android Keystore, sujetas a compatibilidad del proveedor.
- `PublicKeyCodec` usa SubjectPublicKeyInfo (X.509), valida el perfil y calcula SHA-256 del material codificado. Nunca exporta claves privadas.
- Se rechazan perfiles incompatibles con la clave. Los errores no se disfrazan de mensaje vacío ni de `null`.

## 5. Errores y estados

| Error público | Significado |
|---|---|
| `UnsupportedProfile` | Perfil desconocido o no disponible; informar incompatibilidad |
| `InvalidKey` | Clave inválida, incompatible o mal codificada; solicitar clave válida |
| `InvalidEnvelope` | Envelope truncado, campos mal definidos, versión o longitudes inválidas |
| `DecryptionFailed` | Autenticación fallida, clave incorrecta o contenido alterado; mensaje genérico |
| `OperationFailed` | Fallo del proveedor criptográfico; registrar diagnóstico sin exponer secretos |

Permisos, ausencia de SIM, envío, recepción, fragmentos faltantes o duplicados pertenecen a `:app`. No incluir información secreta en logs. Los errores criptográficos visibles para el usuario son genéricos cuando corresponda.

## 6. Envelope cifrado v1 (propiedad de Persona 2)

Orden de bytes en big-endian:

| Orden | Campo | Tamaño / significado |
|---|---|---|
| 1 | Magic | 4 bytes ASCII: `SSMS` |
| 2 | Versión | 1 byte: `0x01` |
| 3 | Perfil | 1 byte: 1 `RSA_2048`, 2 `RSA_3072`, 3 `ECC_P256`, 4 `ECC_P384` |
| 4 | Longitud material | 2 bytes unsigned |
| 5 | Material | RSA: clave AES cifrada con OAEP; ECC: clave pública efímera X.509 SPKI |
| 6 | Longitud salt | 1 byte: RSA 0; ECC 16 |
| 7 | Salt | ECC: 16 bytes aleatorios; RSA: ausente |
| 8 | Nonce GCM | 12 bytes |
| 9 | Longitud ciphertext | 4 bytes unsigned; incluye tag GCM |
| 10 | Ciphertext + tag | Bytes AES-GCM, tag de 16 bytes al final |

- AAD autenticado: bytes desde Magic hasta el nonce inclusive, junto con la longitud del ciphertext.
- Validación: versión, perfil, coherencia de longitudes, límite máximo de 64 KiB, ausencia de bytes sobrantes y material esperado; comprobar curva/punto ECC.
- RSA: AES-256 aleatoria por mensaje, protegida con RSA-OAEP-SHA256, MGF1-SHA256 y PSource vacío.
- ECC: par efímero de la misma curva, ECDH, HKDF-SHA256 con salt del envelope e `info` fijo `SecureSMS-v1-ECDH-AES256GCM` concatenado con el ID de perfil y las SPKI efímera y destinataria con longitudes prefijadas.
- Estos esquemas dan confidencialidad y autenticación del ciphertext, pero no demuestran la identidad del remitente: la clave pública del destinatario debe verificarse por canal independiente.

## 7. Frontera con Android: claves y contactos

- Persona 2 genera claves JVM, valida perfiles, importa/exporta claves públicas, calcula huellas SHA-256 y cifra/descifra con objetos de clave estándar.
- Bruno almacena las claves privadas del dispositivo de forma segura, mantiene claves públicas verificadas por número telefónico y entrega la clave apropiada a `encrypt()`/`decrypt()`.
- Intercambio inicial: clave pública en Base64 o QR; confirmar huella SHA-256 por canal independiente. Sin servidor de claves ni certificados en el MVP.
- Verificar tempranamente soporte RSA-OAEP, ECDH y modos de clave en Android Keystore; documentar el fallback acordado sin exportar secretos a SMS.

## 8. Transporte SMS (contrato de Bruno)

```text
Texto (UTF-8) -> crypto.encrypt() -> envelope binario -> Base64 -> fragmentos SMS -> red celular
Red celular -> fragmentos -> reconstrucción -> Base64 -> envelope binario -> crypto.decrypt() -> texto (UTF-8)
```

Formato lógico: `SS1:<messageId>:<partIndex>:<partCount>:<payload>`.

- `messageId`: aleatorio por envío, por ejemplo 12 caracteres hexadecimales; `partIndex` empieza en 1; `partCount` es el total; `payload` es Base64 parcial ASCII.
- Definir ancho fijo o sintaxis inequívoca, límites máximos (99 fragmentos) y expiración de incompletos. Guardar por remitente + `messageId`; rechazar fragmentos contradictorios.
- Cada fragmento debe caber en una unidad GSM-7 sin doble segmentación. Si no se garantiza la codificación, reducir el máximo.
- Separar «SMS entregado al sistema» de «SMS recibido por el destinatario»; las confirmaciones no acreditan descifrado.
- Registrar tamaño del envelope, longitud Base64 y número de SMS.

**Ampliación acordada el 21/11/2026** (no cambia la API de `:crypto`): anchos fijos de 12/2/2 caracteres para ID/índice/total, payload máximo de 130 caracteres GSM-7, 99 fragmentos y expiración de ensamblados incompletos a los cinco minutos en memoria. Véase `docs/adr/0002-fragmentos-incompletos-expiran.md`.

## 9. Benchmarks y criterios experimentales

- Generación de par: cronometrar RSA y ECC por perfil, separado de cifrado y descifrado.
- Operación asimétrica: RSA-OAEP y, por separado, ECDH + HKDF.
- Cifrado y descifrado integral: medir `encrypt()` y `decrypt()` completos, sin red SMS.
- Transporte: bytes del envelope, caracteres Base64 y SMS por mensaje.
- Datos: textos UTF-8 de 16, 64, 128 y 512 bytes, con casos Unicode independientes.
- Procedimiento: calentamiento, al menos 30 repeticiones, `System.nanoTime()`, media, mediana y desviación estándar.
- Contexto: dispositivo, SO, proveedor criptográfico, versión de app y perfil; incluir datos brutos.
- No presentar todas las configuraciones como seguridad equivalente. El tiempo de red SMS no entra en el benchmark; JVM de escritorio y Android se reportan por separado.

## 10. Criterios de aceptación

Entregables de Persona 2: compilación de `:crypto` sin Android; cifrado/descifrado en los cuatro perfiles; round-trip vacío, ASCII, Unicode y largo; importación/exportación y huellas; detección de alteraciones; dos o más vectores binarios por perfil y README; benchmarks reproducibles con CSV y gráficas.

Entregables de Bruno: `:app` compila y usa la API real sin modificar la biblioteca; claves locales y públicas verificadas; envío/recepción y reconstrucción de largos, desordenados y duplicados; cifrado/descifrado local y pruebas en emulador y físicas; evidencia de error ante incompletos o alterados; ejecución de benchmarks en Android con datos sin modificar.

Integración: envelope JVM descifrable en Android y viceversa; los cuatro perfiles con la misma versión; alterar un byte protegido produce rechazo; el SMS entrega el mismo envelope que produjo `encrypt()`.

## 11. Control de cambios

Día 1: repositorio y estructura, API compilable, Bruno con doble de prueba. Días 2–3: RSA/ECC, codecs, vectores, transporte y claves. Día 4: integración extremo a extremo. Días 5–6: benchmarks, SMS y evidencias. Día 7 (25/11): reporte, video, APK y envío individual.

Cualquier modificación de perfiles, interfaces públicas, esquema HKDF, envelope o formato SMS debe acordarse entre Bruno y Persona 2, actualizar este contrato y los vectores de prueba. Los cambios internos que no rompan API son libres.

## 12. Fuera del contrato

Sin servidor de distribución de claves, nube, firma digital de remitente, mensajería por Internet ni chat comercial. Wireshark y blockchain/Bitcoin pertenecen a Persona 3. Fuentes base: consigna LIS4062, artículos citados y documentación oficial de JCA y Android Keystore. Los parámetros modernos y el protocolo SSMS v1 son decisiones del equipo.
