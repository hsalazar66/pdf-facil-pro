# PDF Fácil Pro 2.1 beta — edición del texto original

Aplicación Android en español, gratuita, local y sin permiso de internet. Android 10 o superior. Esta variante Kotlin está en `version-compose/` en GitHub; la aplicación Java histórica permanece en la raíz del repositorio.

## Qué cambia frente al anotador 1.x

La pestaña **Editar** analiza las instrucciones originales del PDF. Toca un fragmento marcado con borde verde, modifica su contenido y pulsa **Aplicar cambios**. Se reemplaza la instrucción de texto existente; no se añade una caja superpuesta ni un rectángulo blanco para taparla.

Se puede conservar la fuente original o elegir Sans/Serif, negrita, cursiva, tamaño y color. Si la fuente original carece de caracteres, elige Sans o Serif. **Añadir texto** es una acción independiente para insertar contenido nuevo.

## Menús implementados

- **Ver:** lectura, navegación de páginas, zoom con gestos/botón, ajuste, búsqueda en la página actual y compartir una copia.
- **Editar:** selección y sustitución del texto original, formato, texto nuevo e inserción de una imagen en el centro de la página.
- **Anotar:** resaltado por área y firma manuscrita, sin certificado digital.
- **Herramientas:** unir otro PDF al final, girar y eliminar la página actual.
- **Deshacer/Rehacer:** historial de revisiones de esta sesión.
- **Guardar:** exportar el PDF modificado desde el selector de Android.

El menú corresponde a controles reales. No todas las posibilidades de la maqueta están implementadas: consulta los límites siguientes.

## Cómo comprobar la edición

1. En Inicio, confirma que aparece **Versión 2.1 beta**.
2. Pulsa **Probar edición con un PDF de ejemplo** o abre un PDF con texto seleccionable.
3. Entra en **Editar** y toca «Este texto original se puede seleccionar y modificar.».
4. Cambia la frase. Si la fuente original no admite nuevas letras, selecciona Sans.
5. Pulsa **Aplicar cambios** y **Guardar**.
6. Abre el archivo exportado en otro lector y selecciona/copia el texto: debe contener la nueva frase y ya no la anterior.

## Icono y visor predeterminado

Icono elegido: documento PDF con cursor de texto (opción 2). En el primer inicio se ofrece elegir esta app como visor. Android exige seleccionar **PDF Fácil Pro → Siempre**, si esa opción está disponible. Si otro visor ya es predeterminado, puede ser necesario borrar sus valores predeterminados desde Ajustes. La app no cambia esa preferencia por sí sola ni asegura que el usuario la haya confirmado. El botón Visor predeterminado permite repetir la guía.

## Archivos y privacidad

Los originales externos no se sobrescriben. Cada documento se importa al almacenamiento privado de la aplicación. Los cambios aplicados se guardan como borrador local y aparecen en Recientes; usa Guardar para crear una copia PDF fuera de la app. Deshacer/Rehacer solo conserva el historial de la sesión actual. Las revisiones temporales están en caché y Android puede limpiarlas. No hay cuentas, anuncios, suscripciones ni transmisión de documentos. Al compartir con otra app, esa app recibe el PDF exportado.

## Límites explícitos

- La unidad de edición es un **fragmento de texto del PDF**, que puede ser una palabra, línea o parte de una frase. No incluye redistribución automática de párrafos ni de páginas como Word.
- Al alargar un fragmento puede solaparse con contenido vecino: revisa visualmente el resultado.
- No hay OCR: las páginas escaneadas no se convierten en texto editable.
- El texto dentro de objetos complejos, formularios, imágenes o trazados puede no ser editable. Los PDF dañados, con contraseña o restricciones pueden rechazarse o permitir solo lectura.
- No incluye alineación de párrafos, subrayado tipográfico, conversión a Word, edición de formularios ni reordenación de páginas. No se muestran botones que prometan esas funciones.
- La imagen insertada queda centrada, sin control de arrastre o redimensionado en esta beta.
- Firma y resaltado se incorporan visualmente a la página; no son firmas digitales ni anotaciones semánticas.
- PDFBox y la imagen de vista previa consumen memoria: documentos grandes pueden superar la capacidad del dispositivo.

## Compilación y pruebas

En GitHub Actions, el flujo **Compilar PDF Facil Pro Compose** ejecuta `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Descarga únicamente el artefacto **PDF-Facil-Pro-2.1-Menus-APK**, que contiene `PDF-Facil-Pro-2.1-beta.apk`. Los artefactos antiguos corresponden a versiones anteriores.

Las cuatro pruebas comprueban: reemplazo real al guardar y reabrir sin añadir otra instrucción de texto; tamaño y color sin alterar el fragmento vecino; sustitución de fuente con caracteres españoles conservando otra página; restauración del contenido anterior. Se usa PDFBox real, con un puente de recursos exclusivo para pruebas JVM que lee los mismos assets de su AAR. Ese puente no se incluye en la APK.

La compilación y los ensayos automatizados no sustituyen una prueba de interacción en un teléfono. No hay emulador ni teléfono disponible en este entorno. Quedan pendientes la comprobación visual, los gestos, los selectores externos y la asociación como visor en distintos fabricantes.

La APK es de desarrollo. Las claves de depuración de distintos ejecutores pueden diferir: si Android rechaza la actualización, exporta antes todos los documentos que quieras conservar y desinstala la versión previa; al desinstalar se borran sus borradores privados.

Para compilar en Android Studio: SDK 35, JDK 17, Gradle 8.9; genera `local.properties` con la ruta de tu SDK. También puedes ejecutar `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.

## Créditos y licencias

El análisis y la reescritura del contenido se basan en [PDF Inspector](https://github.com/shardulvs/pdfinspector-android), Copyright 2026 Shardul Vikram Singh, licencia MIT incluida en `LICENCIA-PDF-INSPECTOR-MIT.txt` y en los assets de la APK. Se ha añadido restauración del color tras editar un fragmento para conservar el color del contenido posterior.

PDFBox Android y AndroidX usan Apache 2.0; se incluye el texto de licencia. Las fuentes Liberation incluyen sus avisos y su licencia en `app/src/main/assets/fonts/LICENSE-Liberation.txt`. La app permite consultar información y la licencia MIT desde Inicio → Información.

Las fuentes se descargan solo al compilar desde la revisión `ced9758304bd1310b3cc0b7914794154aa3a5710` de PDF Inspector, verificadas con SHA-256. La APK las incluye y funciona sin descargarlas.

## Diseño 2.1

Menú de modos con iconos Ver, Editar, Anotar y Más (Herramientas). Tarjetas Modificar, Texto nuevo e Imagen; flechas para deshacer/rehacer junto a la ayuda; controles de páginas y botones de lectura con iconos. Colores verde, blanco y gris, sin la selección violeta anterior. Página ajustada al ancho y alto disponibles, centrada y con margen uniforme. Se conserva el motor de edición y sus pruebas de regresión.
