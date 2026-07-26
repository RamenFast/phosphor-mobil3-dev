import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = "2.0.0"
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun ndkHome(): String {
    val sdkDir = localProps.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: error("sdk.dir missing from local.properties and ANDROID_HOME unset — run scripts/bootstrap-android.sh, fix: echo \"sdk.dir=\$HOME/Android/Sdk\" > local.properties")
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

fun propertyOrEnvironment(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }

fun signingInputs(prefix: String, fixedCertificate: String? = null) = SigningInputs(
    prefix = prefix,
    storeFile = propertyOrEnvironment("${prefix}_STORE_FILE"),
    storePassword = propertyOrEnvironment("${prefix}_STORE_PASSWORD"),
    keyAlias = propertyOrEnvironment("${prefix}_KEY_ALIAS"),
    keyPassword = propertyOrEnvironment("${prefix}_KEY_PASSWORD"),
    expectedCertificateSha256 = fixedCertificate ?: propertyOrEnvironment("${prefix}_CERT_SHA256"),
)

val fortressCertificateSha256 =
    "e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00"
val fortressSigning = signingInputs("RELEASE", fortressCertificateSha256)
val playSigning = signingInputs("PLAY_UPLOAD")
val debugJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/debug")
val releaseJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/release")

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
        "${inputs.prefix} release signing unavailable: keystore could not be opened (${attempts.joinToString()}); " +
            "fix: verify store/key passwords and keystore format"
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
            "fix: use the ratified signing identity or update the signed decision before building"
    }
}

android {
    namespace = "dev.phosphor.mobil3"
    compileSdk = 36
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "dev.phosphor.mobil3"
        minSdk = 35
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = appVersion
        ndk { abiFilters += "arm64-v8a" }

    }

    signingConfigs {
        if (playSigning.complete) create("playRelease") {
            storeFile = rootProject.file(playSigning.storeFile!!)
            storePassword = playSigning.storePassword
            keyAlias = playSigning.keyAlias
            keyPassword = playSigning.keyPassword
        }
        if (fortressSigning.complete) create("fortressRelease") {
            storeFile = rootProject.file(fortressSigning.storeFile!!)
            storePassword = fortressSigning.storePassword
            keyAlias = fortressSigning.keyAlias
            keyPassword = fortressSigning.keyPassword
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationId = "dev.phosphor.mobil3"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
            // Play never receives build-time estate endpoints. A later public
            // user flow may collect a relay address at runtime.
            buildConfigField("String", "REMOTE_HOSTS", buildConfigString(""))
            signingConfig = signingConfigs.findByName("playRelease")
        }
        create("fortress") {
            dimension = "distribution"
            applicationId = "dev.phosphor.mobil3.fortress"
            buildConfigField("String", "DISTRIBUTION", "\"fortress\"")
            // Private seeded estate endpoints are Fortress-only build-machine facts.
            val fortressRemoteHosts = System.getenv("PHOSPHOR_REMOTE_HOSTS")
                ?: localProps.getProperty("phosphor.remoteHosts") ?: ""
            buildConfigField("String", "REMOTE_HOSTS", buildConfigString(fortressRemoteHosts))
            signingConfig = signingConfigs.findByName("fortressRelease")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
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

    // Native outputs are build products, not source. Keep debug and release
    // isolated so Gradle can safely assemble both distributions in one run.
    sourceSets {
        getByName("main").jniLibs.directories.clear()
        getByName("debug").jniLibs.directories.add(debugJniLibsDir.get().asFile.absolutePath)
        getByName("release").jniLibs.directories.add(releaseJniLibsDir.get().asFile.absolutePath)
    }
}

// ---- Rust engine: cargo-ndk via plain Exec (no plugins, no Python) ----
fun registerCargoTask(
    name: String,
    profileArgs: List<String>,
    outputDir: Provider<Directory>,
) =
    tasks.register<Exec>(name) {
        val libcxx = File(ndkHome(), "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
        workingDir = rootProject.file("rust")
        environment("ANDROID_NDK_HOME", ndkHome())
        commandLine(
            listOf("cargo", "ndk", "-t", "arm64-v8a", "-o", outputDir.get().asFile.absolutePath, "build") + profileArgs
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
        // oboe's C++ needs libc++_shared.so packaged alongside our cdylib.
        doLast {
            val dst = outputDir.get().file("arm64-v8a/libc++_shared.so").asFile
            dst.parentFile.mkdirs()
            if (!dst.exists() || libcxx.lastModified() > dst.lastModified()) {
                libcxx.copyTo(dst, overwrite = true)
            }
        }
    }

val cargoBuildDebug = registerCargoTask("cargoBuildDebug", emptyList(), debugJniLibsDir)
val cargoBuildRelease = registerCargoTask("cargoBuildRelease", listOf("--release"), releaseJniLibsDir)

val verifyPlayReleaseSigning = tasks.register("verifyPlayReleaseSigning") {
    group = "verification"
    description = "Fail closed unless the Play upload signing identity is complete and matches its pinned certificate."
    doLast { verifySigningInputs(playSigning) }
}

val verifyFortressReleaseSigning = tasks.register("verifyFortressReleaseSigning") {
    group = "verification"
    description = "Fail closed unless the RamenFast Fortress signing identity is healthy and exact."
    doLast { verifySigningInputs(fortressSigning) }
}

val playRuntimeDependencyReport = layout.buildDirectory.file("reports/play-boundary/playReleaseRuntimeClasspath.txt")
val writePlayRuntimeDependencyReport = tasks.register("writePlayRuntimeDependencyReport") {
    group = "verification"
    description = "Record the resolved Play runtime component and artifact names for boundary inspection."
    val runtimeClasspath = configurations.named("playReleaseRuntimeClasspath")
    inputs.files(runtimeClasspath)
    outputs.file(playRuntimeDependencyReport)
    doLast {
        val configuration = runtimeClasspath.get()
        val lines = buildList {
            add("playReleaseRuntimeClasspath")
            addAll(configuration.incoming.resolutionResult.allComponents.map { it.id.displayName })
            addAll(configuration.files.map { it.name })
        }.distinct().sorted()
        val report = playRuntimeDependencyReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

tasks.register<Exec>("checkPlayBoundary") {
    group = "verification"
    description = "Build and prove the Play artifact excludes Fortress and private implementation."
    dependsOn("bundlePlayRelease", writePlayRuntimeDependencyReport)
    workingDir = rootProject.projectDir
    commandLine(
        rootProject.file("scripts/check-play-boundary.sh").absolutePath,
        "all",
        "--artifact",
        layout.buildDirectory.file("outputs/bundle/playRelease/app-play-release.aab").get().asFile.absolutePath,
        "--dependencies",
        playRuntimeDependencyReport.get().asFile.absolutePath,
        "--json",
    )
}

// Fails loudly when a desktop-side engine refactor breaks the mobile build (path-dep seam).
tasks.register<Exec>("checkEngine") {
    workingDir = rootProject.file("rust")
    environment("ANDROID_NDK_HOME", ndkHome())
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "check")
}

tasks.configureEach {
    if (name.matches(Regex("merge.*DebugJniLibFolders"))) dependsOn(cargoBuildDebug)
    if (name.matches(Regex("merge.*ReleaseJniLibFolders"))) dependsOn(cargoBuildRelease)
    if (name == "prePlayReleaseBuild") dependsOn(verifyPlayReleaseSigning)
    if (name == "preFortressReleaseBuild") dependsOn(verifyFortressReleaseSigning)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation("androidx.documentfile:documentfile:1.1.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
