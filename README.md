# PDF Fácil Pro — versión beta Android (código fuente)

**Estado:** código fuente preparado. NO es un APK. La sintaxis de Java fue revisada, pero el proyecto **no se ha compilado con el SDK de Android ni probado en un celular**. Se necesitan la compilación en GitHub Actions y pruebas reales antes de instalarlo con confianza.

## Menú de la aplicación
- 📂 Abrir PDF: abrir documentos locales y navegar por páginas.
- ✏ Editar / 🖍 Anotar: escribir texto **encima de las páginas**, dibujar y resaltar; deshacer la última anotación, cambiar color.
- ✍ Firmar: dibujar una firma sencilla con el dedo; no es firma digital certificada.
- 📄 Crear PDF / 🔄 Convertir: crear PDF con una o varias imágenes JPG/PNG del celular.
- 🗂 Organizar: unir varios PDF, extraer un rango de páginas y girar todas las páginas.
- 📷 Escanear: capturar una imagen con la cámara y guardarla como PDF. En esta beta la cámara puede devolver una imagen de resolución reducida; para calidad alta usa la cámara habitual y luego «Crear PDF».
- 💾 Guardar copia: exportar como un nuevo PDF, sin modificar el original.
- ↗ Compartir el documento original desde Android.
- ⚙ Información y privacidad: procesamiento local, sin servidor propio ni cuentas.

## Limitaciones importantes
- El PDF exportado se **rasteriza** (páginas como imágenes): puede perder texto seleccionable, vínculos, etiquetas de accesibilidad y campos interactivos.
- No modifica directamente el texto original de los documentos.
- No incorpora OCR, conversión Word/PDF, firma digital certificada, compresión avanzada, edición de formularios ni reordenación de páginas arrastrando.
- No se han validado PDFs protegidos, dañados, con muchas páginas o de gran tamaño.
- El diseño usa tipografía del sistema, no incluye todavía Atkinson Hyperlegible.
- El proyecto no se debe considerar una aplicación final o de producción.

## Compilación desde GitHub
1. Crea o utiliza el repositorio `hsalazar66/pdf-facil-pro`.
2. Sube **el contenido de esta carpeta** a la raíz del repositorio. No subir solo el ZIP: conserva las rutas como `app/src/main/java/com/pdfacil/pro/MainActivity.java` y `.github/workflows/compilar.yml`.
3. Elimina cualquier carpeta mal escrita como `.github/Worflowd` o archivo `Compilar.yml` que haya quedado de intentos anteriores.
4. En `Actions`, selecciona `Compilar PDF Facil Pro` y `Run workflow`.
5. Si la ejecución se completa en verde, abre sus `Artifacts`, descarga `PDF-Facil-Pro-APK`, extrae `app-debug.apk` e instálala en Android.
6. Prueba abrir un PDF, escribir y dibujar, guardar copia y volver a abrir el resultado; prueba también crear PDF desde fotos y unir documentos.

Nota: el compilador necesita conexión para descargar Gradle / SDK y dependencias. En el entorno de preparación no hay SDK de Android ni acceso de red para validar la compilación.

## Privacidad
El código no solicita acceso a internet, no transmite documentos y utiliza selectores de archivos de Android para recibir permisos puntuales. Al compartir un documento con otra aplicación, se aplican las reglas de privacidad de esa aplicación.
