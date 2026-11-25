# SecureSMS · módulo `:crypto` (Persona 2: José)

Biblioteca criptográfica autónoma en **Kotlin/JVM** para el proyecto final de LIS4062. No usa ninguna clase de
Android ni de SMS: solo la JCA estándar (`java.security`, `javax.crypto`), que también existe en Android 8+
(API 26+). Bruno la integra en la app; las pruebas y benchmarks corren en cualquier PC con JDK 11+.

| Perfil | Esquema | Seguridad aprox. | Overhead por mensaje |
|---|---|---:|---:|
| `RSA_2048` | RSA-OAEP (SHA-256, MGF1-SHA-1) protege una semilla → HKDF-SHA-256 → AES-256-GCM | 112 bits | 280 B |
| `RSA_3072` | igual que arriba | 128 bits | 408 B |
| `ECC_P256` | ECIES: ECDH efímero (P-256) → HKDF-SHA-256 → AES-256-GCM | 128 bits | 57 B |
| `ECC_P384` | ECIES: ECDH efímero (P-384) → HKDF-SHA-384 → AES-256-GCM | 192 bits | 73 B |

## Estructura

```
crypto/        módulo :crypto (la biblioteca + pruebas unitarias + runner de benchmarks sin Android)
benchmark/     módulo :benchmark (CLI para PC y generador de vectores de prueba)
test-vectors/  vectors.json: 4 claves de prueba + 56 mensajes con resultado esperado (para Bruno)
android-snippets/  código listo para copiar en el proyecto Android (benchmark en el teléfono, vectores, adaptador)
results/jvm/   datos crudos del benchmark en PC (timings.csv, sizes.csv, environment.txt)
results/report/ tablas (tables.md) y figuras PNG para el reporte
analysis/      make_charts.py (genera tablas y gráficas desde los CSV)
docs/          FORMATO.md, INTEGRACION_BRUNO.md, PRUEBAS.md, REPORT_SECTION_Persona2.md
```

## Comandos

```bash
./gradlew :crypto:test                         # 80 pruebas unitarias (≈10 s)
./gradlew :crypto:jar                          # crypto/build/libs/securesms-crypto-1.0.1.jar
./gradlew :benchmark:run --args="--out results/jvm --label 'Mi laptop'"   # benchmark completo (~2 min)
./gradlew :benchmark:run --args="--out results/jvm-quick --quick"         # versión corta
./gradlew :benchmark:generateVectors           # regenera test-vectors/vectors.json
pip install matplotlib && python3 analysis/make_charts.py results/jvm [results/android-<modelo>]
```

Requisitos: JDK 11 o superior (probado con OpenJDK 21). Gradle se descarga solo con el wrapper.

## API en 30 segundos

```kotlin
import com.securesms.crypto.*

// 1. Cada usuario genera su par de claves (una vez por perfil que quiera recibir)
val alice = SecureSmsCrypto.generateKeyPair(Profile.ECC_P256)

// 2. Alice comparte su clave pública (texto de una línea, QR, etc.) y su huella para verificarla
val compartida: String = PublicKeys.exportCompact(alice.public)      // "SSPK1:ECC_P256:MFkwEwYH..."
val huella: String = PublicKeys.fingerprint(alice.public).hex       // "3F2A 91C0 ... " (64 hex)

// 3. Bob importa (valida algoritmo/tamaño/curva) y cifra
val contacto = PublicKeys.importCompact(compartida)                  // ImportedPublicKey(profile, key)
val envelope: ByteArray = SecureSmsCrypto.encryptText("Hola 😀", contacto.key, contacto.profile)

// 4. Alice descifra (Bruno reconstruye los fragmentos SMS antes de llamar esto)
val texto = SecureSmsCrypto.decryptText(envelope, alice)            // "Hola 😀"
```

Ejemplos de las cuatro configuraciones (todas con la misma API):

```kotlin
for (p in Profile.values()) {            // RSA_2048, RSA_3072, ECC_P256, ECC_P384
    val kp = SecureSmsCrypto.generateKeyPair(p)
    val env = SecureSmsCrypto.encryptText("Prueba ${p.displayName}", kp.public, p)
    println("${p.displayName}: ${env.size} bytes -> " + SecureSmsCrypto.decryptText(env, kp))
}
```

### Entradas, salidas y errores

| Función | Entrada | Salida | Errores (`SecureSmsException.code`) |
|---|---|---|---|
| `generateKeyPair(profile)` | perfil | `KeyPair` | `PLATFORM_ERROR` |
| `encrypt(bytes, publicKey, profile)` / `encryptText` | ≤ 64 KiB, clave del perfil | envelope binario (`Envelope.sizeFor(profile, n)` bytes) | `KEY_PROFILE_MISMATCH`, `PLAINTEXT_TOO_LARGE` |
| `decrypt(envelope, keyPair)` / `decryptText` | envelope completo | bytes / `String` | `MALFORMED_ENVELOPE`, `TRUNCATED_ENVELOPE`, `UNSUPPORTED_VERSION`, `UNKNOWN_PROFILE`, `KEY_PROFILE_MISMATCH`, `WRONG_RECIPIENT`, `INVALID_EPHEMERAL_KEY`, `AUTHENTICATION_FAILED`, `INVALID_TEXT` |
| `inspect(envelope)` | envelope | `Envelope` (perfil, key id, tamaños) **sin autenticar** | los de formato |
| `PublicKeys.exportDer / exportPem / exportCompact` | clave pública | X.509 SPKI DER / PEM / `SSPK1:...` | `KEY_PROFILE_MISMATCH` |
| `PublicKeys.importDer / importPem / importCompact` | bytes / texto | `PublicKey` validada (o `ImportedPublicKey`) | `INVALID_KEY`, `KEY_PROFILE_MISMATCH` |
| `PublicKeys.fingerprint(key)` | clave pública | `KeyFingerprint` (`hex`, `short`, `keyId`) | `KEY_PROFILE_MISMATCH` |
| `PrivateKeys.exportPkcs8 / importPkcs8` | clave privada | PKCS#8 | `INVALID_KEY`, `KEY_PROFILE_MISMATCH` |

Nunca se devuelve texto si falla cualquier verificación. Ver `docs/FORMATO.md` para el formato binario y
`docs/INTEGRACION_BRUNO.md` para la integración con Android.
