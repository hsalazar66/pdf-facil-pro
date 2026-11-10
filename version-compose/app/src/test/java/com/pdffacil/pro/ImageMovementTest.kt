package com.pdffacil.pro

import androidx.compose.ui.geometry.Offset
import com.pdffacil.pro.engine.ContentStreamEngine
import com.pdffacil.pro.engine.NodeKind
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class ImageMovementTest {
 private fun fixture(rotation:Int=0):ByteArray = PDDocument().use { doc ->
  val page=PDPage();page.rotation=rotation;doc.addPage(page)
  val image=PDImageXObject(doc)
  image.width=2;image.height=2;image.bitsPerComponent=8;image.colorSpace=PDDeviceRGB.INSTANCE
  image.cosObject.createOutputStream(COSName.FLATE_DECODE).use { it.write(byteArrayOf(-1,0,0, 0,-1,0, 0,0,-1, -1,-1,-1)) }
  PDPageContentStream(doc,page).use { s ->
   s.drawImage(image,50f,150f,100f,80f)
   // Same image resource drawn twice: moving one occurrence must not move the other.
   s.drawImage(image,250f,350f,100f,80f)
   s.setNonStrokingColor(0f,1f,0f);s.addRect(10f,10f,20f,20f);s.fill()
  }
  ByteArrayOutputStream().also {doc.save(it)}.toByteArray()
 }
 @Test fun movingExistingImageSurvivesSaveWithoutMovingOtherOccurrence() {
  val original=fixture()
  File("build/verification").mkdirs()
  File("build/verification/imagen-movible.pdf").writeBytes(original)
  PDDocument.load(original).use { doc ->
   val page=doc.getPage(0);val parsed=ContentStreamEngine.parse(page)
   val before=parsed.leaves.filter {it.kind==NodeKind.IMAGE}
   moveImage(doc,0,0,Offset(20f/page.cropBox.width,-30f/page.cropBox.height))
   val saved=ByteArrayOutputStream().also {doc.save(it)}.toByteArray()
   PDDocument.load(saved).use { reopened ->
    val result=ContentStreamEngine.parse(reopened.getPage(0));val images=result.leaves.filter {it.kind==NodeKind.IMAGE}
    assertEquals(2,images.size)
    assertEquals(70f,images[0].bounds!!.minX,0.05f);assertEquals(180f,images[0].bounds!!.minY,0.05f)
    assertEquals(before[1].bounds!!.minX,images[1].bounds!!.minX,0.05f)
    assertEquals(before[1].bounds!!.minY,images[1].bounds!!.minY,0.05f)
    assertEquals(100f,images[0].bounds!!.width,0.05f);assertEquals(80f,images[0].bounds!!.height,0.05f)
    assertEquals(1,result.leaves.count {it.kind==NodeKind.PATH})
   }
  }
 }
 @Test fun movementOnRotatedPageUsesScreenCoordinates() {
  PDDocument.load(fixture(90)).use { doc ->
   val b=doc.getPage(0).cropBox
   moveImage(doc,0,0,Offset(0.1f,0.2f))
   val saved=ByteArrayOutputStream().also {doc.save(it)}.toByteArray()
   PDDocument.load(saved).use { reopened ->
    val image=ContentStreamEngine.parse(reopened.getPage(0)).leaves.first {it.kind==NodeKind.IMAGE}
    assertEquals(50f+b.width*0.2f,image.bounds!!.minX,0.05f)
    assertEquals(150f+b.height*0.1f,image.bounds!!.minY,0.05f)
    assertEquals(90,reopened.getPage(0).rotation)
   }
  }
 }
 @Test fun successiveMovesAccumulateAndCanBeUndoneWithSavedRevision() {
  val original=fixture()
  PDDocument.load(original).use { doc ->
   val b=doc.getPage(0).cropBox
   repeat(2) {moveImage(doc,0,0,Offset(10f/b.width,0f))}
   val image=ContentStreamEngine.parse(doc.getPage(0)).leaves.first {it.kind==NodeKind.IMAGE}
   assertEquals(70f,image.bounds!!.minX,0.05f)
  }
  PDDocument.load(original).use { doc ->
   val image=ContentStreamEngine.parse(doc.getPage(0)).leaves.first {it.kind==NodeKind.IMAGE}
   assertEquals(50f,image.bounds!!.minX,0.05f)
  }
 }
}
