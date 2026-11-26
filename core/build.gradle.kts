plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":engine:calctimes"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
}

kotlin {
    explicitApi()
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
