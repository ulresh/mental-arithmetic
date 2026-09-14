import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing key; keystore.properties and the keystore itself are kept out of the sources.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    // A hyphen is not allowed in an Android package name, hence mental_arithmetic.
    namespace = "com.github.ulresh.mental_arithmetic"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.github.ulresh.mental_arithmetic"
        // Samsung Galaxy M31 shipped with Android 10 and was updated up to Android 12.
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
