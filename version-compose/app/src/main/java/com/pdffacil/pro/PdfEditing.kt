package com.pdffacil.pro

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pdffacil.pro.engine.*
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.*
import com.tom_roush.pdfbox.pdmodel.graphics.image.*
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.*

internal fun insertBlankPage(doc:PDDocument,reference:Int,position:String,size:String):Int {
 require(reference in 0 until doc.numberOfPages)
 val source=doc.getPage(reference)
 val box=when(size) { "A4"->PDRectangle.A4;"Carta"->PDRectangle.LETTER;else->source.cropBox }
 val page=PDPage(PDRectangle(box.width,box.height))
 if(size=="Igual") page.rotation=source.rotation
 return when(position) {
  "Antes"->{doc.pages.insertBefore(page,source);reference}
  "Al final"->{val index=doc.numberOfPages;doc.addPage(page);index}
  else->{doc.pages.insertAfter(page,source);reference+1}
 }
}
internal fun imageNodes(page:PDPage):List<DrawNode> = ContentStreamEngine.parse(page).leaves.filter { it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null }
internal fun imageObject(page:PDPage,node:DrawNode):PDImageXObject {
 val parsed=ContentStreamEngine.parse(page)
 val name=parsed.tokens.subList(node.startIndex,node.endIndex+1).filterIsInstance<COSName>().lastOrNull() ?: error("Imagen no compatible")
 return page.resources.getXObject(name) as? PDImageXObject ?: error("El elemento no es una imagen independiente")
}
internal fun selectedImageBitmap(doc:PDDocument,pageIndex:Int,ordinal:Int):Bitmap {
 require(doc.currentAccessPermission.canExtractContent()) { "El PDF restringe la copia de contenido" }
 val p=doc.getPage(pageIndex);val node=imageNodes(p).getOrNull(ordinal) ?: error("Selecciona una imagen")
 val image=imageObject(p,node)
 val sample=ceil(max(image.width,image.height)/2000.0).toInt().coerceAtLeast(1)
 return image.getImage(null,sample) ?: error("No se puede leer esta imagen")
}
internal fun deleteImage(doc:PDDocument,pageIndex:Int,ordinal:Int) {
 val page=doc.getPage(pageIndex);val parsed=ContentStreamEngine.parse(page)
 val node=parsed.leaves.filter {it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null}.getOrNull(ordinal) ?: error("Selecciona una imagen")
 ElementEditor.deleteRange(doc,page,parsed.tokens,node.startIndex,node.endIndex)
}
internal fun resizeImage(doc:PDDocument,pageIndex:Int,ordinal:Int,factor:Float) {
 require(factor.isFinite() && factor in 0.1f..5f) { "Tamaño no válido" }
 if(abs(factor-1f)<0.0001f) return
 val page=doc.getPage(pageIndex);val parsed=ContentStreamEngine.parse(page)
 val node=parsed.leaves.filter {it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null}.getOrNull(ordinal) ?: error("Selecciona una imagen")
 val b=node.bounds!!
 require(ElementEditor.editElement(doc,page,parsed.tokens,node,EditRequest(scaleX=factor,scaleY=factor,dx=(1-factor)*b.width/2,dy=(1-factor)*b.height/2)) is EditResult.Applied)
}
private fun rewriteDraw(doc:PDDocument,page:PDPage,parsed:ParsedPage,node:DrawNode,replacement:List<Any>) {
 val out=parsed.tokens.take(node.startIndex)+replacement+parsed.tokens.drop(node.endIndex+1)
 val stream=PDStream(doc)
 stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(out) }
 page.setContents(stream)
}
internal fun rotateImage(doc:PDDocument,pageIndex:Int,ordinal:Int) {
 val page=doc.getPage(pageIndex);val parsed=ContentStreamEngine.parse(page)
 val node=parsed.leaves.filter {it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null}.getOrNull(ordinal) ?: error("Selecciona una imagen")
 val b=node.bounds!!;val cx=(b.minX+b.maxX)/2;val cy=(b.minY+b.maxY)/2
 val m=node.ctm ?: error("No se puede girar esta imagen");val inverse=m.inverse() ?: error("Transformación no compatible")
 val turn=Affine.translate(-cx,-cy).then(Affine(0f,-1f,1f,0f,0f,0f)).then(Affine.translate(cx,cy))
 val c=m.then(turn).then(inverse)
 val replacement=listOf(Operator.getOperator("q"),COSFloat(c.a),COSFloat(c.b),COSFloat(c.c),COSFloat(c.d),COSFloat(c.e),COSFloat(c.f),Operator.getOperator("cm"))+parsed.tokens.subList(node.startIndex,node.endIndex+1)+Operator.getOperator("Q")
 rewriteDraw(doc,page,parsed,node,replacement)
}
/** Replace only this occurrence, preserving shared image resources elsewhere. */
internal fun replaceImageCrop(doc:PDDocument,pageIndex:Int,ordinal:Int,replacement:PDImageXObject,crop:Rect) {
 require(crop.left>=0f && crop.top>=0f && crop.right<=1f && crop.bottom<=1f && crop.width>0f && crop.height>0f)
 val page=doc.getPage(pageIndex);val parsed=ContentStreamEngine.parse(page)
 val node=parsed.leaves.filter {it.kind==NodeKind.IMAGE && it.label=="Image" && it.bounds!=null}.getOrNull(ordinal) ?: error("Selecciona una imagen")
 val resources=page.resources ?: PDResources().also {page.resources=it}
 val name=resources.add(replacement)
 val tokens=listOf(Operator.getOperator("q"),COSFloat(crop.width),COSFloat(0f),COSFloat(0f),COSFloat(crop.height),COSFloat(crop.left),COSFloat(1-crop.bottom),Operator.getOperator("cm"),name,Operator.getOperator("Do"),Operator.getOperator("Q"))
 rewriteDraw(doc,page,parsed,node,tokens)
}
internal data class PixelCrop(val left:Int,val top:Int,val right:Int,val bottom:Int) {
 fun normalized(width:Int,height:Int)=Rect(left.toFloat()/width,top.toFloat()/height,right.toFloat()/width,bottom.toFloat()/height)
}
internal fun pixelCrop(width:Int,height:Int,rect:Rect):PixelCrop {
 require(width>0 && height>0 && rect.width>0f && rect.height>0f)
 val left=floor(rect.left.coerceIn(0f,1f)*width).toInt().coerceIn(0,width-1)
 val top=floor(rect.top.coerceIn(0f,1f)*height).toInt().coerceIn(0,height-1)
 return PixelCrop(left,top,ceil(rect.right.coerceIn(0f,1f)*width).toInt().coerceIn(left+1,width),ceil(rect.bottom.coerceIn(0f,1f)*height).toInt().coerceIn(top+1,height))
}
internal fun cropImage(doc:PDDocument,pageIndex:Int,ordinal:Int,rect:Rect) {
 val full=selectedImageBitmap(doc,pageIndex,ordinal)
 try {
  val crop=pixelCrop(full.width,full.height,rect)
  require(crop.left>0 || crop.top>0 || crop.right<full.width || crop.bottom<full.height) { "Selecciona un área de recorte menor" }
  val bitmap=Bitmap.createBitmap(full,crop.left,crop.top,crop.right-crop.left,crop.bottom-crop.top)
  try { replaceImageCrop(doc,pageIndex,ordinal,LosslessFactory.createFromImage(doc,bitmap),crop.normalized(full.width,full.height)) }
  finally {if(bitmap!==full) bitmap.recycle()}
 } finally {full.recycle()}
}
internal fun placeImageObject(doc:PDDocument,pageIndex:Int,image:PDImageXObject,point:Offset?=null) {
 val page=doc.getPage(pageIndex);val box=page.cropBox;val rot=((page.rotation%360)+360)%360
 val visibleWidth=if(rot==90 || rot==270) box.height else box.width
 val visibleHeight=if(rot==90 || rot==270) box.width else box.height
 val width=minOf(visibleWidth*0.45f,300f)
 val height=minOf(width*image.height/image.width,visibleHeight*0.8f)
 val actualWidth=height*image.width/image.height
 val at=point ?: Offset((1-actualWidth/visibleWidth)/2,(1-height/visibleHeight)/2)
 val anchor=pagePoint(page,at)
 val angle=Math.toRadians(rot.toDouble());val co=cos(angle).toFloat();val si=sin(angle).toFloat()
 val matrix=Matrix(co*actualWidth,si*actualWidth,-si*height,co*height,anchor.x+si*height,anchor.y-co*height)
 PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use {it.drawImage(image,matrix)}
}
