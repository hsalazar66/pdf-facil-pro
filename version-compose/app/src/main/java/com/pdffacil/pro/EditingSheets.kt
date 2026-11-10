package com.pdffacil.pro

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ShareDocumentSheet(onDismiss:()->Unit,onDestination:(PdfDestination)->Unit) {
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
   Text("Compartir y guardar",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
   Text("Se enviará una copia PDF con los cambios aplicados.",fontSize=12.sp,color=MenuMuted,modifier=Modifier.padding(horizontal=20.dp,vertical=4.dp))
   listOf(
    Triple(PdfDestination.WHATSAPP,"WhatsApp","WhatsApp personal o Business, si está instalado"),
    Triple(PdfDestination.DRIVE,"Google Drive","Elige tu cuenta y carpeta en la aplicación de Drive"),
    Triple(PdfDestination.EMAIL,"Correo electrónico","Adjunta el PDF en una aplicación de correo"),
    Triple(PdfDestination.DEVICE,"Guardar en el celular","Elige Descargas o una carpeta del dispositivo"),
    Triple(PdfDestination.OTHER,"Otras aplicaciones","Abrir el selector de compartir de Android")
   ).forEach { (destination,title,description) ->
    ListItem(headlineContent={Text(title)},supportingContent={Text(description,fontSize=12.sp)},leadingContent={EditorIcon(if(destination==PdfDestination.DEVICE) "Guardar" else "Compartir")},modifier=Modifier.clickable {onDestination(destination)})
   }
  }
 }
}
internal data class ImageEditorData(val ordinal:Int,val bitmap:Bitmap)
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ImageToolsSheet(data:ImageEditorData,onDismiss:()->Unit,onResize:()->Unit,onRotate:()->Unit,onCrop:(Rect)->Unit) {
 var mode by remember {mutableStateOf("Tamaño")}
 var factor by remember {mutableFloatStateOf(1f)}
 var area by remember {mutableStateOf(Rect(0f,0f,1f,1f))}
 var origin by remember {mutableStateOf(Offset.Zero)}
 var w by remember {mutableIntStateOf(1)};var h by remember {mutableIntStateOf(1)}
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("Editar imagen",style=MaterialTheme.typography.titleLarge)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilterChip(mode=="Tamaño",{mode="Tamaño"},label={Text("Tamaño")})
    FilterChip(mode=="Recortar",{mode="Recortar"},label={Text("Recortar")})
    OutlinedButton(onClick=onRotate) {Text("Girar 90°")}
   }
   BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFFE7EEEA)),contentAlignment=Alignment.Center) {
    val ratio=data.bitmap.width.toFloat()/data.bitmap.height
    val width=minOf(maxWidth,maxHeight*ratio)
    Box(Modifier.size(width,width/ratio).onSizeChanged {w=it.width;h=it.height}.pointerInput(mode) {
     if(mode=="Recortar") detectDragGestures(onDragStart={p->origin=Offset((p.x/w).coerceIn(0f,1f),(p.y/h).coerceIn(0f,1f));area=Rect(origin,origin)},onDragEnd={},onDragCancel={area=Rect(0f,0f,1f,1f)}) { event,_ ->
      event.consume();val end=Offset((event.position.x/w).coerceIn(0f,1f),(event.position.y/h).coerceIn(0f,1f))
      area=Rect(min(origin.x,end.x),min(origin.y,end.y),max(origin.x,end.x),max(origin.y,end.y))
     }
    }) {
     Image(data.bitmap.asImageBitmap(),"Previsualización de imagen",Modifier.fillMaxSize())
     if(mode=="Recortar") Canvas(Modifier.fillMaxSize()) {
      val l=area.left*size.width;val t=area.top*size.height;val r=area.right*size.width;val b=area.bottom*size.height
      val shade=Color(0x88000000)
      drawRect(shade,Offset.Zero,Size(size.width,t));drawRect(shade,Offset(0f,b),Size(size.width,size.height-b))
      drawRect(shade,Offset(0f,t),Size(l,(b-t).coerceAtLeast(0f)));drawRect(shade,Offset(r,t),Size(size.width-r,(b-t).coerceAtLeast(0f)))
      drawRect(MenuGreen,Offset(l,t),Size((r-l).coerceAtLeast(0f),(b-t).coerceAtLeast(0f)),style=Stroke(2.dp.toPx()))
     }
    }
   }
   if(mode=="Tamaño") {
    Text("Ajusta la imagen directamente en la página con dos dedos o arrastrando su esquina.")
    Button(onClick=onResize,modifier=Modifier.fillMaxWidth()) {Text("Redimensionar")}
   } else {
    Text("Arrastra sobre la imagen para seleccionar el área que conservarás.",fontSize=12.sp)
    TextButton(onClick={area=Rect(0f,0f,1f,1f)}) {Text("Restablecer recorte")}
    Button(onClick={onCrop(area)},enabled=area.width>0.02f && area.height>0.02f && area!=Rect(0f,0f,1f,1f),modifier=Modifier.fillMaxWidth()) {Text("Aplicar recorte")}
   }
   TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {Text("Cerrar")}
  }
 }
}
@Composable internal fun NewPageDialog(onDismiss:()->Unit,onInsert:(String,String)->Unit) {
 var position by remember {mutableStateOf("Después")};var size by remember {mutableStateOf("Igual")}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Añadir hoja nueva")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Ubicación")
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("Antes","Después","Al final").forEach {p->FilterChip(position==p,{position=p},label={Text(p,fontSize=12.sp)})}}
  Text("Tamaño de la hoja")
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("Igual","A4","Carta").forEach {p->FilterChip(size==p,{size=p},label={Text(p,fontSize=12.sp)})}}
  Text("Igual conserva el tamaño y orientación de la página actual.",fontSize=12.sp)
 }},confirmButton={TextButton(onClick={onInsert(position,size)}) {Text("Añadir hoja")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}
