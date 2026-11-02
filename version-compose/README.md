# PDF Fácil Pro — primera versión básica

Aplicación nativa en Kotlin y Jetpack Compose para Android 10 o superior. Interfaz en español, sin publicidad, suscripciones, cuentas ni permiso de internet.

## Funciones implementadas

- Abrir PDF con el selector del sistema y leer por páginas.
- Añadir texto sobre una página (fuente Helvetica, 14 puntos).
- Resaltar áreas rectangulares y dibujar firmas manuscritas.
- Deshacer anotaciones, unir otro PDF al final y exportar una copia compatible con lectores PDF.
- Volver a abrir documentos importados desde la lista de recientes.

Los originales no se sobrescriben. Los documentos importados se copian al almacenamiento privado de la app. Las anotaciones están en memoria hasta exportar: guarda una copia antes de cerrar la app. Los recientes muestran las copias importadas, no las exportaciones. El selector del sistema puede ofrecer proveedores en la nube; elige un archivo local para trabajar sin conexión.

## Instalar

Transfiere el APK al teléfono, ábrelo y permite la instalación desde esa fuente cuando Android lo solicite. Es una compilación de desarrollo firmada con una clave de depuración; para distribución estable se necesita una clave de publicación propia.

## Compilar

Abre esta carpeta en Android Studio, instala SDK 35 y usa JDK 17 o 21. Ajusta `local.properties` con tu ruta del SDK (Android Studio lo genera). Ejecuta `./gradlew assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Límites de esta entrega

No incluye OCR, escáner, formularios, imágenes, conversión, zoom ni edición del texto original. Las firmas son dibujos, no firmas digitales certificadas. El resaltado es visual por área, no selección semántica. El texto añadido admite la codificación de Helvetica estándar, incluidos los caracteres habituales del español; otros símbolos pueden impedir la exportación y mostrar un error. Los PDF cifrados o dañados pueden rechazarse. Las páginas giradas se pueden leer, pero la exportación con anotaciones sobre ellas se rechaza para evitar posiciones incorrectas. Los archivos grandes se procesan en memoria y pueden superar la capacidad del dispositivo.

## Comprobación manual en teléfono

1. Abrir un PDF local de varias páginas y avanzar/retroceder.
2. Escribir “Revisión: información, año 2026”, tocar la página y comprobar su posición en el PDF exportado.
3. Resaltar arrastrando en ambas direcciones, firmar y deshacer.
4. Unir un segundo documento, exportar y comprobar el número de páginas en otro lector.
5. Repetir en modo avión; comprobar que el original conserva su contenido.
6. Cancelar los selectores y probar un archivo protegido o incompatible: debe mostrarse un error sin sobrescribirlo.

Compilación de depuración y verificación de firma APK realizadas. Análisis estático Android Lint realizado sin errores; conserva advertencias de versiones de dependencias y de código TLS de Bouncy Castle (la app no tiene permiso de internet). No se ha realizado prueba en dispositivo o emulador en este entorno.

PDFBox Android se distribuye con licencia Apache 2.0; consulta https://github.com/TomRoush/PdfBox-Android. Las dependencias de AndroidX usan Apache 2.0. Conserva los avisos de las bibliotecas al redistribuir.

## GitHub Actions

Esta variante se aloja en `version-compose/` para conservar la aplicación Java existente en la raíz del repositorio. El flujo `Compilar PDF Facil Pro Compose` compila y analiza esta variante en cada cambio relevante. Descarga `PDF-Facil-Pro-Compose-APK` desde Artifacts de una ejecución exitosa. El APK de esta variante tiene un identificador de aplicación distinto al de la versión Java y puede instalarse por separado. Las claves de depuración de GitHub son temporales: las actualizaciones entre ejecuciones podrían requerir desinstalar la versión anterior, lo que borra sus documentos privados; exporta tus archivos primero.
