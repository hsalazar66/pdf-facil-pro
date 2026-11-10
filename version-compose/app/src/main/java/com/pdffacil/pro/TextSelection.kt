package com.pdffacil.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import kotlin.math.*

@Composable internal fun TextSelectionLayer(page:TextPage,selected:Set<Int>,onSelect:(Set<Int>)->Unit) {
 var start by remember {mutableStateOf(Offset.Zero)}
 var area by remember {mutableStateOf<Rect?>(null)}
 val latest by rememberUpdatedState(selected)
 Canvas(Modifier.fillMaxSize().semantics {contentDescription="Seleccionar varias líneas y tablas"}.pointerInput(page) {
  awaitEachGesture {
   val down=awaitFirstDown(requireUnconsumed=false)
   start=Offset(down.position.x/size.width,down.position.y/size.height);area=Rect(start,start)
   do {
    val event=awaitPointerEvent()
    val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
    val end=Offset((pointer.position.x/size.width).coerceIn(0f,1f),(pointer.position.y/size.height).coerceIn(0f,1f))
    area=Rect(min(start.x,end.x),min(start.y,end.y),max(start.x,end.x),max(start.y,end.y))
    pointer.consume()
   } while(event.changes.any {it.pressed})
   val r=area;area=null
   if(r!=null) onSelect(latest+page.cells.indices.filter {r.contains(page.cells[it].rect.center)})
  }
 }) {
  page.cells.forEachIndexed {index,c->if(index in selected || area?.contains(c.rect.center)==true) drawRect(Color(0x6634B79C),Offset(c.rect.left*size.width,c.rect.top*size.height),Size(c.rect.width*size.width,c.rect.height*size.height))}
  area?.let {drawRect(Color(0x22146B5B),Offset(it.left*size.width,it.top*size.height),Size(it.width*size.width,it.height*size.height))}
 }
}
