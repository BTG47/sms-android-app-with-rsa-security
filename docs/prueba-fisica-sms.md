# Ticket 10 · Prueba física de SecureSMS

Estado inicial: **NOT TESTED**. La evidencia del ticket 09 disponible corresponde a dos corridas locales del Samsung. Este protocolo requiere el Samsung y el POCO con servicio SMS. El Huawei queda como auxiliar, fuera de la prueba.

## Preparación

- [ ] Instalar en ambos teléfonos un APK de la misma revisión, con la integración criptográfica del ticket 08. Anotar el commit con `git rev-parse --short HEAD` y comprobar que la pantalla Mensajes abra sin el aviso «modo demo».
- [ ] Confirmar que ambos teléfonos tienen SIM activa, cobertura y servicio SMS. En equipos con dos SIM, seleccionar la SIM que se usará para SMS.
- [ ] Abrir **Mensajes** en ambos dispositivos y conceder los permisos de envío y recepción. Mantener la app abierta durante la prueba.
- [ ] En **Contactos**, generar **Mi clave pública (RSA-2048)** si todavía no existe y agregar al otro teléfono con su número real.
- [ ] Copiar la clave pública Base64 del otro dispositivo e importarla en su contacto con el perfil **RSA-2048**. Comparar la huella SHA-256 completa de la clave importada con la que muestra el dueño en su teléfono, en persona o por otro canal independiente del que transportó la clave. Si coincide, ingresar esa huella y pulsar **Verificar**. Repetir en el otro sentido.

RSA-2048 es el perfil que la pantalla actual permite generar e intercambiar directamente. Los otros perfiles se registran como **NOT TESTED** para esta prueba física mientras no se prueben entre ambos dispositivos.

## Intercambio y criterio de aprobación

1. Desde **Contactos**, abrir **Escribir SMS cifrado** con el contacto verificado. Enviar el texto de prueba indicado en la tabla.
2. Registrar el estado del emisor y los fragmentos mostrados en **Mensajes**. El estado **Aceptado para envío** confirma que la app entregó la solicitud al sistema de telefonía.
3. En el receptor, comprobar que el mensaje llegue a **Descifrado** y que el texto coincida exactamente, incluidos acentos y emoji. Registrar los fragmentos recibidos y la hora local.
4. Repetir en el otro sentido. Si un caso se ejecutó y no llegó a **Descifrado** con el texto exacto, registrar **FAIL**, el último estado observado y el tiempo de espera real. Dejar **NOT TESTED** si aún no se ejecutó.

## Registro de entorno

Commit / versión del APK:

| Equipo | Modelo | Android / API | Operador de la SIM usada |
|---|---|---|---|
| Samsung | SM-S926B | | |
| POCO | 23122PCD1G | | |

| Sentido | Perfil | Texto esperado | Huella verificada | Fragmentos envío / recepción | Estado final del receptor | Resultado | Hora / evidencia |
|---|---|---|---|---|---|---|---|
| Samsung → POCO | RSA-2048 | Prueba Samsung 01 | Pendiente | | | NOT TESTED | |
| POCO → Samsung | RSA-2048 | Prueba POCO 02 · México 🔐 | Pendiente | | | NOT TESTED | |

Conservar para cada dirección una captura del envío y otra del descifrado, junto con un video corto que muestre el intercambio en ambos sentidos. Usar nombres de prueba, ocultar los números telefónicos en las copias para el informe y mantener fuera de la grabación códigos de verificación, notificaciones personales y material privado.

## Cuando esté disponible el POCO

Desde la raíz del repositorio, con depuración USB autorizada, ejecutar:

```bash
bash scripts/benchmark-poco.sh
```

Si cambió el identificador ADB, pasarlo como primer argumento. El comando instala la app y la prueba, mide los cuatro perfiles y copia los CSV y el entorno a la ruta indicada en terminal. La carpeta puede contener corridas anteriores del POCO. Identificar la actual por la ruta de CSV que emite la prueba con la etiqueta `SecureSmsBench` en logcat.

Después, instalar ese mismo APK en el Samsung, abrir **Mensajes**, conceder permisos con **Solicitar permisos** y ejecutar el intercambio físico de la tabla. En **Contactos**, la acción para ir al editor es **Escribir SMS cifrado**.
