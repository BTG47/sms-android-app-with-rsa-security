# Ticket 09 · Benchmark Android y evidencia

Ejecutar con un teléfono conectado por USB a la vez, con la pantalla activa y sin otras aplicaciones en uso. Repetir en Samsung y POCO con la misma revisión de la app y anotar cualquier diferencia de condiciones.

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.securesms.app.benchmark.SecureSmsBenchmarkTest

adb pull /sdcard/Android/data/com.securesms.app/files/securesms-bench ./resultados-benchmark/
```

Cada corrida crea una carpeta con un identificador nuevo. `entorno.txt` registra el modelo, SoC, versión de Android y configuración de muestreo. `mediciones.csv` contiene una fila por operación y muestra, con tiempos en nanosegundos. Si hay fallos, la prueba finaliza con error tras escribir los resultados. Conserva el CSV de esa corrida para el informe.

## Qué se mide

- **Generación**. Cinco pares nuevos por perfil usando la política de StrongBox y fallback del almacén de producción. Cada par usa un alias temporal que se elimina.
- **Cifrado y descifrado**. Tres iteraciones de calentamiento y 30 muestras por perfil y carga. El cifrado incluye la importación de la pública y el descifrado incluye la recuperación del par de Keystore. Se comprueban tanto el texto como el perfil recuperados.
- **Transporte local**. Tiempos de fragmentación SS1 y reensamblado con el código de producción, verificando igualdad de bytes. Se registra el tamaño del Envelope, los caracteres Base64 y la cantidad de fragmentos SS1. La fila `entrega_red_sms` queda en `NOT TESTED` hasta la prueba física del ticket 10.

Las cargas son «Hola», 160 caracteres ASCII y «México 🔐». La columna `carga_bytes` usa bytes UTF-8. `PASS` indica una operación completada con el resultado esperado, `FAIL` un fallo observado y `NOT TESTED` una fase que quedó sin ejecutar.

## Evidencia para el informe

Para comparar RSA-3072 y ECC P-256 (~128 bits), agrupa por dispositivo, carga y operación. Calcula la mediana y el percentil 95 sobre las muestras `PASS`, divide los nanosegundos entre 1 000 000 para obtener milisegundos y reporta también el número de fallos y muestras efectivas. Para generación agrupa por dispositivo y perfil, ya que la carga no interviene.

| Dispositivo y API | Perfil | Carga en bytes | Operación | Muestras PASS | FAIL | Mediana (ms) | P95 (ms) | Envelope / Base64 / SS1 |
|---|---|---:|---|---:|---:|---:|---:|---|
| | | | | | | | | |

Adjunta los CSV originales y una captura de la tabla o de la ejecución donde se vea el perfil y el resultado. El arnés registra métricas y estados sin exportar claves ni guardar textos de prueba en el CSV.
