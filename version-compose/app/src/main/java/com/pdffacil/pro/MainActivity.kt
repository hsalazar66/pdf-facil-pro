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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
  setContent { MaterialTheme(colorScheme=lightColorScheme(primary=MenuGreen,onPrimary=Color.White,primaryContainer=Color(0xFFE6F3EE),secondary=MenuGreen,secondaryContainer=Color(0xFFE6F3EE),onSecondaryContainer=MenuGreen,background=Color(0xFFF4F7F5),surface=Color.White,onSurface=MenuInk,outline=Color(0xFFD6E1DB))) { PdfApp(incoming) { incoming=null } } }
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
 var movingImages by remember { mutableStateOf(false) }
 var selectedImage by remember { mutableStateOf<Int?>(null) }
 var draggingImage by remember { mutableStateOf<Int?>(null) }
 var imageDelta by remember { mutableStateOf(Offset.Zero) }
 var defaultRequest by remember { mutableIntStateOf(0) }
 var detectedViewer by remember { mutableStateOf<String?>(null) }
 var newValue by remember { mutableStateOf("") }
 var stroke by remember { mutableStateOf(listOf<Offset>()) }
 var pageW by remember { mutableIntStateOf(1) }
 var pageH by remember { mutableIntStateOf(1) }
 var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
 var confirmMessage by remember { mutableStateOf("") }
 var exported by remember { mutableStateOf(true) }
 var about by remember { mutableStateOf(false) }
 var shareMenu by remember {mutableStateOf(false)}
 var newPageDialog by remember {mutableStateOf(false)}
 var imageEditor by remember {mutableStateOf<ImageEditorData?>(null)}
 var resizing by remember {mutableStateOf(false)}
 var selectionPage by remember {mutableStateOf<TextPage?>(null)}
 var selectionIndices by remember {mutableStateOf(emptySet<Int>())}
 var tableMode by remember {mutableStateOf(false)}
 var selectedTable by remember {mutableStateOf<String?>(null)}
 var tableEditor by remember {mutableStateOf<ManagedTable?>(null)}
 var tableCell by remember {mutableStateOf(0 to 0)}
 var tableError by remember {mutableStateOf("")}
 var tableDialog by remember {mutableStateOf(false)}
 var copiedTable by remember {mutableStateOf<List<List<String>>?>(null)}
 var tableToPlace by remember {mutableStateOf<ManagedTable?>(null)}
 var exportDialog by remember {mutableStateOf(false)}
 var pendingExport by remember {mutableStateOf<File?>(null)}
 var pasteContent by remember {mutableStateOf<PasteContent?>(null)}
 fun task(block: suspend () -> Unit) {
  if(busy) return
  busy=true
  scope.launch { try { block() } catch(e:Exception) { status="No se pudo completar: ${e.localizedMessage ?: "PDF incompatible"}" } finally { busy=false } }
 }
 suspend fun refresh(file: File, at: Int=page) {
  val p=withContext(Dispatchers.IO) { preview(file,at) }
  resizing=false;selectedTable=null;selectionPage=null;selectionIndices=emptySet();tableToPlace=null;view=p;page=at;zoom=1f;pan=Offset.Zero;selected=null;selectedImage=null;draggingImage=null;imageDelta=Offset.Zero;stroke=emptyList()
 }
 suspend fun establish(source: File, name: String, existingDraft: File?=null) {
  val pair=withContext(Dispatchers.IO) {
   PDDocument.load(source).use { require(it.numberOfPages>0) }
   val safe=name.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(100)
   val saved=existingDraft ?: File(docs,"${System.currentTimeMillis()}-$safe").also { source.copyTo(it,true) }
   val snapshot=File.createTempFile("sesion-",".pdf",work);source.copyTo(snapshot,true)
   Pair(saved,snapshot)
  }
  refresh(pair.second,0);tableMode=false;tableEditor=null;tableDialog=false;imageEditor=null;shareMenu=false;newPageDialog=false;pasteContent=null;movingImages=false;addingText=false;draft=pair.first;current=pair.second;history=listOf(pair.second);revision=0;title=name;exported=true;tab="Ver";status="Documento abierto · procesamiento local"
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
 suspend fun applyChange(label:String, selectImageAfter:Int?=null, pageAfter:Int?=null, transform:(PDDocument)->Unit) {
  val source=current ?: return
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
  refresh(next,(pageAfter ?: page).coerceIn(0,maxPage))
  if(selectImageAfter!=null && !view!!.images.isEmpty()) selectedImage=selectImageAfter.coerceAtMost(view!!.images.lastIndex)
  withContext(Dispatchers.IO) { draft?.let { next.copyTo(it,true) } }
  history=history.take(revision+1)+next;revision=history.lastIndex;current=next;exported=false;status="$label · borrador guardado en el dispositivo"
 }
 fun change(label:String, selectImageAfter:Int?=null, pageAfter:Int?=null, transform:(PDDocument)->Unit) = task { applyChange(label,selectImageAfter,pageAfter,transform) }
 fun restore(index:Int) = task {
  val target=history[index];refresh(target,page.coerceAtMost(withContext(Dispatchers.IO) { PDDocument.load(target).use { it.numberOfPages-1 } }))
  withContext(Dispatchers.IO) { draft?.let { target.copyTo(it,true) } }
  revision=index;current=target;exported=false;status="Cambio recuperado · guarda una copia para exportar"
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openUri(it) } }
 val save=rememberLauncherForActivityResult(object:ActivityResultContracts.CreateDocument("application/pdf") {
  override fun createIntent(context:android.content.Context,input:String):Intent = super.createIntent(context,input).apply {
   putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"))
  }
 }) { uri ->
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
  if(uri!=null) { movingImages=true;addingText=false;change("Imagen añadida: arrastra su marco para moverla",selectImageAfter=Int.MAX_VALUE) { doc ->
   // Bound decoding before allocation; mobile photos can exceed available memory.
   val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
   context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) }
   var factor=1;while(options.outWidth/factor>2000 || options.outHeight/factor>2000) factor*=2
   val bmp=context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=factor }) } ?: error("Imagen incompatible")
   try { addImage(doc,page,bmp) } finally { bmp.recycle() }
  } }
 }
 fun shareDocument(destination:PdfDestination) = task {
  val source=current ?: return@task
  val uri=withContext(Dispatchers.IO) {preparePdfShare(context,source,title)}
  val found=launchPdfShare(context,uri,destination)
  status=if(found) "Elige el destinatario y confirma en la aplicación de destino." else "Ese destino no está disponible. Elige otra aplicación compatible."
 }
 fun beginPaste() {
  try {
   selectionPage=null;resizing=false;tableToPlace=null
   val payload=readPasteContent(context) ?: error("Copia primero texto o una imagen.")
   pasteContent=payload;tab="Editar";addingText=false;movingImages=false;selectedImage=null
   status="Toca la página donde quieres pegar ${if(payload is PasteContent.Text) "el texto" else "la imagen"}."
  } catch(e:Exception) {status=e.localizedMessage ?: "Portapapeles no disponible"}
 }
 fun copySelectedText(text:String,remaining:String?=null,fragment:TextFragment?=null) = task {
  withContext(Dispatchers.IO) {PDDocument.load(current!!).use {require(it.currentAccessPermission.canExtractContent()) {"El PDF restringe copiar contenido"}}}
  putTextClipboard(context,text)
  if(remaining!=null && fragment!=null) applyChange("Texto cortado; pulsa Pegar para colocarlo") {doc -> replaceExistingText(doc,page,fragment.index,remaining,null,null,null)}
  else status="Texto copiado. Pulsa Pegar y elige su posición."
 }
 fun copySelectedImage(cut:Boolean) {
  val which=selectedImage ?: return
  task {
   val uri=withContext(Dispatchers.IO) {
    PDDocument.load(current!!).use {doc ->
     val bitmap=selectedImageBitmap(doc,page,which)
     try {copyBitmapFile(context,bitmap)} finally {bitmap.recycle()}
    }
   }
   putImageClipboard(context,uri)
   if(cut) applyChange("Imagen cortada; pulsa Pegar para colocarla") {doc -> deleteImage(doc,page,which)}
   else status="Imagen copiada. Pulsa Pegar y elige su posición."
  }
 }
 fun openImageTools() {
  val which=selectedImage ?: return
  task {val bitmap=withContext(Dispatchers.IO) {PDDocument.load(current!!).use {selectedImageBitmap(it,page,which)}};imageEditor=ImageEditorData(which,bitmap)}
 }
 val saveWord=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {uri ->
  val file=pendingExport;pendingExport=null
  if(uri!=null && file!=null) task {try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")!!.use {out->file.inputStream().use {it.copyTo(out)}}};status="Word editable guardado. Revisa el formato y el texto reconocido."} finally {file.delete()}} else file?.delete()
 }
 val saveEditable=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri ->
  val file=pendingExport;pendingExport=null
  if(uri!=null && file!=null) task {try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")!!.use {out->file.inputStream().use {it.copyTo(out)}}};status="PDF editable de texto guardado. Revisa el resultado."} finally {file.delete()}} else file?.delete()
 }
 fun exportEditable(word:Boolean)=task {
  val source=current ?: return@task
  val pages=withContext(Dispatchers.IO) {exportPages(source) {n,total->scope.launch {status="Preparando texto y OCR: página $n de $total"}}}
  require(pages.any {it.cells.any {c->c.text.isNotBlank()}}) {"No se reconoció texto. Prueba con un documento más nítido."}
  val output=withContext(Dispatchers.IO) {File.createTempFile("exportar-",if(word) ".docx" else ".pdf",work).also {if(word) writeDocx(pages,it) else writeEditablePdf(context,pages,it)}}
  pendingExport=output
  val name=title.removeSuffix(".pdf")+"-editable"
  if(word) saveWord.launch(name+".docx") else saveEditable.launch(name+".pdf")
 }
 fun beginSelection()=task {
  val source=current ?: return@task
  val extracted=withContext(Dispatchers.IO) {PDDocument.load(source).use {extractTextPage(it,page)}}
  require(extracted.cells.any {it.text.isNotBlank()}) {"Esta página no tiene texto seleccionable. Exporta a PDF editable para aplicar OCR."}
  tab="Editar";resizing=false;movingImages=false;addingText=false;pasteContent=null;selectionIndices=emptySet();selectionPage=extracted
  status="Arrastra sobre varias líneas. Puedes sumar otras áreas a la selección."
 }
 fun openTableEditor(model:ManagedTable?,cell:Pair<Int,Int> = 0 to 0) {
  tableEditor=model;tableCell=cell;tableError="";tableDialog=true
 }
 fun beginTables() {
  tab="Editar";tableMode=true;resizing=false;movingImages=false;addingText=false;pasteContent=null;selectionPage=null;tableToPlace=null
  val existing=view?.tables ?: emptyList();selectedTable=existing.lastOrNull()?.id
  status="Toca una celda para editar. Arrastra la tabla o su esquina para cambiar el ancho."
  if(existing.isEmpty()) openTableEditor(null)
 }
 fun saveTable(model:ManagedTable,label:String) = task {
  try {applyChange(label) {doc->upsertTable(context,doc,page,model)};selectedTable=model.id;tableMode=true;tableDialog=false;tableEditor=null;tableError=""}
  catch(e:Exception) {tableError=e.localizedMessage ?: "No se pudo guardar la tabla";throw e}
 }
 fun copyMany(asTable:Boolean) {
  val source=selectionPage ?: return
  val cells=source.cells.filterIndexed {index,_->index in selectionIndices}
  if(cells.isEmpty()) {status="Arrastra sobre el texto que quieres copiar.";return}
  val rows=layoutRows(cells)
  if(asTable) copiedTable=tableData(rows)
  else {putTextClipboard(context,rows.joinToString("\n") {it.cells.joinToString(" ") {c->c.text}});status="Varias líneas copiadas. Puedes pegarlas en Word, Excel u otro PDF."}
 }
 fun askBeforeLeaving(action:()->Unit) {
  if(!exported) { confirmMessage="Los cambios están guardados como borrador en Recientes. Puedes exportar una copia PDF antes de cambiar de documento.";confirm=action } else action()
 }
 fun home() { tableMode=false;selectedTable=null;tableEditor=null;resizing=false;selectionPage=null;selectionIndices=emptySet();tableDialog=false;copiedTable=null;tableToPlace=null;exportDialog=false; imageEditor=null;shareMenu=false;newPageDialog=false;pasteContent=null;current=null;view=null;selected=null;recent=docs.listFiles()?.filter { it.extension=="pdf" }?.sortedByDescending { it.lastModified() } ?: emptyList();status="Tus documentos, siempre contigo" }
 BackHandler(enabled=current!=null && !busy) { askBeforeLeaving { home() } }
 LaunchedEffect(incoming,busy) {
  if(busy) return@LaunchedEffect
  incoming?.let { incomingIntent ->
   val uri=incomingIntent.data
   if(uri?.scheme=="content") askBeforeLeaving { openUri(uri) } else status="Abre un PDF local desde Archivos."
   consumeIncoming()
  }
 }
 DefaultViewerPrompt(enabled=!busy && selected==null && !pagesDialog && !search && confirm==null && imageEditor==null && !shareMenu && !newPageDialog && !tableDialog && copiedTable==null && !exportDialog,manualRequest=defaultRequest) { detectedViewer=it }
 Scaffold { padding -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
  Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
   if(current!=null) MenuIconButton("Anterior",enabled=!busy) { askBeforeLeaving { home() } }
   else Image(painterResource(com.pdffacil.pro.R.drawable.ic_pdf_foreground),"Icono de PDF Fácil Pro",Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(10.dp)))
   Column(Modifier.weight(1f).padding(horizontal=6.dp)) {
    Text(if(current==null) "PDF Fácil Pro" else title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=if(current==null) 25.sp else 15.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    Text(if(current==null) "Tus documentos, siempre contigo" else "${if(exported) "PDF" else "Borrador"} · Página ${page+1} de ${view?.count ?: 0}",fontSize=12.sp)
   }
   if(current!=null) MenuIconButton("Compartir",enabled=!busy) {shareMenu=true}
   if(current!=null) Button(onClick={ save.launch(title.removeSuffix(".pdf")+"-editado.pdf") },enabled=!busy,shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)) { EditorIcon("Guardar",Color.White,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Guardar",fontSize=13.sp,fontWeight=FontWeight.SemiBold) }
  }
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  Text(if(status.startsWith("PDF guardado")) "Cambios guardados" else status,fontSize=12.sp,color=if(status.startsWith("No se pudo")) MaterialTheme.colorScheme.error else MenuMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
  if(current==null) {
   DefaultViewerCard(enabled=!busy,detectedPackage=detectedViewer) { defaultRequest++ }
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
   Text("Edición real de texto · Versión 2.6 beta",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
   Text("Recientes",style=MaterialTheme.typography.titleMedium)
   LazyColumn(Modifier.weight(1f)) {
    items(recent.filter { it.name.contains(query,true) }) { f ->
     Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable(enabled=!busy) { askBeforeLeaving { startSession(f,f.name.substringAfter('-'),f) } }) {
      Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       Image(painterResource(com.pdffacil.pro.R.drawable.ic_pdf_foreground),"Documento PDF",Modifier.size(40.dp).background(MenuGreen,RoundedCornerShape(10.dp)))
       Column(Modifier.weight(1f)) { Text(f.name.substringAfter('-'),maxLines=1,fontWeight=FontWeight.SemiBold);Text("En este dispositivo · borrador local",fontSize=12.sp) }
      }
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
    TextButton(onClick={ picker.launch(arrayOf("application/pdf")) },enabled=!busy) { Text("Archivos") }
    TextButton(onClick={ about=true }) { Text("Información") }
   }
  } else {
   ModeMenu(active=tab,enabled=!busy) { mode -> tab=mode;tableMode=false;selectedTable=null;resizing=false;selectionPage=null;selectionIndices=emptySet();tableToPlace=null;pasteContent=null;addingText=false;movingImages=false;selectedImage=null;stroke=emptyList() }
   if(tab=="Editar" && !tableMode) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     MenuAction("Modificar",Modifier.weight(1f),selected=!addingText && !movingImages,enabled=!busy) { pasteContent=null;resizing=false;selectionPage=null;tableToPlace=null;addingText=false;movingImages=false;selectedImage=null }
     MenuAction("Texto nuevo",Modifier.weight(1f),selected=addingText,enabled=!busy) { pasteContent=null;resizing=false;selectionPage=null;tableToPlace=null;addingText=true;movingImages=false;selectedImage=null }
     MenuAction("Imagen",Modifier.weight(1f),selected=movingImages,enabled=!busy) { pasteContent=null;resizing=false;selectionPage=null;tableToPlace=null;movingImages=true;addingText=false }
    }
    if(movingImages) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
     TextButton(onClick={image.launch("image/*")},enabled=!busy) { Text("＋ Añadir imagen") }
     Text(if(selectedImage==null) "Toca una imagen" else "Imagen ${selectedImage!!+1} seleccionada",fontSize=12.sp,color=MenuMuted)
    }
    if(!movingImages && selectionPage==null) Row {
     TextButton(onClick={beginSelection()},enabled=!busy) {Text("Seleccionar varias líneas / tabla")}
     TextButton(onClick={beginTables()},enabled=!busy) {Text("Tablas")}
    }
    if(selectionPage!=null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
     TextButton(onClick={copyMany(false)},enabled=!busy && selectionIndices.isNotEmpty()) {Text("Copiar líneas")}
     TextButton(onClick={copyMany(true)},enabled=!busy && selectionIndices.isNotEmpty()) {Text("Copiar tabla")}
     TextButton(onClick={selectionIndices=emptySet()}) {Text("Limpiar")}
     TextButton(onClick={selectionPage=null;selectionIndices=emptySet()}) {Text("Cerrar selección")}
    }
    if(movingImages) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
     TextButton(onClick={copySelectedImage(false)},enabled=!busy && selectedImage!=null,contentPadding=PaddingValues(horizontal=6.dp)) {Text("Copiar")}
     TextButton(onClick={copySelectedImage(true)},enabled=!busy && selectedImage!=null,contentPadding=PaddingValues(horizontal=6.dp)) {Text("Cortar")}
     TextButton(onClick={resizing=!resizing;status=if(resizing) "Arrastra una esquina o usa dos dedos. El tamaño se guarda al soltar." else "Tamaño confirmado"},enabled=!busy && selectedImage!=null,contentPadding=PaddingValues(horizontal=6.dp)) {Text(if(resizing) "Terminar" else "Redimensionar")}
     TextButton(onClick={openImageTools()},enabled=!busy && selectedImage!=null,contentPadding=PaddingValues(horizontal=6.dp)) {Text("Editar imagen")}
    }
    if(movingImages && view?.images?.isEmpty()==true) Text("No se detectan imágenes independientes. Puedes añadir una con el botón de arriba.",fontSize=12.sp,color=MenuMuted)
    if(!movingImages && !addingText && view?.fragments?.isEmpty()==true) Text("No hay texto seleccionable. Añade texto nuevo o pega contenido.",fontSize=12.sp,color=MenuMuted)
   }
   if(tab=="Editar" && tableMode) {
    val chosen=view?.tables?.firstOrNull {it.id==selectedTable}
    ManagedTableToolbar(chosen!=null,busy,onNew={openTableEditor(null)},onEdit={chosen?.let {openTableEditor(it)}},onFit={chosen?.let {saveTable(it.copy(width=0.82f,fontSize=12f),"Tabla autoajustada")}},onCopy={chosen?.let {table->
     val rows=table.cells.map {r->r.map {it.text}}
     val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
     clipboard.setPrimaryClip(android.content.ClipData.newHtmlText("Datos de tabla",tsv(rows),tableHtml(rows)));status="Datos de tabla copiados para Excel o Word."
    }},onDelete={chosen?.let {model->confirmMessage="¿Eliminar esta tabla y sus celdas? Puedes recuperarla con Deshacer.";confirm={change("Tabla eliminada") {doc->removeManagedTable(doc.getPage(page),model.id)}}}},onClose={tableMode=false;selectedTable=null;tableToPlace=null})
   }
   if(tab=="Anotar") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    MenuAction("Resaltar",Modifier.weight(1f),selected=annotation=="Resaltar",enabled=!busy) { annotation="Resaltar" }
    MenuAction("Firma",Modifier.weight(1f),selected=annotation=="Firma",enabled=!busy) { annotation="Firma" }
   }
   if(tab=="Herramientas") {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     MenuAction("Tabla",Modifier.weight(1f),enabled=!busy) {beginTables()}
     MenuAction("Exportar",Modifier.weight(1f),enabled=!busy) {exportDialog=true}
     MenuAction("Seleccionar",Modifier.weight(1f),enabled=!busy) {beginSelection()}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     MenuAction("Hoja nueva",Modifier.weight(1f),enabled=!busy) {newPageDialog=true}
     MenuAction("Unir PDF",Modifier.weight(1f),enabled=!busy) {merge.launch(arrayOf("application/pdf"))}
     MenuAction("Girar",Modifier.weight(1f),enabled=!busy) {change("Página girada") {doc ->val p=doc.getPage(page);p.rotation=(p.rotation+90)%360}}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     MenuAction("Guardar",Modifier.weight(1f),enabled=!busy) {save.launch(title.removeSuffix(".pdf")+"-editado.pdf")}
     MenuAction("Compartir",Modifier.weight(1f),enabled=!busy) {shareMenu=true}
     MenuAction("Eliminar",Modifier.weight(1f),enabled=!busy && (view?.count ?: 0)>1,danger=true) {
      confirmMessage="¿Eliminar la página ${page+1}? Puedes recuperarla con Deshacer."
      confirm={change("Página eliminada") {doc ->require(doc.numberOfPages>1) {"El PDF debe conservar una página."};doc.removePage(page)}}
     }
    }
   }
   if(tab!="Ver") Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    Text(when(tab) {
     "Editar" -> if(tableToPlace!=null) "Toca para colocar la tabla; se ajustará a la página." else if(tableMode) "Toca una celda; arrastra para mover o cambiar el ancho." else if(selectionPage!=null) "Arrastra para seleccionar líneas." else if(resizing) "Usa una esquina o dos dedos." else if(pasteContent!=null) "Toca donde quieres pegar." else if(movingImages) "Arrastra el marco y suelta para mover la imagen." else if(addingText) "Toca donde quieres escribir." else "Toca una frase para modificarla."
     "Anotar" -> if(annotation=="Firma") "Dibuja tu firma con el dedo." else "Arrastra sobre lo que quieres resaltar."
     else -> "Organiza tu documento."
    },fontSize=12.sp,color=MenuMuted,modifier=Modifier.weight(1f),maxLines=2)
    if(tab=="Editar" && !tableMode) TextButton(onClick={if(pasteContent==null) {selectionPage=null;resizing=false;tableToPlace=null;beginPaste()} else pasteContent=null},enabled=!busy) {Text(if(pasteContent==null) "Pegar" else "Cancelar")}
    MenuIconButton("Deshacer",enabled=!busy && revision>0) { restore(revision-1) }
    MenuIconButton("Rehacer",enabled=!busy && revision<history.lastIndex) { restore(revision+1) }
   }
   BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clipToBounds().background(Color(0xFFE7EEEA)).pointerInput(tab,busy,movingImages,tableMode) {
    if(!busy && tab!="Anotar" && !(tab=="Editar" && (movingImages || tableMode || selectionPage!=null || tableToPlace!=null))) detectTransformGestures { _, delta, scale, _ -> zoom=(zoom*scale).coerceIn(1f,4f);pan=if(zoom>1f) pan+delta else Offset.Zero }
   },contentAlignment=Alignment.Center) {
    view?.let { p ->
     val ratio=p.bitmap.width.toFloat()/p.bitmap.height
     val fittedWidth=minOf(maxWidth-16.dp,(maxHeight-16.dp)*ratio).coerceAtLeast(1.dp)
     Box(Modifier.size(fittedWidth,fittedWidth/ratio).shadow(3.dp,RoundedCornerShape(2.dp)).graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=pan.x,translationY=pan.y)
      .onSizeChanged { pageW=it.width;pageH=it.height }
      .pointerInput(p,tab,addingText,movingImages,pasteContent,busy,selectionPage,resizing,tableToPlace,tableMode) {
       if(!busy && tab=="Editar" && selectionPage==null && !resizing && (!tableMode || tableToPlace!=null)) detectTapGestures { point ->
        val norm=Offset(point.x/pageW,point.y/pageH)
        val pasted=pasteContent
        if(tableToPlace!=null) {
         val data=tableToPlace!!
         saveTable(data.copy(x=norm.x,y=norm.y),"Tabla insertada")
        } else if(pasted!=null) {
         pasteContent=null
         when(pasted) {
          is PasteContent.Text -> change("Texto pegado") {doc -> addMultilineText(context,doc,page,norm,pasted.value)}
          is PasteContent.Image -> {
           movingImages=true
           change("Imagen pegada",selectImageAfter=Int.MAX_VALUE) {doc ->val bitmap=decodePastedImage(context,pasted.uri);try {addImage(doc,page,bitmap,norm)} finally {bitmap.recycle()}}
          }
         }
        } else if(!movingImages && !addingText && p.tables.any {it.rect.contains(norm)}) {
         val table=p.tables.last {it.rect.contains(norm)};tableMode=true;selectedTable=table.id;openTableEditor(table,table.cellAt(norm))
        } else if(movingImages) {
         selectedImage=p.images.filter { it.rect.contains(norm) }.minByOrNull { it.rect.width*it.rect.height }?.ordinal
        } else if(addingText) { addTextAt=norm;newValue="" }
        else {
         val hit=p.fragments.filter { f -> val r=f.rect;norm.x>=r.left-0.006f && norm.x<=r.right+0.006f && norm.y>=r.top-0.006f && norm.y<=r.bottom+0.006f }.minByOrNull { it.rect.width*it.rect.height }
         selected=hit;if(hit==null) status="Toca dentro de un fragmento con borde verde."
        }
       }
      }.pointerInput(p,tab,movingImages,busy,resizing) {
       if(!busy && tab=="Editar" && movingImages && !resizing) detectDragGestures(
        onDragStart={point ->
         val norm=Offset(point.x/pageW,point.y/pageH)
         val hit=p.images.filter { it.rect.contains(norm) }.minByOrNull { it.rect.width*it.rect.height }
         draggingImage=hit?.ordinal;selectedImage=hit?.ordinal;imageDelta=Offset.Zero
        },
        onDragCancel={draggingImage=null;imageDelta=Offset.Zero},
        onDragEnd={
         val which=draggingImage;val delta=imageDelta
         draggingImage=null;imageDelta=Offset.Zero
         if(which!=null && delta.getDistance()>0.001f) change("Imagen movida",selectImageAfter=which) { doc -> moveImage(doc,page,which,delta) }
        }
       ) { event,amount -> if(draggingImage!=null) { event.consume();imageDelta+=Offset(amount.x/pageW,amount.y/pageH) } }
      }.pointerInput(p,tab,annotation,busy) {
       if(!busy && tab=="Anotar") detectDragGestures(onDragStart={stroke=listOf(Offset(it.x/pageW,it.y/pageH))},onDragCancel={stroke=emptyList()},onDragEnd={
        val points=stroke;stroke=emptyList();if(points.size>1) change(if(annotation=="Firma") "Firma añadida" else "Área resaltada") { doc -> addAnnotation(doc,page,annotation,points) }
       }) { change,_ -> change.consume();stroke=stroke+Offset((change.position.x/pageW).coerceIn(0f,1f),(change.position.y/pageH).coerceIn(0f,1f)) }
      }) {
      Image(p.bitmap.asImageBitmap(),"Página ${page+1} del documento",Modifier.fillMaxSize())
      Canvas(Modifier.fillMaxSize()) {
       if(tab=="Editar" && !tableMode && !addingText && !movingImages) p.fragments.forEach { f -> val r=f.rect;drawRect(Color(0x8017685C),Offset(r.left*size.width,r.top*size.height),Size(r.width*size.width,r.height*size.height),style=Stroke(1.5f)) }
       if(tab=="Editar" && movingImages) p.images.forEach { image ->
        val delta=if(draggingImage==image.ordinal) imageDelta else Offset.Zero
        val r=image.rect.translate(delta);val chosen=selectedImage==image.ordinal
        val ink=if(chosen) MenuGreen else Color(0x80146B5B)
        drawRect(ink,Offset(r.left*size.width,r.top*size.height),Size(r.width*size.width,r.height*size.height),style=Stroke(if(chosen) 3f else 1.5f))
        if(chosen) listOf(Offset(r.left,r.top),Offset(r.right,r.top),Offset(r.left,r.bottom),Offset(r.right,r.bottom)).forEach { pt -> drawCircle(ink,5.dp.toPx(),Offset(pt.x*size.width,pt.y*size.height)) }
       }
       val points=stroke.map { Offset(it.x*size.width,it.y*size.height) }
       if(points.size>1) if(annotation=="Resaltar") {
        val a=points.first();val b=points.last();drawRect(Color(0x66FFD200),Offset(minOf(a.x,b.x),minOf(a.y,b.y)),Size(kotlin.math.abs(a.x-b.x),kotlin.math.abs(a.y-b.y)))
       } else points.zipWithNext().forEach { (a,b) -> drawLine(Color(0xFF0D3359),a,b,2f) }
      }
      if(tab=="Editar" && tableMode && tableToPlace==null && !busy) TablePageLayer(current!!,page,p.tables,selectedTable,onSelect={selectedTable=it},onEdit={table,cell->openTableEditor(table,cell)},onCommit={model->saveTable(model,"Tabla movida o redimensionada")},onError={status=it})
      if(tab=="Editar" && !busy) selectionPage?.let {textPage->TextSelectionLayer(textPage,selectionIndices) {selectionIndices=it}}
      if(tab=="Editar" && resizing && !busy) p.images.firstOrNull {it.ordinal==selectedImage}?.let {fragment->
       InteractiveImage(current!!,page,fragment,onCommit={factor,delta->
        task {
         applyChange("Imagen redimensionada",selectImageAfter=fragment.ordinal) {doc->resizeImage(doc,page,fragment.ordinal,factor);moveImage(doc,page,fragment.ordinal,delta)}
         resizing=true
        }
       },onError={status=it})
      }
     }
    }
   }
   Surface(shape=RoundedCornerShape(16.dp),color=Color.White,border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFE1E9E5))) {
    Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
     MenuIconButton("Anterior",enabled=!busy && page>0) { task { current?.let { refresh(it,page-1) } } }
     TextButton(onClick={pagesDialog=true},enabled=!busy) { EditorIcon("Páginas",MenuGreen,Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text("Página ${page+1} de ${view?.count ?: 0}",fontSize=13.sp,fontWeight=FontWeight.SemiBold) }
     MenuIconButton("Siguiente",enabled=!busy && page+1<(view?.count ?: 0)) { task { current?.let { refresh(it,page+1) } } }
    }
   }
   if(tab=="Ver") Row(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
    MenuAction("Buscar",Modifier.weight(1f),enabled=!busy) { search=true }
    MenuAction("Ampliar",Modifier.weight(1f),enabled=!busy) { zoom=(zoom+0.5f).coerceAtMost(4f) }
    MenuAction("Ajustar",Modifier.weight(1f),enabled=!busy) { zoom=1f;pan=Offset.Zero }
    MenuAction("Compartir",Modifier.weight(1f),enabled=!busy) {shareMenu=true}
   }
  }
 } }
 if(selected!=null) {
  val fragment=selected!!
  TextEditorSheet(fragment,onDismiss={selected=null},onCopy={value ->selected=null;copySelectedText(value)},onCut={value,remaining ->selected=null;copySelectedText(value,remaining,fragment)},readClipboard={runCatching {(readPasteContent(context) as? PasteContent.Text)?.value}.getOrNull()},onApply={value,family,bold,italic,size,color ->
   selected=null
   change("Texto original modificado") { doc ->
    val font=if(family=="Original") null else fontFor(context,doc,family,bold,italic)
    replaceExistingText(doc,page,fragment.index,value,font,if(size!=fragment.size) size else null,if(color!=fragment.color) color else null)
   }
  })
 }
 if(exportDialog) ExportDialog(onDismiss={exportDialog=false}) {word->exportDialog=false;exportEditable(word)}
 if(tableDialog) ManagedTableSheet(initial=tableEditor,initialCell=tableCell,externalError=tableError,onDismiss={tableDialog=false},onConfirm={model->
  if(tableEditor!=null) saveTable(model,"Celdas de tabla actualizadas")
  else {tableDialog=false;tableToPlace=model;tab="Editar";tableMode=true;movingImages=false;resizing=false;selectionPage=null;pasteContent=null;addingText=false;status="Toca la página para colocar la tabla. Luego podrás moverla y editar sus celdas."}
 })
 copiedTable?.let {data->TableEditor(initial=data,copyOnly=true,onDismiss={copiedTable=null},onConfirm={rows->
  val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
  clipboard.setPrimaryClip(android.content.ClipData.newHtmlText("Tabla PDF",tsv(rows),tableHtml(rows)));copiedTable=null;status="Tabla copiada. Pégala en Excel o Word."
 })}
 if(shareMenu) ShareDocumentSheet(onDismiss={shareMenu=false}) {destination ->
  shareMenu=false
  if(destination==PdfDestination.DEVICE) save.launch(title.removeSuffix(".pdf")+"-editado.pdf") else shareDocument(destination)
 }
 if(newPageDialog) NewPageDialog(onDismiss={newPageDialog=false}) {position,size ->
  newPageDialog=false
  val at=when(position) {"Antes"->page;"Al final"->view!!.count;else->page+1}
  tab="Editar";movingImages=false;addingText=true;pasteContent=null
  change("Hoja nueva añadida",pageAfter=at) {doc -> insertBlankPage(doc,page,position,size)}
 }
 imageEditor?.let {data -> ImageToolsSheet(data,onDismiss={imageEditor=null},onResize={imageEditor=null;resizing=true;selectedImage=data.ordinal;status="Arrastra una esquina o usa dos dedos; se guarda al soltar."},onRotate={imageEditor=null;change("Imagen girada",selectImageAfter=data.ordinal) {doc ->rotateImage(doc,page,data.ordinal)}},onCrop={area ->imageEditor=null;change("Imagen recortada",selectImageAfter=data.ordinal) {doc ->cropImage(doc,page,data.ordinal,area)}}) }
 if(addTextAt!=null) AlertDialog(onDismissRequest={addTextAt=null},title={Text("Añadir texto nuevo")},text={OutlinedTextField(newValue,{newValue=it.replace('\n',' ')},label={Text("Texto nuevo")},singleLine=true)},confirmButton={TextButton(onClick={val point=addTextAt!!;addTextAt=null;change("Texto nuevo añadido") { doc -> addNewText(context,doc,page,point,newValue) }},enabled=newValue.isNotBlank()) { Text("Añadir") }},dismissButton={TextButton(onClick={addTextAt=null}) { Text("Cancelar") }})
 if(confirm!=null) AlertDialog(onDismissRequest={confirm=null},title={Text("Confirmar")},text={Text(confirmMessage)},confirmButton={TextButton(onClick={val action=confirm;confirm=null;action?.invoke()}) { Text("Continuar") }},dismissButton={TextButton(onClick={confirm=null}) { Text("Volver") }})
 if(pagesDialog) AlertDialog(onDismissRequest={pagesDialog=false},title={Text("Ir a página")},text={LazyColumn { items((1..(view?.count ?: 0)).toList()) { n -> TextButton(onClick={pagesDialog=false;task { current?.let { refresh(it,n-1) } }}) { Text("Página $n") } } }},confirmButton={TextButton(onClick={pagesDialog=false}) { Text("Cerrar") }})
 if(search) {
  var term by remember { mutableStateOf("") }
  AlertDialog(onDismissRequest={search=false},title={Text("Buscar en esta página")},text={Column { OutlinedTextField(term,{term=it},label={Text("Texto a buscar")});view?.fragments?.filter { term.isNotBlank() && it.text.contains(term,true) }?.forEach { f -> TextButton(onClick={search=false;tab="Editar";selected=f}) { Text(f.text,maxLines=2) } } }},confirmButton={TextButton(onClick={search=false}) { Text("Cerrar") }})
 }
 if(about) AlertDialog(onDismissRequest={about=false},title={Text("PDF Fácil Pro 2.6 beta")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
  Text("Editor gratuito, local y sin anuncios. La edición sustituye fragmentos de texto dentro del PDF. Incluye OCR latino local para exportar a Word y PDF de texto. La conversión puede cambiar el formato y requiere revisión.\n\nMotor basado en PDF Inspector, de Shardul Vikram Singh (MIT), y PDFBox Android (Apache 2.0). Fuentes Liberation con sus licencias. Avisos incluidos en la aplicación.")
  Text(context.assets.open("LICENCIA-PDF-INSPECTOR-MIT.txt").bufferedReader().use { it.readText() },fontSize=10.sp)
 }},confirmButton={TextButton(onClick={about=false}) { Text("Cerrar") }})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TextEditorSheet(fragment:TextFragment,onDismiss:()->Unit,onCopy:(String)->Unit,onCut:(String,String)->Unit,readClipboard:()->String?,onApply:(String,String,Boolean,Boolean,Float,Int)->Unit) {
 var value by remember(fragment.index) { mutableStateOf(TextFieldValue(fragment.text,TextRange(fragment.text.length))) }
 var clipboardMessage by remember {mutableStateOf("")}
 var family by remember { mutableStateOf("Original") }
 var bold by remember { mutableStateOf(fragment.font.contains("bold",true)) }
 var italic by remember { mutableStateOf(fragment.font.contains("italic",true) || fragment.font.contains("oblique",true)) }
 var size by remember { mutableStateOf(fragment.size.toString()) }
 var color by remember { mutableIntStateOf(fragment.color) }
 ModalBottomSheet(onDismissRequest=onDismiss) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Text("Editar texto original",style=MaterialTheme.typography.titleLarge)
   Text("${fragment.font} · Fragmento seleccionado",fontSize=12.sp)
   OutlinedTextField(value,{value=it.copy(text=it.text.replace('\n',' ').replace('\r',' '))},label={Text("Contenido del PDF")},modifier=Modifier.fillMaxWidth(),maxLines=4)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    TextButton(onClick={val r=value.selection;onCopy(if(r.collapsed) value.text else value.text.substring(r.min,r.max))},enabled=value.text.isNotEmpty()) {Text("Copiar")}
    TextButton(onClick={val r=if(value.selection.collapsed) TextRange(0,value.text.length) else value.selection;onCut(value.text.substring(r.min,r.max),value.text.removeRange(r.min,r.max))},enabled=value.text.isNotEmpty()) {Text("Cortar")}
    TextButton(onClick={
     val pasted=readClipboard()?.replace('\n',' ')?.replace('\r',' ')
     if(pasted==null) clipboardMessage="Copia texto primero. Para pegar una imagen usa Pegar en la página."
     else {val r=value.selection;value=TextFieldValue(value.text.replaceRange(r.min,r.max,pasted),TextRange(r.min+pasted.length));clipboardMessage=""}
    }) {Text("Pegar")}
   }
   if(clipboardMessage.isNotEmpty()) Text(clipboardMessage,fontSize=12.sp)
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
   Button(onClick={onApply(value.text,family,bold,italic,size.replace(',','.').toFloat(),color)},enabled=size.replace(',','.').toFloatOrNull()?.let { it in 4f..120f }==true,modifier=Modifier.fillMaxWidth()) { Text("Aplicar cambios") }
   TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Cancelar") }
  }
 }
}
