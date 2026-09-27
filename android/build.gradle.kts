// Declarar os plugins Kotlin aqui coloca o KGP da versão do catálogo no classpath,
// substituindo a versão que o AGP 9 traz embutida.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
