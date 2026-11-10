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

## Firma permanente y actualizaciones

Los APK anteriores de depuración se firmaron con claves temporales distintas, por lo que Android puede exigir desinstalarlos. Esas claves antiguas no están disponibles y no se pueden reconstruir desde el APK. Para migrar a esta nueva firma, exporta tus documentos antes de retirar la versión anterior.

La clave permanente nueva se conserva fuera del repositorio. Su certificado público está fijado en `signing-certificate.sha256`. Las entregas siguientes deben conservar applicationId `com.pdffacil.pro`, esa misma clave y un versionCode creciente. Entonces Android podrá actualizar sin borrar los datos; la confirmación de instalación continúa siendo responsabilidad del sistema.

Para automatizar la firma en GitHub, el propietario debe crear dos Repository secrets en Settings → Secrets and variables → Actions:

- `ANDROID_KEYSTORE_BASE64`: archivo base64 de la clave permanente.
- `ANDROID_KEYSTORE_PASSWORD`: contraseña de esa misma clave.

El alias fijo es `pdffacilpro`. El respaldo privado se entrega al propietario por separado y nunca se sube al repositorio ni a Releases. La conexión usada para editar el código no tiene permiso para configurar esos secretos (HTTP 403).

El flujo Compilar PDF Facil Pro Compose compila, ejecuta pruebas y firma la versión release usando los secretos. Comprueba el certificado permanente y verifica en un emulador una actualización real desde la APK 2.3 publicada, conservando borradores. Si falta la firma o falla una comprobación, no publica la nueva APK. No crea claves de sustitución.

## Validación

Las pruebas JVM usan PDFBox real: sustitución y reapertura del texto, caracteres españoles y estilo; movimiento, tamaño, giro, recorte e independencia de imágenes; cortar y pegar; inserción de hojas y conversión del área de recorte a píxeles. Un puente exclusivo de pruebas carga los assets reales del AAR y no se incluye en la APK.

El flujo de push/ejecución manual arranca Android 10 API 29. Comprueba la oferta de visor, reinstalación y apertura externa; mueve, amplía, gira, recorta, copia, corta y pega imágenes; inserta una hoja; corta y pega texto; exporta el documento al almacenamiento. Una prueba JVM reabre ese PDF exportado y verifica sus páginas, imágenes y texto. Otro emulador instala la APK release 2.3 y la actualiza con la release 2.4 sin borrar datos. Las capturas y resultados quedan como artefactos de GitHub Actions. La entrega se publica solo si pasan esas comprobaciones.

Para compilar: JDK 17, SDK 35, Gradle 8.9; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Las fuentes se descargan al compilar de una revisión fijada y se comprueban con SHA-256; se incluyen en la APK para uso sin conexión. Conviene comprobar también en el teléfono la elección Siempre, el regreso desde Ajustes y el envío a las aplicaciones que tenga instaladas el usuario.

## Límites y licencias

Sin OCR, redistribución automática de párrafos, firma digital certificada ni conversión a Word. El texto alargado puede solaparse; revisa el resultado. Los PDF protegidos, dañados o muy grandes pueden no ser compatibles.

Motor adaptado de PDF Inspector, Copyright 2026 Shardul Vikram Singh (MIT), PDFBox Android y AndroidX (Apache 2.0), fuentes Liberation con licencia incluida. Avisos y licencias se incluyen en los assets y el repositorio. Información permite consultar el aviso MIT.

