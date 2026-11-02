package com.pdffacil.pro

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android owns PDF associations. A local preference records only that we asked. */
@Composable
internal fun DefaultViewerPrompt(offerAutomatically: Boolean, enabled: Boolean) {
 val context = LocalContext.current
 val prefs = remember { context.getSharedPreferences("preferencias", Context.MODE_PRIVATE) }
 var show by remember { mutableStateOf(offerAutomatically && !prefs.getBoolean("visor_ofrecido", false)) }
 var launching by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf<String?>(null) }
 val scope = rememberCoroutineScope()
 fun dismiss() {
  prefs.edit().putBoolean("visor_ofrecido", true).apply()
  show = false
 }
 TextButton(onClick = { show = true }, enabled = enabled && !launching) {
  Text(if (launching) "Preparando ejemplo…" else "Visor predeterminado")
 }
 if (show) AlertDialog(
  onDismissRequest = { dismiss() },
  title = { Text("¿Usar PDF Fácil Pro como visor predeterminado?") },
  text = { Text("Se abrirá un PDF de ejemplo. En la pantalla de Android elige PDF Fácil Pro y pulsa «Siempre», si aparece.\n\nSi se abre directamente otro visor, ve a Ajustes → Aplicaciones → ese visor → Abrir de forma predeterminada y borra sus valores predeterminados. Después vuelve a intentarlo. Los nombres pueden variar según tu celular.") },
  confirmButton = {
   TextButton(onClick = {
    dismiss()
    scope.launch {
     launching = true
     try {
      val uri = withContext(Dispatchers.IO) {
       val folder = File(context.cacheDir, "visor_demo").apply { mkdirs() }
       val sample = File(folder, "Bienvenido-a-PDF-Facil-Pro.pdf")
       PDDocument().use { doc ->
        val page = PDPage(); doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
         stream.beginText(); stream.setFont(PDType1Font.HELVETICA_BOLD, 22f)
         stream.newLineAtOffset(50f, 720f); stream.showText("PDF Fácil Pro")
         stream.setFont(PDType1Font.HELVETICA, 12f)
         stream.newLineAtOffset(0f, -32f); stream.showText("Este es un PDF de ejemplo para elegir tu visor.")
         stream.newLineAtOffset(0f, -22f); stream.showText("La selección del visor predeterminado se confirma en Android.")
         stream.endText()
        }
        doc.save(sample)
       }
       FileProvider.getUriForFile(context, "${context.packageName}.files", sample)
      }
      // Do not use createChooser: its UI cannot save an 'Always' preference.
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
       setDataAndType(uri, "application/pdf")
       clipData = ClipData.newRawUri("PDF de ejemplo", uri)
       addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
     } catch (_: Exception) {
      error = "No se pudo abrir el ejemplo. También puedes abrir un PDF desde Archivos y elegir PDF Fácil Pro → Siempre en el selector de Android."
     } finally { launching = false }
    }
   }) { Text("Sí, elegir visor") }
  },
  dismissButton = { TextButton(onClick = { dismiss() }) { Text("Ahora no") } },
 )
 if (error != null) AlertDialog(
  onDismissRequest = { error = null },
  title = { Text("Elegir visor") }, text = { Text(error!!) },
  confirmButton = { TextButton(onClick = { error = null }) { Text("Entendido") } },
 )
}
