# Vectores de interoperabilidad JVM ↔ Android

Persona 2 entregó `:crypto` v1.0.1 con 56 vectores en JSON. Los resultados en dispositivos quedan pendientes.

## Pruebas disponibles

- `:crypto:test` ejecuta los vectores y las pruebas de parámetros OAEP en JVM.
- `VectorsInstrumentedTest` lee los mismos vectores desde el APK de pruebas. Incluyen pares exclusivos de prueba, válidos para verificar JVM → Android con claves de software.
- `CryptoGatewayKeystoreTest` comprueba el adaptador en los cuatro perfiles con claves no exportables y alias temporales. Prueba texto vacío, ASCII y Unicode. Se ejecuta por separado de las pruebas anteriores del almacén que usan alias de producción.

Para ejecutar solo las pruebas del ticket en un teléfono conectado:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.securesms.app.VectorsInstrumentedTest,com.securesms.app.integration.CryptoGatewayKeystoreTest
```

La prueba de vectores y el round-trip local de Keystore cubren casos distintos. Para verificar un cruce de runtimes con Keystore, cifra en JVM con la pública de Android y descifra en el teléfono. En el otro sentido, cifra en Android con una pública de prueba de JVM y descifra en JVM. Registra cada resultado por perfil y dispositivo.

La verificación de interoperabilidad se ejecuta solo con vectores entregados por Persona 2. Un cifrado correcto en Android no demuestra por sí solo la interoperabilidad: hace falta que el mismo mensaje circule en ambos sentidos con los parámetros OAEP acordados en D-022.

## Casos obligatorios

| Caso | Perfil | Sentido | Texto |
|---|---|---|---|
| Cifrar en JVM, descifrar en Android | RSA-2048 | `:crypto` → Android | ASCII |
| Cifrar en JVM, descifrar en Android | RSA-3072 | `:crypto` → Android | ASCII |
| Cifrar en Android, descifrar en JVM | RSA-2048 | Android → `:crypto` | ASCII |
| Cifrar en Android, descifrar en JVM | RSA-3072 | Android → `:crypto` | ASCII |
| Texto vacío | RSA-2048 | ambos sentidos | cadena vacía |
| Unicode | RSA-2048 y RSA-3072 | ambos sentidos | caracteres no ASCII en UTF-8 |
| Clave recuperada de Keystore | RSA-2048 y RSA-3072 | descifrado en Android | ASCII |

Cada caso se ejecuta en los dos dispositivos propios, Samsung `R5CX80AMTKN` (API 37) y POCO `4eeb389e` (API 36), con StrongBox opcional según lo anunciado por cada uno. Los resultados se registran como `PASS`, `FAIL` o `NOT TESTED`, nunca como suposición.

## Formato del vector

Para el intercambio manual con Keystore registra:

```text
perfil: RSA_2048 | RSA_3072
tamano_clave_bits: 2048 | 3072
digest_mensaje: SHA-256
funcion_mascara: MGF1
digest_mgf1: SHA-1
psource: PSpecified.DEFAULT
texto_claro_hex: <hexadecimal del plaintext en UTF-8>
cifrado_hex: <hexadecimal del ciphertext>
clave_publica_prueba: <clave pública del par de prueba, sin material privado>
```

La clave pública de prueba se usa solo para el cifrado en JVM. La privada permanece en Android Keystore y no se entrega, imprime ni escribe en el vector. El `cifrado_hex` se produce con los parámetros anteriores; no se reutilizan vectores generados con MGF1-SHA256 porque no son descifrables con la configuración acordada (ADR-0005).

## Criterios de aceptación

- El descifrado en Android del vector JVM devuelve exactamente `texto_claro_hex`.
- El descifrado en JVM del cifrado producido en Android devuelve exactamente el mismo `texto_claro_hex`.
- La operación sigue funcionando tras `keyStore.getKey(alias, null)`.
- El proveedor y el nivel de seguridad se registran solo cuando la operación termina correctamente.
- No aparece material privado en logs, archivos ni reportes.
