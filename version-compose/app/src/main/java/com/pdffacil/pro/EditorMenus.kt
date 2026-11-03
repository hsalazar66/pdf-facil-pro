package com.pdffacil.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val MenuGreen=Color(0xFF146B5B)
internal val MenuInk=Color(0xFF203C35)
internal val MenuMuted=Color(0xFF64766F)

/** Geometric icons drawn locally; no font glyphs, network assets or new dependencies. */
@Composable internal fun EditorIcon(name:String, color:Color=MenuGreen, modifier:Modifier=Modifier.size(24.dp)) {
 Canvas(modifier) {
  withTransform({scale(size.width/24f,size.height/24f,pivot=Offset.Zero)}) {
   val stroke=Stroke(1.7f,cap=StrokeCap.Round)
   fun line(x:Float,y:Float,a:Float,b:Float)=drawLine(color,Offset(x,y),Offset(a,b),1.7f,cap=StrokeCap.Round)
   fun box(x:Float,y:Float,w:Float,h:Float)=drawRoundRect(color,Offset(x,y),Size(w,h),androidx.compose.ui.geometry.CornerRadius(1.8f),style=stroke)
   fun path(block:Path.()->Unit)=drawPath(Path().apply(block),color,style=stroke)
   when(name) {
    "Ver" -> { path { moveTo(2f,12f);cubicTo(7f,3f,17f,3f,22f,12f);cubicTo(17f,21f,7f,21f,2f,12f) };drawCircle(color,3f,Offset(12f,12f),style=stroke) }
    "Editar","Modificar" -> { path { moveTo(4f,16f);lineTo(15f,5f);lineTo(19f,9f);lineTo(8f,20f);lineTo(3f,21f);close() };line(13f,7f,17f,11f) }
    "Anotar","Resaltar" -> { path { moveTo(7f,15f);lineTo(16f,4f);lineTo(21f,8f);lineTo(12f,19f);close() };line(5f,20f,13f,20f);line(7f,15f,12f,19f) }
    "Más","Herramientas" -> { box(3f,3f,7f,7f);box(14f,3f,7f,7f);box(3f,14f,7f,7f);box(14f,14f,7f,7f) }
    "Texto nuevo" -> { line(3f,5f,16f,5f);line(9f,5f,9f,20f);line(5f,20f,13f,20f);line(18f,12f,18f,20f);line(14f,16f,22f,16f) }
    "Imagen" -> { box(3f,4f,18f,16f);drawCircle(color,1.6f,Offset(8f,9f));path { moveTo(4f,18f);lineTo(10f,12f);lineTo(14f,16f);lineTo(17f,13f);lineTo(21f,17f) } }
    "Firma" -> { path { moveTo(3f,18f);cubicTo(9f,12f,14f,0f,10f,5f);cubicTo(5f,11f,5f,21f,13f,14f);cubicTo(18f,9f,13f,21f,21f,14f) };line(4f,22f,21f,22f) }
    "Deshacer","Rehacer" -> {
     withTransform({if(name=="Rehacer") scale(-1f,1f,Offset(12f,12f))}) {
      path { moveTo(5f,9f);lineTo(13f,9f);cubicTo(22f,9f,22f,20f,13f,20f) };line(5f,9f,10f,4f);line(5f,9f,10f,14f)
     }
    }
    "Unir PDF" -> { box(2f,3f,9f,13f);box(13f,8f,9f,13f);line(7f,20f,7f,17f);line(7f,20f,11f,20f) }
    "Girar" -> { drawArc(color,25f,280f,false,Offset(4f,4f),Size(16f,16f),style=stroke);path { moveTo(13f,2f);lineTo(18f,3f);lineTo(16f,8f) } }
    "Eliminar" -> { box(6f,7f,12f,14f);line(4f,6f,20f,6f);line(9f,3f,15f,3f);line(10f,10f,10f,17f);line(14f,10f,14f,17f) }
    "Guardar" -> { box(3f,3f,18f,18f);box(7f,3f,10f,7f);box(7f,14f,10f,7f) }
    "Buscar","Ampliar" -> { drawCircle(color,6.5f,Offset(10f,10f),style=stroke);line(15f,15f,21f,21f);if(name=="Ampliar") {line(7f,10f,13f,10f);line(10f,7f,10f,13f)} }
    "Ajustar" -> { path { moveTo(3f,9f);lineTo(3f,3f);lineTo(9f,3f);moveTo(15f,3f);lineTo(21f,3f);lineTo(21f,9f);moveTo(21f,15f);lineTo(21f,21f);lineTo(15f,21f);moveTo(9f,21f);lineTo(3f,21f);lineTo(3f,15f) } }
    "Compartir" -> { line(7f,11f,17f,6f);line(7f,13f,17f,18f);listOf(Offset(5f,12f),Offset(19f,5f),Offset(19f,19f)).forEach {drawCircle(color,2.5f,it,style=stroke)} }
    "Anterior","Siguiente" -> { val left=name=="Anterior";path {moveTo(if(left) 15f else 9f,5f);lineTo(if(left) 8f else 16f,12f);lineTo(if(left) 15f else 9f,19f)} }
    "Páginas" -> {box(6f,3f,14f,17f);path {moveTo(3f,7f);lineTo(3f,22f);lineTo(16f,22f)} }
    else -> { path {moveTo(5f,12f);lineTo(10f,17f);lineTo(20f,6f)} }
   }
  }
 }
}

@Composable internal fun ModeMenu(active:String, enabled:Boolean, onMode:(String)->Unit) {
 Surface(shape=RoundedCornerShape(20.dp),color=Color.White,shadowElevation=2.dp,border=BorderStroke(1.dp,Color(0xFFE1E9E5))) {
  Row(Modifier.fillMaxWidth().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   listOf("Ver","Editar","Anotar","Herramientas").forEach { mode ->
    val selected=mode==active;val ink=if(selected) Color.White else MenuMuted
    Surface(onClick={onMode(mode)},enabled=enabled,shape=RoundedCornerShape(15.dp),color=if(selected) MenuGreen else Color.White,modifier=Modifier.weight(1f).semantics { role=Role.Tab;this.selected=selected;contentDescription=mode }) {
     Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
      EditorIcon(mode,ink,Modifier.size(21.dp))
      Text(if(mode=="Herramientas") "Más" else mode,color=ink,fontSize=12.sp,fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,maxLines=1,softWrap=false)
     }
    }
   }
  }
 }
}

@Composable internal fun MenuAction(label:String,modifier:Modifier=Modifier,selected:Boolean=false,enabled:Boolean=true,danger:Boolean=false,onClick:()->Unit) {
 val accent=if(danger) Color(0xFFB74242) else MenuGreen
 Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),color=if(selected) Color(0xFFE6F3EE) else Color.White,border=BorderStroke(if(selected) 1.5.dp else 1.dp,if(selected) accent else Color(0xFFE1E9E5)),modifier=modifier.heightIn(min=76.dp).semantics { contentDescription=label;role=Role.Button }) {
  Column(Modifier.padding(horizontal=4.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
   EditorIcon(label,if(enabled) accent else Color(0xFFAFBDB6),Modifier.size(25.dp))
   Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=if(enabled) MenuInk else MenuMuted,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
  }
 }
}

@Composable internal fun MenuIconButton(label:String,enabled:Boolean=true,onClick:()->Unit) {
 IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics {contentDescription=label}) { EditorIcon(label,if(enabled) MenuGreen else Color(0xFFB9C5BF),Modifier.size(21.dp)) }
}
