# SecureSMS — Alcance aceptado del frente Android

Objetivo: construir exclusivamente el módulo `:app` de SecureSMS (LIS4062) en Kotlin: interfaz, permisos, claves locales, importación de claves públicas, transporte SMS real, codificación/fragmentación/reconstrucción y adaptador a `:crypto`.

## Éxito observable
- `:app` compila, instala y arranca.
- Cifra con la API real, envía y recibe SMS, reconstruye el envelope íntegro y descifra con la clave correcta.
- Evidencia reproducible con estados `PASS / FAIL / NOT TESTED`, distinguiendo emulador de SIM física.

## No-objetivos explícitos
- RSA/ECC/AES/ECDH/HKDF, envelope, serialización binaria ni benchmarks propios (Persona 2).
- Wireshark/TLS, blockchain/Bitcoin, reporte completo (Persona 3).
- Backend, servidor de claves, login, Firebase, Internet, chat grupal, multimedia.
- No presentar SMS emulado como prueba real ni medir latencia de red como tiempo criptográfico.

## Restricciones y convenciones
- Un repositorio, Gradle Kotlin DSL (`*.gradle.kts`); paquete sugerido `com.securesms.app`.
- `:crypto` no depende de Android; `:app` no modifica internamente la biblioteca.
- Todo archivo nuevo o modificado: comentarios profesionales en español, sin emojis; KDoc en API pública; cambios incrementales y pruebas reproducibles.
- Sin secretos ni claves privadas en logs, SMS, capturas ni evidencia.
- Sin commits, ramas, push ni PR sin autorización explícita (no hay repo Git inicializado al 2026-11-21).

## Fuente normativa del contrato
- El usuario declaró como contrato definido: `SecureSMS_Contrato_Integracion_v02_Detallado-1.pdf` (v0.2, 18/11/2026). Es la referencia de trabajo para nombres, formatos y reglas.
- **OBSERVADO:** el propio PDF dice «propuesta para aprobación conjunta» y exige congelar v1 antes de integrar. **PENDIENTE:** confirmar con Persona 2 el congelamiento y crear `docs/contract.md` como copia controlada antes de fijar el parser SMS definitivo.
- Ante divergencia entre el PDF y `ANDROID_IMPLEMENTATION.md`, prevalece el contrato y se resuelve con Persona 2.

## Reglas del contrato que rigen el trabajo (v0.2)
- Perfiles: `RSA_2048`, `RSA_3072`, `ECC_P256`, `ECC_P384`. API en `com.securesms.crypto.api`: `MessageCrypto` y `PublicKeyCodec`.
- `:app` convierte texto a UTF-8 antes de `encrypt()` y desde UTF-8 después de `decrypt()`; el envelope es binario opaco, sin Base64 ni datos SMS.
- Errores públicos: `UnsupportedProfile`, `InvalidKey`, `InvalidEnvelope`, `DecryptionFailed`, `OperationFailed`. Errores de permisos/SIM/fragmentos son de `:app`.
- Envelope v1 (propiedad de Persona 2): `SSMS`, versión `0x01`, byte de perfil, longitudes big-endian, nonce GCM de 12 bytes, tag de 16 bytes, AAD sobre la cabecera, límite inicial 64 KiB. No reimplementar en `:app`.
- Transporte (propiedad de Bruno): `SS1:<messageId>:<partIndex>:<partCount>:<payload>`; `messageId` de 12 hex; índice desde 1; máximo orientativo 99 partes; cada fragmento cabe en un SMS GSM-7 sin doble segmentación; agrupar por remitente + `messageId`; tolerar duplicados, rechazar contradicciones, expirar incompletos; no descifrar envelopes incompletos.
- Registrar bytes del envelope, caracteres Base64 y número de SMS. Separar «aceptado por el sistema» de «recibido/descifrado por el destinatario».
- Claves públicas verificadas por huella SHA-256 en canal independiente; importarlas no las vuelve verificadas. Claves privadas protegidas, nunca exportadas.
- Comparar RSA-3072 con ECC P-256 como nivel ~128 bits; no presentar todos los perfiles como seguridad equivalente.
- Todo cambio de perfiles, API, envelope, HKDF o formato SMS requiere acuerdo de Bruno y Persona 2, actualización del contrato y vectores de prueba.

## Roles
- **Humano (Bruno):** decisiones de alcance, pruebas en dispositivo real con SIM, intercambio y verificación de claves con Persona 2, video y texto académico propio.
- **IA (asistente):** andamiaje Kotlin/Compose, código incremental, pruebas locales y explicaciones didácticas del código que escribe. No toca `:crypto` ni inventa evidencia.

## Interés de aprendizaje
- Objetivo personal: aprender Kotlin desde cero y realizar la primera aplicación Android. El modo de acompañamiento (recursos / diseño guiado / flujo directo) queda por confirmar.
