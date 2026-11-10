package com.pdffacil.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.*

/** Bundled Latin OCR; no model download or runtime network required. Pages processed serially. */
internal suspend fun exportPages(source:File,onProgress:(Int,Int)->Unit):List<TextPage> {
 val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
 try {
  return PDDocument.load(source).use {doc ->
   require(doc.currentAccessPermission.canExtractContent()) {"El PDF restringe extraer contenido"}
   (0 until doc.numberOfPages).map {index ->
    onProgress(index+1,doc.numberOfPages)
    val page=extractTextPage(doc,index)
    if(page.cells.any {it.text.isNotBlank()}) page else {
     val bitmap=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->PdfRenderer(fd).use {renderer->renderer.openPage(index).use {p->
      val scale=min(3f,2200f/max(p.width,p.height));Bitmap.createBitmap(max(1,(p.width*scale).roundToInt()),max(1,(p.height*scale).roundToInt()),Bitmap.Config.ARGB_8888).also {it.eraseColor(Color.WHITE);p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}
     }}}
     try {
      val result=recognizer.process(InputImage.fromBitmap(bitmap,0)).await()
      val cells=result.textBlocks.flatMap {it.lines}.flatMap {it.elements}.mapNotNull {e->e.boundingBox?.let {b->LayoutCell(e.text,Rect(b.left.toFloat()/bitmap.width,b.top.toFloat()/bitmap.height,b.right.toFloat()/bitmap.width,b.bottom.toFloat()/bitmap.height))}}
      page.copy(cells=cells,ocr=true)
     } finally {bitmap.recycle()}
    }
   }
  }
 } finally {recognizer.close()}
}
internal fun safeFontText(font:PDFont,text:String):String=buildString {text.replace('\n',' ').replace('\r',' ').forEach {c->if(c.code>=32) append(if(runCatching {font.encode(c.toString());true}.getOrDefault(false)) c else '?')}}
internal fun writeEditablePdf(context:Context,pages:List<TextPage>,target:File) {
 PDDocument().use {doc ->
  val font=fontFor(context,doc,"Sans",false,false)
  pages.forEach {source ->
   val page=PDPage(PDRectangle(source.width,source.height));doc.addPage(page)
   PDPageContentStream(doc,page).use {stream ->
    layoutRows(source.cells).flatMap {it.cells}.forEach {cell ->
     val value=safeFontText(font,cell.text);val b=cell.rect
     val size=(b.height*source.height).coerceIn(5f,72f)
     val natural=font.getStringWidth(value)/1000*size
     stream.beginText();stream.setFont(font,size);stream.setNonStrokingColor(0f,0f,0f)
     stream.setHorizontalScaling(if(natural>0) min(100f,b.width*source.width/natural*100) else 100f)
     stream.newLineAtOffset(b.left*source.width,(1-b.bottom)*source.height);stream.showText(value);stream.endText()
    }
   }
  };doc.save(target)
 }
}
internal fun wrappedLines(font:PDFont,text:String,size:Float,maxWidth:Float):List<String> {
 val lines=mutableListOf<String>();var line=""
 for(c in safeFontText(font,text)) {
  val next=line+c
  if(line.isNotEmpty() && font.getStringWidth(next)/1000*size>maxWidth) {lines.add(line);line=c.toString()} else line=next
 };lines.add(line);return lines
}
/** Native PDF table: text operands and vector grid, never a screenshot. */
internal fun insertTable(context:Context,doc:PDDocument,index:Int,at:Offset,data:List<List<String>>) {
 require(data.isNotEmpty() && data.size<=20 && data.first().isNotEmpty() && data.first().size<=10)
 val page=doc.getPage(index);val box=page.cropBox;val rot=((page.rotation%360)+360)%360
 val width=if(rot==90 || rot==270) box.height else box.width
 val height=if(rot==90 || rot==270) box.width else box.height
 val columns=data.maxOf {it.size};val availableWidth=(width*(1-at.x)-16).coerceAtLeast(60f)
 val tableWidth=min(width*0.85f,availableWidth);val colWidth=tableWidth/columns
 val font=fontFor(context,doc,"Sans",false,false);val fontSize=10f
 val rows=data.map {row->List(columns) {c->wrappedLines(font,row.getOrElse(c) {""},fontSize,(colWidth-8).coerceAtLeast(5f))}}
 val heights=rows.map {row->max(24f,row.maxOf {it.size}*13f+10)}
 require(heights.sum()<=height*(1-at.y)-10) {"La tabla no cabe aquí. Añade una hoja y colócala más arriba, o reduce sus filas."}
 val anchor=pagePoint(page,at)
 PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use {s->
  s.saveGraphicsState();s.transform(Matrix.getRotateInstance(Math.toRadians(rot.toDouble()),anchor.x,anchor.y));s.setLineWidth(0.6f);s.setStrokingColor(0.2f,0.35f,0.3f)
  var top=0f
  rows.forEachIndexed {r,cells ->
   val h=heights[r]
   cells.forEachIndexed {c,lines ->
    s.addRect(c*colWidth,top-h,colWidth,h);s.stroke()
    lines.forEachIndexed {line,text->s.beginText();s.setFont(font,fontSize);s.setNonStrokingColor(0.05f,0.15f,0.12f);s.newLineAtOffset(c*colWidth+4,top-14-line*13);s.showText(text);s.endText()}
   };top-=h
  };s.restoreGraphicsState()
 }
}

internal fun addMultilineText(context:Context,doc:PDDocument,index:Int,at:Offset,text:String) {
 val page=doc.getPage(index);val box=page.cropBox;val rot=((page.rotation%360)+360)%360
 val width=if(rot==90 || rot==270) box.height else box.width
 val height=if(rot==90 || rot==270) box.width else box.height
 val font=fontFor(context,doc,"Sans",false,false)
 val lines=text.replace("\r\n","\n").replace('\r','\n').split('\n').flatMap {wrappedLines(font,it.replace('\t',' '),14f,(width*(1-at.x)-12).coerceAtLeast(20f))}
 require(lines.size*17f<height*(1-at.y)) {"El texto no cabe aquí. Añade una hoja o pégalo más arriba."}
 val anchor=pagePoint(page,at)
 PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use {s->s.saveGraphicsState();s.transform(Matrix.getRotateInstance(Math.toRadians(rot.toDouble()),anchor.x,anchor.y));lines.forEachIndexed {i,line->s.beginText();s.setFont(font,14f);s.newLineAtOffset(0f,-i*17f);s.showText(line);s.endText()};s.restoreGraphicsState()}
}
