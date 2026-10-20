// Configuración raíz de HemoCheck.
// - :core es Kotlin puro (lógica de dominio, sin Android) y se puede compilar y probar
//   también por separado con `gradle -p core test` (ver core/settings.gradle.kts).
// - :app es la aplicación Android.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hemocheck"
include(":core")
include(":app")
