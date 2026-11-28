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

/**
 * T-BUILD-4 for duas and hadith (INV-C1, INV-C6, ADR-22): one task per kind, the same core gate, the deed's provenance
 * check. Until the reviewer delivers `content/duas.json` or `content/hadith.json` its absence passes with a notice;
 * `-PduasRequired=true` and `-PhadithRequired=true` make absence a failure, and a store build that ships the Today
 * screen must set the one for each kind it ships. The real file is refused if it says `test_fixture: true`.
 */
fun registerKindGates(kind: String, fileName: String, requiredProperty: String): List<TaskProvider<JavaExec>> {
    val realFile = rootProject.layout.projectDirectory.file("content/$fileName")
    val real = tasks.register<JavaExec>("check${kind}Content") {
        group = "verification"
        description = "Runs the review gate over content/$fileName."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.senseix21.muminsday.tools.gate.KindMain")
        val required = providers.gradleProperty(requiredProperty).orElse("false")
        args(kind, realFile.asFile.absolutePath, required.get(), "false")
        inputs.files(realFile).optional().withPropertyName("content")
        inputs.property("required", required)
    }
    // The FAKE debug fixture goes through the same gate with test fixtures allowed, so it cannot rot.
    val debugFile = rootProject.layout.projectDirectory.file("app/src/debug/assets/$fileName")
    val debug = tasks.register<JavaExec>("checkDebug${kind}Content") {
        group = "verification"
        description = "Runs the review gate over the debug fixture app/src/debug/assets/$fileName."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.senseix21.muminsday.tools.gate.KindMain")
        args(kind, debugFile.asFile.absolutePath, "true", "true")
        inputs.files(debugFile).withPropertyName("debugContent")
    }
    return listOf(real, debug)
}

val kindGates = registerKindGates("Dua", "duas.json", "duasRequired") + registerKindGates("Hadith", "hadith.json", "hadithRequired")

tasks.check { dependsOn(checkDeedContent, checkDistricts, checkDebugDistricts, checkDebugDeeds, checkZakatRules, checkDebugZakatRules) }
tasks.check { dependsOn(kindGates) }

/**
 * T-BUILD-6 (INV-QT1): every file in `content/quran/` must have the SHA-256 recorded in `content/quran-SOURCES.md`,
 * nothing unrecorded may be there, a translation needs its written permission recorded, and the text, search text and
 * metadata must describe the same 114 surahs. The app's asset staging depends on this task, so no build that packages
 * the Quran can skip it.
 */
val quranSources = rootProject.layout.projectDirectory.file("content/quran-SOURCES.md")
val quranDirectory = rootProject.layout.projectDirectory.dir("content/quran")
val checkQuranText = tasks.register<JavaExec>("checkQuranText") {
    group = "verification"
    description = "T-BUILD-6: the bundled Quran files match their recorded SHA-256 and describe 114 surahs."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.QuranMain")
    args(quranSources.asFile.absolutePath, quranDirectory.asFile.absolutePath, rootProject.layout.projectDirectory.dir("content").asFile.absolutePath)
    inputs.file(quranSources).withPropertyName("sources")
    inputs.dir(quranDirectory).withPropertyName("quran")
}
tasks.check { dependsOn(checkQuranText) }

/**
 * ADR-27: Hisnul Muslim, unreviewed, kept only in the git-ignored `content/local/hisnul-muslim/`. Absent (CI, a fresh
 * clone) passes with a notice; present, every file must have the SHA-256 recorded in `content/hisnul-muslim-SOURCES.md`
 * and the counts there must hold. The debug build's staging depends on this task; no release build packages the files.
 */
val hisnulSources = rootProject.layout.projectDirectory.file("content/hisnul-muslim-SOURCES.md")
val hisnulDirectory = rootProject.layout.projectDirectory.dir("content/local/hisnul-muslim")
val checkHisnulMuslim = tasks.register<JavaExec>("checkHisnulMuslim") {
    group = "verification"
    description = "ADR-27: the local Hisnul Muslim files, when present, match their recorded SHA-256 and counts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.HisnulMain")
    args(hisnulSources.asFile.absolutePath, hisnulDirectory.asFile.absolutePath)
    inputs.file(hisnulSources).withPropertyName("sources")
    // A file tree, not inputs.dir: the folder is absent on every machine but the one that fetched it.
    inputs.files(fileTree(hisnulDirectory)).withPropertyName("hisnulMuslim")
}
tasks.check { dependsOn(checkHisnulMuslim) }

/**
 * ADR-28: eight hadith books from fawazahmed0's hadith-api, unreviewed, kept only in the git-ignored
 * `content/local/hadith/`. Absent (CI, a fresh clone) passes with a notice; present, every file must have the SHA-256
 * recorded in `content/hadith-books-SOURCES.md`, each book's two editions must pair, and the counts there must hold. The
 * debug build's staging depends on this task; no release build packages the files (T-BUILD-7).
 */
val hadithBooksSources = rootProject.layout.projectDirectory.file("content/hadith-books-SOURCES.md")
val hadithBooksDirectory = rootProject.layout.projectDirectory.dir("content/local/hadith")
val checkHadithBooks = tasks.register<JavaExec>("checkHadithBooks") {
    group = "verification"
    description = "ADR-28: the local hadith books, when present, match their recorded SHA-256 and counts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.senseix21.muminsday.tools.gate.HadithBooksMain")
    args(hadithBooksSources.asFile.absolutePath, hadithBooksDirectory.asFile.absolutePath)
    inputs.file(hadithBooksSources).withPropertyName("sources")
    // A file tree, not inputs.dir: the folder is absent on every machine but the one that fetched it.
    inputs.files(fileTree(hadithBooksDirectory)).withPropertyName("hadithBooks")
}
tasks.check { dependsOn(checkHadithBooks) }
