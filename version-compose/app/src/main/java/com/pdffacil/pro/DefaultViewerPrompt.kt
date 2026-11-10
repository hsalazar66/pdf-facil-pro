package com.pdffacil.pro

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun DefaultViewerCard(enabled:Boolean, detectedPackage:String?, onClick:()->Unit) {
 val context=LocalContext.current
 Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=Color(0xFFE6F3EE),border=BorderStroke(1.dp,Color(0xFFB8D8CA)),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
   Text("Abrir PDF siempre aquí",style=MaterialTheme.typography.titleMedium,color=MenuGreen)
   Text(if(detectedPackage==context.packageName) "Android reconoce esta app para abrir PDF." else "Elige PDF Fácil Pro y «Siempre» en Android.",fontSize=12.sp,color=MenuInk)
   Text("Configurar visor  ›",fontSize=14.sp,color=MenuGreen)
  }
 }
}

/** Installed/updated timestamp controls the offer. Only Android can set a PDF preference. */
@Composable internal fun DefaultViewerPrompt(enabled:Boolean, manualRequest:Int, onViewerDetected:(String?)->Unit) {
 val context=LocalContext.current
 val prefs=remember { context.getSharedPreferences("preferencias",Context.MODE_PRIVATE) }
 val info=remember(context) { context.packageManager.getPackageInfo(context.packageName,0) }
 val installation="${info.longVersionCode}:${info.lastUpdateTime}"
 val scope=rememberCoroutineScope()
 val report by rememberUpdatedState(onViewerDetected)
 var show by remember { mutableStateOf(false) }
 var launching by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf<String?>(null) }
 var otherPackage by remember { mutableStateOf<String?>(null) }
 var afterSettings by remember { mutableStateOf(false) }
 var returningFromSettings by remember { mutableStateOf(false) }
 val sample=remember { File(context.cacheDir,"visor_demo/Bienvenido-a-PDF-Facil-Pro.pdf") }
 val sampleUri=remember { FileProvider.getUriForFile(context,"${context.packageName}.files",sample) }
 fun viewIntent()=Intent(Intent.ACTION_VIEW).apply {
  setDataAndType(sampleUri,"application/pdf")
  clipData=ClipData.newRawUri("PDF de ejemplo",sampleUri)
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 }
 fun detect():String? {
  val pkg=context.packageManager.resolveActivity(viewIntent(),PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
  val resolved=pkg?.takeUnless { it=="android" || it=="com.android.intentresolver" }
  report(resolved)
  return resolved
 }
 fun dismissOffer() {
  prefs.edit().putString("visor_instalacion_ofrecida",installation).apply()
  show=false
 }
 fun chooseViewer() {
  val existing=detect()
  if(existing!=null && existing!=context.packageName) { otherPackage=existing;return }
  scope.launch {
   launching=true
   try {
    withContext(Dispatchers.IO) {
     sample.parentFile!!.mkdirs()
     PDDocument().use { doc ->
      val page=PDPage();doc.addPage(page)
      PDPageContentStream(doc,page).use { s ->
       s.beginText();s.setFont(PDType1Font.HELVETICA_BOLD,22f);s.newLineAtOffset(50f,720f);s.showText("PDF Fácil Pro")
       s.setFont(PDType1Font.HELVETICA,12f);s.newLineAtOffset(0f,-32f);s.showText("Documento de ejemplo para elegir tu visor PDF.")
       s.newLineAtOffset(0f,-24f);s.showText("En Android, elige PDF Fácil Pro y Siempre, si está disponible.");s.endText()
      };doc.save(sample)
     }
    }
    // A chooser created with createChooser cannot persist an 'Always' preference.
    context.startActivity(viewIntent())
   } catch (_:Exception) { error="No se pudo abrir el ejemplo. Abre un PDF desde Archivos y elige PDF Fácil Pro → Siempre." }
   finally { launching=false }
  }
 }
 DisposableEffect(context) {
  val lifecycle=(context as? ComponentActivity)?.lifecycle
  val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) {
   detect()
   if(returningFromSettings) { returningFromSettings=false;afterSettings=true }
  } }
  lifecycle?.addObserver(observer)
  onDispose { lifecycle?.removeObserver(observer) }
 }
 LaunchedEffect(installation,enabled) {
  detect()
  if(enabled && prefs.getString("visor_instalacion_ofrecida",null)!=installation) show=true
 }
 LaunchedEffect(manualRequest) { if(manualRequest>0) show=true }
 if(show) AlertDialog(
  onDismissRequest={dismissOffer()},
  title={Text("¿Quieres abrir tus PDF con PDF Fácil Pro?")},
  text={Text("Android debe confirmar tu elección. Pulsa Configurar ahora y, cuando aparezca la lista de aplicaciones, selecciona PDF Fácil Pro y «Siempre».\n\nSi ya hay otro visor elegido, te ayudaremos a abrir sus ajustes para borrar esa preferencia.")},
  confirmButton={TextButton(onClick={dismissOffer();chooseViewer()},enabled=enabled && !launching) {Text("Configurar ahora")}},
  dismissButton={TextButton(onClick={dismissOffer()}) {Text("Ahora no")}}
 )
 if(otherPackage!=null) AlertDialog(
  onDismissRequest={otherPackage=null},title={Text("Hay otro visor predeterminado")},
  text={Text("En los ajustes de ese visor entra en «Abrir de forma predeterminada» y borra sus valores predeterminados. Después vuelve aquí para elegir PDF Fácil Pro. Los nombres dependen del celular.")},
  confirmButton={TextButton(onClick={
   val pkg=otherPackage!!;otherPackage=null
   try { returningFromSettings=true;context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",pkg,null))) }
   catch (_:Exception) {returningFromSettings=false;error="Abre Ajustes → Aplicaciones → tu visor actual y borra sus valores predeterminados."}
  }) {Text("Abrir ajustes del visor")}},
  dismissButton={TextButton(onClick={otherPackage=null}) {Text("Volver")}}
 )
 if(afterSettings) AlertDialog(
  onDismissRequest={afterSettings=false},title={Text("Elegir PDF Fácil Pro")},
  text={Text("Ahora abre el ejemplo y selecciona PDF Fácil Pro → Siempre. Si Android sigue abriendo el otro visor, todavía conserva su preferencia.")},
  confirmButton={TextButton(onClick={afterSettings=false;chooseViewer()},enabled=!launching) {Text("Elegir visor")}},
  dismissButton={TextButton(onClick={afterSettings=false}) {Text("Más tarde")}}
 )
 if(error!=null) AlertDialog(onDismissRequest={error=null},title={Text("Configurar visor")},text={Text(error!!)},confirmButton={TextButton(onClick={error=null}) {Text("Entendido")}})
}
