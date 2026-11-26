plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
}

tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
    from("LICENSE.GPL-3.0") { into("META-INF") }
    from("NOTICE.txt") { into("META-INF") }
}
