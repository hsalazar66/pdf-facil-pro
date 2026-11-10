# PDF Fácil Pro 2.6 beta

Android 10+, español, sin suscripción ni anuncios. Compilación de prueba en GitHub. Exporta los borradores a Descargas antes de desinstalar una versión con firma distinta.

## Cambios 2.6: tablas editables

Las tablas nuevas se guardan como objetos PDF independientes con datos de celdas. Más → Tabla o Editar → Tablas activa sus controles; toca una celda para modificarla. Cada celda acepta texto, números e imágenes (también texto junto a imagen). El contenido se conserva al guardar, reabrir, mover o cambiar el ancho. Usa Editar celdas para añadir/quitar filas o columnas.

Arrastra la tabla para moverla y su esquina inferior derecha para cambiar el ancho. Se previsualiza el PDF durante el gesto y se confirma al soltar, con Deshacer/Rehacer. Autoajustar distribuye columnas según su contenido; el texto se parte en líneas, las filas crecen y las fotos mantienen sus proporciones. La colocación se limita a los bordes de la página para evitar una tabla diminuta por tocar cerca del margen derecho.

Se reconocen los bloques de tabla generados por la versión 2.5. Su primera modificación los convierte en objetos editables sin duplicar el texto original. Las tablas arbitrarias de otros programas siguen disponibles mediante Seleccionar / Copiar tabla, pero no se promete convertir automáticamente cualquier diseño en un objeto estructurado.

Las imágenes de celdas se reducen a 640 píxeles de lado para limitar memoria. Hasta 20 filas y 10 columnas. El tamaño de letra se ajusta hasta 8 puntos si hace falta; si una tabla no cabe en una página, se conserva la edición y se pide dividir sus filas. Guardar mantiene sus datos; una herramienta externa que aplane o reescriba los objetos puede perder esa estructura editable.

## Funciones 2.5 conservadas

- Imagen → Redimensionar: arrastra la esquina o usa dos dedos, con previsualización del PDF real. Al soltar se guarda una única operación que se puede deshacer. Se puede arrastrar el interior para mover; Terminar cierra el modo. Se sustituyó el deslizador y el botón Aplicar tamaño.
- Editar → Seleccionar varias líneas / tabla: arrastra sobre varias líneas y añade otras áreas. Copiar líneas conserva los saltos de línea y usa el portapapeles del sistema para Word, Excel u otro PDF. Pegar en PDF mantiene los saltos y ajusta las líneas al ancho disponible.
- Copiar tabla: infiere filas y columnas por las posiciones del texto, permite revisar las celdas y copia TSV y HTML al portapapeles. Excel puede conservar las columnas. No detecta perfectamente tablas complejas, celdas combinadas o textos inclinados; revisa y corrige los datos.
- Tablas con texto PDF real, imágenes y cuadrícula vectorial, sin convertir toda la tabla en una captura.
- Más → Exportar → Word editable (.docx): genera un DOCX real con párrafos, saltos de página y tablas simples inferidas por alineación. El texto y las celdas son editables. Es una reconstrucción: cambia la distribución y no incluye las imágenes ni los gráficos originales.
- Más → Exportar → PDF editable (texto): reconstruye las páginas con texto real seleccionable y modificable. No es un escaneo ni texto escrito encima de una imagen. Este modo no conserva imágenes, cuadrículas, firmas ni anotaciones del original. Guardar sigue conservando el PDF completo con sus imágenes.
- Para las páginas sin texto extraíble se usa OCR latino ML Kit incluido en la APK, sin descargar modelos. Las páginas se procesan de una en una, con imágenes de hasta 2200 píxeles por lado. Revisa el reconocimiento, especialmente cifras, acentos, tablas y documentos de poca calidad. Las páginas con texto parcial no pasan automáticamente por OCR.

## Funciones conservadas

Abrir y leer PDF; buscar; reemplazar fragmentos de texto original; añadir texto e imágenes; copiar/cortar/pegar imágenes; mover, recortar y girar imágenes independientes; resaltar y firmar a mano; añadir, girar y eliminar páginas; unir PDF; deshacer/rehacer; compartir por WhatsApp, Drive, correo y el selector Android; guardar mediante el selector de carpetas. Los gráficos dentro de Form y las imágenes en línea pueden no seleccionarse individualmente. No incluye redistribución automática de párrafos como Word ni firma digital certificada.

Las restricciones de modificación y extracción del PDF se respetan. Los documentos se procesan localmente. El manifiesto elimina INTERNET de las bibliotecas incorporadas. Compartir entrega una copia a la aplicación elegida y requiere confirmar allí el destinatario o cuenta.

## Instalación y visor

La APK de prueba se firma automáticamente durante la compilación de GitHub. No requiere secretos ni contiene claves privadas publicadas. Puede necesitar desinstalar versiones con otra firma; guarda antes tus documentos fuera de la app, porque se borran los borradores privados al desinstalar. No se garantiza compatibilidad de actualización entre firmas de depuración de distintas compilaciones. El certificado antiguo de 2.3 queda como referencia y no corresponde a esta entrega.

Al primer inicio tras instalar o actualizar se ofrece configurar el visor. Android exige que el usuario elija PDF Fácil Pro y Siempre; la app no puede imponerse. Las miniaturas e iconos en Descargas los controla el gestor de archivos.

## Compilación y validación

JDK 17, SDK 35 y Gradle 8.9. GitHub ejecuta pruebas JVM, assembleDebug y lintDebug; después prueba gestos y acciones reales en Android API 29. Reabre los PDF exportados y verifica los textos, páginas y tablas del DOCX. Publica el mismo APK probado tras verificar su firma. Las capturas y resultados quedan como artefactos del flujo.

Las fuentes Liberation están fijadas y verificadas por SHA-256 al compilar. Las licencias de PDF Inspector (MIT), PDFBox Android/AndroidX (Apache 2.0) y las fuentes se incluyen en los assets. El OCR incorpora ML Kit de Google; consulta sus términos de servicio y avisos en https://developers.google.com/ml-kit/terms.
