package com.pdffacil.pro

import androidx.compose.ui.geometry.Rect
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class DocumentLayoutTest {
 private fun cell(text:String,x:Float,y:Float)=LayoutCell(text,Rect(x,y,x+0.08f,y+0.02f))
 @Test fun selectionKeepsMultipleLinesInReadingOrder() {
  val page=TextPage(600f,800f,listOf(cell("Segunda",0.1f,0.2f),cell("línea",0.19f,0.2f),cell("Primera",0.1f,0.1f),cell("fuera",0.1f,0.8f)))
  assertEquals("Primera\nSegunda línea",selectionText(page,Rect(0f,0f,0.9f,0.4f)))
 }
 @Test fun tableKeepsEmptyCellsAndUnicodeForExcel() {
  val rows=listOf(LayoutRow(listOf(cell("Nombre",0.1f,0.1f),cell("Monto",0.6f,0.1f))),LayoutRow(listOf(cell("José",0.1f,0.2f))),LayoutRow(listOf(cell("42",0.6f,0.3f))))
  val data=tableData(rows)
  assertEquals(listOf(listOf("Nombre","Monto"),listOf("José",""),listOf("","42")),data)
  assertEquals("Nombre\tMonto\nJosé\t\n\t42",tsv(data));assertTrue(tableHtml(data).contains("<td>José</td>"))
 }
 @Test fun wordIsAnEditableDocxWithRealTablesAndEscapedText() {
  val file=File.createTempFile("word-",".docx")
  try {
   writeDocx(listOf(TextPage(600f,800f,listOf(cell("A & B",0.1f,0.1f),cell("Monto",0.6f,0.1f),cell("José",0.1f,0.2f),cell("42",0.6f,0.2f))),TextPage(600f,800f,listOf(cell("Página dos",0.1f,0.1f)))),file)
   ZipFile(file).use {zip->
    for(name in listOf("[Content_Types].xml","_rels/.rels","word/document.xml")) {
     val bytes=zip.getInputStream(zip.getEntry(name)).readBytes()
     DocumentBuilderFactory.newInstance().apply {isNamespaceAware=true}.newDocumentBuilder().parse(bytes.inputStream())
    }
    val xml=zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
    assertTrue(xml.contains("<w:tbl>"));assertTrue(xml.contains("A &amp; B"));assertTrue(xml.contains("José"));assertTrue(xml.contains("w:type=\"page\""));assertFalse(xml.contains("w:drawing"))
   }
  } finally {file.delete()}
 }
 @Test fun glyphExtractionSelectsAcrossOriginalPdfTextOperands() {
  PDDocument().use {doc->
   doc.addPage(PDPage())
   val font=File("src/main/assets/fonts/LiberationSans-Regular.ttf").inputStream().use {PDType0Font.load(doc,it,false)}
   PDPageContentStream(doc,doc.getPage(0)).use {s->s.beginText();s.setFont(font,14f);s.newLineAtOffset(50f,700f);s.showText("Primera línea");s.newLineAtOffset(0f,-30f);s.showText("Segunda línea");s.endText()}
   val result=selectionText(extractTextPage(doc,0),Rect(0f,0f,1f,1f))
   assertTrue(result.contains("Primera línea"));assertTrue(result.contains("Segunda línea"));assertEquals(2,result.lines().size)
  }
 }
 @Test fun createScannedFixtureForOfflineOcrOnAndroid() {
  PDDocument().use {doc->
   val page=PDPage();doc.addPage(page)
   val im=com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject(doc).apply {width=1200;height=600;bitsPerComponent=8;colorSpace=com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE}
   val compressed=java.util.Base64.getDecoder().decode(File("src/test/resources/ocr-fixture.rgb.gz.b64").readText().trim())
   val rgb=java.util.zip.GZIPInputStream(compressed.inputStream()).use {it.readBytes()}
   im.cosObject.createOutputStream(com.tom_roush.pdfbox.cos.COSName.FLATE_DECODE).use {it.write(rgb)}
   PDPageContentStream(doc,page).use {it.drawImage(im,20f,400f,570f,285f)}
   val output=File("build/verification/escaneo-ocr.pdf");output.parentFile.mkdirs();doc.save(output)
   assertTrue(extractTextPage(doc,0).cells.isEmpty())
  }
 }
}
