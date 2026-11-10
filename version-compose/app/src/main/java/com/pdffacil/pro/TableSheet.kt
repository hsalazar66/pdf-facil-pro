package com.pdffacil.pro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ManagedTableSheet(initial:ManagedTable?,initialCell:Pair<Int,Int> = 0 to 0,externalError:String="",onDismiss:()->Unit,onConfirm:(ManagedTable)->Unit) {
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 val original=remember {initial ?: ManagedTable()}
 var cells by remember {mutableStateOf(original.cells)}
 var row by remember {mutableIntStateOf(initialCell.first.coerceIn(0,cells.lastIndex))}
 var column by remember {mutableIntStateOf(initialCell.second.coerceIn(0,cells.first().lastIndex))}
 var numeric by remember {mutableStateOf(false)}
 var loading by remember {mutableStateOf(false)}
 var error by remember {mutableStateOf("")}
 fun update(cell:TableCell) {cells=cells.mapIndexed {r,values->values.mapIndexed {c,old->if(r==row && c==column) cell else old}}}
 fun shape(r:Int,c:Int) {cells=List(r) {y->List(c) {x->cells.getOrNull(y)?.getOrNull(x) ?: TableCell()}};row=row.coerceAtMost(r-1);column=column.coerceAtMost(c-1)}
 val photo=rememberLauncherForActivityResult(object:ActivityResultContracts.OpenDocument() {
  override fun createIntent(context:android.content.Context,input:Array<String>):android.content.Intent=super.createIntent(context,input).apply {
   putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3APictures"))
  }
 }) {uri->if(uri!=null) {
  loading=true;scope.launch {try {
   val cell=withContext(Dispatchers.IO) {
    val decoded=decodePastedImage(context,uri)
    try {
     val scale=minOf(1f,640f/maxOf(decoded.width,decoded.height));val w=(decoded.width*scale).toInt().coerceAtLeast(1);val h=(decoded.height*scale).toInt().coerceAtLeast(1)
     val bitmap=if(scale<1) Bitmap.createScaledBitmap(decoded,w,h,true) else decoded
     try {val output=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);cells[row][column].copy(picture=output.toByteArray(),pictureWidth=w,pictureHeight=h)} finally {if(bitmap!==decoded) bitmap.recycle()}
    } finally {decoded.recycle()}
   };update(cell);error=""
  } catch(e:Exception) {error=e.localizedMessage ?: "No se pudo cargar la imagen"} finally {loading=false}}
 }}
 ModalBottomSheet(onDismissRequest={if(!loading) onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text(if(initial==null) "Nueva tabla" else "Editar tabla",style=MaterialTheme.typography.titleLarge)
   Text("Toca una celda. Escribe texto o números, o añade una imagen. Las columnas y filas se ajustan al contenido.",fontSize=12.sp)
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
    TextButton(onClick={shape((cells.size-1).coerceAtLeast(1),cells.first().size)},enabled=!loading) {Text("− fila")}
    Text("${cells.size} × ${cells.first().size}")
    TextButton(onClick={shape((cells.size+1).coerceAtMost(20),cells.first().size)},enabled=!loading) {Text("+ fila")}
    TextButton(onClick={shape(cells.size,(cells.first().size+1).coerceAtMost(10))},enabled=!loading) {Text("+ col.")}
    TextButton(onClick={shape(cells.size,(cells.first().size-1).coerceAtLeast(1))},enabled=!loading) {Text("− col.")}
   }
   val gridWidth=(cells.first().size*116).dp
   Box(Modifier.fillMaxWidth().height(154.dp).horizontalScroll(rememberScrollState())) {
    LazyColumn(Modifier.width(gridWidth)) {items(cells.size) {r->Row {cells[r].forEachIndexed {c,cell->
     Surface(onClick={row=r;column=c},enabled=!loading,color=if(row==r && column==c) Color(0xFFE1F2EB) else Color.White,border=BorderStroke(1.dp,MenuGreen),modifier=Modifier.width(116.dp).height(74.dp).semantics {contentDescription="Celda ${r+1},${c+1}"}) {
      Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
       Text("${r+1},${c+1}",fontSize=10.sp,color=MenuMuted)
       cell.picture?.let {bytes->val bitmap=remember(bytes) {BitmapFactory.decodeByteArray(bytes,0,bytes.size)};bitmap?.let {Image(it.asImageBitmap(),"Imagen de celda ${r+1},${c+1}",Modifier.height(30.dp))}}
       Text(cell.text.ifEmpty {"Vacía"},fontSize=12.sp,maxLines=2)
      }
     }
    }}}}
   }
   Text("Celda ${row+1},${column+1}",fontSize=14.sp)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilterChip(!numeric,{numeric=false},label={Text("Texto")})
    FilterChip(numeric,{numeric=true},label={Text("Número")})
    TextButton(onClick={update(cells[row][column].copy(text=""))},enabled=!loading) {Text("Borrar contenido")}
   }
   OutlinedTextField(cells[row][column].text,{update(cells[row][column].copy(text=it))},label={Text("Contenido de la celda")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),maxLines=4,enabled=!loading)
   Row {
    OutlinedButton(onClick={photo.launch(arrayOf("image/*"))},enabled=!loading) {Text("Imagen en celda")}
    if(cells[row][column].picture!=null) TextButton(onClick={update(cells[row][column].copy(picture=null,pictureWidth=0,pictureHeight=0))},enabled=!loading) {Text("Quitar imagen")}
   }
   if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
   if(externalError.isNotEmpty()) Text(externalError,color=MaterialTheme.colorScheme.error)
   if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
   Button(onClick={onConfirm(original.copy(cells=cells))},enabled=!loading,modifier=Modifier.fillMaxWidth()) {Text(if(initial==null) "Colocar tabla" else "Guardar tabla")}
   TextButton(onClick=onDismiss,enabled=!loading,modifier=Modifier.fillMaxWidth()) {Text("Cancelar")}
  }
 }
}
@Composable internal fun ManagedTableToolbar(selected:Boolean,busy:Boolean,onNew:()->Unit,onEdit:()->Unit,onFit:()->Unit,onCopy:()->Unit,onDelete:()->Unit,onClose:()->Unit) {
 Row(Modifier.fillMaxWidth()) {
  Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
  TextButton(onClick=onNew,enabled=!busy) {Text("Nueva tabla")}
  TextButton(onClick=onEdit,enabled=selected && !busy) {Text("Editar celdas")}
  TextButton(onClick=onFit,enabled=selected && !busy) {Text("Autoajustar")}
  TextButton(onClick=onCopy,enabled=selected && !busy) {Text("Copiar datos")}
  TextButton(onClick=onDelete,enabled=selected && !busy) {Text("Eliminar tabla")}
  }
  IconButton(onClick=onClose,enabled=!busy,modifier=Modifier.semantics {contentDescription="Cerrar tablas"}) {Text("×",fontSize=24.sp)}
 }
}
