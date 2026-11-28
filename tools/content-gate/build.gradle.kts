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
 * notice. Set `-PdeedsRequired=true` (a store release build must pass it; it stays off while the reviewer's file does not exist, G9) to make absence a failure.
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

val districtsFile = rootProject.layout.projectDirectory.file("content/districts.json")

/**
 * T-BUILD-4's twin for the district list (ADR-8). Until the list is delivered (Q4) its absence passes with a notice;
 * `-PdistrictsRequired=true` makes absence a failure, and a release build for the store must set it.
 */
val checkDistricts = tasks.register<JavaExec>("checkDistricts") {
    group = "verification"
    description = "Runs the districts gate over content/districts.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.DistrictsMain")
    val required = providers.gradleProperty("districtsRequired").orElse("false")
    args(districtsFile.asFile.absolutePath, required.get())
    inputs.files(districtsFile).optional().withPropertyName("districts")
    inputs.property("required", required)
}

// The development fixture shipped in debug builds goes through the same gate, so the fake records cannot rot.
val debugDistrictsFile = rootProject.layout.projectDirectory.file("app/src/debug/assets/districts.json")

val checkDebugDistricts = tasks.register<JavaExec>("checkDebugDistricts") {
    group = "verification"
    description = "Runs the districts gate over the debug fixture app/src/debug/assets/districts.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.DistrictsMain")
    args(debugDistrictsFile.asFile.absolutePath, "true")
    inputs.files(debugDistrictsFile).withPropertyName("debugDistricts")
}

// The same for the deed fixture: fake records ("TEST DEED n") must keep passing the real gate, so the debug build's card
// screen is exercised with content that is valid by the same rules as the real file.
val debugDeedsFile = rootProject.layout.projectDirectory.file("app/src/debug/assets/deeds.json")

val checkDebugDeeds = tasks.register<JavaExec>("checkDebugDeeds") {
    group = "verification"
    description = "Runs the review gate over the debug fixture app/src/debug/assets/deeds.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.MainKt")
    args(debugDeedsFile.asFile.absolutePath, "true")
    inputs.files(debugDeedsFile).withPropertyName("debugDeeds")
}

val zakatRulesFile = rootProject.layout.projectDirectory.file("content/zakat-rules.json")

/**
 * T-BUILD-4 for the Zakat rules (INV-Z1). Until the reviewer signs rules the file does not exist and its absence passes
 * with a notice; once it exists it must be complete, signed, and not a test fixture. `-PzakatRulesRequired=true` makes
 * absence a failure, and a store build that ships the Zakat screen must set it.
 */
val checkZakatRules = tasks.register<JavaExec>("checkZakatRules") {
    group = "verification"
    description = "Runs the Zakat rules gate over content/zakat-rules.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.ZakatMain")
    val required = providers.gradleProperty("zakatRulesRequired").orElse("false")
    args(zakatRulesFile.asFile.absolutePath, required.get(), "false")
    inputs.files(zakatRulesFile).optional().withPropertyName("zakatRules")
    inputs.property("required", required)
}

// The development fixture (FAKE numbers, test_fixture true) goes through the same gate, so it cannot rot.
val debugZakatRulesFile = rootProject.layout.projectDirectory.file("app/src/debug/assets/zakat-rules.json")

val checkDebugZakatRules = tasks.register<JavaExec>("checkDebugZakatRules") {
    group = "verification"
    description = "Runs the Zakat rules gate over the debug fixture app/src/debug/assets/zakat-rules.json."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.ZakatMain")
    args(debugZakatRulesFile.asFile.absolutePath, "true", "true")
    inputs.files(debugZakatRulesFile).withPropertyName("debugZakatRules")
}

tasks.check { dependsOn(checkDeedContent, checkDistricts, checkDebugDistricts, checkDebugDeeds, checkZakatRules, checkDebugZakatRules) }
