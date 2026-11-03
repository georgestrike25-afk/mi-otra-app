# HemoCheck — memoria del proyecto

App Android nativa (español, gratuita, 100 % local) que corrobora documentalmente la serología
de una unidad de sangre: planilla convencional (SC) + molecular (NAT) contra la etiqueta de la bolsa.
**Límite clínico:** nunca declarar «apto», «liberado» ni «seguro»; es apoyo documental.

## Comunicación con el usuario
- Escribir siempre en español (rioplatense), breve, contando el avance.
- Entregar cada APK **adjunto en el chat** (SendUserFile); el link directo de GitHub no siempre descarga.
- Preferencias: pocas advertencias (solo las relevantes), verificación visual en el collage,
  la seguridad no es prioridad máxima (las planillas solo tienen números, sin datos personales).
- Nunca usar datos reales de pacientes en pruebas, capturas ni repositorio.

## Tecnología
Kotlin 2.0.21, Compose (BOM 2024.12.01), CameraX 1.4.1, ML Kit texto 16.0.1 y barras 17.3.0
(empaquetados, sin red), Room 2.6.1 + SQLCipher 4.6.1, Keystore, AES-256-GCM, WorkManager,
biometric 1.1.0, Robolectric 4.14.1. AGP 8.7.3, Gradle 8.11.1, minSdk 26, targetSdk 35.
Versiones en `gradle/libs.versions.toml` (si cambia Kotlin, actualizar `core/settings.gradle.kts`).

## Estructura
- `core/` — Kotlin puro, sin Android (se prueba localmente):
  `analisis/` (AnalizadorHoja, FilaCompacta, DetectorTipoHoja, CalidadLectura),
  `identificador/` (ISBT 128 Mod 37-2, formato numérico local 295216G → 295216),
  `etiqueta/`, `resultado/` (clasificación REACTIVO/NO REACTIVO, catálogo de determinaciones),
  `corroboracion/` (MotorCorroboracion), `imagen/`, `ocr/`, `geometria/`, `retencion/`, `seguridad/`.
- `app/` — Android: `ui/` (inicio, camara, serologia, etiqueta, comparacion/PantallaCollage,
  historial, ajustes, bloqueo, tema), `datos/` (Repositorio, Ajustes, ArchivosCifrados, db/),
  `imagen/` (Procesador, Recortes, LectorPdf), `ocr/MotorOcr`, `seguridad/`, `retencion/Limpieza`.
- `docs/ideas/multiplataforma.md` — idea pendiente: PC (Windows/Linux) + iPhone (PWA), sincronización por QR.
- `scripts/verificar-version-optimizada.sh` — prueba de arranque del APK optimizado en la CI.

## Compilación y pruebas
- **Local solo se puede probar `core`:** `gradle -p core test` (dl.google.com está bloqueado acá).
- La app compila **solo en GitHub Actions** (`.github/workflows/hemocheck.yml`), en cada push a
  `app/**`, `core/**`, `gradle/**`, `scripts/**` o el workflow:
  `:core:test testDebugUnitTest assembleDebug assembleRelease`, chequeo de que no haya permiso de
  Internet, y job de emulador (API 34, modo avión) con pruebas instrumentadas, capturas de pantalla
  (rama `capturas`) y arranque de la variante `verificacion` (R8, todas las ABI).
- Publica `hemocheck-v0.1.N/HemoCheck-0.1.N.apk` (latest) y `hemocheck-ultima-version/HemoCheck.apk`.
- Release: R8 + shrinkResources, solo arm64-v8a, ~27,6 MB. Firma con `app/hemocheck-pruebas.keystore`.
- Leer logs de CI con las herramientas MCP de GitHub; los artefactos (blob.core.windows.net) no se
  pueden descargar desde acá, por eso las capturas van a la rama `capturas`.
- Rama de trabajo: `claude/hemocheck-android-dev-8ocvji` (repo público `georgestrike25-afk/mi-otra-app`).

## Decisiones importantes
- Indicador principal = «Estado» de la planilla (Apto / No Apto rojo). REACTIVO leído con «Apto» → nota amarilla.
- Collage: franja SC/NAT arriba de cada foto (no tapa números), recorte compacto (ID + Estado + vecinos),
  fila completa con encabezados deslizable, recorte solo de la etiqueta adhesiva.
- Tipo NAT/SC se detecta por encabezados (DetectorTipoHoja); si no se ve, se usa el elegido.
- Duplicados: el mismo día solo se guarda otra foto de la misma unidad/tipo si se lee mejor
  (CalidadLectura); otro día no se repite la misma lectura. Filas de la misma foto con el mismo
  número no se juntan. El collage muestra una por tipo salvo Estados distintos.
- Columna de referencias en orden creciente: número fuera de orden se corrige si un dígito encaja
  (ID_CORREGIDO_POR_ORDEN) o se marca (ID_FUERA_DE_ORDEN). Sin coincidencia → «números parecidos».
- «NO» pegado a la línea del cuadro («INO», «NC», «N», «MO») cuenta como NO REACTIVO.
- Huella activada por omisión; PIN de respaldo. Salida breve al selector de PDF sin bloquear.
- Retención 6 meses con borrado automático; sin permiso de Internet; FLAG_SECURE con modo pruebas.
- Paleta: turquesa #08B5D6, azul #123F68, amarillo #FFC928, carbón #10151D; rojo solo para alertas.

## Problemas conocidos / pendientes
- Fotos borrosas generan lecturas erróneas; la verificación final siempre es visual.
- Las verificaciones guardan un informe: tras un cambio de lógica hay que verificar de nuevo la unidad.
- Pendiente: PDF reales del servicio (función ya implementada), versión iPhone/PC (ver docs/ideas).
