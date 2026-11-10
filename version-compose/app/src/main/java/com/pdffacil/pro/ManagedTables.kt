package com.pdffacil.pro

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pdffacil.pro.engine.ContentStreamEngine
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.*
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.*

internal data class TableCell(val text:String="",val picture:ByteArray?=null,val pictureWidth:Int=0,val pictureHeight:Int=0)
internal data class ManagedTable(
 val id:String=UUID.randomUUID().toString(),val cells:List<List<TableCell>>=List(3) {List(3) {TableCell()}},
 val x:Float=0.09f,val y:Float=0.15f,val width:Float=0.82f,val height:Float=0f,
 val columns:List<Float> = emptyList(),val rows:List<Float> = emptyList(),val rotation:Int=0,val legacy:Boolean=false,
 val fontSize:Float=12f
) {
 val rect get()=Rect(x,y,x+width,y+height)
 fun cellAt(point:Offset):Pair<Int,Int> {
  var cx=x;var cy=y
  val c=columns.indexOfFirst {part->cx+=part;point.x<=cx}.let {if(it<0) (cells.firstOrNull()?.lastIndex ?: 0) else it}
  val r=rows.indexOfFirst {part->cy+=part;point.y<=cy}.let {if(it<0) cells.lastIndex else it}
  return r.coerceAtLeast(0) to c.coerceAtLeast(0)
 }
}
private val TABLE_KEY=COSName.getPDFName("PFPTableV1")
private fun visibleSize(page:PDPage):Pair<Float,Float> = if(((page.rotation%180)+180)%180==90) page.cropBox.height to page.cropBox.width else page.cropBox.width to page.cropBox.height
private fun streams(page:PDPage):List<PDStream> = page.contentStreams.asSequence().toList()
private fun number(d:COSDictionary,key:String,default:Float)=((d.getDictionaryObject(COSName.getPDFName(key)) as? COSNumber)?.floatValue() ?: default)
private fun floats(values:List<Float>)=COSArray().apply {values.forEach {add(COSFloat(it))}}
private fun floatList(d:COSDictionary,key:String)=((d.getDictionaryObject(COSName.getPDFName(key)) as? COSArray)?.toList()?.mapNotNull {(it as? COSNumber)?.floatValue()} ?: emptyList())
private fun bytes(stream:COSStream):ByteArray = stream.createInputStream().use {input->
 val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
 while(true) {val n=input.read(buffer);if(n<0) break;total+=n;require(total<=3_000_000) {"Imagen de celda demasiado grande"};out.write(buffer,0,n)};out.toByteArray()
}
private fun decode(d:COSDictionary):ManagedTable {
 val data=d.getDictionaryObject(COSName.getPDFName("Cells")) as? COSArray ?: error("Faltan las celdas")
 require(data.size() in 1..20)
 val cells=data.map {row->val a=row as COSArray;require(a.size() in 1..10);a.map {value->
  val c=value as COSDictionary;val picture=c.getDictionaryObject(COSName.getPDFName("Picture")) as? COSStream
  TableCell(c.getString("Text") ?: "",picture?.let {bytes(it)},c.getInt("PictureWidth"),c.getInt("PictureHeight"))
 }}
 return ManagedTable(d.getString("Id") ?: error("Falta identificador"),cells,number(d,"X",0.09f),number(d,"Y",0.15f),number(d,"Width",0.82f),number(d,"Height",0f),floatList(d,"Columns"),floatList(d,"Rows"),d.getInt("Rotation"),false,number(d,"FontSize",12f))
}
private fun encode(doc:PDDocument,table:ManagedTable,resource:COSName):COSDictionary=COSDictionary().apply {
 setString("Id",table.id);setString("Resource",resource.name)
 mapOf("X" to table.x,"Y" to table.y,"Width" to table.width,"Height" to table.height,"FontSize" to table.fontSize).forEach {(key,value)->setFloat(key,value)}
 setInt("Rotation",table.rotation);setItem(COSName.getPDFName("Columns"),floats(table.columns));setItem(COSName.getPDFName("Rows"),floats(table.rows))
 setItem(COSName.getPDFName("Cells"),COSArray().apply {table.cells.forEach {row->add(COSArray().apply {row.forEach {cell->add(COSDictionary().apply {
  setString("Text",cell.text);setInt("PictureWidth",cell.pictureWidth);setInt("PictureHeight",cell.pictureHeight)
  cell.picture?.let {image->val stream=PDStream(doc);stream.createOutputStream(COSName.FLATE_DECODE).use {it.write(image)};setItem(COSName.getPDFName("Picture"),stream)}
 })}})}})
}
private fun pdfScreen(page:PDPage,p:Offset):Offset {
 val b=page.cropBox;val u=(p.x-b.lowerLeftX)/b.width;val v=(p.y-b.lowerLeftY)/b.height
 return when(((page.rotation%360)+360)%360) {90->Offset(v,u);180->Offset(1-u,v);270->Offset(1-v,1-u);else->Offset(u,1-v)}
}
/** Only recognizes the dedicated grid streams emitted by version 2.5, never arbitrary page text. */
private fun legacyTable(page:PDPage,stream:PDStream):ManagedTable?=runCatching {
 val parser=PDFStreamParser(stream);parser.parse();val tokens=parser.tokens
 val ops=tokens.filterIsInstance<Operator>().map {it.name}
 val allowed=setOf("q","Q","cm","RG","rg","w","re","S","BT","ET","Tf","Td","Tj")
 if(ops.any {it !in allowed} || ops.count {it=="re"}<4 || ops.count {it=="cm"}!=1 || !ops.contains("Tj")) return null
 val rects=tokens.indices.filter {(tokens[it] as? Operator)?.name=="re"}.map {at->tokens.subList(at-4,at).map {(it as COSNumber).floatValue()}}
 if(rects.any {it[2]<=0 || it[3]<=0}) return null
 val xs=rects.map {it[0]}.distinct().sorted();val ys=rects.map {it[1]+it[3]}.distinct().sortedDescending()
 if(xs.size !in 1..10 || ys.size !in 1..20 || xs.size*ys.size!=rects.size) return null
 val cm=tokens.indexOfFirst {(it as? Operator)?.name=="cm"};val m=tokens.subList(cm-6,cm).map {(it as COSNumber).floatValue()}
 fun screen(x:Float,y:Float)=pdfScreen(page,Offset(m[0]*x+m[2]*y+m[4],m[1]*x+m[3]*y+m[5]))
 val minX=rects.minOf {it[0]};val maxX=rects.maxOf {it[0]+it[2]};val top=rects.maxOf {it[1]+it[3]};val bottom=rects.minOf {it[1]}
 // The old app draws upright relative to the page. Rotated legacy streams remain selectable as a group.
 val corners=listOf(screen(minX,top),screen(maxX,top),screen(minX,bottom),screen(maxX,bottom))
 val bounds=Rect(corners.minOf {it.x},corners.minOf {it.y},corners.maxOf {it.x},corners.maxOf {it.y})
 val temp=PDPage(page.mediaBox);temp.cropBox=page.cropBox;temp.rotation=page.rotation;temp.resources=page.resources;temp.setContents(stream)
 val nodes=textRuns(ContentStreamEngine.parse(temp).root).filter {!it.text.isNullOrBlank() && it.bounds!=null}
 val cells=ys.map {y->xs.map {x->
  val grid=rects.first {abs(it[0]-x)<0.01f && abs(it[1]+it[3]-y)<0.01f}
  val a=screen(x,y);val b=screen(x+grid[2],y-grid[3]);val cell=Rect(min(a.x,b.x)-0.001f,min(a.y,b.y)-0.001f,max(a.x,b.x)+0.001f,max(a.y,b.y)+0.001f)
  TableCell(nodes.filter {n->val v=n.bounds!!;cell.contains(pdfScreen(page,Offset((v.minX+v.maxX)/2,(v.minY+v.maxY)/2)))}.sortedByDescending {it.bounds!!.maxY}.joinToString("\n") {it.text!!})
 }}
 val digest=MessageDigest.getInstance("SHA-256").digest(stream.createInputStream().use {it.readBytes()}).take(12).joinToString("") {"%02x".format(it)}
 ManagedTable("legacy-$digest",cells,bounds.left,bounds.top,bounds.width,bounds.height,List(xs.size) {bounds.width/xs.size},ys.map {y->rects.first {abs(it[1]+it[3]-y)<0.01f}[3]/(top-bottom)*bounds.height},page.rotation,true,10f)
}.getOrNull()
internal fun readManagedTables(page:PDPage):List<ManagedTable> = streams(page).mapNotNull {stream->
 val d=stream.cosObject.getDictionaryObject(TABLE_KEY) as? COSDictionary
 if(d!=null) runCatching {decode(d)}.getOrNull() else legacyTable(page,stream)
}.map {table->
 val difference=((page.rotation-table.rotation)%360+360)%360
 if(difference==0 || table.legacy) table else {
  val b=table.rect
  val r=when(difference) {90->Rect(1-b.bottom,b.left,1-b.top,b.right);180->Rect(1-b.right,1-b.bottom,1-b.left,1-b.top);else->Rect(b.top,1-b.right,b.bottom,1-b.left)}
  table.copy(x=r.left,y=r.top,width=r.width,height=r.height,columns=List(table.cells.first().size) {r.width/table.cells.first().size},rows=List(table.cells.size) {r.height/table.cells.size},rotation=page.rotation)
 }
}
private data class TableLayout(val model:ManagedTable,val widths:List<Float>,val heights:List<Float>,val lines:List<List<List<String>>>,val pictureHeights:List<List<Float>>)
private fun tableLayout(page:PDPage,input:ManagedTable,font:PDFont):TableLayout {
 require(input.cells.size in 1..20 && input.cells.all {it.size in 1..10}) {"Usa de 1 a 20 filas y de 1 a 10 columnas"}
 val (pageWidth,pageHeight)=visibleSize(page);val count=input.cells.maxOf {it.size}
 val cells=input.cells.map {row->List(count) {row.getOrElse(it) {TableCell()}}}
 require(cells.flatten().all {it.picture==null || (it.pictureWidth>0 && it.pictureHeight>0)}) {"La imagen no tiene dimensiones válidas"}
 val width=(input.width.coerceIn(0.2f,0.94f)*pageWidth).coerceAtLeast(min(100f,pageWidth*0.94f))
 val weights=(0 until count).map {c->cells.maxOf {row->val cell=row[c];max(if(cell.picture!=null) 130f else 48f,cell.text.lines().maxOfOrNull {font.getStringWidth(safeFontText(font,it))/1000*12+12}?.coerceAtMost(220f) ?: 48f)}}
 val widths=weights.map {it/weights.sum()*width}
 val maxHeight=pageHeight*0.94f
 var fontSize=input.fontSize.coerceIn(8f,16f)
 while(true) {
  val lines=cells.map {row->row.mapIndexed {c,cell->cell.text.replace("\r\n","\n").split('\n').flatMap {wrappedLines(font,it,fontSize,(widths[c]-10).coerceAtLeast(8f))}}}
  val images=cells.map {row->row.mapIndexed {c,cell->if(cell.picture!=null && cell.pictureWidth>0 && cell.pictureHeight>0) min((widths[c]-12).coerceAtLeast(8f)*cell.pictureHeight/cell.pictureWidth,(maxHeight/cells.size*0.55f).coerceIn(18f,100f)) else 0f}}
  val heights=cells.indices.map {r->max(fontSize*2.5f,cells[r].indices.maxOf {c->lines[r][c].size*fontSize*1.25f+12+images[r][c]})}
  if(heights.sum()<=maxHeight) {
   val x=input.x.coerceIn(0.03f,(0.97f-width/pageWidth).coerceAtLeast(0.03f))
   val y=input.y.coerceIn(0.03f,(0.97f-heights.sum()/pageHeight).coerceAtLeast(0.03f))
   return TableLayout(input.copy(cells=cells,x=x,y=y,width=width/pageWidth,height=heights.sum()/pageHeight,columns=widths.map {it/pageWidth},rows=heights.map {it/pageHeight},rotation=page.rotation,legacy=false,fontSize=fontSize),widths,heights,lines,images)
  }
  require(fontSize>8.1f) {"La tabla tiene demasiado contenido para una página. Divide sus filas en dos tablas."};fontSize=(fontSize-1).coerceAtLeast(8f)
 }
}
internal fun removeManagedTable(page:PDPage,id:String) {
 val keep=streams(page).filter {s->val d=s.cosObject.getDictionaryObject(TABLE_KEY) as? COSDictionary;(d?.getString("Id") ?: legacyTable(page,s)?.id)!=id}
 page.setContents(keep)
}
internal fun writeManagedTable(doc:PDDocument,pageIndex:Int,input:ManagedTable,font:PDFont,imageFactory:(ByteArray)->PDImageXObject):ManagedTable {
 val page=doc.getPage(pageIndex);val layout=tableLayout(page,input,font);val table=layout.model
 val width=layout.widths.sum();val height=layout.heights.sum()
 val temp=PDPage(PDRectangle(width,height))
 PDPageContentStream(doc,temp).use {s->
  s.setLineWidth(0.65f);s.setStrokingColor(0.16f,0.35f,0.3f)
  var top=height
  table.cells.forEachIndexed {r,row->var left=0f
   row.forEachIndexed {c,cell->
    val cw=layout.widths[c];val rh=layout.heights[r]
    s.setNonStrokingColor(1f,1f,1f);s.addRect(left,top-rh,cw,rh);s.fill();s.addRect(left,top-rh,cw,rh);s.stroke()
    layout.lines[r][c].forEachIndexed {line,text->s.beginText();s.setFont(font,table.fontSize);s.setNonStrokingColor(0.07f,0.17f,0.14f);s.newLineAtOffset(left+5,top-5-table.fontSize-line*table.fontSize*1.25f);s.showText(text);s.endText()}
    cell.picture?.let {bytes->val image=imageFactory(bytes);val ih=layout.pictureHeights[r][c];val iw=ih*cell.pictureWidth/cell.pictureHeight
     s.drawImage(image,left+(cw-iw)/2,top-7-layout.lines[r][c].size*table.fontSize*1.25f-ih,iw,ih)}
    left+=cw
   };top-=layout.heights[r]
  }
 }
 val form=PDFormXObject(doc);form.bBox=PDRectangle(width,height);form.resources=temp.resources
 form.cosObject.createOutputStream(COSName.FLATE_DECODE).use {out->temp.contents.use {it.copyTo(out)}}
 // Isolate inherited/shared resources before adding a new table instance.
 val resources=COSDictionary(page.resources?.cosObject ?: COSDictionary())
 resources.getCOSDictionary(COSName.XOBJECT)?.let {resources.setItem(COSName.XOBJECT,COSDictionary(it))}
 page.resources=PDResources(resources)
 val old=streams(page);val at=old.indexOfFirst {s->val d=s.cosObject.getDictionaryObject(TABLE_KEY) as? COSDictionary;(d?.getString("Id") ?: legacyTable(page,s)?.id)==table.id}
 if(at>=0) {val d=old[at].cosObject.getDictionaryObject(TABLE_KEY) as? COSDictionary;d?.getString("Resource")?.let {name->page.resources.cosObject.getCOSDictionary(COSName.XOBJECT)?.removeItem(COSName.getPDFName(name))}}
 val name=page.resources.add(form)
 val anchor=pagePoint(page,Offset(table.x,table.y));val angle=Math.toRadians(page.rotation.toDouble());val co=cos(angle).toFloat();val si=sin(angle).toFloat()
 val content=PDStream(doc)
 val tokens=listOf(Operator.getOperator("q"),COSFloat(co),COSFloat(si),COSFloat(-si),COSFloat(co),COSFloat(anchor.x+si*height),COSFloat(anchor.y-co*height),Operator.getOperator("cm"),name,Operator.getOperator("Do"),Operator.getOperator("Q"))
 content.createOutputStream(COSName.FLATE_DECODE).use {ContentStreamWriter(it).writeTokens(tokens)}
 content.cosObject.setItem(TABLE_KEY,encode(doc,table,name))
 val updated=old.toMutableList();if(at>=0) updated[at]=content else updated.add(content);page.setContents(updated)
 return table
}
internal fun upsertTable(context:Context,doc:PDDocument,page:Int,table:ManagedTable):ManagedTable = writeManagedTable(doc,page,table,fontFor(context,doc,"Sans",false,false)) {PDImageXObject.createFromByteArray(doc,it,"imagen de celda")}
