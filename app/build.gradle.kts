import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = "1.0.3"
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

        // Relay hosts are a build-machine fact, never source: local.properties
        // `phosphor.remoteHosts=label:host:port,label:host:port` (or env). Empty is a
        // fine default — the REMOTE sheet just shows no seeded hosts.
        val remoteHosts = localProps.getProperty("phosphor.remoteHosts")
            ?: System.getenv("PHOSPHOR_REMOTE_HOSTS") ?: ""
        buildConfigField("String", "REMOTE_HOSTS", "\"$remoteHosts\"")
    }

    signingConfigs {
        create("release") {
            val store = localProps.getProperty("RELEASE_STORE_FILE") ?: System.getenv("RELEASE_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
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
}

// ---- Rust engine: cargo-ndk via plain Exec (no plugins, no Python) ----
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

fun registerCargoTask(name: String, profileArgs: List<String>) =
    tasks.register<Exec>(name) {
        workingDir = rootProject.file("rust")
        environment("ANDROID_NDK_HOME", ndkHome())
        commandLine(
            listOf("cargo", "ndk", "-t", "arm64-v8a", "-o", jniLibsDir.asFile.absolutePath, "build") + profileArgs
        )
        inputs.dir(rootProject.file("rust/src"))
        inputs.files(rootProject.file("rust/Cargo.toml"))
        outputs.dir(jniLibsDir)
        // oboe's C++ needs libc++_shared.so packaged alongside our cdylib.
        doLast {
            val src = File(ndkHome(), "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
            val dst = File(jniLibsDir.asFile, "arm64-v8a/libc++_shared.so")
            if (!dst.exists() || src.lastModified() > dst.lastModified()) {
                src.copyTo(dst, overwrite = true)
            }
        }
    }

val cargoBuildDebug = registerCargoTask("cargoBuildDebug", emptyList())
val cargoBuildRelease = registerCargoTask("cargoBuildRelease", listOf("--release"))

// Fails loudly when a desktop-side engine refactor breaks the mobile build (path-dep seam).
tasks.register<Exec>("checkEngine") {
    workingDir = rootProject.file("rust")
    environment("ANDROID_NDK_HOME", ndkHome())
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "check")
}

tasks.configureEach {
    if (name == "mergeDebugJniLibFolders") dependsOn(cargoBuildDebug)
    if (name == "mergeReleaseJniLibFolders") dependsOn(cargoBuildRelease)
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
}
