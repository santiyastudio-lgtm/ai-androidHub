import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.10.3"
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.animation)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.santiya.localaihub.desktop.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.santiya.localaihub.desktop.MainKt"

        nativeDistributions {
            packageName = "SantiyaLocalAiHub"
            packageVersion = "1.0.1"
            vendor = "Santiya"
            description = "Android-first Windows client for SantiyaLocalAiHub"
            includeAllModules = true
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)

            windows {
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                console = false
                iconFile.set(project.file("src/main/resources/images/app_icon.ico"))
            }
        }
    }
}
