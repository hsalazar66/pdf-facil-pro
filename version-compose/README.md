# PDF Fácil Pro 2.4 beta

Aplicación Android 10+ gratuita, local y en español, en `version-compose/`. Sin suscripción, anuncios ni permiso de internet. La aplicación Java histórica se conserva en la raíz del repositorio.

## Texto e imágenes

Ver, Editar, Anotar y Más organizan las herramientas. Editar → Modificar sustituye un fragmento de texto original del PDF, con opciones de fuente, tamaño, color, negrita y cursiva. Texto nuevo añade contenido aparte. No es un procesador de párrafos como Word.

Editar → Imagen activa la selección de imágenes independientes ya presentes. Toca una y arrastra su marco; al soltar se escribe la nueva posición y se vuelve a mostrar el PDF. Añadir imagen importa una foto, la selecciona y permite moverla del mismo modo. Se puede deshacer y rehacer. El desplazamiento funciona en páginas giradas y solo afecta a la instancia seleccionada, aunque varias compartan el mismo recurso gráfico.

Los elementos gráficos agrupados dentro de objetos Form, imágenes en línea y ciertos PDF complejos pueden no ofrecer selección individual. No se separa una foto escaneada en sus distintos elementos. El marco muestra la posición de destino durante el arrastre y el contenido se actualiza al soltar. Si se mueve contenido fuera de la página puede quedar recortado; usa Deshacer.

Compartir y guardar ofrece WhatsApp (personal o Business), Google Drive, correo, otras aplicaciones y Guardar en el celular. Android entrega una copia del PDF editado mediante una URI temporal con permiso de lectura. La cuenta, carpeta y destinatario se confirman en la aplicación elegida; si falta ese destino se abre el selector general. Guardar en el celular usa el selector de documentos de Android, inicialmente en Descargas, sin pedir acceso a todo el almacenamiento.

Editar → Imagen → Editar imagen permite ampliar o reducir conservando proporciones, girar 90° y recortar con una selección sobre la previsualización. Mover, girar y redimensionar conservan el recurso original; recortar crea una imagen PNG para esa instancia. La decodificación de imágenes se limita a 2000 píxeles por lado para reducir el consumo de memoria. Copiar/cortar imágenes usa el portapapeles de Android; Pegar permite elegir su posición tocando la página. Las restricciones de extracción del PDF se respetan.

En Modificar, Copiar y Cortar utilizan la selección del campo de texto o el fragmento completo si no hay selección. Cortar elimina ese contenido del PDF; Pegar inserta texto del portapapeles en el campo o permite colocarlo en la página. Más → Hoja nueva añade una hoja antes, después o al final, del mismo tamaño y orientación que la actual, A4 o Carta. Todas estas operaciones permiten deshacer y rehacer.

También incluye zoom, búsqueda en la página, compartir, resaltado por área, firma manuscrita, unión de PDF y giro/eliminación de páginas. Los borradores se guardan dentro de la app; Guardar crea una copia externa sin sobrescribir el original. La desinstalación borra los borradores internos: expórtalos antes.

## Visor predeterminado

Al abrir la app en primer plano tras instalar o actualizar se ofrece configurar el visor, incluso si se ha iniciado desde un PDF externo. Se usa versionCode + lastUpdateTime para detectar una instalación/actualización nueva. Ahora no evita repetir la oferta hasta otra instalación/actualización. La tarjeta Abrir PDF siempre aquí permite repetirla manualmente.

Configurar ahora consulta el visor que Android resuelve para un PDF. Si ya es otro, ofrece abrir los ajustes de esa aplicación para borrar sus valores predeterminados. Al volver permite abrir un ejemplo y elegir PDF Fácil Pro → Siempre, si Android lo ofrece. La app observa de nuevo la resolución al volver al primer plano: nunca registra que esté configurada solo porque se pulsó aceptar.

Android exige la confirmación del usuario y no permite a esta app imponerse como visor. Las apps con lectores internos pueden ignorar la asociación. El icono que muestra Descargas/Archivos lo decide ese gestor: puede mantener la miniatura o un icono PDF genérico. El icono de PDF Fácil Pro aparece en el selector de aplicaciones; esta app no puede sustituir los iconos de otro gestor.

## Instalación de esta entrega de prueba

Por decisión del propietario, la entrega 2.4 utiliza la APK de depuración compilada y firmada automáticamente en GitHub. No requiere configurar secretos. El flujo comprueba su firma con apksigner y publica el mismo archivo probado en el emulador, sin modificarlo.

**La firma de esta APK difiere de la versión 2.3.** Exporta tus documentos a una carpeta fuera de la app antes de desinstalar la anterior e instalar la 2.4. Desinstalar borra los borradores privados. No se garantiza que próximas compilaciones con una firma de prueba puedan actualizar esta entrega. Android requiere la confirmación de instalación.

El certificado público de la antigua firma permanente se conserva en `signing-certificate.sha256` para referencia; no corresponde a la firma de esta entrega. No se publica ninguna clave privada. Para retomar actualizaciones compatibles con la 2.3 se necesitaría su respaldo de clave privada y contraseña.

## Validación

Las pruebas JVM usan PDFBox real: sustitución y reapertura del texto, caracteres españoles y estilo; movimiento, tamaño, giro, recorte e independencia de imágenes; cortar y pegar; inserción de hojas y conversión del área de recorte a píxeles. Un puente exclusivo de pruebas carga los assets reales del AAR y no se incluye en la APK.

El flujo de push/ejecución manual arranca Android 10 API 29. Comprueba la oferta de visor, reinstalación y apertura externa; mueve, amplía, gira, recorta, copia, corta y pega imágenes; inserta una hoja; corta y pega texto; exporta el documento al almacenamiento. Una prueba JVM reabre ese PDF exportado y verifica sus páginas, imágenes y texto. La entrega de prueba exige reinstalar al cambiar de la firma 2.3; el script de actualización con la antigua firma queda como referencia y no se ejecuta en este flujo. Las capturas y resultados quedan como artefactos de GitHub Actions. La entrega se publica solo si pasan esas comprobaciones.

Para compilar: JDK 17, SDK 35, Gradle 8.9; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Las fuentes se descargan al compilar de una revisión fijada y se comprueban con SHA-256; se incluyen en la APK para uso sin conexión. Conviene comprobar también en el teléfono la elección Siempre, el regreso desde Ajustes y el envío a las aplicaciones que tenga instaladas el usuario.

## Límites y licencias

Sin OCR, redistribución automática de párrafos, firma digital certificada ni conversión a Word. El texto alargado puede solaparse; revisa el resultado. Los PDF protegidos, dañados o muy grandes pueden no ser compatibles.

Motor adaptado de PDF Inspector, Copyright 2026 Shardul Vikram Singh (MIT), PDFBox Android y AndroidX (Apache 2.0), fuentes Liberation con licencia incluida. Avisos y licencias se incluyen en los assets y el repositorio. Información permite consultar el aviso MIT.

