package com.pdffacil.pro

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import java.io.File
import kotlin.math.*

@Composable internal fun TablePageLayer(source:File,page:Int,tables:List<ManagedTable>,selected:String?,onSelect:(String?)->Unit,onEdit:(ManagedTable,Pair<Int,Int>)->Unit,onCommit:(ManagedTable)->Unit,onError:(String)->Unit) {
 val context=LocalContext.current
 var request by remember(source) {mutableStateOf<ManagedTable?>(null)}
 var rendered by remember(source) {mutableStateOf<Bitmap?>(null)}
 var measured by remember(source) {mutableStateOf<ManagedTable?>(null)}
 val latestSelected by rememberUpdatedState(selected)
 val select by rememberUpdatedState(onSelect);val edit by rememberUpdatedState(onEdit);val commit by rememberUpdatedState(onCommit)
 LaunchedEffect(source) {
  snapshotFlow {request}.conflate().collect {pending->if(pending!=null) {
   delay(50)
   try {
    val result=withContext(Dispatchers.IO) {
     val temp=File.createTempFile("tabla-vista-",".pdf",source.parentFile)
     try {var result:ManagedTable?=null;PDDocument.load(source).use {doc->result=upsertTable(context,doc,page,pending);doc.save(temp)};preview(temp,page).bitmap to result!!} finally {temp.delete()}
    };rendered=result.first;measured=result.second
   } catch(e:Exception) {if(e is CancellationException) throw e;onError(e.localizedMessage ?: "No se pudo previsualizar la tabla")}
  }}
 }
 BoxWithConstraints(Modifier.fillMaxSize().semantics {contentDescription="Tablas editables de la página"}.pointerInput(source,tables.map {it.id}) {
  awaitEachGesture {
   val down=awaitFirstDown(requireUnconsumed=false)
   val w=size.width.toFloat();val h=size.height.toFloat();val norm=Offset(down.position.x/w,down.position.y/h)
   val current=tables.firstOrNull {it.id==latestSelected}
   fun near(table:ManagedTable):Boolean {
    val corner=Offset(table.rect.right*w,table.rect.bottom*h);val handle=Offset(table.rect.center.x*w,table.rect.top*h-10.dp.toPx())
    return (down.position-corner).getDistance()<20.dp.toPx() || (down.position-handle).getDistance()<20.dp.toPx()
   }
   val table=if(current!=null && near(current)) current else tables.lastOrNull {it.rect.contains(norm)}
   if(table==null) {select(null);return@awaitEachGesture}
   select(table.id)
   val corner=Offset(table.rect.right*w,table.rect.bottom*h)
   val resize=(down.position-corner).getDistance()<20.dp.toPx()
   var dragged=false;var updated:ManagedTable=table
   do {
    val event=awaitPointerEvent();val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
    val delta=pointer.position-down.position
    if(delta.getDistance()>viewConfiguration.touchSlop || dragged) {
     dragged=true
     updated=if(resize) table.copy(width=(table.width+delta.x/w).coerceIn(0.2f,0.94f)) else table.copy(x=table.x+delta.x/w,y=table.y+delta.y/h)
     request=updated;pointer.consume()
    }
   } while(event.changes.any {it.pressed})
   if(dragged) commit(updated) else if(table.rect.contains(norm)) edit(table,table.cellAt(norm))
  }
 }) {
  rendered?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize())}
  Canvas(Modifier.fillMaxSize()) {tables.forEach {original->
   val table=if(request?.id==original.id) measured ?: request!! else original
   val b=table.rect;val active=table.id==selected
   drawRect(MenuGreen.copy(alpha=if(active) 1f else 0.6f),Offset(b.left*size.width,b.top*size.height),Size(b.width*size.width,b.height*size.height),style=Stroke(if(active) 2.dp.toPx() else 1.dp.toPx()))
   if(active) {drawCircle(MenuGreen,7.dp.toPx(),Offset(b.right*size.width,b.bottom*size.height));drawCircle(MenuGreen,7.dp.toPx(),Offset(b.center.x*size.width,b.top*size.height-10.dp.toPx()))}
  }}
  tables.forEachIndexed {index,original->
   val table=if(request?.id==original.id) measured ?: request!! else original
   val b=table.rect
   Box(Modifier.offset(maxWidth*b.left,maxHeight*b.top).size(maxWidth*b.width,maxHeight*b.height).semantics {contentDescription="Tabla ${index+1}"})
   if(table.id==selected) {
    Box(Modifier.offset(maxWidth*b.right-12.dp,maxHeight*b.bottom-12.dp).size(24.dp).semantics {contentDescription="Cambiar ancho de tabla"})
    Box(Modifier.offset(maxWidth*b.center.x-12.dp,maxHeight*b.top-22.dp).size(24.dp).semantics {contentDescription="Mover tabla"})
   }
  }
 }
}

