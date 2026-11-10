package com.pdffacil.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pdffacil.pro.engine.*
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.*
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

internal data class TextFragment(val index: Int, val text: String, val size: Float, val font: String, val color: Int, val rect: Rect)
internal data class ImageFragment(val ordinal: Int, val rect: Rect)
internal data class PagePreview(val bitmap: Bitmap, val count: Int, val fragments: List<TextFragment>, val images: List<ImageFragment>, val width: Float, val height: Float, val rotation: Int)
private data class PreviewMeta(val fragments: List<TextFragment>, val images: List<ImageFragment>, val width: Float, val height: Float, val rotation: Int)
internal fun textRuns(node: DrawNode): List<DrawNode> = if (node.kind == NodeKind.TEXT && node.children.isEmpty()) listOf(node) else node.children.flatMap { textRuns(it) }

/** Replaces the existing Tj/TJ operand. It does not append a visual replacement. */
internal fun replaceExistingText(doc: PDDocument, pageIndex: Int, tokenIndex: Int, value: String, font: PDFont?, size: Float?, color: Int?) {
 require(!value.contains('\n') && !value.contains('\r')) { "Edita una línea o fragmento cada vez." }
 val page = doc.getPage(pageIndex)
 val parsed = ContentStreamEngine.parse(page)
 val node = textRuns(parsed.root).firstOrNull { it.startIndex == tokenIndex } ?: error("El fragmento cambió. Selecciónalo de nuevo.")
 val scale = if (size != null && node.fontSize > 0f) size / node.fontSize else 1f
 val substitute = font ?: if (size != null) node.font else null
 when (ElementEditor.editElement(doc, page, parsed.tokens, node, EditRequest(newText = value, fillArgb = color), substitute, scale)) {
  is EditResult.Applied -> Unit
  is EditResult.TextEncodeFailed -> error("La fuente original no contiene esos caracteres. Selecciona Sans o Serif.")
  else -> error("Este fragmento no admite edición.")
 }
}

internal fun fontFor(context: Context, doc: PDDocument, family: String, bold: Boolean, italic: Boolean): PDFont {
 val stem = if (family == "Serif") "LiberationSerif" else "LiberationSans"
 val style = when { bold && italic -> "BoldItalic"; bold -> "Bold"; italic -> "Italic"; else -> "Regular" }
 return context.assets.open("fonts/$stem-$style.ttf").use { PDType0Font.load(doc, it, true) }
}

internal fun preview(file: File, number: Int): PagePreview {
 val meta = PDDocument.load(file).use { doc ->
  val p = doc.getPage(number); val c = p.cropBox; val rotation = ((p.rotation % 360)+360)%360
  fun screen(x: Float, y: Float): Offset {
   val u=x-c.lowerLeftX; val v=y-c.lowerLeftY
   return when(rotation) { 90 -> Offset(v/c.height,u/c.width); 180 -> Offset((c.width-u)/c.width,v/c.height); 270 -> Offset((c.height-v)/c.height,(c.width-u)/c.width); else -> Offset(u/c.width,(c.height-v)/c.height) }
  }
  val parsed=try { ContentStreamEngine.parse(p) } catch (_:Exception) { null }
  val nodes=parsed?.let { textRuns(it.root) } ?: emptyList()
  val fragments = nodes.mapNotNull { n ->
   val b = n.bounds ?: return@mapNotNull null
   val corners = listOf(screen(b.minX,b.minY),screen(b.minX,b.maxY),screen(b.maxX,b.minY),screen(b.maxX,b.maxY))
   if (n.text.isNullOrBlank() || n.font == null) return@mapNotNull null
   TextFragment(n.startIndex,n.text!!,n.fontSize,n.font?.name ?: "Original",n.colorArgb ?: android.graphics.Color.BLACK,Rect(corners.minOf { it.x },corners.minOf { it.y },corners.maxOf { it.x },corners.maxOf { it.y }))
  }
  val images=parsed?.leaves?.filter { it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null }?.mapIndexed { ordinal,node ->
   val b=node.bounds!!
   val corners=listOf(screen(b.minX,b.minY),screen(b.minX,b.maxY),screen(b.maxX,b.minY),screen(b.maxX,b.maxY))
   ImageFragment(ordinal,Rect(corners.minOf { it.x },corners.minOf { it.y },corners.maxOf { it.x },corners.maxOf { it.y }))
  } ?: emptyList()
  PreviewMeta(fragments,images,c.width,c.height,rotation)
 }
 return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(number).use { p ->
  val scale=minOf(2f,1800f/p.width); val bitmap=Bitmap.createBitmap((p.width*scale).toInt(),(p.height*scale).toInt(),Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(android.graphics.Color.WHITE); p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
  PagePreview(bitmap,renderer.pageCount,meta.fragments,meta.images,meta.width,meta.height,meta.rotation)
 } } }
}

internal fun pagePoint(page: PDPage, point: Offset): Offset {
 val b=page.cropBox
 val u=point.x.coerceIn(0f,1f);val v=point.y.coerceIn(0f,1f)
 val p=when(((page.rotation%360)+360)%360) { 90 -> Offset(v*b.width,u*b.height); 180 -> Offset((1-u)*b.width,v*b.height); 270 -> Offset((1-v)*b.width,(1-u)*b.height); else -> Offset(u*b.width,(1-v)*b.height) }
 return Offset(b.lowerLeftX+p.x,b.lowerLeftY+p.y)
}
internal fun addAnnotation(doc: PDDocument, pageIndex: Int, kind: String, points: List<Offset>) {
 require(points.size>1)
 val page=doc.getPage(pageIndex); val pts=points.map { pagePoint(page,it) }
 PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { stream ->
  stream.saveGraphicsState()
  if(kind=="Resaltar") {
   val a=pts.first(); val b=pts.last();stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant=0.35f })
   stream.setNonStrokingColor(1f,0.82f,0f);stream.addRect(minOf(a.x,b.x),minOf(a.y,b.y),kotlin.math.abs(a.x-b.x),kotlin.math.abs(a.y-b.y));stream.fill()
  } else {
   stream.setStrokingColor(0.05f,0.2f,0.35f);stream.setLineWidth(1.6f);stream.moveTo(pts.first().x,pts.first().y)
   pts.drop(1).forEach { stream.lineTo(it.x,it.y) };stream.stroke()
  };stream.restoreGraphicsState()
 }
}
internal fun addNewText(context: Context, doc: PDDocument, pageIndex: Int, point: Offset, value: String) {
 val page=doc.getPage(pageIndex);val p=pagePoint(page,point)
 val font=fontFor(context,doc,"Sans",false,false)
 PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { stream ->
  stream.beginText();stream.setFont(font,14f);stream.setNonStrokingColor(0.1f,0.2f,0.2f)
  stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(page.rotation.toDouble()),p.x,p.y));stream.showText(value);stream.endText()
 }
}
internal fun addImage(doc: PDDocument, pageIndex: Int, bitmap: Bitmap, point:Offset?=null) {
 placeImageObject(doc,pageIndex,LosslessFactory.createFromImage(doc,bitmap),point)
}

/** Translate one image occurrence, preserving shared resources and other occurrences. */
internal fun moveImage(doc:PDDocument, pageIndex:Int, ordinal:Int, delta:Offset) {
 require(delta.x.isFinite() && delta.y.isFinite()) { "Movimiento no válido" }
 val page=doc.getPage(pageIndex)
 val parsed=ContentStreamEngine.parse(page)
 val images=parsed.leaves.filter { it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null }
 val image=images.getOrNull(ordinal) ?: error("Selecciona una imagen independiente de la página.")
 val b=page.cropBox
 val shift=normalizedPageDelta(page.rotation,b.width,b.height,delta)
 val result=ElementEditor.editElement(doc,page,parsed.tokens,image,EditRequest(dx=shift.x,dy=shift.y))
 require(result is EditResult.Applied) { "No se puede mover esta imagen." }
}
internal fun normalizedPageDelta(rotation:Int,width:Float,height:Float,delta:Offset):Offset = when(((rotation%360)+360)%360) {
 90 -> Offset(delta.y*width,delta.x*height)
 180 -> Offset(-delta.x*width,delta.y*height)
 270 -> Offset(-delta.y*width,-delta.x*height)
 else -> Offset(delta.x*width,-delta.y*height)
}
