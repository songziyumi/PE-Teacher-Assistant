import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

fun readKeyProperty(name: String): String? {
    val direct = keystoreProperties.getProperty(name)
    if (!direct.isNullOrBlank()) return direct
    // PowerShell-generated UTF-8 BOM can be treated as part of the first key.
    return keystoreProperties.getProperty("\uFEFF$name")
}

val hasReleaseSigning = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
).all { !readKeyProperty(it).isNullOrBlank() }

android {
    namespace = "com.example.pe_teacher_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.pe_teacher_app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(readKeyProperty("storeFile"))
                storePassword = readKeyProperty("storePassword")
                keyAlias = readKeyProperty("keyAlias")
                keyPassword = readKeyProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Missing Android release signing config. " +
                    "Create android/key.properties from android/key.properties.example first.",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("verifyReleaseSigning")
}

flutter {
    source = "../.."
}
