package com.pdffacil.pro

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import java.io.File
import kotlin.math.*

/** Render the actual transformed PDF while fingers move; commit once on release. */
@Composable internal fun InteractiveImage(source:File,page:Int,fragment:ImageFragment,onCommit:(Float,Offset)->Unit,onError:(String)->Unit) {
 var factor by remember(source,fragment.ordinal) {mutableFloatStateOf(1f)}
 var delta by remember(source,fragment.ordinal) {mutableStateOf(Offset.Zero)}
 var rendered by remember(source) {mutableStateOf<Bitmap?>(null)}
 val latestCommit by rememberUpdatedState(onCommit)
 val latestError by rememberUpdatedState(onError)
 LaunchedEffect(source,fragment.ordinal) {
  snapshotFlow {factor to delta}.conflate().collect {(scale,shift)->
   if(scale!=1f || shift!=Offset.Zero) {
    delay(40)
    try {
     val result=withContext(Dispatchers.IO) {
      val file=File.createTempFile("tamaño-",".pdf",source.parentFile)
      try {PDDocument.load(source).use {doc->resizeImage(doc,page,fragment.ordinal,scale);moveImage(doc,page,fragment.ordinal,shift);doc.save(file)};preview(file,page).bitmap} finally {file.delete()}
     }
     rendered=result
    } catch(e:Exception) {if(e is CancellationException) throw e;latestError(e.localizedMessage ?: "No se pudo previsualizar")}
   }
  }
 }
 // Don't recycle the last frame during composition; Android releases it after this overlay disappears.
 BoxWithConstraints(Modifier.fillMaxSize().semantics {contentDescription="Redimensionar imagen con los dedos"}.pointerInput(source,fragment.ordinal) {
  awaitEachGesture {
   val down=awaitFirstDown(requireUnconsumed=false)
   val width=size.width.toFloat();val height=size.height.toFloat()
   val base=fragment.rect
   val center=Offset(base.center.x*width,base.center.y*height)
   val corner=listOf(base.topLeft,base.topRight,base.bottomLeft,base.bottomRight).map {Offset(it.x*width,it.y*height)}.minByOrNull {(down.position-it).getDistance()}!!
   val handle=(down.position-corner).getDistance()<32.dp.toPx()
   val normalized=Offset(down.position.x/width,down.position.y/height)
   if(!base.inflate(0.04f).contains(normalized)) return@awaitEachGesture
   var active=false
   do {
    val event=awaitPointerEvent()
    val pressed=event.changes.count {it.pressed}
    if(event.changes.any {it.positionChanged()}) {
     active=true
     if(pressed>=2) {factor=(factor*event.calculateZoom()).coerceIn(0.25f,3f);val pan=event.calculatePan();delta+=Offset(pan.x/width,pan.y/height)}
     else if(handle && pressed==1) {
      val pos=event.changes.first {it.pressed}.position
      factor=((pos-center).getDistance()/(corner-center).getDistance().coerceAtLeast(1f)).coerceIn(0.25f,3f)
     } else if(pressed==1) {val pan=event.calculatePan();delta+=Offset(pan.x/width,pan.y/height)}
     event.changes.forEach {it.consume()}
    }
   } while(event.changes.any {it.pressed})
   if(active && (abs(factor-1f)>0.001f || delta.getDistance()>0.001f)) latestCommit(factor,delta)
  }
 }) {
  rendered?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize())}
  Canvas(Modifier.fillMaxSize()) {
   val b=fragment.rect;val center=b.center+delta
   val r=Rect(center.x-b.width*factor/2,center.y-b.height*factor/2,center.x+b.width*factor/2,center.y+b.height*factor/2)
   drawRect(MenuGreen,Offset(r.left*size.width,r.top*size.height),Size(r.width*size.width,r.height*size.height),style=Stroke(2.dp.toPx()))
   listOf(r.topLeft,r.topRight,r.bottomLeft,r.bottomRight).forEach {drawCircle(MenuGreen,7.dp.toPx(),Offset(it.x*size.width,it.y*size.height))}
  }
  val bounds=fragment.rect
  val center=bounds.center+delta
  val right=center.x+bounds.width*factor/2;val bottom=center.y+bounds.height*factor/2
  Box(Modifier.offset(x=maxWidth*right-12.dp,y=maxHeight*bottom-12.dp).size(24.dp).semantics {contentDescription="Esquina inferior derecha de imagen"})
  Text("${(factor*100).roundToInt()} %",color=MenuGreen)
 }
}
