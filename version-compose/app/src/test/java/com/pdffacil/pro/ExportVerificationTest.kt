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
}
