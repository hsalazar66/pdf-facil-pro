package com.tom_roush.pdfbox.android

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile

/** Host-only asset bridge. Parsing, editing, font encoding and saving use the real PDFBox. */
class PDFBoxResourceLoader {
 companion object {
  @JvmStatic fun init(unused: Context?) { }
  @JvmStatic fun isReady() = true
  @JvmStatic fun getStream(path:String):InputStream = ZipFile(System.getProperty("pdfbox.aar")).use { zip ->
   val entry=zip.getEntry("assets/$path") ?: throw FileNotFoundException(path)
   ByteArrayInputStream(zip.getInputStream(entry).use { it.readBytes() })
  }
 }
}
