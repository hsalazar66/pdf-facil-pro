package com.pdffacil.pro

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pdffacil.pro.engine.*
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class BasicEditingTest {
 private fun image(doc:PDDocument):PDImageXObject = PDImageXObject(doc).apply {
  width=2;height=2;bitsPerComponent=8;colorSpace=PDDeviceRGB.INSTANCE
  cosObject.createOutputStream(COSName.FLATE_DECODE).use {it.write(byteArrayOf(-1,0,0,0,-1,0,0,0,-1,-1,-1,-1))}
 }
 private fun fixture():ByteArray = PDDocument().use {doc ->
  val p=PDPage();doc.addPage(p)
  val im=image(doc)
  PDPageContentStream(doc,p).use {s->s.drawImage(im,50f,150f,100f,80f);s.drawImage(im,250f,350f,100f,80f)}
  ByteArrayOutputStream().also {doc.save(it)}.toByteArray()
 }
 private fun reopened(doc:PDDocument,check:(PDDocument)->Unit) {
  val data=ByteArrayOutputStream().also {doc.save(it)}.toByteArray()
  PDDocument.load(data).use(check)
 }
 @Test fun enlargeAndReducePreserveCenterAndOtherOccurrence() {
  PDDocument.load(fixture()).use {doc ->
   resizeImage(doc,0,0,2f)
   moveImage(doc,0,0,Offset.Zero)
   reopened(doc) {copy ->
    val images=imageNodes(copy.getPage(0));val b=images[0].bounds!!
    assertEquals(200f,b.width,0.05f);assertEquals(160f,b.height,0.05f)
    assertEquals(100f,(b.minX+b.maxX)/2,0.05f);assertEquals(190f,(b.minY+b.maxY)/2,0.05f)
    assertEquals(250f,images[1].bounds!!.minX,0.05f)
    resizeImage(copy,0,0,0.5f)
    assertEquals(50f,imageNodes(copy.getPage(0))[0].bounds!!.minX,0.05f)
   }
  }
 }
 @Test fun rotateKeepsCenterAndSwapsVisibleDimensions() {
  PDDocument.load(fixture()).use {doc ->
   rotateImage(doc,0,0)
   reopened(doc) {copy ->
    val b=imageNodes(copy.getPage(0))[0].bounds!!
    assertEquals(80f,b.width,0.05f);assertEquals(100f,b.height,0.05f)
    assertEquals(100f,(b.minX+b.maxX)/2,0.05f);assertEquals(190f,(b.minY+b.maxY)/2,0.05f)
    assertEquals(250f,imageNodes(copy.getPage(0))[1].bounds!!.minX,0.05f)
   }
  }
 }
 @Test fun cropReplacementChangesOnlyChosenOccurrenceAndItsBounds() {
  PDDocument.load(fixture()).use {doc ->
   val oldName=imageObject(doc.getPage(0),imageNodes(doc.getPage(0))[1]).cosObject
   replaceImageCrop(doc,0,0,image(doc),Rect(0f,0f,0.5f,1f))
   reopened(doc) {copy ->
    val nodes=imageNodes(copy.getPage(0));assertEquals(2,nodes.size)
    assertEquals(50f,nodes[0].bounds!!.width,0.05f);assertEquals(80f,nodes[0].bounds!!.height,0.05f)
    assertEquals(50f,nodes[0].bounds!!.minX,0.05f)
    assertEquals(100f,nodes[1].bounds!!.width,0.05f)
    assertNotSame(imageObject(copy.getPage(0),nodes[0]).cosObject,imageObject(copy.getPage(0),nodes[1]).cosObject)
   }
   assertSame(oldName,imageObject(doc.getPage(0),imageNodes(doc.getPage(0))[1]).cosObject)
  }
 }
 @Test fun imageCutRemovesOneOccurrenceAndPasteAddsAnImageOnAnotherPage() {
  PDDocument.load(fixture()).use {doc ->
   val im=imageObject(doc.getPage(0),imageNodes(doc.getPage(0))[0])
   deleteImage(doc,0,0)
   insertBlankPage(doc,0,"Después","Igual")
   placeImageObject(doc,1,im,Offset(0.1f,0.2f))
   reopened(doc) {copy ->
    assertEquals(1,imageNodes(copy.getPage(0)).size);assertEquals(1,imageNodes(copy.getPage(1)).size)
    assertEquals(250f,imageNodes(copy.getPage(0))[0].bounds!!.minX,0.05f)
    val b=imageNodes(copy.getPage(1))[0].bounds!!
    assertEquals(copy.getPage(1).cropBox.width*0.1f,b.minX,0.05f)
    assertEquals(copy.getPage(1).cropBox.height*0.8f,b.maxY,0.05f)
   }
  }
 }
 @Test fun blankPageInsertionPreservesOrderSizeAndRotation() {
  PDDocument().use {doc ->
   val first=PDPage(PDRectangle(400f,500f));first.rotation=90;doc.addPage(first)
   val second=PDPage();doc.addPage(second)
   assertEquals(1,insertBlankPage(doc,0,"Después","Igual"))
   assertEquals(400f,doc.getPage(1).cropBox.width,0.01f);assertEquals(90,doc.getPage(1).rotation)
   assertSame(first.cosObject,doc.getPage(0).cosObject);assertSame(second.cosObject,doc.getPage(2).cosObject)
   assertEquals(0,insertBlankPage(doc,0,"Antes","A4"));assertEquals(PDRectangle.A4.width,doc.getPage(0).cropBox.width,0.01f)
   assertEquals(4,insertBlankPage(doc,0,"Al final","Carta"));assertEquals(5,doc.numberOfPages)
   reopened(doc) {assertEquals(5,it.numberOfPages);assertEquals(90,it.getPage(2).rotation)}
  }
 }
 @Test fun cuttingTextThenPastingDoesNotRetainTheOriginalOccurrence() {
  PDDocument().use {doc ->
   val font=File("src/main/assets/fonts/LiberationSans-Regular.ttf").inputStream().use {PDType0Font.load(doc,it,false)}
   doc.addPage(PDPage());doc.addPage(PDPage())
   PDPageContentStream(doc,doc.getPage(0)).use {s->s.beginText();s.setFont(font,14f);s.newLineAtOffset(40f,700f);s.showText("Texto seleccionado");s.endText()}
   val fragment=textRuns(ContentStreamEngine.parse(doc.getPage(0)).root).first()
   val copied=fragment.text!!
   replaceExistingText(doc,0,fragment.startIndex,"",null,null,null)
   PDPageContentStream(doc,doc.getPage(1)).use {s->s.beginText();s.setFont(font,14f);s.newLineAtOffset(40f,600f);s.showText(copied);s.endText()}
   reopened(doc) {copy ->
    val first=PDFTextStripper().apply {startPage=1;endPage=1}.getText(copy)
    val second=PDFTextStripper().apply {startPage=2;endPage=2}.getText(copy)
    assertFalse(first.contains(copied));assertTrue(second.contains(copied))
   }
  }
 }
 @Test fun cropPixelsClampToValidBitmapWithoutChangingAspectByRounding() {
  val p=pixelCrop(200,100,Rect(0.1f,0.2f,0.75f,0.8f))
  assertEquals(PixelCrop(20,20,150,80),p)
  assertEquals(Rect(0.1f,0.2f,0.75f,0.8f),p.normalized(200,100))
  val tiny=pixelCrop(2,2,Rect(0.01f,0.01f,0.49f,0.99f))
  assertEquals(PixelCrop(0,0,1,2),tiny)
 }
}
