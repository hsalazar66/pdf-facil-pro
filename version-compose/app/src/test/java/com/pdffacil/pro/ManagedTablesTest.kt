package com.pdffacil.pro

import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream

class ManagedTablesTest {
 private fun font(doc:PDDocument)=File("src/main/assets/fonts/LiberationSans-Regular.ttf").inputStream().use {PDType0Font.load(doc,it,false)}
 private fun image(doc:PDDocument)=PDImageXObject(doc).apply {width=2;height=2;bitsPerComponent=8;colorSpace=PDDeviceRGB.INSTANCE;cosObject.createOutputStream(COSName.FLATE_DECODE).use {it.write(byteArrayOf(-1,0,0,0,-1,0,0,0,-1,-1,-1,-1))}}
 private fun reopen(doc:PDDocument,check:(PDDocument)->Unit) {val bytes=ByteArrayOutputStream().also {doc.save(it)}.toByteArray();PDDocument.load(bytes).use(check)}
 private fun data()=listOf(listOf(TableCell("Nombre"),TableCell("Monto")),listOf(TableCell("Inicial"),TableCell("123,45")))
 private fun legacy(doc:PDDocument,index:Int) {
  val f=font(doc);val page=doc.getPage(index)
  PDPageContentStream(doc,page).use {s->
   s.saveGraphicsState();s.transform(com.tom_roush.pdfbox.util.Matrix(1f,0f,0f,1f,100f,500f));s.setLineWidth(0.6f);s.setStrokingColor(0.2f,0.35f,0.3f)
   val rows=listOf(listOf("Legado","Monto"),listOf("Cliente","42"))
   rows.forEachIndexed {r,row->row.forEachIndexed {c,text->s.addRect(c*100f,-(r+1)*30f,100f,30f);s.stroke();s.beginText();s.setFont(f,10f);s.setNonStrokingColor(0.05f,0.15f,0.12f);s.newLineAtOffset(c*100f+4,-r*30f-14);s.showText(text);s.endText()}}
   s.restoreGraphicsState()
  }
 }
 @Test fun editMoveAndReopenPreserveOneTableWithoutDuplicateText() {
  PDDocument().use {doc->doc.addPage(PDPage());val f=font(doc)
   val table=writeManagedTable(doc,0,ManagedTable(cells=data()),f) {image(doc)}
   reopen(doc) {copy->val old=readManagedTables(copy.getPage(0)).single();assertEquals(table.id,old.id)
    val cells=old.cells.mapIndexed {r,row->row.mapIndexed {c,cell->if(r==1 && c==0) cell.copy(text="Actualizado") else cell}}
    writeManagedTable(copy,0,old.copy(cells=cells,x=0.15f,y=0.5f,width=0.7f),font(copy)) {image(copy)}
    reopen(copy) {final->val saved=readManagedTables(final.getPage(0)).single();assertEquals("Actualizado",saved.cells[1][0].text);assertEquals(0.5f,saved.y,0.001f)
     val text=PDFTextStripper().getText(final);assertFalse(text.contains("Inicial"));assertEquals(1,Regex("Actualizado").findAll(text).count());assertTrue(text.contains("123,45"))}
   }
  }
 }
 @Test fun autoFitWrapsGrowsRowsAndKeepsEverythingInsidePage() {
  PDDocument().use {doc->doc.addPage(PDPage());val f=font(doc)
   val short=writeManagedTable(doc,0,ManagedTable(cells=data(),x=0.95f,y=0.95f),f) {image(doc)}
   val long=short.copy(cells=listOf(listOf(TableCell("Texto largo con números 123 y varias palabras ".repeat(12)),TableCell("42"))))
   val result=writeManagedTable(doc,0,long,f) {image(doc)}
   assertTrue(result.rect.right<=0.971f);assertTrue(result.rect.bottom<=0.971f);assertTrue(result.rows.first()>short.rows.first());assertTrue(result.fontSize>=8)
  }
 }
 @Test fun cellsWithPicturesStayEditableAndCanBeRemovedWithTheWholeTable() {
  PDDocument().use {doc->doc.addPage(PDPage());val f=font(doc)
   val cells=listOf(listOf(TableCell("Foto",byteArrayOf(1,2,3),2,2),TableCell("99")))
   val table=writeManagedTable(doc,0,ManagedTable(cells=cells),f) {image(doc)}
   reopen(doc) {copy->val restored=readManagedTables(copy.getPage(0)).single();assertArrayEquals(byteArrayOf(1,2,3),restored.cells[0][0].picture)
    assertTrue(PDFTextStripper().getText(copy).contains("Foto"));removeManagedTable(copy.getPage(0),table.id)
    reopen(copy) {assertTrue(readManagedTables(it.getPage(0)).isEmpty());assertFalse(PDFTextStripper().getText(it).contains("Foto"))}
   }
  }
 }
 @Test fun old25TablesAreRecoveredAndConvertedWithoutAnOverlayDuplicate() {
  PDDocument().use {doc->doc.addPage(PDPage());legacy(doc,0)
   val old=readManagedTables(doc.getPage(0)).single();assertTrue(old.legacy);assertEquals("Legado",old.cells[0][0].text);assertEquals("42",old.cells[1][1].text)
   writeManagedTable(doc,0,old.copy(width=0.82f),font(doc)) {image(doc)}
   reopen(doc) {copy->val table=readManagedTables(copy.getPage(0)).single();assertFalse(table.legacy);assertEquals(1,Regex("Legado").findAll(PDFTextStripper().getText(copy)).count())}
  }
 }
 @Test fun rotatedPageAndIndependentTablesSurviveEditing() {
  PDDocument().use {doc->doc.addPage(PDPage().apply {rotation=90});val f=font(doc)
   val a=writeManagedTable(doc,0,ManagedTable(cells=data(),width=0.4f),f) {image(doc)}
   val b=writeManagedTable(doc,0,ManagedTable(cells=data(),y=0.6f,width=0.4f),f) {image(doc)}
   writeManagedTable(doc,0,a.copy(x=0.4f,width=0.5f),f) {image(doc)}
   reopen(doc) {copy->val all=readManagedTables(copy.getPage(0));assertEquals(2,all.size);assertEquals(b.y,all.first {it.id==b.id}.y,0.001f);assertTrue(all.all {it.rect.right<1 && it.rect.bottom<1})}
  }
 }
 @Test fun createFixtureForAndroidTableGesturesAndLegacyEditing() {
  PDDocument().use {doc->doc.addPage(PDPage());doc.addPage(PDPage())
   writeManagedTable(doc,0,ManagedTable(cells=data(),y=0.15f),font(doc)) {image(doc)};legacy(doc,1)
   val file=File("build/verification/tablas-editables.pdf");file.parentFile.mkdirs();doc.save(file)
  }
 }
}
