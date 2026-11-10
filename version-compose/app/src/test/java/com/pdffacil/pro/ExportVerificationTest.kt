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
}
