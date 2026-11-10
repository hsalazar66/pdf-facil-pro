package com.pdffacil.pro

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun TableEditor(initial:List<List<String>>?=null,copyOnly:Boolean=false,onDismiss:()->Unit,onConfirm:(List<List<String>>)->Unit) {
 var rows by remember {mutableIntStateOf(initial?.size?.coerceIn(1,20) ?: 3)}
 var columns by remember {mutableIntStateOf(initial?.maxOfOrNull {it.size}?.coerceIn(1,10) ?: 3)}
 var cells by remember {mutableStateOf(initial ?: List(3) {List(3) {""}})}
 fun shape(r:Int,c:Int) {cells=List(r) {y->List(c) {x->cells.getOrNull(y)?.getOrNull(x) ?: ""}};rows=r;columns=c}
 AlertDialog(onDismissRequest=onDismiss,title={Text(if(copyOnly) "Revisar tabla para copiar" else "Insertar tabla")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(if(copyOnly) "Revisa columnas y datos antes de pegarlos en Excel o Word." else "Completa las celdas. Después toca la página para colocar la tabla.",fontSize=12.sp)
  Row {TextButton(onClick={shape((rows-1).coerceAtLeast(1),columns)}) {Text("− fila")};Text("$rows");TextButton(onClick={shape((rows+1).coerceAtMost(20),columns)}) {Text("+ fila")}}
  Row {TextButton(onClick={shape(rows,(columns-1).coerceAtLeast(1))}) {Text("− col.")};Text("$columns");TextButton(onClick={shape(rows,(columns+1).coerceAtMost(10))}) {Text("+ col.")}}
  Column(Modifier.horizontalScroll(rememberScrollState())) {repeat(rows) {r->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {repeat(columns) {c->OutlinedTextField(cells.getOrNull(r)?.getOrNull(c) ?: "",{value->cells=List(rows) {y->List(columns) {x->if(y==r && x==c) value else cells.getOrNull(y)?.getOrNull(x) ?: ""}}},label={Text("${r+1},${c+1}")},modifier=Modifier.width(120.dp),maxLines=3)}}}}
 }},confirmButton={TextButton(onClick={onConfirm(cells)}) {Text(if(copyOnly) "Copiar tabla" else "Colocar tabla")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}
@Composable internal fun ExportDialog(onDismiss:()->Unit,onExport:(Boolean)->Unit) {
 AlertDialog(onDismissRequest=onDismiss,title={Text("Exportar documento editable")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Word: texto y tablas detectadas en un archivo .docx. La distribución puede cambiar; las imágenes y los gráficos no se incluyen.")
  Text("PDF editable: reconstruye solo el texto, seleccionable y modificable, en páginas nuevas. No conserva imágenes ni anotaciones.")
  Text("Las páginas sin texto pasan por OCR local. Revisa nombres, cifras y tablas reconocidas antes de usar el resultado.")
  Button(onClick={onExport(true)},modifier=Modifier.fillMaxWidth()) {Text("Word editable (.docx)")}
  OutlinedButton(onClick={onExport(false)},modifier=Modifier.fillMaxWidth()) {Text("PDF editable (texto)")}
 }},confirmButton={TextButton(onClick=onDismiss) {Text("Cerrar")}})
}
