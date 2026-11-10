package com.pdffacil.pro

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ExportVerificationTest {
 @Test fun androidSavedPdfContainsBothPagesAndPastedContent() {
  val path=System.getenv("PDF_SMOKE_EXPORT")
  assumeTrue("Se ejecuta después de la prueba en Android",path!=null)
  PDDocument.load(File(path!!)).use {doc ->
   assertEquals(2,doc.numberOfPages)
   assertEquals(2,imageNodes(doc.getPage(0)).size)
   val text=PDFTextStripper().getText(doc)
   assertEquals(1,Regex("TextoClipboard").findAll(text).count())
   assertTrue(PDFTextStripper().apply {startPage=2;endPage=2}.getText(doc).contains("TextoClipboard"))
  }
 }
 @Test fun androidExportsContainEditableTableAndText() {
  val source=System.getenv("PDF_SMOKE_EXPORT")
  assumeTrue("Después del emulador",source!=null)
  val dir=File(source!!).parentFile!!
  PDDocument.load(File(dir,"redimensionado.pdf")).use {doc->val images=imageNodes(doc.getPage(0));assertTrue(images[0].bounds!!.width>105f);assertEquals(100f,images[1].bounds!!.width,0.1f)}
  java.util.zip.ZipFile(File(dir,"exportado-editable.docx")).use {zip->
   val text=zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
   assertTrue(text.contains("TextoClipboard"));assertTrue(text.contains("Nombre"));assertTrue(text.contains("Monto"));assertTrue(text.contains("<w:tbl>"))
  }
  PDDocument.load(File(dir,"exportado-editable.pdf")).use {doc->
   val text=PDFTextStripper().getText(doc)
   assertTrue(text.contains("TextoClipboard"));assertTrue(text.contains("Nombre"));assertTrue(text.contains("42"))
   assertEquals(2,doc.numberOfPages);assertEquals(0,imageNodes(doc.getPage(0)).size)
  }
 }
 @Test fun scannedPdfBecomesRealEditableTextWithBundledOcr() {
  val source=System.getenv("PDF_SMOKE_EXPORT");assumeTrue("Después del emulador",source!=null)
  val dir=File(source!!).parentFile!!
  PDDocument.load(File(dir,"ocr-editable.pdf")).use {doc->val text=PDFTextStripper().getText(doc);assertTrue(text.contains("FACTURA"));assertTrue(text.contains("12345"));assertEquals(0,imageNodes(doc.getPage(0)).size)}
  java.util.zip.ZipFile(File(dir,"ocr-editable.docx")).use {zip->val text=zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText();assertTrue(text.contains("FACTURA"));assertTrue(text.contains("12345"))}
 }
 @Test fun managedTablesSaveGestureDataPicturesAndLegacyConversion() {
  val source=System.getenv("PDF_SMOKE_EXPORT");assumeTrue("Después del emulador",source!=null)
  val dir=File(source!!).parentFile!!
  PDDocument.load(File(dir,"tabla-gesto.pdf")).use {doc->val table=readManagedTables(doc.getPage(0)).single();assertTrue(table.y>0.18f);assertTrue(table.width<0.8f)}
  PDDocument.load(File(dir,"tablas-2.6-final.pdf")).use {doc->
   val table=readManagedTables(doc.getPage(0)).single();assertEquals("Actualizado",table.cells[1][0].text);assertEquals("9876",table.cells[1][1].text);assertEquals(0.82f,table.width,0.001f)
   assertNotNull(table.cells[0][1].picture);assertTrue(table.cells[0][1].pictureWidth>0);assertTrue(table.cells[0][1].pictureHeight>0)
   val old=readManagedTables(doc.getPage(1)).single();assertFalse(old.legacy);assertEquals("Legado",old.cells[0][0].text);assertEquals("77",old.cells[1][1].text)
   val text=PDFTextStripper().getText(doc);assertEquals(1,Regex("Actualizado").findAll(text).count());assertFalse(text.contains("Inicial"));assertTrue(text.contains("9876"));assertTrue(text.contains("77"))
  }
 }
}
