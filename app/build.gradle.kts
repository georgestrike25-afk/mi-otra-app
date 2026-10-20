plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hemocheck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hemocheck"
        minSdk = 26
        targetSdk = 35
        // En CI cada compilación tiene un número mayor, así Android acepta la actualización
        val numeroCompilacion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = numeroCompilacion
        versionName = "0.1.$numeroCompilacion"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Clave fija para que las actualizaciones se instalen encima de la versión anterior.
        // Si existen las variables de entorno (secretos de GitHub), se usa la clave privada del servicio.
        create("hemocheck") {
            val almacenPropio = System.getenv("HEMOCHECK_KEYSTORE_PATH")
            if (almacenPropio != null) {
                storeFile = file(almacenPropio)
                storePassword = System.getenv("HEMOCHECK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HEMOCHECK_KEY_ALIAS")
                keyPassword = System.getenv("HEMOCHECK_KEY_PASSWORD")
            } else {
                storeFile = file("hemocheck-pruebas.keystore")
                storePassword = "hemocheck-pruebas"
                keyAlias = "hemocheck"
                keyPassword = "hemocheck-pruebas"
            }
        }
    }

    buildTypes {
        debug {
            // Se puede instalar junto a la versión de uso (no depurable)
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-depuracion"
            signingConfig = signingConfigs.getByName("hemocheck")
        }
        release {
            // Solo procesadores ARM de 64 bits (Samsung S24+ y casi todos los teléfonos actuales):
            // reduce el APK a la mitad. La versión de depuración conserva todas las arquitecturas
            // para poder probarla en el emulador.
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("hemocheck")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Los avisos de lint se revisan en el informe; no detienen la compilación
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)

    // Cámara integrada
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // OCR y códigos de barras en el dispositivo (modelos incluidos en el APK, sin descarga)
    implementation(libs.mlkit.texto)
    implementation(libs.mlkit.barras)

    // Base de datos local cifrada
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
