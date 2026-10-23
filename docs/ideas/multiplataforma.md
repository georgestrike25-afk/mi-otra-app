# Idea pendiente: HemoCheck en PC (Windows/Linux) e iPhone

Estado: **guardada para implementar más adelante** (sin costo: no se paga cuenta de Apple ni servicios).

## Objetivo
- **PC (Windows/Linux):** no se usa la cámara. Sirve para recibir las planillas en PDF
  (convencional y molecular) y pasarlas a los celulares que usan HemoCheck.
- **iPhone:** compañeros del servicio la usarían. Falta definir la mejor forma gratuita.
- **Android:** sigue con el APK nativo (mejor OCR con ML Kit, FLAG_SECURE, Keystore).

## Opción evaluada: app web instalable (PWA)
Ventajas: una sola app para iPhone/Windows/Linux, gratis, sin App Store, funciona sin
conexión después de instalarla, cámara dentro de la página, lectura de PDF.

Contras: OCR web (Tesseract) peor que ML Kit en fotos (bien en PDF digital); no se pueden
bloquear capturas; sin almacén de claves de hardware (cifrado solo por PIN); el navegador
puede borrar los datos; cámara limitada en iPhone; requiere publicarse por HTTPS
(GitHub Pages gratis solo con repositorio público).

Propuesta: camino mixto. APK nativo en Android + PWA para iPhone y PC, compartiendo las
reglas del módulo `:core` (Kotlin puro, compilable a JS/Wasm con Kotlin Multiplatform).
Antes de construirla: probar el OCR web con planillas reales en PDF y en foto.

## Pasar los PDF de la PC a los celulares (seguridad)
- Preferir transferencia local: cable USB o red interna (PC → celular directo).
- Evitar WhatsApp, correo o nubes (quedan copias en servidores de terceros).
- Las planillas tienen números de unidad y resultados (datos de salud): tratarlas como
  confidenciales y borrar el PDF de Descargas después de subirlo a HemoCheck.
- Dentro de HemoCheck las páginas quedan cifradas (AES-256-GCM) y se borran a los 6 meses.

## Pendiente de decidir
- Mecanismo PC → celular sin Internet (USB, carpeta compartida local o código QR por lotes).
- Forma gratuita estable para iPhone (PWA es la única sin costo y sin reinstalar cada 7 días).

## Preferencias del usuario (para retomar)
- Pasar los PDF de la PC al celular **por Wi-Fi o datos móviles**, emparejando con un **código QR**
  (la PC muestra el QR, el celular lo escanea con la cámara integrada y quedan sincronizados).
- Las planillas probablemente lleguen **por mail**: en la PC se descargan y se envían al celular.
- Esquema **mixto**: también se pueden subir los PDF directo desde el celular (ya implementado).
- A resolver con cuidado: hoy el APK **no tiene permiso de Internet** (requisito de seguridad).
  Opciones: (a) transferencia solo en la red local sin Internet, (b) cifrado de extremo a extremo
  con la clave que va en el QR, para que ningún servidor intermedio pueda leer los PDF.

## Criterio de seguridad acordado con el usuario
Las planillas solo tienen números de unidad y resultados, sin datos que identifiquen a la
persona. Para la sincronización PC ↔ celular se prioriza **que funcione simple** sobre la
protección máxima: se puede habilitar Internet solo para esa función. Se mantienen las
protecciones actuales que no estorban (PIN, cifrado local, borrado a los 6 meses).
