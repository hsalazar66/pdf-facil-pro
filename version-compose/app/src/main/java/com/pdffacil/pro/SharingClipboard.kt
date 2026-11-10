package com.pdffacil.pro

import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal enum class PdfDestination { WHATSAPP, DRIVE, EMAIL, OTHER, DEVICE }
internal sealed class PasteContent {
 data class Text(val value:String):PasteContent()
 data class Image(val uri:Uri):PasteContent()
}
internal fun readPasteContent(context:Context):PasteContent? {
 val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
 val clip=clipboard.primaryClip ?: return null
 if(clip.itemCount==0) return null
 val item=clip.getItemAt(0)
 val uri=item.uri
 if(uri!=null && (clip.description.hasMimeType("image/*") || runCatching {context.contentResolver.getType(uri)?.startsWith("image/")==true}.getOrDefault(false))) return PasteContent.Image(uri)
 if(clip.description.hasMimeType("text/*") || item.text!=null) return item.coerceToText(context)?.toString()?.takeIf {it.isNotEmpty()}?.let {PasteContent.Text(it)}
 return null
}
internal fun putTextClipboard(context:Context,value:String) {
 (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Texto de PDF",value))
}
internal fun copyBitmapFile(context:Context,bitmap:Bitmap):Uri {
 val dir=File(context.cacheDir,"portapapeles").apply {mkdirs()}
 val file=File.createTempFile("imagen-",".png",dir)
 file.outputStream().use {require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) {"No se pudo copiar la imagen"}}
 return FileProvider.getUriForFile(context,"${context.packageName}.files",file)
}
internal fun putImageClipboard(context:Context,uri:Uri) {
 (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newUri(context.contentResolver,"Imagen de PDF",uri))
}
internal fun decodePastedImage(context:Context,uri:Uri):Bitmap {
 val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
 context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,options)}
 require(options.outWidth>0 && options.outHeight>0) {"Imagen del portapapeles incompatible"}
 var sample=1;while(options.outWidth/sample>2000 || options.outHeight/sample>2000) sample*=2
 return context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("No se puede leer la imagen")
}
internal fun preparePdfShare(context:Context,source:File,title:String):Uri {
 val dir=File(context.cacheDir,"compartir/${UUID.randomUUID()}").apply {mkdirs()}
 val safe=title.removeSuffix(".pdf").replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(100).ifBlank {"Documento"}+"-editado.pdf"
 val target=File(dir,safe);source.copyTo(target,true)
 return FileProvider.getUriForFile(context,"${context.packageName}.files",target)
}
internal fun pdfSendIntent(uri:Uri)=Intent(Intent.ACTION_SEND).apply {
 type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri)
 clipData=ClipData.newRawUri("Documento PDF",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
/** Opens a destination; the user still chooses the recipient/account and confirms sending. */
internal fun launchPdfShare(context:Context,uri:Uri,destination:PdfDestination):Boolean {
 val send=pdfSendIntent(uri)
 when(destination) {
  PdfDestination.WHATSAPP -> {
   for(pkg in listOf("com.whatsapp","com.whatsapp.w4b")) {
    try {context.startActivity(Intent(send).setPackage(pkg));return true} catch (_:ActivityNotFoundException) { }
   }
  }
  PdfDestination.DRIVE -> try {context.startActivity(Intent(send).setPackage("com.google.android.apps.docs"));return true} catch (_:ActivityNotFoundException) { }
  PdfDestination.EMAIL -> {
   val packages=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")),PackageManager.MATCH_DEFAULT_ONLY).map {it.activityInfo.packageName}.distinct()
   val candidates=packages.map {Intent(send).setPackage(it)}.filter {context.packageManager.resolveActivity(it,PackageManager.MATCH_DEFAULT_ONLY)!=null}
   if(candidates.isNotEmpty()) {
    val chooser=Intent.createChooser(candidates.first(),"Enviar PDF por correo")
    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,candidates.drop(1).toTypedArray<android.os.Parcelable>())
    context.startActivity(chooser);return true
   }
  }
  else -> {context.startActivity(Intent.createChooser(send,"Compartir PDF"));return true}
 }
 context.startActivity(Intent.createChooser(send,"Elige una aplicación disponible para PDF"))
 return false
}
