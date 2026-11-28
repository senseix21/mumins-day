import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

/** `name=sha256,...` from the `sha256 <hex> <file>` lines of `content/quran-SOURCES.md`, the record T-BUILD-6 reads. */
fun recordedQuranHashes(): String {
    val sources = rootProject.file("content/quran-SOURCES.md")
    if (!sources.isFile) return ""
    return Regex("^sha256 ([0-9a-f]{64}) (\\S+)$", RegexOption.MULTILINE).findAll(sources.readText())
        .joinToString(",") { "${it.groupValues[2]}=${it.groupValues[1]}" }
}

android {
    namespace = "com.senseix21.muminsday"
    compileSdk = 37

    defaultConfig {
        // Cannot change after the first Play release.
        applicationId = "com.senseix21.muminsday"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // INV-QT1 on the phone: the hashes T-BUILD-6 checks, so the app can check its own assets the same way (ADR-23).
        buildConfigField("String", "QURAN_HASHES", "\"${recordedQuranHashes()}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        // The user picks Bengali or English inside the app (F1), so both must always be installed. Play's per-language
        // split would drop the one the device is not set to: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        // VERSION_NAME for "Send a report". Generates one class; adds no library.
        buildConfig = true
    }

    // Play upload key (phase 8). Never in the repository: the four values come from the environment of the machine that
    // makes the store build (see tools/release.sh). Without all four, the release build is simply left unsigned.
    val uploadKey = listOf("MUMINS_UPLOAD_STORE_FILE", "MUMINS_UPLOAD_STORE_PASSWORD", "MUMINS_UPLOAD_KEY_ALIAS", "MUMINS_UPLOAD_KEY_PASSWORD")
        .associateWith { providers.environmentVariable(it).orNull }
    if (uploadKey.values.all { !it.isNullOrBlank() }) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKey.getValue("MUMINS_UPLOAD_STORE_FILE")!!)
            storePassword = uploadKey.getValue("MUMINS_UPLOAD_STORE_PASSWORD")
            keyAlias = uploadKey.getValue("MUMINS_UPLOAD_KEY_ALIAS")
            keyPassword = uploadKey.getValue("MUMINS_UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // ADR-15. Manifest components are kept by the plugin's generated rules; the app uses only platform APIs and
            // org.json, so no rules file is needed. Signing happens outside the repository.
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    // JVM tests of the store's pure parts. T-BUILD-3 reads the release/debug runtime classpath, not this one.
    testImplementation(kotlin("test-junit"))
}

// ---------------------------------------------------------------------------------------------------------------
// Build gates. Each fails the build; none can be skipped by a variant, because each is wired into `check`.
// ---------------------------------------------------------------------------------------------------------------

/**
 * T-BUILD-1 (INV-P1): the merged manifest does not request INTERNET.
 * T-BUILD-2 (INV-P3): the permissions it does request are exactly app/allowlist/permissions.txt.
 * The merged manifest is what ships: it includes whatever a library's own manifest adds.
 */
abstract class CheckPermissions : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:InputFile
    abstract val allowlistFile: RegularFileProperty

    @TaskAction
    fun check() {
        val android = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(mergedManifest.get().asFile).documentElement
        val requested = mutableSetOf<String>()
        // Every element that asks for a permission, under any of its spellings.
        for (tag in listOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as org.w3c.dom.Element
                val name = element.getAttributeNS(android, "name")
                val maxSdk = element.getAttributeNS(android, "maxSdkVersion")
                requested += if (maxSdk.isEmpty()) name else "$name maxSdkVersion=$maxSdk"
            }
        }
        val allowed = allowlistFile.get().asFile.readLines()
            .map { it.substringBefore('#').trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.toSet()

        val problems = mutableListOf<String>()
        if (requested.any { it.startsWith("android.permission.INTERNET") } || allowed.any { it.startsWith("android.permission.INTERNET") }) {
            problems += "INTERNET is requested or allowlisted. v1 makes no network requests (INV-P1)."
        }
        (requested - allowed).sorted().forEach { problems += "requested but not on the allowlist: $it" }
        (allowed - requested).sorted().forEach { problems += "on the allowlist but not requested: $it" }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Permission gate failed (INV-P1, INV-P3) for ${mergedManifest.get().asFile.name}:\n" +
                    problems.joinToString("\n") { "  - $it" } +
                    "\nThe allowlist is app/allowlist/permissions.txt; changing it needs an ADR.",
            )
        }
        logger.lifecycle("permission gate: passed, ${requested.size} permissions, no INTERNET.")
    }
}

/** T-BUILD-3 (INV-P4): the libraries that end up in the app are exactly app/allowlist/dependencies.txt. */
abstract class CheckDependencies : DefaultTask() {
    @get:Input
    abstract val resolved: SetProperty<String>

    @get:InputFile
    abstract val allowlistFile: RegularFileProperty

    @TaskAction
    fun check() {
        val allowed = allowlistFile.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val shipped = resolved.get()
        val problems = (shipped - allowed).sorted().map { "in the app but not on the allowlist: $it" } +
            (allowed - shipped).sorted().map { "on the allowlist but not in the app: $it" }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Dependency gate failed (INV-P4):\n" + problems.joinToString("\n") { "  - $it" } +
                    "\nThe allowlist is app/allowlist/dependencies.txt; changing it needs an ADR.",
            )
        }
        logger.lifecycle("dependency gate: passed, ${shipped.size} libraries.")
    }
}

/** What the string gates read: every translatable resource in a `values*` directory, as key to its list of values. */
object StringResources {
    private val RESOURCE_TAGS = setOf("string", "string-array", "plurals")

    fun read(files: Collection<File>): Map<String, List<String>> {
        val found = linkedMapOf<String, List<String>>()
        for (file in files.sortedBy { it.path }) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val children = root.childNodes
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                if (element.tagName !in RESOURCE_TAGS || element.getAttribute("translatable") == "false") continue
                val values = if (element.tagName == "string") {
                    listOf(element.textContent)
                } else {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                }
                found[element.getAttribute("name")] = values
            }
        }
        return found
    }

    /** A value as the user will see it: `\uXXXX` escapes resolved, so one cannot smuggle a Latin digit past the gate. */
    fun unescape(value: String): String =
        Regex("\\\\u([0-9a-fA-F]{4})").replace(value) { it.groupValues[1].toInt(16).toChar().toString() }

    /** printf placeholders such as %d, %1\$d, %02d, %.1f and %%: their digits are format syntax, not text. */
    private val PLACEHOLDER = Regex("%(?:\\d+\\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

    fun withoutPlaceholders(value: String): String = PLACEHOLDER.replace(value, "")

    /** Review file lines: comments (#) and blanks dropped. */
    fun reviewKeys(file: File): List<String> =
        file.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
}

/**
 * T-BUILD-5 (INV-L1, INV-L2): Bengali (values/) and English (values-en/) hold exactly the same keys, no value is empty,
 * array lengths match (12 months, 7 weekdays), and no Bengali value contains a Latin digit 0-9 outside a printf
 * placeholder. Also fails if app/l10n-review.txt names a key that is not a Bengali string, so it cannot rot.
 */
abstract class CheckStrings : DefaultTask() {
    @get:InputFiles
    abstract val bengaliFiles: ConfigurableFileCollection

    @get:InputFiles
    abstract val englishFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val reviewFile: RegularFileProperty

    @TaskAction
    fun check() {
        val bengali = StringResources.read(bengaliFiles.files)
        val english = StringResources.read(englishFiles.files)
        val problems = mutableListOf<String>()

        (bengali.keys - english.keys).sorted().forEach { problems += "\"$it\" is in values/ (Bengali) but not in values-en/ (English)" }
        (english.keys - bengali.keys).sorted().forEach { problems += "\"$it\" is in values-en/ (English) but not in values/ (Bengali)" }
        for (key in bengali.keys.intersect(english.keys).sorted()) {
            val bn = bengali.getValue(key)
            val en = english.getValue(key)
            if (bn.size != en.size) problems += "\"$key\" has ${bn.size} items in Bengali and ${en.size} in English"
        }
        for ((key, expected) in mapOf("months" to 12, "weekdays" to 7, "hijri_months" to 12)) {
            for ((language, resources) in mapOf("Bengali" to bengali, "English" to english)) {
                val actual = resources[key]?.size
                if (actual != expected) problems += "\"$key\" must have $expected items in $language, found ${actual ?: "none"}"
            }
        }
        for ((key, values) in english) {
            values.forEachIndexed { index, value -> if (value.isBlank()) problems += "English \"$key\"${position(index, values.size)} is empty" }
        }
        for ((key, values) in bengali) {
            values.forEachIndexed { index, value ->
                if (value.isBlank()) problems += "Bengali \"$key\"${position(index, values.size)} is empty"
                val shown = StringResources.withoutPlaceholders(StringResources.unescape(value))
                val digits = shown.filter { it in '0'..'9' }
                if (digits.isNotEmpty()) problems += "Bengali \"$key\"${position(index, values.size)} contains Latin digit(s) \"$digits\" (INV-L2): $value"
            }
        }
        val review = StringResources.reviewKeys(reviewFile.get().asFile)
        review.filter { it !in bengali.keys }.distinct().sorted().forEach {
            problems += "app/l10n-review.txt lists \"$it\", which is not a Bengali string key (remove the line)"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "String gate failed (INV-L1, INV-L2, T-BUILD-5):\n" + problems.joinToString("\n") { "  - $it" } +
                    "\nBengali is values/strings.xml, English is values-en/strings.xml.",
            )
        }
        logger.lifecycle("string gate: passed, ${bengali.size} keys in Bengali and English, no Latin digit in Bengali.")
    }

    private fun position(index: Int, size: Int) = if (size > 1) " item ${index + 1}" else ""
}

/** Reports, never fails: how many Bengali keys no native speaker has cleared yet. */
abstract class ReportL10nReview : DefaultTask() {
    @get:InputFiles
    abstract val bengaliFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val reviewFile: RegularFileProperty

    @TaskAction
    fun report() {
        val keys = StringResources.read(bengaliFiles.files).keys
        val unreviewed = StringResources.reviewKeys(reviewFile.get().asFile).distinct().filter { it in keys }
        logger.lifecycle("l10n review: ${unreviewed.size} of ${keys.size} Bengali keys are still unreviewed (app/l10n-review.txt).")
    }
}

/** Copies the content files the app ships into one folder that becomes the release build's assets. */
abstract class StageContent : DefaultTask() {
    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun stage() {
        val target = assets.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        files.files.filter { it.isFile }.forEach { it.copyTo(File(target, it.name)) }
    }
}

/**
 * The engine jar's META-INF/LICENSE and NOTICE.txt are dropped when the APK is packaged (G43), so the licence texts
 * are copied byte for byte from the repository into assets/licences/ for the Licences screen (LGPL-3.0 section 4b).
 * The order here is the order the screen shows them in. Assets, not strings.xml: legal English, untranslated.
 */
abstract class StageLicenceTexts : DefaultTask() {
    @get:InputFile
    abstract val lgpl: RegularFileProperty

    @get:InputFile
    abstract val gpl: RegularFileProperty

    @get:InputFile
    abstract val engineNotice: RegularFileProperty

    @get:InputFile
    abstract val apache: RegularFileProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun stage() {
        val target = File(assets.get().asFile, "licences")
        target.deleteRecursively()
        target.mkdirs()
        lgpl.get().asFile.copyTo(File(target, "calctimes-LGPL-3.0.txt"))
        gpl.get().asFile.copyTo(File(target, "GPL-3.0.txt"))
        engineNotice.get().asFile.copyTo(File(target, "calctimes-NOTICE.txt"))
        apache.get().asFile.copyTo(File(target, "Apache-2.0.txt"))
    }
}

val stageLicenceTexts = tasks.register<StageLicenceTexts>("stageLicenceTexts") {
    val engine = rootProject.layout.projectDirectory.dir("engine/calctimes")
    lgpl.set(engine.file("LICENSE"))
    gpl.set(engine.file("LICENSE.GPL-3.0"))
    engineNotice.set(engine.file("NOTICE.txt"))
    apache.set(rootProject.layout.projectDirectory.file("LICENSE"))
    assets.set(layout.buildDirectory.dir("generated/licenceAssets"))
}

val resourceDirectory = layout.projectDirectory.dir("src/main/res")
val bengaliStrings = files(fileTree(resourceDirectory.dir("values")) { include("*.xml") })
val englishStrings = files(fileTree(resourceDirectory.dir("values-en")) { include("*.xml") })
val reviewList = layout.projectDirectory.file("l10n-review.txt")

val checkStrings = tasks.register<CheckStrings>("checkStrings") {
    group = "verification"
    description = "T-BUILD-5: Bengali and English hold the same keys; no empty value; no Latin digit in Bengali."
    bengaliFiles.from(bengaliStrings)
    englishFiles.from(englishStrings)
    reviewFile.set(reviewList)
}

val reportL10nReview = tasks.register<ReportL10nReview>("reportL10nReview") {
    group = "verification"
    description = "Reports how many Bengali keys are still listed in l10n-review.txt. Never fails."
    bengaliFiles.from(bengaliStrings)
    reviewFile.set(reviewList)
}

tasks.named("check") { dependsOn(checkStrings, reportL10nReview) }

// The district list and the deed file ship in release builds from content/ (the debug build has its own fake ones in
// src/debug/assets). While Q4 and G9 are open neither file exists and the release build simply has none; the gates in
// tools/content-gate decide whether that is allowed (-PdistrictsRequired=true, -PdeedsRequired=true), and a store build
// must set both. deedsRequired stays off by default because content/deeds.json cannot exist until the reviewer delivers
// it (G9): turning it on now would fail every build. What a release without the file does: the app shows no deed and
// says so in a neutral message, it never falls back to anything else (INV-C1).
val contentFiles = listOf("districts.json", "deeds.json", "zakat-rules.json", "duas.json", "hadith.json").map { rootProject.layout.projectDirectory.file("content/$it") }
val stageReleaseContent = tasks.register<StageContent>("stageReleaseContent") {
    files.from(contentFiles)
    assets.set(layout.buildDirectory.dir("generated/releaseContent"))
    // Said in the build output so nobody ships a release and finds out from a user.
    val missing = contentFiles.filter { !it.asFile.isFile }.map { "content/" + it.asFile.name }
    doFirst {
        missing.forEach { logger.lifecycle("release content: $it is missing, so this release build ships without it. A store build must pass the gate with -PdeedsRequired=true -PdistrictsRequired=true -PzakatRulesRequired=true, and -PduasRequired=true -PhadithRequired=true for each kind it ships.") }
    }
}

/**
 * The Quran assets (ADR-23, ADR-26): every recorded file of `content/quran/`, its `translations/` folders included, into
 * `assets/quran/`, byte for byte, in every build. T-BUILD-6 runs first, so a build that packages the text cannot skip the
 * hash check (INV-QT1). Hidden files (a Finder `.DS_Store`) are not content and are neither staged nor checked.
 */
abstract class StageQuran : DefaultTask() {
    @get:InputDirectory
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun stage() {
        val target = File(assets.get().asFile, "quran")
        target.deleteRecursively()
        target.mkdirs()
        val root = source.get().asFile
        root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.forEach { it.copyTo(File(target, it.relativeTo(root).path)) }
    }
}

val stageQuran = tasks.register<StageQuran>("stageQuran") {
    dependsOn(":tools:content-gate:checkQuranText")
    source.set(rootProject.layout.projectDirectory.dir("content/quran"))
    assets.set(layout.buildDirectory.dir("generated/quranAssets"))
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }

        val permissions = tasks.register<CheckPermissions>("check${name}Permissions") {
            group = "verification"
            description = "Fails if the ${variant.name} manifest's permissions differ from the allowlist."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowlistFile.set(layout.projectDirectory.file("allowlist/permissions.txt"))
        }

        // Everything that is packaged: the runtime classpath.
        val runtime = configurations.named("${variant.name}RuntimeClasspath")
        val dependencies = tasks.register<CheckDependencies>("check${name}Dependencies") {
            group = "verification"
            description = "Fails if the libraries packaged into ${variant.name} differ from the allowlist."
            resolved.set(
                provider {
                    runtime.get().incoming.resolutionResult.allComponents
                        .mapNotNull { it.id as? ModuleComponentIdentifier }
                        .map { "${it.group}:${it.module}" }
                        .toSet()
                },
            )
            allowlistFile.set(layout.projectDirectory.file("allowlist/dependencies.txt"))
        }

        variant.sources.assets?.addGeneratedSourceDirectory(stageLicenceTexts, StageLicenceTexts::assets)
        variant.sources.assets?.addGeneratedSourceDirectory(stageQuran, StageQuran::assets)

        if (variant.buildType == "release") {
            variant.sources.assets?.addGeneratedSourceDirectory(stageReleaseContent, StageContent::assets)
        }

        tasks.named("check") { dependsOn(permissions, dependencies) }
    }
}
