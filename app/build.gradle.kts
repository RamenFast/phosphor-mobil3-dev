import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = "2.0.0"
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }
val appVersionCode = vMajor * 1_000_000 + vMinor * 1_000 + vPatch

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun propertyOrEnvironment(name: String): String? =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: localProps.getProperty(name)?.takeIf(String::isNotBlank)

fun ndkHome(): String {
    val sdkDir = localProps.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: error(
            "sdk.dir missing from local.properties and ANDROID_HOME unset; " +
                "fix: run scripts/bootstrap-android.sh",
        )
    return "$sdkDir/ndk/${libs.versions.ndk.get()}"
}

data class SigningInputs(
    val prefix: String,
    val storeFile: String?,
    val storePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?,
    val expectedCertificateSha256: String?,
) {
    val missing: List<String> = buildList {
        if (storeFile.isNullOrBlank()) add("${prefix}_STORE_FILE")
        if (storePassword.isNullOrBlank()) add("${prefix}_STORE_PASSWORD")
        if (keyAlias.isNullOrBlank()) add("${prefix}_KEY_ALIAS")
        if (keyPassword.isNullOrBlank()) add("${prefix}_KEY_PASSWORD")
        if (expectedCertificateSha256.isNullOrBlank()) add("${prefix}_CERT_SHA256")
    }
    val complete: Boolean get() = missing.isEmpty()
}

fun signingInputs(prefix: String) = SigningInputs(
    prefix = prefix,
    storeFile = propertyOrEnvironment("${prefix}_STORE_FILE"),
    storePassword = propertyOrEnvironment("${prefix}_STORE_PASSWORD"),
    keyAlias = propertyOrEnvironment("${prefix}_KEY_ALIAS"),
    keyPassword = propertyOrEnvironment("${prefix}_KEY_PASSWORD"),
    expectedCertificateSha256 = propertyOrEnvironment("${prefix}_CERT_SHA256"),
)

val signingProfile = propertyOrEnvironment("PHOSPHOR_SIGNING_PROFILE") ?: "production"
check(signingProfile in setOf("production", "play-upload")) {
    "unsupported PHOSPHOR_SIGNING_PROFILE '$signingProfile'; fix: use production or play-upload"
}
val productionSigning = signingInputs("RELEASE")
val playUploadSigning = signingInputs("PLAY_UPLOAD")
val releaseSigning = if (signingProfile == "play-upload") playUploadSigning else productionSigning

fun gitOutput(vararg arguments: String): String? = runCatching {
    providers.exec {
        workingDir = rootProject.projectDir
        commandLine("git", *arguments)
    }.standardOutput.asText.get().trim()
}.getOrNull()

val gitHead = gitOutput("rev-parse", "--verify", "HEAD")?.takeIf(String::isNotEmpty)
val gitCommit = gitHead?.take(12) ?: "unknown"
val gitDirty = gitOutput("status", "--porcelain", "--untracked-files=normal")
    ?.isNotEmpty() ?: true
val debugGitCommit = when {
    gitHead == null -> "unknown"
    gitDirty -> "$gitCommit-dirty"
    else -> gitCommit
}

val debugJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/debug")
val releaseJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/release")

// Independent content identity stays identical across dirty/clean Git transitions.
val rootAudioInputs = files(
    rootProject.fileTree("root-helper") { include("java/**/*.java", "native/src/**/*.rs", "native/Cargo.toml", "native/Cargo.lock", "native/rust-toolchain.toml") },
    rootProject.file("docs/plans/mobile-expansion/section-02-helper-contract.md"),
    file("build.gradle.kts"),
    fileTree("src/debug") { include("**/RootAudio*.kt", "**/SelfTestReceiver.kt", "AndroidManifest.xml") },
)
val rootAudioBuild = MessageDigest.getInstance("SHA-256").run {
    rootAudioInputs.files.sortedBy { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }.forEach {
        update(it.relativeTo(rootProject.projectDir).invariantSeparatorsPath.toByteArray())
        update(0.toByte())
        update(it.readBytes())
        update(0.toByte())
    }
    digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
val rootAudioAssets = objects.directoryProperty().convention(layout.buildDirectory.dir("generated/rootAudio/assets"))
val rootAudioJni = objects.directoryProperty().convention(layout.buildDirectory.dir("generated/rootAudio/jniLibs"))
val rootAudioJava = layout.buildDirectory.dir("generated/rootAudio/java")
val rootAudioClasses = layout.buildDirectory.dir("generated/rootAudio/classes")

fun normalizedSha256(value: String): String = value.lowercase().replace(":", "").trim()

fun buildConfigString(value: String): String = "\"" + value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n") + "\""

fun loadSigningCertificate(inputs: SigningInputs): java.security.cert.Certificate {
    check(inputs.complete) {
        "${inputs.prefix} release signing unavailable: missing ${inputs.missing.joinToString()}; " +
            "fix: provide every ${inputs.prefix}_* signing input outside the repository"
    }
    val store = rootProject.file(inputs.storeFile!!)
    check(store.isFile) {
        "${inputs.prefix} release signing unavailable: keystore not found at $store; " +
            "fix: correct ${inputs.prefix}_STORE_FILE"
    }
    val attempts = mutableListOf<String>()
    for (type in listOf("PKCS12", "JKS")) {
        try {
            val keyStore = KeyStore.getInstance(type)
            store.inputStream().use { keyStore.load(it, inputs.storePassword!!.toCharArray()) }
            check(keyStore.isKeyEntry(inputs.keyAlias)) {
                "${inputs.prefix} release signing unavailable: alias is not a private key entry; " +
                    "fix: correct ${inputs.prefix}_KEY_ALIAS"
            }
            return checkNotNull(keyStore.getCertificate(inputs.keyAlias)) {
                "${inputs.prefix} release signing unavailable: alias has no certificate"
            }
        } catch (error: Exception) {
            attempts += "$type=${error.javaClass.simpleName}"
        }
    }
    error(
        "${inputs.prefix} release signing unavailable: keystore could not be opened " +
            "(${attempts.joinToString()}); fix: verify store/key passwords and keystore format",
    )
}

fun verifySigningInputs(inputs: SigningInputs) {
    val certificate = loadSigningCertificate(inputs)
    val actual = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val expected = normalizedSha256(inputs.expectedCertificateSha256!!)
    check(actual == expected) {
        "${inputs.prefix} release certificate mismatch: actual $actual, expected $expected; " +
            "fix: use the approved signing identity or update the signed decision before building"
    }
}

android {
    namespace = "dev.phosphor.mobil3"
    compileSdk = 36
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "dev.phosphor.mobil3"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseSigning.complete) create("release") {
            storeFile = rootProject.file(releaseSigning.storeFile!!)
            storePassword = releaseSigning.storePassword
            keyAlias = releaseSigning.keyAlias
            keyPassword = releaseSigning.keyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "BUILD_COMMIT", buildConfigString(debugGitCommit))
            buildConfigField("String", "ROOT_AUDIO_BUILD", buildConfigString(rootAudioBuild))
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "BUILD_COMMIT", buildConfigString(gitCommit))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").jniLibs.directories.clear()
        getByName("debug").jniLibs.directories.add(debugJniLibsDir.get().asFile.absolutePath)
        getByName("release").jniLibs.directories.add(releaseJniLibsDir.get().asFile.absolutePath)
    }
}

val generateRootAudioIdentity = tasks.register("generateRootAudioIdentity") {
    inputs.property("identity", rootAudioBuild)
    outputs.dir(rootAudioJava)
    doLast {
        rootAudioJava.get().file("dev/phosphor/mobil3/root/HelperBuild.java").asFile.apply {
            parentFile.mkdirs()
            writeText("package dev.phosphor.mobil3.root; final class HelperBuild { static final String ID = \"$rootAudioBuild\"; }\n")
        }
    }
}
val rootAudioAndroidJar = File(ndkHome()).parentFile.parentFile.resolve("platforms/android-36/android.jar")
val compileRootAudioJava = tasks.register<JavaCompile>("compileRootAudioJava") {
    dependsOn(generateRootAudioIdentity)
    source(rootProject.fileTree("root-helper/java") { include("**/*.java") }, rootAudioJava)
    classpath = files(rootAudioAndroidJar)
    destinationDirectory.set(rootAudioClasses)
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
val dexRootAudioHelper = tasks.register<Exec>("dexRootAudioHelper") {
    dependsOn(compileRootAudioJava)
    inputs.dir(rootAudioClasses)
    inputs.file(rootAudioAndroidJar)
    outputs.dir(rootAudioAssets)
    doFirst {
        val jar = rootAudioAssets.get().file("root-audio/helper.jar").asFile
        jar.parentFile.mkdirs()
        commandLine(listOf(
            rootAudioAndroidJar.parentFile.parentFile.parentFile.resolve("build-tools/36.0.0/d8").absolutePath,
            "--min-api", "29", "--lib", rootAudioAndroidJar.absolutePath, "--output", jar.absolutePath,
        ) + rootAudioClasses.get().asFile.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.sorted().toList())
    }
    doLast {
        val jar = rootAudioAssets.get().file("root-audio/helper.jar").asFile
        val hash = MessageDigest.getInstance("SHA-256").digest(jar.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        rootAudioAssets.get().file("root-audio/helper.sha256").asFile.writeText(hash)
    }
}
val buildRootAudioLauncher = tasks.register<Exec>("buildRootAudioLauncher") {
    dependsOn(dexRootAudioHelper)
    inputs.files(rootAudioInputs)
    inputs.dir(rootAudioAssets)
    inputs.property("identity", rootAudioBuild)
    outputs.dir(rootAudioJni)
    val target = layout.buildDirectory.dir("rootAudioCargo").get().asFile
    workingDir = rootProject.file("root-helper/native")
    environment("ANDROID_NDK_HOME", ndkHome())
    environment("CARGO_TARGET_DIR", target.absolutePath)
    environment("ROOT_HELPER_BUILD", rootAudioBuild)
    environment("RUSTFLAGS", "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=4096")
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-P", "29", "build", "--release", "--locked", "--bin", "phosphor-root-launcher")
    doFirst { environment("ROOT_HELPER_SHA256", rootAudioAssets.get().file("root-audio/helper.sha256").asFile.readText().trim()) }
    doLast {
        val destination = rootAudioJni.get().file("arm64-v8a/libphosphor_root_launcher.so").asFile
        destination.parentFile.mkdirs()
        target.resolve("aarch64-linux-android/release/phosphor-root-launcher").copyTo(destination, overwrite = true)
    }
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.packaging.jniLibs.useLegacyPackaging.set(true)
    variant.sources.assets?.addGeneratedSourceDirectory(dexRootAudioHelper) { rootAudioAssets }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRootAudioLauncher) { rootAudioJni }
}

fun registerCargoTask(
    name: String,
    profileArgs: List<String>,
    outputDir: Provider<Directory>,
) = tasks.register<Exec>(name) {
    val libcxx = File(
        ndkHome(),
        "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so",
    )
    workingDir = rootProject.file("rust")
    environment("ANDROID_NDK_HOME", ndkHome())
    commandLine(
        listOf(
            "cargo",
            "ndk",
            "-t",
            "arm64-v8a",
            "-P",
            "29",
            "-o",
            outputDir.get().asFile.absolutePath,
            "build",
            "--locked",
        ) + profileArgs,
    )
    inputs.dir(rootProject.file("rust/src"))
    inputs.dir(rootProject.file("../phosphor/crates"))
    inputs.files(
        rootProject.file("rust/Cargo.toml"),
        rootProject.file("rust/Cargo.lock"),
        rootProject.file("rust/rust-toolchain.toml"),
        libcxx,
    )
    outputs.dir(outputDir)
    doLast {
        val destination = outputDir.get().file("arm64-v8a/libc++_shared.so").asFile
        destination.parentFile.mkdirs()
        if (!destination.exists() || libcxx.lastModified() > destination.lastModified()) {
            libcxx.copyTo(destination, overwrite = true)
        }
    }
}

val cargoBuildDebug = registerCargoTask("cargoBuildDebug", emptyList(), debugJniLibsDir)
val cargoBuildRelease = registerCargoTask("cargoBuildRelease", listOf("--release"), releaseJniLibsDir)

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fail closed unless the selected release signing identity is complete and exact."
    doLast { verifySigningInputs(releaseSigning) }
}

tasks.register("verifyProductionSigning") {
    group = "verification"
    description = "Verify the Ben-controlled signer used for the direct release APK."
    doLast { verifySigningInputs(productionSigning) }
}

tasks.register("verifyPlayUploadSigning") {
    group = "verification"
    description = "Verify the upload signer used for the Google Play AAB."
    doLast { verifySigningInputs(playUploadSigning) }
}

val verifyReleaseProvenance = tasks.register<Exec>("verifyReleaseProvenance") {
    group = "verification"
    description = "Fail closed unless release source is clean, exactly tagged, and commit-identical."
    workingDir = rootProject.projectDir
    commandLine(
        buildList {
            add(rootProject.file("scripts/check-release-provenance.sh").absolutePath)
            addAll(
                listOf(
                    "--repo",
                    rootProject.projectDir.absolutePath,
                    "--version",
                    appVersion,
                    "--dependency-repo",
                    rootProject.file("../phosphor").absolutePath,
                ),
            )
            propertyOrEnvironment("GIT_COMMIT")?.let {
                addAll(listOf("--commit", it))
            }
            add("--json")
        },
    )
}

val bundletool by configurations.creating

tasks.register("writeBoundaryBundletoolClasspath") {
    group = "verification"
    description = "Resolve the existing pinned bundletool for private manifest decoding and isolated fixtures. No app build."
    doLast {
        val output = providers.gradleProperty("boundaryClasspathOutput").orNull
            ?: error("boundaryClasspathOutput missing; fix: pass an absolute private scratch output path")
        val target = file(output)
        check(target.isAbsolute && target.parentFile.isDirectory) {
            "classpath output parent unavailable; fix: create a private scratch directory"
        }
        target.writeText(bundletool.asPath)
    }
}

val releaseRuntimeDependencyReport =
    layout.buildDirectory.file("reports/play-boundary/releaseRuntimeClasspath.txt")
val writeReleaseRuntimeDependencyReport = tasks.register("writeReleaseRuntimeDependencyReport") {
    group = "verification"
    description = "Record the resolved release runtime components and artifacts for boundary inspection."
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    inputs.files(runtimeClasspath)
    outputs.file(releaseRuntimeDependencyReport)
    doLast {
        val configuration = runtimeClasspath.get()
        val lines = buildList {
            add("releaseRuntimeClasspath")
            addAll(configuration.incoming.resolutionResult.allComponents.map { it.id.displayName })
            addAll(configuration.files.map { it.name })
        }.distinct().sorted()
        val report = releaseRuntimeDependencyReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

tasks.register<Exec>("checkPlayBoundary") {
    group = "verification"
    description = "Build and check the approved production boundary, without asserting store approval."
    dependsOn("bundleRelease", writeReleaseRuntimeDependencyReport)
    doFirst {
        environment("PHOSPHOR_BOUNDARY_BUNDLETOOL_CLASSPATH", bundletool.asPath)
    }
    workingDir = rootProject.projectDir
    commandLine(
        rootProject.file("scripts/check-play-boundary.sh").absolutePath,
        "all",
        "--artifact",
        layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile.absolutePath,
        "--dependencies",
        releaseRuntimeDependencyReport.get().asFile.absolutePath,
        "--json",
    )
}

tasks.register<JavaExec>("validateReleaseBundle") {
    group = "verification"
    description = "Validate the signed release AAB with the pinned bundletool runtime."
    dependsOn("bundleRelease")
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    args(
        "validate",
        "--bundle=${layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile}",
    )
}

tasks.register<JavaExec>("validateBundle") {
    group = "verification"
    description = "Validate an existing AAB supplied with -PphosphorBundle=/absolute/path."
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    doFirst {
        val bundle = providers.gradleProperty("phosphorBundle").orNull
            ?.let(rootProject::file)
            ?: error("phosphorBundle missing; fix: pass -PphosphorBundle=/absolute/path/to/app.aab")
        check(bundle.isFile) {
            "bundle unavailable at $bundle; fix: pass an existing AAB"
        }
        args("validate", "--bundle=$bundle")
    }
}

tasks.register<Exec>("checkEngine") {
    group = "verification"
    description = "Check the mobile engine against the pinned sibling source and lockfile."
    workingDir = rootProject.file("rust")
    environment("ANDROID_NDK_HOME", ndkHome())
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-P", "29", "check", "--locked")
}

tasks.configureEach {
    if (name.matches(Regex("merge.*DebugJniLibFolders"))) dependsOn(cargoBuildDebug)
    if (name.matches(Regex("merge.*ReleaseJniLibFolders"))) dependsOn(cargoBuildRelease)
    if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning, verifyReleaseProvenance)
}

dependencies {
    bundletool(libs.bundletool)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.androidx.documentfile)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(libs.json)
}
