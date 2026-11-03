package com.pdffacil.pro

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdffacil.pro.engine.ContentStreamEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import kotlinx.coroutines.*
import java.io.File

class MainActivity : ComponentActivity() {
 private var incoming by mutableStateOf<Intent?>(null)
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  PDFBoxResourceLoader.init(applicationContext)
  incoming=intent.takeIf { it.action==Intent.ACTION_VIEW }
  setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF17685C),background=Color(0xFFF3F6F5),surface=Color.White)) { PdfApp(incoming) { incoming=null } } }
 }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);if(intent.action==Intent.ACTION_VIEW) incoming=intent }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PdfApp(incoming: Intent?, consumeIncoming: () -> Unit) {
 val context=androidx.compose.ui.platform.LocalContext.current
 val scope=rememberCoroutineScope()
 val docs=remember { File(context.filesDir,"documentos").apply { mkdirs() } }
 val work=remember { File(context.cacheDir,"sesiones").apply { mkdirs() } }
 var recent by remember { mutableStateOf(docs.listFiles()?.filter { it.extension=="pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList()) }
 var current by remember { mutableStateOf<File?>(null) }
 var draft by remember { mutableStateOf<File?>(null) }
 var history by remember { mutableStateOf(listOf<File>()) }
 var revision by remember { mutableIntStateOf(0) }
 var title by remember { mutableStateOf("") }
 var page by remember { mutableIntStateOf(0) }
 var view by remember { mutableStateOf<PagePreview?>(null) }
 var busy by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("Tus documentos, siempre contigo") }
 var tab by remember { mutableStateOf("Ver") }
 var annotation by remember { mutableStateOf("Resaltar") }
 var zoom by remember { mutableFloatStateOf(1f) }
 var pan by remember { mutableStateOf(Offset.Zero) }
 var query by remember { mutableStateOf("") }
 var search by remember { mutableStateOf(false) }
 var pagesDialog by remember { mutableStateOf(false) }
 var selected by remember { mutableStateOf<TextFragment?>(null) }
 var addTextAt by remember { mutableStateOf<Offset?>(null) }
 var addingText by remember { mutableStateOf(false) }
 var newValue by remember { mutableStateOf("") }
 var stroke by remember { mutableStateOf(listOf<Offset>()) }
 var pageW by remember { mutableIntStateOf(1) }
 var pageH by remember { mutableIntStateOf(1) }
 var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
 var confirmMessage by remember { mutableStateOf("") }
 var exported by remember { mutableStateOf(true) }
 var about by remember { mutableStateOf(false) }
 fun task(block: suspend () -> Unit) {
  if(busy) return
  busy=true
  scope.launch { try { block() } catch(e:Exception) { status="No se pudo completar: ${e.localizedMessage ?: "PDF incompatible"}" } finally { busy=false } }
 }
 suspend fun refresh(file: File, at: Int=page) {
  val p=withContext(Dispatchers.IO) { preview(file,at) }
  view=p;page=at;zoom=1f;pan=Offset.Zero;selected=null;stroke=emptyList()
 }
 suspend fun establish(source: File, name: String, existingDraft: File?=null) {
  val pair=withContext(Dispatchers.IO) {
   PDDocument.load(source).use { require(it.numberOfPages>0) }
   val safe=name.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(100)
   val saved=existingDraft ?: File(docs,"${System.currentTimeMillis()}-$safe").also { source.copyTo(it,true) }
   val snapshot=File.createTempFile("sesion-",".pdf",work);source.copyTo(snapshot,true)
   Pair(saved,snapshot)
  }
  refresh(pair.second,0);draft=pair.first;current=pair.second;history=listOf(pair.second);revision=0;title=name;exported=true;tab="Ver";status="Documento abierto · procesamiento local"
  recent=docs.listFiles()?.filter { it.extension=="pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList()
 }
 fun startSession(source: File, name: String, existingDraft: File?=null) = task { establish(source,name,existingDraft) }
 fun openUri(uri:Uri) = task {
   try {
    val info=withContext(Dispatchers.IO) {
     val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "Documento.pdf"
     val copy=File.createTempFile("entrada-",".pdf",work)
     context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
     Pair(copy,name)
    };establish(info.first,info.second)
   } catch(e:Exception) { status="No se pudo abrir el PDF: ${e.localizedMessage}" }
 }
 fun change(label:String, transform:(PDDocument)->Unit) = task {
  val source=current ?: return@task
  val next=withContext(Dispatchers.IO) {
   val result=File.createTempFile("revision-",".pdf",work)
   PDDocument.load(source).use { doc ->
    require(doc.currentAccessPermission.canModify()) { "El documento no permite modificaciones." }
    transform(doc);doc.save(result)
   }
   PDDocument.load(result).use { require(it.numberOfPages>0) }
   result
  }
  val maxPage=withContext(Dispatchers.IO) { PDDocument.load(next).use { it.numberOfPages-1 } }
  refresh(next,page.coerceAtMost(maxPage))
  withContext(Dispatchers.IO) { draft?.let { next.copyTo(it,true) } }
  history=history.take(revision+1)+next;revision=history.lastIndex;current=next;exported=false;status="$label · borrador guardado en el dispositivo"
 }
 fun restore(index:Int) = task {
  val target=history[index];refresh(target,page.coerceAtMost(withContext(Dispatchers.IO) { PDDocument.load(target).use { it.numberOfPages-1 } }))
  withContext(Dispatchers.IO) { draft?.let { target.copyTo(it,true) } }
  revision=index;current=target;exported=false;status="Cambio recuperado · guarda una copia para exportar"
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openUri(it) } }
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
  if(uri!=null) task {
   val source=current ?: return@task
   withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")!!.use { output -> source.inputStream().use { it.copyTo(output) } } }
   exported=true;status="PDF guardado: los cambios están dentro del documento"
  }
 }
 val merge=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) change("Documentos unidos") { doc -> context.contentResolver.openInputStream(uri)!!.use { input -> PDDocument.load(input).use { extra -> for(p in extra.pages) doc.importPage(p) } } }
 }
 val image=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
  if(uri!=null) change("Imagen insertada en el centro de la página") { doc ->
   // Bound decoding before allocation; mobile photos can exceed available memory.
   val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
   context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) }
   var factor=1;while(options.outWidth/factor>2000 || options.outHeight/factor>2000) factor*=2
   val bmp=context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=factor }) } ?: error("Imagen incompatible")
   try { addImage(doc,page,bmp) } finally { bmp.recycle() }
  }
 }
 fun askBeforeLeaving(action:()->Unit) {
  if(!exported) { confirmMessage="Los cambios están guardados como borrador en Recientes. Puedes exportar una copia PDF antes de cambiar de documento.";confirm=action } else action()
 }
 fun home() { current=null;view=null;selected=null;recent=docs.listFiles()?.filter { it.extension=="pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList();status="Tus documentos, siempre contigo" }
 BackHandler(enabled=current!=null && !busy) { askBeforeLeaving { home() } }
 LaunchedEffect(incoming,busy) {
  if(busy) return@LaunchedEffect
  incoming?.let { incomingIntent ->
   val uri=incomingIntent.data
   if(uri?.scheme=="content") askBeforeLeaving { openUri(uri) } else status="Abre un PDF local desde Archivos."
   consumeIncoming()
  }
 }
 Scaffold { padding -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
   if(current!=null) TextButton(onClick={ askBeforeLeaving { home() } },enabled=!busy) { Text("‹ Inicio") }
   else Image(painterResource(com.pdffacil.pro.R.drawable.ic_pdf_foreground),"Icono de PDF Fácil Pro",Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(10.dp)))
   Column(Modifier.weight(1f).padding(horizontal=6.dp)) {
    Text(if(current==null) "PDF Fácil Pro" else title,maxLines=1,fontSize=if(current==null) 25.sp else 15.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    Text(if(current==null) "Tus documentos, siempre contigo" else "${if(exported) "PDF" else "Borrador"} · Página ${page+1} de ${view?.count ?: 0}",fontSize=12.sp)
   }
   if(current!=null) Button(onClick={ save.launch(title.removeSuffix(".pdf")+"-editado.pdf") },enabled=!busy) { Text("Guardar") }
  }
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  Text(status,fontSize=12.sp,color=MaterialTheme.colorScheme.primary,maxLines=3)
  if(current==null) {
   DefaultViewerPrompt(offerAutomatically=incoming==null,enabled=!busy)
   Text("¿Qué quieres hacer hoy?",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
   Button(onClick={ picker.launch(arrayOf("application/pdf")) },enabled=!busy,modifier=Modifier.fillMaxWidth().height(52.dp)) { Text("Abrir PDF",fontSize=17.sp) }
   OutlinedButton(onClick={
    task {
     val demo=withContext(Dispatchers.IO) {
      val file=File.createTempFile("ejemplo-",".pdf",work)
      PDDocument().use { doc ->
       val font=fontFor(context,doc,"Sans",false,false)
       repeat(3) { number ->
        val p=PDPage();doc.addPage(p)
        PDPageContentStream(doc,p).use { s ->
         s.beginText();s.setFont(font,22f);s.newLineAtOffset(50f,730f);s.showText("INFORME DE TRABAJO")
         s.setFont(font,14f);s.newLineAtOffset(0f,-45f);s.showText("Resumen del proyecto")
         s.newLineAtOffset(0f,-32f);s.showText("Este texto original se puede seleccionar y modificar.")
         s.newLineAtOffset(0f,-27f);s.showText("El equipo revisará el informe durante esta semana.")
         s.newLineAtOffset(0f,-27f);s.showText("Página ${number+1} de 3 · PDF de ejemplo");s.endText()
        }
       };doc.save(file)
      };file
     }
     establish(demo,"Informe de trabajo.pdf")
    }
   },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Probar edición con un PDF de ejemplo") }
   OutlinedTextField(query,{query=it},label={Text("Buscar documentos recientes")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("Edición real de texto · Versión 2.0 beta",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
   Text("Recientes",style=MaterialTheme.typography.titleMedium)
   LazyColumn(Modifier.weight(1f)) {
    items(recent.filter { it.name.contains(query,true) }) { f ->
     Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable(enabled=!busy) { askBeforeLeaving { startSession(f,f.name.substringAfter('-'),f) } }) {
      Column(Modifier.padding(14.dp)) { Text(f.name.substringAfter('-'),maxLines=1,fontWeight=FontWeight.SemiBold);Text("En este dispositivo · borrador local",fontSize=12.sp) }
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
    TextButton(onClick={ picker.launch(arrayOf("application/pdf")) },enabled=!busy) { Text("Archivos") }
    TextButton(onClick={ about=true }) { Text("Información") }
   }
  } else {
   Row(Modifier.fillMaxWidth()) { listOf("Ver","Editar","Anotar","Herramientas").forEach { item ->
    FilterChip(selected=tab==item,onClick={tab=item;addingText=false;stroke=emptyList()},enabled=!busy,label={Text(item,fontSize=12.sp)},modifier=Modifier.weight(1f).padding(horizontal=2.dp))
   } }
   if(tab=="Editar") {
    Text(if(addingText) "Toca donde quieres añadir texto nuevo." else "Toca el texto original para modificarlo. Los bordes indican fragmentos editables.",fontSize=12.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     OutlinedButton(onClick={addingText=false},enabled=!busy) { Text("Seleccionar texto") }
     OutlinedButton(onClick={addingText=true},enabled=!busy) { Text("Añadir texto") }
     OutlinedButton(onClick={image.launch("image/*")},enabled=!busy) { Text("Imagen") }
    }
    if(view?.fragments?.isEmpty()==true) Text("No se detectó texto editable en esta página. Puede ser un escaneo o contenido convertido en imágenes/trazados. OCR aún no disponible.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
   }
   if(tab=="Anotar") {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Resaltar","Firma").forEach { option -> FilterChip(annotation==option,{annotation=option},label={Text(option)},enabled=!busy) } }
    Text(if(annotation=="Firma") "Dibuja tu firma con el dedo. Es una firma manuscrita, sin certificado." else "Arrastra sobre un área para resaltarla.",fontSize=12.sp)
   }
   if(tab=="Herramientas") {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     OutlinedButton(onClick={merge.launch(arrayOf("application/pdf"))},enabled=!busy) { Text("Unir PDF") }
     OutlinedButton(onClick={change("Página girada") { doc -> val p=doc.getPage(page);p.rotation=(p.rotation+90)%360 }},enabled=!busy) { Text("Girar página") }
     OutlinedButton(onClick={confirmMessage="¿Eliminar la página ${page+1}? Puedes recuperar este cambio con Deshacer.";confirm={change("Página eliminada") { doc -> require(doc.numberOfPages>1) { "El PDF debe conservar al menos una página." };doc.removePage(page) }}},enabled=!busy && (view?.count ?: 0)>1) { Text("Eliminar página") }
    }
   }
   if(tab!="Ver") Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    TextButton(onClick={restore(revision-1)},enabled=!busy && revision>0) { Text("↶ Deshacer") }
    TextButton(onClick={restore(revision+1)},enabled=!busy && revision<history.lastIndex) { Text("↷ Rehacer") }
   }
   Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color(0xFFE0E5E3)).pointerInput(tab,busy) {
    if(!busy && tab!="Anotar") detectTransformGestures { _, delta, scale, _ -> zoom=(zoom*scale).coerceIn(1f,4f);pan=if(zoom>1f) pan+delta else Offset.Zero }
   },contentAlignment=Alignment.Center) {
    view?.let { p ->
     val ratio=p.bitmap.width.toFloat()/p.bitmap.height
     Box(Modifier.aspectRatio(ratio,matchHeightConstraintsFirst=true).fillMaxHeight().graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=pan.x,translationY=pan.y)
      .onSizeChanged { pageW=it.width;pageH=it.height }
      .pointerInput(p,tab,addingText,busy) {
       if(!busy && tab=="Editar") detectTapGestures { point ->
        val norm=Offset(point.x/pageW,point.y/pageH)
        if(addingText) { addTextAt=norm;newValue="" }
        else {
         val hit=p.fragments.filter { f -> val r=f.rect;norm.x>=r.left-0.006f && norm.x<=r.right+0.006f && norm.y>=r.top-0.006f && norm.y<=r.bottom+0.006f }.minByOrNull { it.rect.width*it.rect.height }
         selected=hit;if(hit==null) status="Toca dentro de un fragmento con borde verde."
        }
       }
      }.pointerInput(p,tab,annotation,busy) {
       if(!busy && tab=="Anotar") detectDragGestures(onDragStart={stroke=listOf(Offset(it.x/pageW,it.y/pageH))},onDragCancel={stroke=emptyList()},onDragEnd={
        val points=stroke;stroke=emptyList();if(points.size>1) change(if(annotation=="Firma") "Firma añadida" else "Área resaltada") { doc -> addAnnotation(doc,page,annotation,points) }
       }) { change,_ -> change.consume();stroke=stroke+Offset((change.position.x/pageW).coerceIn(0f,1f),(change.position.y/pageH).coerceIn(0f,1f)) }
      }) {
      Image(p.bitmap.asImageBitmap(),"Página ${page+1} del documento",Modifier.fillMaxSize())
      Canvas(Modifier.fillMaxSize()) {
       if(tab=="Editar" && !addingText) p.fragments.forEach { f -> val r=f.rect;drawRect(Color(0x8017685C),Offset(r.left*size.width,r.top*size.height),Size(r.width*size.width,r.height*size.height),style=Stroke(1.5f)) }
       val points=stroke.map { Offset(it.x*size.width,it.y*size.height) }
       if(points.size>1) if(annotation=="Resaltar") {
        val a=points.first();val b=points.last();drawRect(Color(0x66FFD200),Offset(minOf(a.x,b.x),minOf(a.y,b.y)),Size(kotlin.math.abs(a.x-b.x),kotlin.math.abs(a.y-b.y)))
       } else points.zipWithNext().forEach { (a,b) -> drawLine(Color(0xFF0D3359),a,b,2f) }
      }
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
    TextButton(onClick={task { current?.let { refresh(it,page-1) } }},enabled=!busy && page>0) { Text("‹ Anterior",fontSize=12.sp) }
    TextButton(onClick={pagesDialog=true},enabled=!busy) { Text("${page+1} / ${view?.count ?: 0}") }
    TextButton(onClick={task { current?.let { refresh(it,page+1) } }},enabled=!busy && page+1<(view?.count ?: 0)) { Text("Siguiente ›",fontSize=12.sp) }
   }
   if(tab=="Ver") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
    TextButton(onClick={search=true},enabled=!busy) { Text("Buscar") }
    TextButton(onClick={zoom=(zoom+0.5f).coerceAtMost(4f)},enabled=!busy) { Text("Zoom +") }
    TextButton(onClick={zoom=1f;pan=Offset.Zero},enabled=!busy) { Text("Ajustar") }
    TextButton(onClick={task {
     val uri=withContext(Dispatchers.IO) {
      val folder=File(context.cacheDir,"compartir").apply { mkdirs() }
      val target=File(folder,"Documento-editado.pdf");current!!.copyTo(target,true)
      androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",target)
     }
     context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);clipData=android.content.ClipData.newRawUri("PDF",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"Compartir PDF"))
    }},enabled=!busy) { Text("Compartir") }
   }
  }
 } }
 if(selected!=null) {
  val fragment=selected!!
  TextEditorSheet(fragment,onDismiss={selected=null},onApply={value,family,bold,italic,size,color ->
   selected=null
   change("Texto original modificado") { doc ->
    val font=if(family=="Original") null else fontFor(context,doc,family,bold,italic)
    replaceExistingText(doc,page,fragment.index,value,font,if(size!=fragment.size) size else null,if(color!=fragment.color) color else null)
   }
  })
 }
 if(addTextAt!=null) AlertDialog(onDismissRequest={addTextAt=null},title={Text("Añadir texto nuevo")},text={OutlinedTextField(newValue,{newValue=it.replace('\n',' ')},label={Text("Texto nuevo")},singleLine=true)},confirmButton={TextButton(onClick={val point=addTextAt!!;addTextAt=null;change("Texto nuevo añadido") { doc -> addNewText(context,doc,page,point,newValue) }},enabled=newValue.isNotBlank()) { Text("Añadir") }},dismissButton={TextButton(onClick={addTextAt=null}) { Text("Cancelar") }})
 if(confirm!=null) AlertDialog(onDismissRequest={confirm=null},title={Text("Confirmar")},text={Text(confirmMessage)},confirmButton={TextButton(onClick={val action=confirm;confirm=null;action?.invoke()}) { Text("Continuar") }},dismissButton={TextButton(onClick={confirm=null}) { Text("Volver") }})
 if(pagesDialog) AlertDialog(onDismissRequest={pagesDialog=false},title={Text("Ir a página")},text={LazyColumn { items((1..(view?.count ?: 0)).toList()) { n -> TextButton(onClick={pagesDialog=false;task { current?.let { refresh(it,n-1) } }}) { Text("Página $n") } } }},confirmButton={TextButton(onClick={pagesDialog=false}) { Text("Cerrar") }})
 if(search) {
  var term by remember { mutableStateOf("") }
  AlertDialog(onDismissRequest={search=false},title={Text("Buscar en esta página")},text={Column { OutlinedTextField(term,{term=it},label={Text("Texto a buscar")});view?.fragments?.filter { term.isNotBlank() && it.text.contains(term,true) }?.forEach { f -> TextButton(onClick={search=false;tab="Editar";selected=f}) { Text(f.text,maxLines=2) } } }},confirmButton={TextButton(onClick={search=false}) { Text("Cerrar") }})
 }
 if(about) AlertDialog(onDismissRequest={about=false},title={Text("PDF Fácil Pro 2.0 beta")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
  Text("Editor gratuito, local y sin anuncios. La edición sustituye fragmentos de texto dentro del PDF. No incluye OCR ni redistribución automática de párrafos.\n\nMotor basado en PDF Inspector, de Shardul Vikram Singh (MIT), y PDFBox Android (Apache 2.0). Fuentes Liberation con sus licencias. Avisos incluidos en la aplicación.")
  Text(context.assets.open("LICENCIA-PDF-INSPECTOR-MIT.txt").bufferedReader().use { it.readText() },fontSize=10.sp)
 }},confirmButton={TextButton(onClick={about=false}) { Text("Cerrar") }})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TextEditorSheet(fragment:TextFragment,onDismiss:()->Unit,onApply:(String,String,Boolean,Boolean,Float,Int)->Unit) {
 var value by remember(fragment.index) { mutableStateOf(fragment.text) }
 var family by remember { mutableStateOf("Original") }
 var bold by remember { mutableStateOf(fragment.font.contains("bold",true)) }
 var italic by remember { mutableStateOf(fragment.font.contains("italic",true) || fragment.font.contains("oblique",true)) }
 var size by remember { mutableStateOf(fragment.size.toString()) }
 var color by remember { mutableIntStateOf(fragment.color) }
 ModalBottomSheet(onDismissRequest=onDismiss) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text("Editar texto original",style=MaterialTheme.typography.titleLarge)
   Text("${fragment.font} · Fragmento seleccionado",fontSize=12.sp)
   OutlinedTextField(value,{value=it.replace('\n',' ').replace('\r',' ')},label={Text("Contenido del PDF")},modifier=Modifier.fillMaxWidth(),maxLines=4)
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Original","Sans","Serif").forEach { f -> FilterChip(family==f,{family=f},label={Text(f)}) } }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
    OutlinedTextField(size,{size=it},label={Text("Tamaño")},singleLine=true,modifier=Modifier.width(95.dp))
    FilterChip(bold,{bold=!bold;if(family=="Original") family="Sans"},label={Text("N",fontWeight=FontWeight.Bold)})
    FilterChip(italic,{italic=!italic;if(family=="Original") family="Sans"},label={Text("Cursiva")})
   }
   Text("Color del texto",fontSize=12.sp)
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    listOf(0xFF172E2B.toInt(),0xFF17685C.toInt(),0xFF163F80.toInt(),0xFFB3261E.toInt(),0xFF000000.toInt()).forEach { c ->
     Box(Modifier.size(36.dp).border(if(color==c) 3.dp else 1.dp,if(color==c) MaterialTheme.colorScheme.primary else Color.LightGray,RoundedCornerShape(18.dp)).padding(5.dp).background(Color(c),RoundedCornerShape(18.dp)).clickable { color=c })
    }
   }
   Text("Se sustituye este fragmento. El texto más largo puede ocupar más espacio; revisa el resultado. Si falta un carácter en la fuente original, selecciona Sans o Serif.",fontSize=12.sp)
   Button(onClick={onApply(value,family,bold,italic,size.replace(',','.').toFloat(),color)},enabled=size.replace(',','.').toFloatOrNull()?.let { it in 4f..120f }==true,modifier=Modifier.fillMaxWidth()) { Text("Aplicar cambios") }
   TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Cancelar") }
  }
 }
}
