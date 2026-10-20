// Núcleo de HemoCheck: lógica de dominio en Kotlin puro (sin dependencias de Android),
// para poder probarla en la JVM: OCR estructurado, asociación de filas, búsqueda exacta,
// estados de corroboración, retención, PIN y cifrado AES-GCM.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // Las pruebas de privacidad revisan el código de la app (sin ejecutarlo)
    systemProperty("hemocheck.raiz", rootDir.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
