plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core"))
    // The JSON parser is the one bundled with Gradle itself: nothing is downloaded and nothing here ships in the app
    // (ADR-10). The app parses the same file with the platform's parser and runs the same core gate on the result.
    implementation(localGroovy())
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

val deedsFile = rootProject.layout.projectDirectory.file("content/deeds.json")

/**
 * T-BUILD-4 (INV-C1, INV-C3): the build fails if `content/deeds.json` holds anything the review gate rejects.
 * Until the reviewer delivers that file there is nothing to ship and nothing to check, so its absence passes with a
 * notice. Set `-PdeedsRequired=true` (the app module will, once it packages the file) to make absence a failure.
 */
val checkDeedContent = tasks.register<JavaExec>("checkDeedContent") {
    group = "verification"
    description = "Runs the review gate over content/deeds.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.MainKt")
    val required = providers.gradleProperty("deedsRequired").orElse("false")
    args(deedsFile.asFile.absolutePath, required.get())
    inputs.files(deedsFile).optional().withPropertyName("deeds")
    inputs.property("required", required)
}

tasks.check { dependsOn(checkDeedContent) }
