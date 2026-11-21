# Decisiones — SecureSMS / frente Android

Identificadores estables. `OBSERVADO` = hecho verificado; `ACEPTADO` = acuerdo del usuario; `PROPUESTO` = pendiente de confirmación.

- **D-001 — Fuente normativa del contrato** (`ACEPTADO` y confirmado en ronda 2 de grill): el usuario envió el PDF v0.2 a Persona 2, quien está de acuerdo; quedó autorizado como versión definitiva. Copia controlada en `docs/contract.md`; ante divergencia prevalece el PDF original. Motivo: evitar dos contratos canónicos y permitir cambios versionados.

- **D-002 — Modo de acompañamiento** (`ACEPTADO`): recursos. Cada bloque entrega un concepto Kotlin/Compose mínimo y un ejercicio corto antes del código. Motivo: objetivo personal de aprender Kotlin desde cero, primera app Android.

- **D-003 — Instalación del toolchain** (`ACEPTADO`): el usuario instala Android Studio y sus componentes personalmente; la IA no descarga ni instala nada. Motivo: control del entorno y de cambios externos.

- **D-004 — Arquitectura ligera** (`PROPUESTO`): Compose + ViewModel, sin Room, sin DI pesado; fachada `CryptoGateway` sobre `MessageCrypto`, codec de fragmentos separado y assembler con expiración. Motivo: el documento maestro prioriza compilación y transporte fiables antes que pulido; detalle en `ARCHITECTURE.md`.

- **D-005 — Adaptador temporal explícito** (`OBSERVADO` del contrato): si `:crypto` no está listo, se usa un doble de prueba aislado en `integration/`, etiquetado como demo, nunca como cifrado real. Motivo: permitir pruebas de transporte sin bloquearse.

- **D-006 — Operaciones Git** (`OBSERVADO`): no hay repo Git inicializado y no se crean commits, ramas, push ni PR sin autorización explícita. Motivo: política de colaboración del entorno.

- **D-007 — Idioma y estilo** (`OBSERVADO` del documento maestro): comentarios profesionales en español, sin emojis; KDoc en API pública; cambios incrementales con pruebas. Motivo: convención acordada del equipo.

- **D-008 — Vocabulario y modelo de dominio** (`ACEPTADO` en ronda 1 de grill): términos canónicos Envelope, Fragmento, SMS y Mensaje; `Contacto` separado de `Clave pública importada`, con verificación tripartita. Detalle en `GLOSSARY.md` y ADR-0001. Motivo: evitar que un booleano confunda importación con identidad verificada.

- **D-009 — Alcance sin corte por fecha** (`ACEPTADO` en ronda 1 de grill): el usuario descartó recortar el frente Android por el cronograma; se construye la app completa y las pruebas físicas con dos teléfonos propios. El Huawei conectado no es necesariamente el dispositivo de prueba. Motivo: prioridad de aprendizaje y corrección sobre fecha.

- **D-010 — Issue tracker y ramas** (`ACEPTADO`): los issues y specs se gestionan en GitHub (`BTG47/sms-android-app-with-rsa-security`) con `gh`; las cinco etiquetas de triage son las canónicas. Al trabajar un ticket se crea una rama `bruno/feature/<nombre_de_rama>`. La creación de rama está autorizada; commits y push siguen requiriendo autorización separada. Motivo: trazabilidad de trabajo por integrante.
