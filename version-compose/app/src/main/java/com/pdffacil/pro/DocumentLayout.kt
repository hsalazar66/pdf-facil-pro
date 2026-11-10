package com.pdffacil.pro

import androidx.compose.ui.geometry.Rect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.*

internal data class LayoutCell(val text:String,val rect:Rect)
internal data class LayoutRow(val cells:List<LayoutCell>)
internal data class TextPage(val width:Float,val height:Float,val cells:List<LayoutCell>,val ocr:Boolean=false)
internal fun layoutRows(cells:List<LayoutCell>,area:Rect=Rect(0f,0f,1f,1f)):List<LayoutRow> {
 val lines=mutableListOf<MutableList<LayoutCell>>()
 cells.filter {it.text.isNotBlank() && area.contains(it.rect.center)}.sortedBy {it.rect.center.y}.forEach {cell ->
  val line=lines.lastOrNull()
  if(line!=null && abs(line.map {it.rect.center.y}.average().toFloat()-cell.rect.center.y)<max(0.003f,cell.rect.height*0.6f)) line.add(cell) else lines.add(mutableListOf(cell))
 }
 return lines.map {line ->
  val groups=mutableListOf<LayoutCell>();var text="";var box:Rect?=null
  fun flush() {box?.let {groups.add(LayoutCell(text.trim(),it))};text="";box=null}
  line.sortedBy {it.rect.left}.forEach {c ->
   val last=box;val gap=if(last==null) 0f else c.rect.left-last.right
   if(last!=null && gap>max(0.018f,c.rect.height*1.2f)) flush()
   val old=box
   if(old==null) {text=c.text;box=c.rect} else {
    text+=(if(gap>c.rect.height*0.16f && !text.endsWith(" ") && !c.text.startsWith(" ")) " " else "")+c.text
    box=Rect(min(old.left,c.rect.left),min(old.top,c.rect.top),max(old.right,c.rect.right),max(old.bottom,c.rect.bottom))
   }
  };flush();LayoutRow(groups)
 }
}
internal fun extractTextPage(doc:PDDocument,pageIndex:Int):TextPage {
 require(doc.currentAccessPermission.canExtractContent()) {"El PDF restringe extraer contenido"}
 val page=doc.getPage(pageIndex);val rot=((page.rotation%360)+360)%360
 val w=if(rot==90 || rot==270) page.cropBox.height else page.cropBox.width
 val h=if(rot==90 || rot==270) page.cropBox.width else page.cropBox.height
 val cells=mutableListOf<LayoutCell>()
 val stripper=object:PDFTextStripper() {
  override fun processTextPosition(t:TextPosition) {
   if(!t.unicode.isNullOrEmpty()) cells.add(LayoutCell(t.unicode,Rect(t.x/w,(t.y-t.height)/h,(t.x+t.width)/w,t.y/h)))
   super.processTextPosition(t)
  }
 };stripper.startPage=pageIndex+1;stripper.endPage=pageIndex+1;stripper.sortByPosition=true;stripper.getText(doc)
 return TextPage(w,h,cells)
}
/** Align cells by x anchors, retaining empty cells instead of shifting table columns. */
internal fun tableData(rows:List<LayoutRow>):List<List<String>> {
 val anchors=mutableListOf<Float>()
 rows.flatMap {it.cells}.sortedBy {it.rect.left}.forEach {cell ->if(anchors.none {abs(it-cell.rect.left)<0.025f}) anchors.add(cell.rect.left)}
 return rows.map {row ->MutableList(anchors.size) {""}.also {out ->row.cells.forEach {c->val i=anchors.indices.minByOrNull {abs(anchors[it]-c.rect.left)}!!;out[i]=(out[i]+" "+c.text).trim()}} }
}
internal fun selectionText(page:TextPage,area:Rect)=layoutRows(page.cells,area).joinToString("\n") {it.cells.joinToString("\t") {c->c.text}}
internal fun tsv(rows:List<List<String>>)=rows.joinToString("\n") {r->r.joinToString("\t") {it.replace('\t',' ').replace('\n',' ')}}
internal fun xml(value:String)=buildString {value.forEach {c->when(c) {'&'->append("&amp;");'<'->append("&lt;");'>'->append("&gt;");'\"'->append("&quot;");'\''->append("&apos;");else->if(c=='\n'||c=='\t'||c.code>=32) append(c)}}}
internal fun tableHtml(rows:List<List<String>>)=rows.joinToString("", "<table>","</table>") {r->r.joinToString("","<tr>","</tr>") {"<td>${xml(it)}</td>"}}
internal fun writeDocx(pages:List<TextPage>,target:File) {
 fun paragraph(text:String)="<w:p><w:r><w:t xml:space=\"preserve\">${xml(text)}</w:t></w:r></w:p>"
 val body=buildString {
  pages.forEachIndexed {index,page ->
   if(index>0) append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
   val rows=layoutRows(page.cells);var at=0
   while(at<rows.size) {
    if(rows[at].cells.size>=2) {
     val group=mutableListOf<LayoutRow>();while(at<rows.size && rows[at].cells.size>=2) group.add(rows[at++])
     append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
     for(edge in listOf("top","left","bottom","right","insideH","insideV")) append("<w:$edge w:val=\"single\" w:sz=\"4\" w:color=\"64766F\"/>")
     append("</w:tblBorders></w:tblPr>")
     val data=tableData(group);append("<w:tblGrid>");repeat(data.firstOrNull()?.size ?: 0) {append("<w:gridCol w:w=\"2400\"/>")};append("</w:tblGrid>")
     data.forEach {r->append("<w:tr>");r.forEach {append("<w:tc><w:tcPr><w:tcW w:w=\"2400\" w:type=\"dxa\"/></w:tcPr>${paragraph(it)}</w:tc>")};append("</w:tr>")};append("</w:tbl>")
    } else append(paragraph(rows[at++].cells.joinToString(" ") {it.text}))
   }
   // Empty pages are represented explicitly, including in documents without text.
   if(rows.isEmpty()) append(paragraph(""))
  }
  append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>")
 }
 val files=mapOf(
  "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
  "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
  "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
 )
 ZipOutputStream(target.outputStream()).use {zip->files.forEach {(name,content)->zip.putNextEntry(ZipEntry(name));zip.write(content.toByteArray(Charsets.UTF_8));zip.closeEntry()}}
}
