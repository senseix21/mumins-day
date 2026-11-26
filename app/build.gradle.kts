import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
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
    }

    compileOptions {
        // java.time on Android 8 and 9, which :core relies on.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
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

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }

        val permissions = tasks.register<CheckPermissions>("check${name}Permissions") {
            group = "verification"
            description = "Fails if the ${variant.name} manifest's permissions differ from the allowlist."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowlistFile.set(layout.projectDirectory.file("allowlist/permissions.txt"))
        }

        // Everything that is packaged: the runtime classpath, plus the desugaring library, which is dexed in.
        val runtime = configurations.named("${variant.name}RuntimeClasspath")
        val desugaring = configurations.named("coreLibraryDesugaring")
        val dependencies = tasks.register<CheckDependencies>("check${name}Dependencies") {
            group = "verification"
            description = "Fails if the libraries packaged into ${variant.name} differ from the allowlist."
            resolved.set(
                provider {
                    listOf(runtime.get(), desugaring.get())
                        .flatMap { it.incoming.resolutionResult.allComponents }
                        .mapNotNull { it.id as? ModuleComponentIdentifier }
                        .map { "${it.group}:${it.module}" }
                        .toSet()
                },
            )
            allowlistFile.set(layout.projectDirectory.file("allowlist/dependencies.txt"))
        }

        tasks.named("check") { dependsOn(permissions, dependencies) }
    }
}
