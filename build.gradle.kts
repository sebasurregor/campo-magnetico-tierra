// AGP 9 trae Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
// El plugin del compilador de Compose fija la versión de Kotlin (libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
