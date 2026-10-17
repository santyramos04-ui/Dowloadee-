import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

// Nombre y versión: los mismos de la app de Android (version.properties en la raíz).
val props = Properties().apply { file("../version.properties").inputStream().use { load(it) } }
val nombreApp = (props["nombre"] as String?)?.trim().orEmpty().ifEmpty { "Mirador" }
// Windows solo acepta números (1.0.33); la versión de prueba «1.0.33-prueba» se recorta.
val versionApp = (findProperty("versionName") as String? ?: "1.0.0").substringBefore("-")

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    testImplementation(kotlin("test"))
}

// Las mismas fuentes de la app (Bricolage Grotesque y Atkinson Hyperlegible) sin duplicarlas.
tasks.processResources {
    from("../app/src/main/res/font") { into("fuentes") }
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "com.santyramos.mirador.escritorio.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dmirador.nombre=$nombreApp", "-Dmirador.version=$versionApp")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = nombreApp
            packageVersion = versionApp
            description = "Ver y descargar videos"
            vendor = "santyramos04-ui"
            copyright = "Licencia GPLv3"
            licenseFile.set(project.file("../LICENSE"))
            // yt-dlp, ffmpeg, mpv y QuickJS (los descarga el CI) van junto a la app.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("recursos"))
            includeAllModules = true
            windows {
                iconFile.set(project.file("instalador/mirador.ico"))
                menuGroup = nombreApp
                perUserInstall = true
                shortcut = true
                upgradeUuid = "6f1d3c2a-8b54-4e0d-9a7e-2c5b1f0e7a91"
            }
        }
    }
}
