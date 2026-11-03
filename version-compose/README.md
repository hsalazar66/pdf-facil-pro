# PDF Fácil Pro 2.3 beta

Aplicación Android 10+ gratuita, local y en español, en `version-compose/`. Sin suscripción, anuncios ni permiso de internet. La aplicación Java histórica se conserva en la raíz del repositorio.

## Texto e imágenes

Ver, Editar, Anotar y Más organizan las herramientas. Editar → Modificar sustituye un fragmento de texto original del PDF, con opciones de fuente, tamaño, color, negrita y cursiva. Texto nuevo añade contenido aparte. No es un procesador de párrafos como Word.

Editar → Imagen activa la selección de imágenes independientes ya presentes. Toca una y arrastra su marco; al soltar se escribe la nueva posición y se vuelve a mostrar el PDF. Añadir imagen importa una foto, la selecciona y permite moverla del mismo modo. Se puede deshacer y rehacer. El desplazamiento funciona en páginas giradas y solo afecta a la instancia seleccionada, aunque varias compartan el mismo recurso gráfico.

Los elementos gráficos agrupados dentro de objetos Form, imágenes en línea y ciertos PDF complejos pueden no ofrecer selección individual. No se separa una foto escaneada en sus distintos elementos. El marco muestra la posición de destino durante el arrastre y el contenido se actualiza al soltar. Si se mueve contenido fuera de la página puede quedar recortado; usa Deshacer.

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

El flujo Compilar PDF Facil Pro Compose compila y prueba, y después genera release. Si están los secretos, firma y comprueba que el certificado coincide con el público fijado antes de publicar `PDF-Facil-Pro-2.3-Actualizable-APK`. Si faltan, publica exclusivamente un artefacto para firma del propietario: **NO INSTALAR**. No publica una APK con otra clave temporal.

La entrega compilada en GitHub también puede firmarse fuera de GitHub con esa misma clave y verificarse antes de publicar el APK final. La clave y su contraseña nunca deben compartirse en el repositorio público.

## Validación

Siete pruebas JVM con PDFBox real: cuatro de texto original (sustitución, reapertura, caracteres españoles, estilo y restauración), tres de movimiento de imágenes (persistencia, independencia de instancias, coordenadas de páginas giradas y movimientos sucesivos). Un puente exclusivo de pruebas carga los assets reales desde el AAR; no se incluye en la APK.

Pendiente en teléfono: selección/arrastre, oferta automática del visor al iniciar desde icono y desde PDF externo, Ahora no, retorno de Ajustes, elección Siempre, apertura desde Archivos y actualización sobre otra APK firmada con la misma clave. Compilación y pruebas automatizadas no sustituyen estas comprobaciones.

Para compilar: JDK 17, SDK 35, Gradle 8.9; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Las fuentes se descargan al compilar de una revisión fijada y se comprueban con SHA-256; se incluyen en la APK para uso sin conexión.

## Límites y licencias

Sin OCR, redistribución automática de párrafos, firma digital certificada ni conversión a Word. El texto alargado puede solaparse; revisa el resultado. Los PDF protegidos, dañados o muy grandes pueden no ser compatibles.

Motor adaptado de PDF Inspector, Copyright 2026 Shardul Vikram Singh (MIT), PDFBox Android y AndroidX (Apache 2.0), fuentes Liberation con licencia incluida. Avisos y licencias se incluyen en los assets y el repositorio. Información permite consultar el aviso MIT.

El flujo de push/ejecución manual también arranca un emulador Android 10 (API 29) y ejecuta `scripts/android_smoke.py`. Comprueba oferta inicial, rechazo y repetición manual, reinstalación con la misma firma conservando un borrador, oferta tras actualización, apertura externa y arrastre de una imagen. Las capturas se publican como Verificacion-Android-2.3 y usan únicamente documentos sintéticos.
