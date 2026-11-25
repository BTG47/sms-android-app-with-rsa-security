# Registro de pruebas (Persona 2)

Comando: `./gradlew :crypto:test` · Fecha: 23 nov 2026 (v1.0.1) · OpenJDK 21.0.13 (Linux x86-64) · JUnit 5.
**Resultado: 80 pruebas ejecutadas, 80 pasaron, 0 fallaron, 0 omitidas.**
El reporte HTML queda en `crypto/build/reports/tests/test/index.html` después de correrlas.

Las pruebas parametrizadas se ejecutan una vez por perfil (RSA-2048, RSA-3072, P-256, P-384).

| Clase | Pruebas | Qué verifica |
|---|---:|---|
| `RoundTripTest` | 21 | Ida y vuelta de texto vacío, corto ("Hola"), 160 caracteres, ~13 KB y Unicode (ñ, acentos, chino, árabe, cirílico, emoji, bandera, carácter combinado); binario con los 256 valores de byte; mensaje de tamaño máximo (64 KiB); dos cifrados del mismo texto son distintos (cifrado aleatorizado); descifrado con claves reimportadas (compacto + PKCS#8); overhead exacto por perfil. |
| `NegativeTest` | 30 | Destinatario equivocado → `WRONG_RECIPIENT`; clave privada equivocada con key id correcto → `AUTHENTICATION_FAILED` (la criptografía lo detecta, no solo el key id); **cada bit** 0 y 7 de **cada byte** del envelope invertido → rechazado; **cada truncamiento** posible → rechazado; bytes añadidos → rechazado; magic, versión 2, perfil desconocido, perfil cambiado; puntos efímeros inválidos (prefijo 0x04, x fuera de la curva, x ≥ p); claves de otro tamaño/curva/algoritmo y RSA-1024 rechazadas al cifrar y al descifrar; texto > 64 KiB; bytes no UTF-8 en `decryptText`. |
| `KeyCodecTest` | 16 | Exportar/importar DER, PEM y compacto sin diferencias (bytes idénticos y misma huella); PKCS#8 ida y vuelta; huella estable, distinta entre claves y con formato correcto; importar clave de otro perfil, basura, base64 inválido o perfil falso → error controlado; curva P-521 rechazada; las constantes de P-256/P-384 escritas en el código son idénticas a las de la JCA; compresión/descompresión de 100 puntos. |
| `FormatAndHkdfTest` | 8 | HKDF-SHA-256 contra los vectores oficiales del RFC 5869 (casos 1 y 3); disposición del header byte por byte e `inspect`; estimación de segmentos SMS (límites 160/153, 70/67, 140/134); prueba corta del runner de benchmarks. |
| `OaepParametersTest` | 4 | Fija la configuración RSA-OAEP acordada con Android (OAEP SHA-256, MGF1 SHA-1, etiqueta vacía) para RSA-2048 y RSA-3072. Un `Cipher` independiente configurado como Android Keystore abre el bloque de clave, y uno con MGF1-SHA-256 lo rechaza. Además, un envelope armado sin `encrypt()` de la biblioteca (OAEP externo, HKDF y AES-GCM a mano, como lo haría otra implementación) se descifra correctamente. |
| `TestVectorsTest` | 1 | Los 56 vectores publicados para Bruno dan exactamente el resultado esperado (14 por perfil). |

## Qué falló durante el desarrollo (y se corrigió)

* El generador de vectores se autoverifica antes de escribir: detectó que un vector "fragmento faltante" de
  60 bytes era, para P-256, más largo que el mínimo (57 B), así que daba `AUTHENTICATION_FAILED` y no
  `TRUNCATED_ENVELOPE`. Se cambió a "mínimo − 1 byte" para todos los perfiles.
* En la primera corrida del benchmark, el primer perfil medido (RSA-2048) salía más lento en la operación
  asimétrica que en el cifrado completo, lo cual es imposible: era calentamiento del JIT. Se agregó una fase
  de calentamiento global (1 500 rondas por perfil antes de medir) y la anomalía desapareció.

## Pendiente / no verificado todavía

* **Ejecución en Android real**: las pruebas y los vectores no se han corrido en un teléfono. Depende de que
  Bruno integre el módulo y corra `VectorsInstrumentedTest` y `CryptoBenchmarkInstrumentedTest` (snippets listos).
* **Claves dentro de Android Keystore**: la API acepta claves no exportables, pero el uso de OAEP/ECDH con el
  proveedor AndroidKeyStore no se ha probado en un dispositivo.
* No hay pruebas de canal lateral (tiempos constantes) ni de rendimiento energético; quedan fuera del alcance.
