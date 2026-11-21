# SecureSMS — Contexto maestro para la implementación Android

**Curso:** LIS4062 — Information Security (Autumn 2026)  
**Proyecto:** Secure SMS Encryption Using RSA and ECC & SSL/TLS Wireshark Analysis  
**Entrega académica:** miércoles 25 de noviembre de 2026, 23:59  
**Responsable de este frente:** Bruno — aplicación Android, transporte SMS e integración  
**Lenguaje acordado:** Kotlin  
**Estado:** planeación general aceptada por el equipo; implementación Android por iniciar.

> **Propósito de este archivo.** Este documento proporciona a un agente de desarrollo el contexto necesario para construir exclusivamente la parte Android de SecureSMS. Se basa en la consigna, los artículos entregados y los acuerdos de organización y contrato del equipo. No sustituye a `docs/contract.md` ni autoriza modificar unilateralmente el protocolo o la biblioteca criptográfica.

## 1. Contexto y objetivo académico

El proyecto consiste en demostrar el envío de **mensajes SMS cifrados** entre dispositivos Android usando RSA y criptografía de curva elíptica (ECC), comparando distintos tamaños de clave y tiempos de cifrado/descifrado. El profesor también solicita un análisis SSL/TLS con Wireshark y una investigación con ejemplo sobre blockchain, Bitcoin y funciones hash. Esos últimos trabajos pertenecen a otra persona del equipo y **no forman parte de la app Android**.

La aplicación debe permitir redactar un mensaje, seleccionar un destinatario cuya clave pública esté disponible, cifrar el contenido, enviarlo **por SMS real** —no mediante HTTP o un backend—, recibir y reconstruir los mensajes y descifrarlos con la clave privada correspondiente. Debe evidenciarse el flujo y documentar las limitaciones observadas.

Las referencias proporcionadas incluyen: Bodur y Kara (2015), sobre cifrado RSA en aplicaciones Android y la longitud de SMS/Base64; Agoyi y Seral (2010), sobre tiempos de algoritmos asimétricos y expansión de mensajes; Jintcharadze y Abashidze (2023), sobre comparación RSA/ECC y enfoques híbridos. **No se exige reproducir literalmente sus diseños antiguos:** el equipo eligió construcciones criptográficas modernas, que deberán explicarse como decisiones propias y no como requisitos textuales del profesor.

El resultado académico incluye reporte PDF con portada, abstract, introducción, metodología, resultados/discusión, conclusiones y bibliografía; video **en inglés**, con la cámara encendida y participación de los tres integrantes, enlazado desde el reporte. Cada integrante debe entregar individualmente el PDF `FinalProject_ID_ID_ID.pdf` en Blackboard. Bruno debe producir las evidencias y el texto correspondientes a Android, no el reporte completo.

## 2. Distribución de responsabilidades y límites

| Responsable | Sí realiza | No realiza |
|---|---|---|
| **Bruno — `:app`** | Proyecto Android, UI, estados, permisos, claves locales, importación de claves públicas, asociación con teléfonos, SMS, codificación/fragmentación/reconstrucción, integración y pruebas en Android | Implementación matemática RSA/ECC, diseño interno de envelopes, benchmarks criptográficos desde cero, investigación TLS/Bitcoin |
| **Persona 2 — `:crypto`, `:benchmarks`** | Biblioteca Kotlin/JVM autónoma, cifrado/descifrado RSA/ECC, importación/exportación pública, validación, serialización binaria, pruebas unitarias, generador de benchmarks y análisis | Pantallas Android y transporte SMS |
| **Persona 3** | Wireshark, 15 incisos TLS, blockchain/Bitcoin, consolidación de reporte y video | Código de Android o criptografía ajena |

**Frontera no negociable:** `:crypto` entrega/recibe bytes binarios; `:app` es dueño del transporte SMS y de los datos específicos de Android. La Persona 2 no debe recibir números telefónicos o fragmentos SMS. Bruno no debe implementar ni modificar internamente las primitivas criptográficas.

## 3. Organización técnica acordada

Un solo repositorio Git con Gradle Kotlin DSL (`*.gradle.kts`):

```text
SecureSMS/
├── app/             # Aplicación Android (Bruno)
├── crypto/          # Biblioteca Kotlin/JVM (Persona 2)
├── benchmarks/      # Benchmarks (Persona 2)
├── docs/
│   ├── contract.md  # Contrato versionado entre los dos desarrolladores
│   ├── evidence/    # Capturas y pruebas
│   └── results/     # Resultados medidos
└── settings.gradle.kts
```

La app depende de `:crypto`. `:crypto` no depende de Android, de `:app`, de Room ni de las APIs SMS. Un paquete sugerido para el código Android es `com.securesms.app`, dividido de manera ligera en `ui`, `messaging`, `storage`, `keys` e `integration` según sea útil. Evitar arquitecturas extensas que no ayuden a entregar el MVP.

Se recomienda **Kotlin + Jetpack Compose + ViewModel**, y almacenamiento local sencillo cuando sea necesario. No se requiere backend, servidor de usuarios, sincronización cloud ni servicios externos.

### Convención de código

Todos los archivos nuevos o modificados deben tener comentarios profesionales **en español**, sin emojis: breve encabezado sobre su responsabilidad; KDoc para clases y funciones públicas relevantes; comentarios internos solo cuando la lógica no sea evidente. Priorizar nombres descriptivos, módulos pequeños, pruebas reproducibles y cambios incrementales.

## 4. Contrato de integración con criptografía — lo que la app debe consumir

**Referencia normativa del equipo:** `SecureSMS_Contrato_Integracion_v02_Detallado.pdf` y su futura copia controlada en `docs/contract.md`. Este resumen no sustituye las definiciones exactas del PDF; ante divergencias, resolverlas con Persona 2 y actualizar la versión del contrato.

Los perfiles acordados son:

| Perfil | Significado |
|---|---|
| `RSA_2048` | RSA-2048 híbrido con RSA-OAEP-SHA256 + AES-256-GCM |
| `RSA_3072` | RSA-3072 con el mismo esquema |
| `ECC_P256` | ECDH secp256r1 + HKDF-SHA256 + AES-256-GCM |
| `ECC_P384` | ECDH secp384r1 + HKDF-SHA256 + AES-256-GCM |

Las APIs públicas **propuestas en el contrato v0.2** son:

```kotlin
package com.securesms.crypto.api

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/** Perfiles criptográficos admitidos por el protocolo. */
enum class CryptoProfile { RSA_2048, RSA_3072, ECC_P256, ECC_P384 }

/** API compartida: no conoce Android ni SMS. */
interface MessageCrypto {
    fun generateKeyPair(profile: CryptoProfile): KeyPair
    fun encrypt(
        plaintext: ByteArray,
        recipientPublicKey: PublicKey,
        profile: CryptoProfile
    ): ByteArray
    fun decrypt(
        envelope: ByteArray,
        recipientPrivateKey: PrivateKey
    ): ByteArray
}

/** Importación, exportación y verificación de claves públicas. */
interface PublicKeyCodec {
    fun encode(publicKey: PublicKey): ByteArray
    fun decode(encoded: ByteArray, profile: CryptoProfile): PublicKey
    fun fingerprint(publicKey: PublicKey): ByteArray
}
```

**Reglas para Bruno:**

1. La app convierte texto a UTF-8 **antes** de `encrypt()` y recupera UTF-8 **después** de `decrypt()`; `:crypto` trata bytes arbitrarios.
2. `encrypt()` devuelve un **envelope binario completo**; es opaco para la app. No construir o interpretar sus campos salvo a través de las APIs públicas acordadas.
3. `decrypt()` recibe exactamente el envelope binario reconstruido. No alterar, limpiar, recortar ni transformar sus bytes.
4. `PublicKeyCodec` usa claves públicas X.509 SubjectPublicKeyInfo, y huella SHA-256 para verificación. No exportar claves privadas.
5. Identificar perfiles y errores mediante tipos públicos de `:crypto`; nunca distinguir RSA/ECC inspeccionando manualmente el ciphertext.
6. `CryptoException` contempla categorías como `UnsupportedProfile`, `InvalidKey`, `InvalidEnvelope`, `DecryptionFailed` y `OperationFailed`. El texto exacto de los mensajes de error de UI es responsabilidad de Android.
7. El contrato propone un envelope v1 con magic `SSMS`, versión, perfil, material criptográfico, nonce y ciphertext autenticado. **El codec es propiedad de Persona 2.** Los detalles byte a byte se encuentran en el contrato PDF; no reimplementarlos en `:app`.
8. No introducir por iniciativa propia una segunda biblioteca criptográfica o una segunda serialización paralela.

**Nota de compatibilidad importante:** Android Keystore puede imponer restricciones a claves privadas y operaciones ECDH/RSA-OAEP. Verificar soporte real de dispositivo y versión de Android tempranamente. Si surge una incompatibilidad, documentarla y acordar el ajuste o fallback con Persona 2 antes de cambiar interfaces. Los pares de claves JVM en memoria sirven para las pruebas iniciales pero no sustituyen una estrategia razonable de persistencia de claves en el teléfono.

## 5. Funcionalidades Android que sí se implementarán

### 5.1. Preparación y arranque

- Crear el módulo `:app` en Kotlin y asegurar compilación reproducible con Gradle.
- Definir versión mínima de Android y comprobar permisos/API disponibles en el dispositivo real.
- Configurar Compose y una separación pequeña entre presentación, estado, almacenamiento y SMS.
- Crear una capa/adaptador que utilice `MessageCrypto`; durante el inicio, puede usar un **doble de prueba** para validar interfaces y flujo. Este doble no debe fingir seguridad ni utilizarse como implementación final.
- Mantener README de compilación, instalación y pruebas.

### 5.2. Interfaz mínima para demostrar el objetivo

- Identificar usuario/clave propia y mostrar si existe un par de claves utilizable.
- Permitir ingresar número de teléfono del destinatario, asociar/importar su clave pública y mostrar su huella verificable.
- Permitir redactar un mensaje y seleccionar `CryptoProfile` válido compatible con la clave del destinatario.
- Botón de enviar, con estados `preparando`, `cifrado`, `enviando`, `aceptado para envío`, `recibido/reconstruido` y `error` según lo que la app realmente conozca.
- Lista o vista sencilla de mensajes enviados/recibidos con texto descifrado cuando corresponda.
- Mostrar fallos comprensibles: permisos ausentes, sin SIM, clave faltante, fragmentos incompletos, envelope inválido, descifrado fallido.
- Mostrar diagnósticos útiles para la demostración: perfil, tamaño de envelope, cantidad de caracteres Base64 y número de SMS generados.

No es necesario desarrollar un cliente SMS comercial, listas de contactos completas, notificaciones complejas ni funciones avanzadas de conversación.

### 5.3. Claves y contactos

- Generar u obtener claves del usuario de forma compatible con el contrato y Android Keystore donde resulte viable.
- Persistir el vínculo entre número/contacto, perfil y clave pública importada.
- Permitir importar/exportar **solo claves públicas**, por Base64 o QR si se desea simplificar el intercambio manual.
- Exponer y verificar con el compañero la huella SHA-256 por canal independiente. **No afirmar que una clave está verificada solo porque se importó.**
- Mantener las claves privadas protegidas; nunca incluirlas en SMS, logs, capturas ni archivos de evidencia.
- Las claves de diferentes perfiles deben distinguirse claramente; si un contacto no tiene clave del perfil seleccionado, impedir el envío o solicitar la clave correcta.

### 5.4. Transporte SMS real

Flujo emisor:

```text
Texto → UTF-8 → :crypto.encrypt() → envelope binario
→ Base64 → fragmentos de texto SMS → SmsManager → red celular
```

Flujo receptor:

```text
SMS recibidos → identificación del protocolo → reagrupación
→ ensamblado Base64 → envelope binario → :crypto.decrypt()
→ UTF-8 → presentación
```

Requisitos del transporte:

- Solicitar/verificar permisos SMS apropiados en tiempo de ejecución y comprobar disponibilidad de subscripción/SIM. Manejar restricciones de versiones Android y del dispositivo.
- Usar APIs oficiales de Android para enviar SMS. Separar confirmación de **solicitud de envío** de confirmación de entrega y, especialmente, de **descifrado exitoso**.
- Identificar solo los SMS de SecureSMS mediante prefijo/versionado propio; no interpretar cualquier SMS ordinario como mensaje cifrado.
- Fragmentar el Base64 para que **cada fragmento lógico quepa en un solo SMS de texto**, evitando doble segmentación inadvertida del propio fragmento por el sistema. Comprobar codificación y tamaño efectivo.
- Reconstruir fragmentos desordenados; tolerar duplicados idénticos; rechazar fragmentos contradictorios; identificar faltantes y expirar ensamblados incompletos.
- Agrupar como mínimo por remitente + identificador de mensaje; limitar memoria, cantidad de partes y tamaño máximo para evitar acumulación indefinida.
- **No descifrar envelopes incompletos.** Después de reconstruir, cualquier alteración de bytes debe ser detectada por el módulo criptográfico.
- Registrar el número real de mensajes SMS utilizados para evidencia y análisis del costo de transporte.

**Formato lógico propuesto en el contrato:**

```text
SS1:<messageId>:<partIndex>:<partCount>:<payload>
```

`messageId` identifica el envío; `partIndex` inicia en 1; `partCount` indica total; `payload` contiene un segmento Base64. El contrato propone un ID aleatorio de 12 caracteres hex y máximo orientativo de 99 fragmentos. **Antes de implementar el parser definitivo**, cerrar con Persona 2 o en `docs/contract.md` la sintaxis exacta de campos, anchuras, caracteres permitidos y límites; cualquier cambio debe versionarse. La cabecera de transporte SMS no reemplaza la autenticación criptográfica del envelope.

### 5.5. Almacenamiento mínimo

- Persistir claves y asociaciones de destinatarios según el nivel de protección apropiado.
- Si se guarda historial, diferenciar metadatos, mensajes recibidos/pendientes, estados y datos cifrados. Evitar almacenar texto en claro innecesariamente; documentar la decisión de demo.
- Persistir provisionalmente fragmentos incompletos si hace falta soportar recepción asíncrona y reinicios; aplicar expiración y límites.
- No introducir Room por obligación si el MVP puede hacerse de forma razonable con almacenamiento más simple. Elegir solo lo necesario para pruebas repetibles.

## 6. Lo que **NO** debe implementar el agente Android

- **No** escribir algoritmos RSA, ECC, AES, ECDH, HKDF o serializers del envelope: son responsabilidad de Persona 2.
- **No** diseñar otra API incompatible con `MessageCrypto` ni inventar perfiles alternativos sin acuerdo.
- **No** crear backend, servidor central de claves, login, Firebase, mensajes por Internet o infraestructura de certificados.
- **No** implementar Wireshark/TLS, blockchain/Bitcoin ni redactar por completo el reporte académico.
- **No** construir funciones sociales, chat grupal, multimedia, llamadas, notificaciones avanzadas o un reemplazo comercial de la app SMS.
- **No** afirmar que SMS emulado es equivalente a una prueba real por red celular.
- **No** medir latencia de red y llamarla tiempo de cifrado RSA/ECC.
- **No** introducir cambios masivos, dependencias no justificadas o refactors no relacionados con los requisitos.

## 7. Pruebas y validación de Bruno

### 7.1. Pruebas de construcción y flujo local

- [ ] `:app` compila, instala y arranca en emulador/dispositivo.
- [ ] La UI muestra el perfil seleccionado, contacto y estados correctamente.
- [ ] Puede cifrar un mensaje con un stub controlado y generar los fragmentos SMS sin depender de que `:crypto` esté terminado.
- [ ] Round-trip de Base64 y segmentación/reconstrucción conserva **exactamente** los mismos bytes binarios.
- [ ] Fragmentos recibidos fuera de orden se reensamblan correctamente.
- [ ] Duplicados idénticos no duplican el mensaje; conflictivos se rechazan.
- [ ] Fragmentos faltantes expiran o permanecen explícitamente incompletos, sin intentar descifrar.

### 7.2. Integración criptográfica con Persona 2

- [ ] La app importa `:crypto` sin modificar su implementación.
- [ ] RSA-2048, RSA-3072, ECC P-256 y ECC P-384 pasan pruebas locales de cifrado/descifrado cuando el módulo esté disponible.
- [ ] Un envelope producido en JVM puede descifrarse en Android, y viceversa, con claves compatibles.
- [ ] Se prueba texto ASCII, Unicode, vacío y largo.
- [ ] Clave privada equivocada, cambios en ciphertext y envelope alterado son rechazados sin mostrar texto corrupto.
- [ ] La generación/almacenamiento de claves funciona en el Android disponible o se documenta el fallback acordado.

### 7.3. Pruebas de SMS

- [ ] Se puede enviar y recibir SMS en el entorno de prueba compatible.
- [ ] La app diferencia SMS propios y ordinarios.
- [ ] Un SMS cifrado se convierte al mismo envelope original después del transporte.
- [ ] Se prueban mensajes largos, multipartes, duplicados y mensajes incompletos.
- [ ] Se intenta la prueba con **dos teléfonos Android y SIM**; si solo hay uno, se documenta con transparencia qué se verificó en emulador y qué quedó pendiente.
- [ ] Se documentan permisos, errores del operador, compatibilidad, número de SMS y limitaciones.

### 7.4. Benchmarks Android (apoyo a Persona 2)

- [ ] Ejecutar el procedimiento de benchmark provisto por Persona 2 sobre el dispositivo Android.
- [ ] Registrar dispositivo, versión Android, proveedor criptográfico y perfil.
- [ ] Entregar CSV/datos sin alterar, con circunstancias de medición.
- [ ] No elaborar resultados inventados ni mezclar operaciones de red con tiempo criptográfico.

## 8. Evidencias y entregables propios

Al terminar, Bruno debe entregar:

1. **Código `:app`** funcional, compilable y documentado en GitHub.
2. **APK de demostración** y pasos básicos para instalar/ejecutar.
3. **Capturas o video** de preparación de claves, selección de destinatario, cifrado, envío, recepción, reconstrucción y descifrado; registrar los errores probados.
4. **Registro de pruebas** con estado `PASS / FAIL / NOT TESTED` y razones verificables, distinguiendo emulador de SMS físico.
5. **Evidencia del transporte**: tamaño de envelope, Base64, número de fragmentos/SMS y mensajes de prueba (sin claves privadas).
6. **Resultados de benchmarks Android** ejecutados siguiendo el procedimiento de Persona 2, para que esa persona realice el análisis académico.
7. **Texto de metodología y resultados Android** para Persona 3: arquitectura resumida, implementación de SMS, dispositivo y condiciones, pruebas, dificultades, limitaciones y capturas debidamente identificadas.
8. **Participación en el video en inglés**, con cámara encendida y demostración de la parte Android.

## 9. Orden recomendado para implementar sin bloquearse

1. **Inventariar el repositorio real.** Revisar qué módulos, código, rama y herramientas existen. No sobrescribir trabajo ajeno. Si `:crypto` aún no existe, crear solo los puentes necesarios sin apropiarse del módulo de Persona 2.
2. **Montar la app mínima.** Compilación, pantalla simple, selección de contacto/perfil, estructura de UI/estado y manejo de permisos.
3. **Probar SMS sin cifrado en un entorno controlado.** Validar envío/recepción y errores de permisos/SIM. No enviar texto sensible real.
4. **Implementar codec de transporte y reensamblado de SMS con pruebas propias.** Usar bytes opacos de prueba, no implementar el envelope criptográfico.
5. **Implementar importación y asociación de claves públicas + almacenamiento local apropiado.** Validar muy temprano compatibilidad Keystore.
6. **Conectar un adaptador temporal a la interfaz `MessageCrypto`.** Aislar el stub para quitarlo cuando llegue el módulo real; distinguir claramente en UI/logs modo demo y modo cifrado real.
7. **Integrar `:crypto` real y vectores de prueba de Persona 2.** Comprobar todos los perfiles antes de probar red celular completa.
8. **Realizar pruebas end-to-end y preparar evidencia.** Idealmente con dos Android físicos con SIM; documentar bloqueos reales.
9. **Ejecutar benchmarks Android entregados por Persona 2 y compartir datos originales.** No cambiar metodología de medición sin consensuarla.
10. **Cerrar código y entrega académica propia.** README, APK, capturas, sección de reporte y guion/segmento de video.

**Regla de prioridad:** primero compilación y transporte fiable, luego integración criptográfica y claves, finalmente pulido de interfaz. No sacrificar las pruebas reales para añadir funcionalidades cosméticas.

## 10. Reglas de colaboración y cambios

- Persona 2 es propietaria de `:crypto`, del envelope, los perfiles y los benchmarks; Bruno de `:app`, SMS, almacenamiento Android e interfaz. Hacer commits pequeños y separados.
- El contrato v0.2 es la base acordada para implementar. Su PDF se preparó como propuesta técnica detallada; confirmar que la versión concreta que consumirá el código esté congelada en `docs/contract.md` y que ambos desarrolladores la estén usando.
- **Cualquier cambio** de API pública, formato envelope, HKDF, perfiles o formato SMS debe comunicarse a ambos, documentarse y acompañarse de pruebas actualizadas. No arreglar unilateralmente incompatibilidades modificando `:crypto`.
- Mantener diagnóstico de errores sin imprimir claves privadas, secretos compartidos ni texto sensible en logs.
- Registrar incidencias con pasos de reproducción y el entorno donde ocurrieron.
- No afirmar resultados de pruebas que no se hayan ejecutado.

## 11. Instrucciones para el agente externo

Trabaja **solo en la parte Android del proyecto SecureSMS**. Antes de editar, inspecciona el estado real del repositorio, los módulos existentes y cualquier copia de `docs/contract.md`. No asumas que existen implementaciones RSA/ECC si todavía no se han integrado. Propón e implementa pasos pequeños y comprobables, ejecutando compilación y pruebas al finalizar cada incremento. Evita cambios masivos, no toques componentes ajenos sin necesidad y no inventes evidencias de SMS físicos o benchmarks.

Cuando falten interfaces de Persona 2, usa un adaptador o fake explícito **solo para tests e integración provisional**; nunca lo presentes como cifrado real. Documenta con claridad qué funciona y qué depende de otro integrante. Prioriza interoperabilidad, exactitud del transporte SMS, seguridad básica de claves, manejo de errores y reproducibilidad sobre detalles estéticos.

Al terminar cada bloque comunica: archivos cambiados, decisión de diseño, comandos ejecutados y resultados de pruebas, fallos pendientes y dependencias abiertas con Persona 2.

## 12. Referencias y fuentes de trabajo

- **Consigna principal:** `Final Project LIS4062 Autumn 26.pdf` — objetivo, RSA/ECC, evaluación y entrega.
- **Documento de organización:** `SecureSMS_Organizacion_Equipo_v3_Detallada.pdf` — responsabilidades y evidencias de los tres integrantes.
- **Contrato del equipo:** `SecureSMS_Contrato_Integracion_v02_Detallado.pdf` — APIs, formatos, perfiles, pruebas de aceptación y cambios controlados.
- **Artículo [1]:** Bodur y Kara (2015), *Secure SMS Encryption Using RSA Encryption Algorithm on Android Message Application* — arquitectura de envío y restricciones de longitud/base64.
- **Artículo [2]:** Agoyi y Seral (2010), *SMS Security: An Asymmetric Encryption Approach* — tiempos y expansión del ciphertext.
- **Artículo [3]:** Jintcharadze y Abashidze (2023), *Performance and Comparative Analysis of Elliptic Curve Cryptography and RSA* — comparación RSA/ECC y esquemas híbridos.
- **Documento [4]:** `SSL TLS Using Wireshark Autumn 2026.pdf` — asignado a Persona 3, no a la implementación Android.

---

**Criterio final de éxito del frente Android:** se puede instalar la aplicación, gestionar claves públicas y privadas de forma apropiada, seleccionar algoritmo/destinatario, cifrar con el módulo real, enviar y recibir SMS, reconstruir el envelope íntegro, descifrar con la clave correspondiente y presentar evidencia reproducible. Todo lo que falte por razones de hardware, red o integración debe quedar declarado explícitamente.
