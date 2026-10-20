# HemoCheck

Aplicación Android nativa, gratuita y **sin conexión** para apoyar la **corroboración documental**
entre los registros de serología (convencional y molecular) de un donante y la etiqueta de un
hemocomponente.

> **Límite clínico.** HemoCheck es una herramienta de apoyo. No declara que una unidad sea
> «apta», «liberada» o «segura», no deduce resultados ausentes y no interpreta un campo
> ilegible como negativo. La decisión clínica y la liberación del hemocomponente corresponden al
> personal autorizado según los procedimientos del servicio.

## Qué hace

| Función | Implementación |
|---|---|
| Cámara propia (no abre la cámara de Samsung ni otra app) | CameraX con vista previa en la pantalla, captura a memoria, enfoque al tocar, flash/linterna, guía de encuadre, avisos en vivo de luz y nitidez, varias páginas seguidas |
| Hojas de serología | Detección de bordes, corrección de perspectiva y rotación, imagen optimizada para OCR; la foto original se conserva sin cambios |
| OCR local | ML Kit Text Recognition con el modelo **incluido en el APK** (no se descarga nada) |
| Etiqueta | OCR del número de unidad + lectura del código de barras ISBT 128 como verificación cruzada; el usuario siempre confirma el número antes de buscar |
| Asociación de filas | Ubica identificadores, encabezados de columna y resultados por coordenadas. Ante cualquier duda (dos filas, dos columnas, dos unidades en una fila, baja confianza) **no asocia** y marca la fila como pendiente de revisión |
| Revisión manual | Corrección del identificador y de cada resultado, agregar o descartar filas; todas las correcciones quedan registradas |
| Búsqueda | **Exacta** sobre el identificador canonizado. Los identificadores parecidos (O/0, I/1…) solo se informan como posible error de OCR, nunca como coincidencia |
| Comparación visual | Foto de la etiqueta, recorte ampliado del número, recortes de las filas de las hojas convencional y molecular, resultados documentados, estados y advertencias; acceso a las fotos originales |
| Estados | 13 estados separados (coincidencia exacta, no encontrado, ilegible, pendiente de revisión, reactivo/no reactivo/indeterminado/pendiente documentado, contradictorios, duplicado, error de OCR, incompleto, no verificable), siempre con ícono y texto |
| Historial | Corroboraciones y registros de los últimos 6 meses, búsqueda por identificador exacto, filtro de pendientes, correcciones |
| Seguridad | PIN (PBKDF2 + HMAC de Keystore), huella opcional, bloqueo por inactividad y al salir, `FLAG_SECURE`, sin copias de seguridad, sin permiso de Internet |
| Retención | 6 meses calendario desde la creación; limpieza al abrir y diaria con WorkManager; los vencidos se bloquean aunque el borrado esté pendiente |

## Arquitectura

```
core/   Kotlin puro (sin Android), probado en la JVM
  identificador/   ISBT 128 (DIN + carácter de control ISO 7064 Mod 37-2), numérico, patrón propio, confusiones de OCR
  analisis/        AnalizadorHoja: asociación conservadora fila/columna/resultado
  etiqueta/        AnalizadorEtiqueta: candidato del número de unidad + código de barras
  resultado/       Clasificación del texto documentado y catálogo de determinaciones
  corroboracion/   MotorCorroboracion: estados y avisos (solo coincidencias exactas)
  retencion/       Seis meses calendario, reloj protegido, limpieza recuperable ante fallos
  seguridad/       AES-256-GCM, derivación y verificación del PIN, política de intentos
  imagen/          Calidad (luz, nitidez, reflejos), detección de la hoja, recortes
app/    Android (MVVM ligero con Compose + estado en flujos)
  camara/ (ui)     Cámara integrada con CameraX
  ocr/             ML Kit (texto y códigos de barras)
  imagen/          Perspectiva, rotación y realce con android.graphics
  datos/           Room sobre SQLCipher, imágenes cifradas, repositorio
  seguridad/       Android Keystore, PIN, sesión y bloqueo
  retencion/       Limpieza y WorkManager
  ui/              Pantallas en Jetpack Compose (español)
```

## Protección de datos: qué se garantiza y qué no

- **Imágenes**: cada página (original y procesada) se cifra con AES-256-GCM usando una clave
  aleatoria propia de esa página; el nombre del archivo se usa como dato asociado (AAD), de modo
  que un archivo no puede reemplazarse por otro sin que falle el descifrado. Se guardan en
  `noBackupFilesDir/boveda` con nombres aleatorios. Nunca se escriben en la galería ni en archivos
  temporales en claro: se descifran y recortan solo en memoria.
- **Base de datos**: SQLCipher (AES-256) con `secure_delete`. Su clave es aleatoria y se guarda
  envuelta con una clave AES-256-GCM no exportable de **Android Keystore** (StrongBox si está
  disponible, si no el TEE). Las claves de cada imagen viven dentro de esta base cifrada.
- **PIN**: se guarda solo un verificador PBKDF2-HMAC-SHA256 (200 000 iteraciones, sal aleatoria)
  pasado por un HMAC con clave de Keystore, por lo que no se pueden probar PINs fuera del
  teléfono. Espera creciente después de 5 intentos fallidos.
- **Acceso**: bloqueo al salir de la app o apagar la pantalla y por inactividad (1–10 min). Al
  bloquearse se descartan las fotos aún no guardadas.
- **Pantallas**: `FLAG_SECURE` en toda la app (sin capturas, grabaciones ni vista previa en
  Recientes).
- **Red**: el manifiesto **elimina** el permiso `INTERNET` (que ML Kit agregaría para su registro
  de uso); CI verifica en cada compilación que el APK no lo tenga. No hay analíticas ni publicidad.
- **Copias de seguridad**: `allowBackup=false` y reglas de extracción que excluyen todo.
- **Registros de depuración**: la app no usa `Log`; una prueba automática lo verifica.

**Límites honestos.** Las claves no exigen autenticación del usuario (para que la limpieza
programada funcione en segundo plano): el PIN es un control de acceso de la interfaz, y el
cifrado protege contra la lectura del almacenamiento o de copias. Al vencer un registro se borra
su fila (y con ella la clave de sus imágenes) y luego sus archivos. Esto hace que el contenido
sea irrecuperable para la app y para quien copie el almacenamiento. Pero **no se puede
garantizar el borrado físico de cada bloque** de la memoria flash, por el nivelado de desgaste
del hardware. Atrasar el reloj del teléfono no revive registros vencidos, porque se usa el mayor
instante observado. Adelantarlo sí puede provocar un borrado anticipado.

## Retención de seis meses

- El vencimiento se fija al crear cada registro (`plusMonths(6)` en la zona del teléfono; el 31/08
  vence el último día de febrero) y **no se renueva al consultar**.
- Todas las consultas filtran `vence > ahora`: un dato vencido deja de verse aunque su borrado
  físico todavía no haya ocurrido.
- La limpieza corre al abrir/volver a la app y una vez por día con WorkManager. **Android puede
  demorar las tareas en segundo plano** (ahorro de batería, Doze); por eso también corre al abrir.
- Si un archivo no se puede borrar, queda anotado en `borrados_pendientes` y se reintenta. Los
  archivos huérfanos (p. ej. por un cierre durante un guardado) se eliminan después de una hora.

## Compilar

Requisitos: JDK 17 y Android SDK 35 (Android Studio Ladybug o posterior, o solo las herramientas
de línea de comandos).

```bash
./gradlew :core:test testDebugUnitTest      # pruebas
./gradlew assembleRelease                    # APK de uso: app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug                      # APK de depuración (com.hemocheck.debug)
./gradlew connectedDebugAndroidTest          # pruebas en un teléfono o emulador conectado
```

El núcleo también se puede probar sin el SDK de Android: `gradle -p core test`.

**CI**: `.github/workflows/hemocheck.yml` corre las pruebas, compila los APK, verifica que no
tengan permiso de Internet, ejecuta las pruebas instrumentadas en un emulador en modo avión y
publica el APK en la versión preliminar `hemocheck-ultima-version`.

**Firma**: los APK se firman con `app/hemocheck-pruebas.keystore`, una clave de prueba incluida en
el repositorio para que las actualizaciones se instalen encima de la versión anterior. Para uso
real en el servicio, creá una clave propia y configurá los secretos `HEMOCHECK_KEYSTORE_*`, ya que
quien tenga acceso al repositorio podría firmar una versión con la clave de prueba.

## Instalar en el Samsung Galaxy S24+

1. En el teléfono, abrí
   `https://github.com/georgestrike25-afk/mi-otra-app/releases/download/hemocheck-ultima-version/HemoCheck.apk`
   (o descargá el artifact `HemoCheck-apk-N` desde la pestaña *Actions*).
2. Abrí el archivo descargado. Si Android lo pide, permití «Instalar apps desconocidas» para el
   navegador o «Mis archivos» (Ajustes → Aplicaciones → Acceso especial → Instalar apps
   desconocidas).
3. Si aparece Play Protect, elegí «Más detalles → Instalar de todas formas» (la app no está en
   Google Play).
4. Al abrir HemoCheck: creá el PIN, aceptá el alcance y, opcionalmente, activá la huella en
   Configuración.

## Probar la cámara integrada

1. Inicio → **Verificar hemocomponente**. Aceptá el permiso de cámara.
2. Comprobá que la vista previa aparece **dentro** de HemoCheck (con el marco de encuadre y el
   texto «Vista previa activa»); no debe abrirse la app Cámara.
3. Tocá un punto para enfocar; probá el botón de flash (apagado → automático → encendido →
   linterna).
4. Tomá una foto: aparece «Revisá la foto» con las advertencias de calidad. Probá **Repetir foto** y
   luego **Usar foto**.
5. Para hojas: Inicio → **Registrar serología** → Fotografiar hoja → «Usar y tomar otra» para
   capturar varias páginas sin salir de la cámara.

## Primera prueba con documentos ficticios

1. Abrí `docs/prueba/documentos-ficticios.html` en una computadora e imprimilo (A4, sin escalar).
2. **Registrar serología** → *Serología convencional* → fotografiá la página 1 apoyada sobre una
   mesa oscura → revisá las filas (deberían verse los cinco identificadores; los dos 000105 se
   marcan como repetidos) → marcá «Revisé esta fila» en las que estén bien → **Guardar registro**.
3. Tocá «Registrar ahora las hojas de Serología molecular» y repetí con la página 2.
4. **Verificar hemocomponente** con cada etiqueta de la página 3 y comprobá los resultados
   esperados que figuran en la primera página del documento.

## Pruebas automatizadas

- `core` (JVM): identificadores y caracteres parecidos, tablas y columnas, varias unidades por
  página y por fila, texto con baja confianza, duplicados, ausentes, ambiguos, valores sin
  interpretar, inclinación, estados de corroboración, código de barras, seis meses calendario,
  limpieza y reintento, reloj protegido, AES-GCM (alteración, clave y AAD incorrectos), PIN,
  calidad de imagen, bordes y recortes, y revisión estática de privacidad.
- `app/src/test` (Robolectric): guardado cifrado, búsqueda exacta, pendientes, duplicados y
  similares, correcciones, bloqueo de vencidos, limpieza de originales y derivados, PIN
  incorrecto y espera, descarte de borradores al bloquear, pantallas de PIN y permiso denegado.
- `app/src/androidTest` (emulador en CI, modo avión): vista previa y captura dentro de la app sin
  abrir otra aplicación; flujo completo con documentos sintéticos (OCR real, guardado con SQLCipher
  y Keystore, búsqueda y corroboración).

Todas las pruebas usan datos ficticios (identificadores `X9999 26 0001xx`).

## Estado de la versión 0.1 y próximos pasos

Verificado en CI (corrida 4): 74 pruebas del núcleo, 17 pruebas Robolectric y 2 pruebas
instrumentadas en un emulador Android 14 en modo avión, todas exitosas. APK sin permiso de
Internet.

Problemas conocidos:
- El APK pesa ~100 MB porque incluye los modelos de OCR y códigos de barras para todas las
  arquitecturas. Se puede reducir limitándolo a `arm64-v8a`.
- La cámara se probó en emulador (escena virtual), **todavía no en un S24+ real**.
- Los umbrales de nitidez, luz y confianza son orientativos: hay que calibrarlos con planillas
  y etiquetas reales del servicio (sin usar datos reales en el repositorio).
- La detección de bordes necesita un fondo más oscuro que la hoja.
- El texto manuscrito se reconoce mal.
- El desbloqueo biométrico no está vinculado criptográficamente a las claves.
- La clave de firma de prueba está en el repositorio: para uso institucional, usar una propia.

Siguientes pasos recomendados: probar en el S24+ con los documentos de `docs/prueba`, calibrar
con planillas representativas, adaptar el catálogo de determinaciones a los formularios del
servicio, reducir el tamaño del APK, y agregar exportación de informes y auditoría de accesos.
