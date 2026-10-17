// Mirador para Windows: proyecto aparte de la app de Android (se compila con ../gradlew -p escritorio).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // NewPipeExtractor se publica solo en JitPack
        maven("https://jitpack.io") { content { includeGroup("com.github.TeamNewPipe") } }
    }
}

rootProject.name = "MiradorEscritorio"
