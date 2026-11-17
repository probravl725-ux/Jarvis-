import java.util.Base64

plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

// Иконка хранится текстом (base64) в файле icon.b64, при сборке превращается в картинку
val iconText = file("icon.b64")
if (iconText.exists()) {
    val iconDir = file("res/mipmap-xxxhdpi")
    iconDir.mkdirs()
    file("res/mipmap-xxxhdpi/ic_launcher.webp")
        .writeBytes(Base64.getMimeDecoder().decode(iconText.readText().trim()))
}

android {
    namespace = "com.sergey.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sergey.jarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            res.srcDirs("res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
