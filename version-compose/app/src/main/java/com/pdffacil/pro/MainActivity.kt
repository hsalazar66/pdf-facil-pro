package com.pdffacil.pro

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.*
import java.io.File

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  PDFBoxResourceLoader.init(applicationContext)
  setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF17685C), background = Color(0xFFF4F7F5))) { Editor() } }
 }
}
private data class Mark(val page: Int, val kind: String, val points: List<Offset>, val text: String = "")

@Composable private fun Editor() {
 val context = androidx.compose.ui.platform.LocalContext.current
 val scope = rememberCoroutineScope()
 var file by remember { mutableStateOf<File?>(null) }
 var title by remember { mutableStateOf("") }
 var page by remember { mutableIntStateOf(0) }
 var count by remember { mutableIntStateOf(0) }
 var pdfWidth by remember { mutableFloatStateOf(612f) }
 var bitmap by remember { mutableStateOf<Bitmap?>(null) }
 var marks by remember { mutableStateOf(listOf<Mark>()) }
 var tool by remember { mutableStateOf("Leer") }
 var text by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("Tus documentos, siempre contigo") }
 var stroke by remember { mutableStateOf(listOf<Offset>()) }
 var width by remember { mutableIntStateOf(1) }
 var height by remember { mutableIntStateOf(1) }
 var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
 var pending by remember { mutableStateOf<Uri?>(null) }
 val recent = remember { mutableStateListOf<File>().apply { addAll(File(context.filesDir, "documentos").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()) } }
 fun runJob(block: suspend () -> Unit) { scope.launch { busy = true; try { block() } catch (e: Exception) { status = "No se pudo completar: ${e.localizedMessage ?: "documento incompatible"}" } finally { busy = false } } }
 fun open(uri: Uri, merge: Boolean) = runJob {
  val dest = withContext(Dispatchers.IO) {
   val originalName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Documento.pdf"
   val safeName = originalName.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(90).removeSuffix(".pdf")
   val dir = File(context.filesDir, "documentos").apply { mkdirs() }
   val result = File(dir, "${safeName}-${System.currentTimeMillis()}.pdf")
   if (merge && file != null) {
    PDDocument.load(file).use { original -> context.contentResolver.openInputStream(uri)!!.use { input -> PDDocument.load(input).use { extra -> for (p in extra.pages) original.importPage(p); original.save(result) } } }
   } else { context.contentResolver.openInputStream(uri)!!.use { input -> result.outputStream().use { input.copyTo(it) } }; PDDocument.load(result).use { require(it.numberOfPages > 0) } }
   result
  }
  file = dest; title = dest.name; page = 0; if (!merge) marks = emptyList(); recent.add(0, dest); status = if (merge) "Documentos unidos. Guarda una copia para exportarlos." else "Documento abierto"
 }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> pending = uri } }
 val merger = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> open(uri, true) } }
 val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
  if (uri != null && file != null) runJob {
   withContext(Dispatchers.IO) {
    PDDocument.load(file).use { doc ->
     for (mark in marks) {
      val p = doc.getPage(mark.page); val box = p.cropBox
      require(p.rotation == 0) { "Las anotaciones en páginas giradas todavía no son compatibles" }
      PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true).use { out ->
       fun x(v: Offset) = box.lowerLeftX + v.x * box.width
       fun y(v: Offset) = box.upperRightY - v.y * box.height
       out.saveGraphicsState()
       when (mark.kind) {
        "Texto" -> { out.setNonStrokingColor(25, 45, 50); out.beginText(); out.setFont(PDType1Font.HELVETICA, 14f); out.newLineAtOffset(x(mark.points.first()), y(mark.points.first())); out.showText(mark.text); out.endText() }
        "Resaltar" -> { out.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }); out.setNonStrokingColor(255, 210, 0); val a = mark.points.first(); val b = mark.points.last(); out.addRect(x(a), y(a), (b.x-a.x)*box.width, (a.y-b.y)*box.height); out.fill() }
        "Firma" -> { out.setStrokingColor(20, 55, 100); out.setLineWidth(2f); out.moveTo(x(mark.points.first()), y(mark.points.first())); for (v in mark.points.drop(1)) out.lineTo(x(v), y(v)); out.stroke() }
       }
       out.restoreGraphicsState()
      }
     }
     val output = File(context.cacheDir, "exportado.pdf"); doc.save(output)
     context.contentResolver.openOutputStream(uri, "wt")!!.use { stream -> output.inputStream().use { it.copyTo(stream) } }
    }
   }; status = "Copia PDF guardada correctamente"
  }
 }
 LaunchedEffect(pending) { pending?.let { open(it, false) }; pending = null }
 LaunchedEffect(file, page) {
  val source = file ?: return@LaunchedEffect
  try { bitmap = withContext(Dispatchers.IO) { ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> count = renderer.pageCount; renderer.openPage(page).use { p -> pdfWidth = p.width.toFloat(); val scale = minOf(2f, 1600f / p.width); Bitmap.createBitmap((p.width*scale).toInt(), (p.height*scale).toInt(), Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } } } } } } catch (e: Exception) { status = "No se puede mostrar este PDF: ${e.localizedMessage}"; bitmap = null }
 }
 if (confirmAction != null) AlertDialog(onDismissRequest = { confirmAction = null }, title = { Text("Hay anotaciones en esta sesión") }, text = { Text("Guarda una copia antes de cambiar de documento si quieres conservarlas.") }, confirmButton = { TextButton(onClick = { confirmAction?.invoke(); confirmAction = null }) { Text("Cambiar documento") } }, dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Volver a guardar") } })
 Scaffold { padding -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
  Text("PDF Fácil Pro", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
  Text(status, style = MaterialTheme.typography.bodyMedium)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   Button(onClick = { if (marks.isNotEmpty()) confirmAction = { picker.launch(arrayOf("application/pdf")) } else picker.launch(arrayOf("application/pdf")) }, enabled = !busy) { Text("Abrir PDF") }
   if (file != null) OutlinedButton(onClick = { save.launch("PDF-Facil-${System.currentTimeMillis()}.pdf") }, enabled = !busy) { Text("Guardar copia") }
  }
  if (marks.isNotEmpty()) Text("${marks.size} anotaciones en sesión · guarda una copia antes de salir", fontSize = 12.sp)
  if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  if (file == null) {
   Text("¿Qué quieres hacer hoy?", fontSize = 22.sp)
   Text("Lee, añade texto, resalta, firma y une tus PDF. Sin suscripción, sin anuncios y sin conexión.")
   Text("Documentos recientes", style = MaterialTheme.typography.titleMedium)
   Column(Modifier.verticalScroll(rememberScrollState())) { recent.forEach { f -> TextButton(onClick = { file = f; title = f.name; page = 0; marks = emptyList() }) { Text(f.name) } } }
  } else {
   Text(title, maxLines = 1)
   Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf("Leer", "Texto", "Resaltar", "Firma").forEach { t -> FilterChip(selected = tool == t, onClick = { tool = t }, label = { Text(t) }) }
    OutlinedButton(onClick = { merger.launch(arrayOf("application/pdf")) }, enabled = !busy) { Text("Unir PDF") }
    OutlinedButton(onClick = { marks = marks.dropLast(1) }, enabled = marks.isNotEmpty() && !busy) { Text("Deshacer") }
   }
   if (tool == "Texto") OutlinedTextField(value = text, onValueChange = { text = it.replace("\n", " ") }, label = { Text("Texto: después toca la página") }, singleLine = true, modifier = Modifier.fillMaxWidth())
   if (tool == "Firma") Text("Dibuja tu firma sobre la página. Es una firma manuscrita, sin certificado digital.", fontSize = 12.sp)
   if (tool == "Resaltar") Text("Arrastra sobre el área que quieres resaltar.", fontSize = 12.sp)
   Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
    bitmap?.let { bmp ->
     Box(Modifier.aspectRatio(bmp.width.toFloat()/bmp.height).onSizeChanged { width = it.width; height = it.height }
      .pointerInput(tool, text, page, busy) { if (tool == "Texto" && !busy) detectTapGestures { p -> if (text.isNotBlank()) marks = marks + Mark(page, tool, listOf(Offset(p.x/width, p.y/height)), text) } }
      .pointerInput(tool, page, busy) { if ((tool == "Firma" || tool == "Resaltar") && !busy) detectDragGestures(onDragStart = { stroke = listOf(Offset(it.x/width, it.y/height)) }, onDragEnd = { if (stroke.size > 1) marks = marks + Mark(page, tool, stroke); stroke = emptyList() }, onDragCancel = { stroke = emptyList() }) { change, _ -> change.consume(); stroke = stroke + Offset((change.position.x/width).coerceIn(0f,1f), (change.position.y/height).coerceIn(0f,1f)) } }) {
      Image(bmp.asImageBitmap(), "Página ${page+1}", Modifier.fillMaxSize())
      Canvas(Modifier.fillMaxSize()) {
       val visible = marks.filter { it.page == page } + if (stroke.isNotEmpty()) listOf(Mark(page, tool, stroke)) else emptyList()
       for (m in visible) {
        val pts = m.points.map { Offset(it.x*size.width,it.y*size.height) }
        when(m.kind) {
         "Firma" -> pts.zipWithNext().forEach { (a,b) -> drawLine(Color(0xFF143764), a, b, 2f*size.width/bmp.width) }
         "Resaltar" -> { val a=pts.first(); val b=pts.last(); drawRect(Color(0x59FFD200), Offset(minOf(a.x,b.x),minOf(a.y,b.y)), androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x-a.x),kotlin.math.abs(b.y-a.y))) }
         "Texto" -> { drawContext.canvas.nativeCanvas.drawText(m.text, pts.first().x, pts.first().y, android.graphics.Paint().apply { color=android.graphics.Color.rgb(25,45,50); textSize=14f*size.width/pdfWidth }) }
        }
       }
      }
     }
    }
   }
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = { page-- }, enabled = page > 0 && !busy) { Text("Anterior") }
    Text("Página ${page+1} de $count")
    TextButton(onClick = { page++ }, enabled = page+1 < count && !busy) { Text("Siguiente") }
   }
  }
 } }
}
