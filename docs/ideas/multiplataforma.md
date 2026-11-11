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

## Versión para Windows (mesada de transfusión): estado
- Maqueta de la idea hecha (5 pantallas navegables, datos ficticios) para mostrar y pedir aprobación.
  **No se programa nada hasta que la aprueben.**
- Idea: lectura de PDF (texto directo o OCR si son imágenes), lector de código de barras USB (funciona
  como teclado), carpeta compartida de red vigilada cada 30 s (solo lectura), sin Internet, sin fotos de
  bolsas, datos cifrados, borrado a los 6 meses. Reutiliza el módulo `:core`.
- Pendiente a futuro (pedido del usuario): **bloqueo para que la app no se copie a otras PC** (que solo
  funcione en la PC que decida el usuario), para proteger la autoría. Se define después de la aprobación.

## Decisiones acordadas para la versión de Windows (noviembre 2026)
- **Una copia de HemoCheck por PC** (carpeta portable por pendrive), cada una con su PIN, su lector de código
  de barras y su propia base cifrada. No se comparte la base entre PC (no se pone en carpeta de red).
- **Todas las PC leen la misma carpeta compartida de PDF**, en solo lectura. La carpeta vive físicamente en
  una PC de hemoterapia (la de la mesada) y las demás entran por la red; la PC que la aloja la lee como local.
- Subcarpetas por servicio: Hemoterapia, Laboratorio_Central, Biologia_Molecular (el tipo SC/NAT se
  reconoce por el contenido). Pedido a Sistemas: carpeta compartida, permisos (laboratorios guardan,
  mesada lee), autorizar el programa, definir conservación de los PDF.
- **Revisión de la carpeta configurable**: seguido en horario de trabajo (p. ej. cada 15 min) o a una hora fija
  (p. ej. 19:00) según decida la jefatura; botón «Revisar ahora». Preferible seguido, para que la serología del
  mismo día esté disponible.
- La marca «revisada» y el historial son **por PC**: no hace falta saber quién marcó cada unidad, así que no se
  hace modo servidor.
- Los PDF llegan por la carpeta (no por mail automático): leer una casilla exige que Sistemas cree una casilla
  dedicada. Mientras tanto se puede arrastrar el PDF a la app. (Corrección: las reglas de Outlook no guardan
  adjuntos solas.)
- Confirmación de la unidad con mouse, Enter o escaneando de nuevo la misma etiqueta; luego vuelve a la pantalla
  de escaneo. Definir: escanear otra bolsa sin confirmar pasa a la nueva; para «No Apto», pedir clic o Enter.
- Pendiente: bloqueo contra copias (lista de PC autorizadas, activación por equipo), tras la aprobación.
