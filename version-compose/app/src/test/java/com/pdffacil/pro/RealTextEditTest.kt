package com.pdffacil.pro

import com.pdffacil.pro.engine.*
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class RealTextEditTest {
 private fun fixture():ByteArray = PDDocument().use { doc ->
  val font=File("src/main/assets/fonts/LiberationSans-Regular.ttf").inputStream().use { PDType0Font.load(doc,it,false) }
  repeat(2) { number ->
   val page=PDPage();doc.addPage(page)
   PDPageContentStream(doc,page).use { s ->
    s.beginText();s.setFont(font,14f);s.newLineAtOffset(40f,700f);s.setNonStrokingColor(0f,0f,0f)
    s.showText(if(number==0) "Texto ORIGINAL con información y año." else "Segunda página intacta.")
    s.newLineAtOffset(0f,-28f);s.showText("Fragmento vecino intacto.");s.endText()
   }
  }
  ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
 }
 @Test fun replacementSurvivesSaveAndReopenWithoutKeepingOriginalOrAddingTextOperators() {
  val edited=PDDocument.load(fixture()).use { doc ->
   val before=ContentStreamEngine.parse(doc.getPage(0))
   val target=textRuns(before.root).first { it.text!!.contains("ORIGINAL") }
   replaceExistingText(doc,0,target.startIndex,"Texto EDITADO: revisión número dos.",null,null,null)
   val after=ContentStreamEngine.parse(doc.getPage(0))
   fun shows(p:ParsedPage)=p.tokens.filterIsInstance<Operator>().count { it.name=="Tj" || it.name=="TJ" }
   assertEquals("Replacement must not add another text object on top",shows(before),shows(after))
   ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
  }
  PDDocument.load(edited).use { doc ->
   val text=PDFTextStripper().getText(doc)
   assertTrue(text,text.contains("Texto EDITADO: revisión número dos."))
   assertFalse(text,text.contains("ORIGINAL"))
   assertTrue(text.contains("Fragmento vecino intacto."))
   assertTrue(text.contains("Segunda página intacta."))
   assertEquals(2,doc.numberOfPages)
  }
 }
 @Test fun fontSizeAndColorChangeAffectOnlySelectedFragment() {
  PDDocument.load(fixture()).use { doc ->
   val before=textRuns(ContentStreamEngine.parse(doc.getPage(0)).root)
   replaceExistingText(doc,0,before.first().startIndex,"Cambio con ñ y acentos: áéíóú.",null,19f,0xFFB3261E.toInt())
   val bytes=ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
   PDDocument.load(bytes).use { reopened ->
    val after=textRuns(ContentStreamEngine.parse(reopened.getPage(0)).root)
    assertEquals(19f,after.first().fontSize,0.01f)
    assertEquals(0xFFB3261E.toInt(),after.first().colorArgb)
    assertEquals(before[1].text,after[1].text)
    assertEquals(before[1].fontSize,after[1].fontSize,0.01f)
    assertEquals(before[1].colorArgb,after[1].colorArgb)
   }
  }
 }
 @Test fun fontSubstitutionKeepsSpanishTextAndOtherPage() {
  PDDocument.load(fixture()).use { doc ->
   val first=textRuns(ContentStreamEngine.parse(doc.getPage(0)).root).first()
   val font=File("src/main/assets/fonts/LiberationSerif-BoldItalic.ttf").inputStream().use { PDType0Font.load(doc,it,true) }
   replaceExistingText(doc,0,first.startIndex,"Edición real: España, pingüino y revisión.",font,16f,null)
   val bytes=ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
   PDDocument.load(bytes).use { reopened ->
    val text=PDFTextStripper().getText(reopened)
    assertTrue(text,text.contains("España, pingüino y revisión."))
    assertTrue(text.contains("Segunda página intacta."))
    assertFalse(text.contains("ORIGINAL"))
   }
  }
 }
 @Test fun restoreRecoversPreviousPageContents() {
  PDDocument.load(fixture()).use { doc ->
   val page=doc.getPage(0);val snapshot=ElementEditor.snapshot(page)!!
   val first=textRuns(ContentStreamEngine.parse(page).root).first()
   replaceExistingText(doc,0,first.startIndex,"Cambio temporal",null,null,null)
   ElementEditor.restore(doc,page,snapshot)
   val result=ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
   PDDocument.load(result).use { assertTrue(PDFTextStripper().getText(it).contains("ORIGINAL")) }
  }
 }
}
