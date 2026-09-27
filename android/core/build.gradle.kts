import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo Kotlin puro: domínio, algoritmo de repetição espaçada, protocolo e servidor de
// sincronização, importadores. Não depende do Android, então os testes rodam direto na JVM.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // As interfaces de repositório expõem Flow; os DTOs do protocolo são @Serializable.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    // Servidor HTTP local que o side service do Zepp App chama.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

// Arquivos compartilhados com os testes do relógio: vetores do algoritmo e exemplos do protocolo.
val srsVectors: File = rootProject.file("../shared/srs-test-vectors.json")
val protocolExamples: File = rootProject.file("../shared/protocol-examples.json")

tasks.named<Test>("test") {
    inputs.file(srsVectors)
    inputs.file(protocolExamples)
    systemProperty("srs.vectors", srsVectors.absolutePath)
    systemProperty("protocol.examples", protocolExamples.absolutePath)
}
