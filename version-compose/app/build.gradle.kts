import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.pdffacil.pro"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "com.pdffacil.pro"; minSdk = 29; targetSdk = 35; versionCode = 3; versionName = "2.0-beta" }
 buildFeatures { compose = true }
 testOptions { unitTests.isReturnDefaultValues = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
// Host tests use the same PDFBox asset bytes that Android obtains via AssetManager.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
 doFirst {
  val aar = configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.first { it.moduleVersion.id.name == "pdfbox-android" }.file
  systemProperty("pdfbox.aar", aar.absolutePath)
 }
}

// Free fonts are pinned and verified at build time, never fetched by the app.
val fontChecksums = mapOf(
 "LiberationSans-Bold.ttf" to "3973aa5054fb467dd5627245d3dc82e37bf16fe075756156a570455871351582",
 "LiberationSans-BoldItalic.ttf" to "c80fa7f2ffa0e01d4d8dcd6a6d1e43eda665222d1b4db597dde1174c456006cf",
 "LiberationSans-Italic.ttf" to "830c5fa600505fb4c1a271b4271c53c44bae43f492b2a240d0e98a3a7a380121",
 "LiberationSans-Regular.ttf" to "4659bc0c58c5028dd488ec928d41d9265db43d9b669fc14ca8b0832daca7b144",
 "LiberationSerif-Bold.ttf" to "9e66c25e20868756bf12eb24a520a4552727c9cd108f577f7ae33e3c0110e39c",
 "LiberationSerif-BoldItalic.ttf" to "379010e87421a883f7bbfa7936d23dfe0257a54ce850819b74556e9f2c615a8a",
 "LiberationSerif-Italic.ttf" to "eaeaf7f2b12544ecde64290c1fa7ec403ff70fd58b313637b265400b20249242",
 "LiberationSerif-Regular.ttf" to "705903ae1382f0150f115d6f10a30f5b2f3d6ade649c474afe6366c39174fb65"
)
val prepareFonts by tasks.registering {
 inputs.property("fontChecksums", fontChecksums)
 outputs.files(fontChecksums.keys.map { layout.projectDirectory.file("src/main/assets/fonts/$it") })
 doLast {
  val folder = layout.projectDirectory.dir("src/main/assets/fonts").asFile
  folder.mkdirs()
  fun checksum(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
  fontChecksums.forEach { (name, expected) ->
   val fontFile = File(folder, name)
   if (!fontFile.exists() || checksum(fontFile) != expected) {
    val temporary = File(folder, "$name.download")
    val address = URI("https://raw.githubusercontent.com/shardulvs/pdfinspector-android/ced9758304bd1310b3cc0b7914794154aa3a5710/app/src/main/assets/fonts/$name").toURL()
    address.openConnection().apply { connectTimeout = 30000; readTimeout = 60000 }.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
    check(checksum(temporary) == expected) { "La fuente $name no supera SHA-256" }
    temporary.copyTo(fontFile, overwrite = true)
    temporary.delete()
   }
  }
 }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareFonts) }
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { dependsOn(prepareFonts) }
