import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The Mac (desktop) version of Vertune. It only reuses the plain-Kotlin provider modules,
// so nothing here affects the Android app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.12.2"
}

dependencies {
    implementation(projects.providers.common)
    implementation(projects.providers.innertube)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.kugou)
    implementation(projects.providers.sponsorblock)

    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    implementation(libs.kotlin.coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.json:json:20260814")
    implementation("org.slf4j:slf4j-nop:2.0.20")
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

compose.desktop {
    application {
        mainClass = "app.vertune.desktop.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            // Lets the mini player reach its macOS window to float over full-screen apps (MacWindows.kt).
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
        )

        // yt-dlp, ffmpeg and quickjs are downloaded into this folder by CI (see build.yml)
        // and shipped inside the app bundle.
        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            targetFormats(TargetFormat.Dmg)
            packageName = "Vertune"
            packageVersion = "1.0.0"
            description = "Vertune music player"
            vendor = "Vertune"
            // The parts of Java the app and its libraries use (includeAllModules makes jlink fail on JDK 25).
            modules(
                "java.sql", "java.naming", "java.prefs", "jdk.unsupported", "java.desktop", "java.logging",
                "java.management", "java.net.http", "jdk.zipfs", "jdk.charsets", "jdk.localedata", "jdk.net"
            )

            macOS {
                bundleID = "app.vertune.desktop"
                iconFile.set(project.file("icons/vertune.icns"))
                appCategory = "public.app-category.music"
                dockName = "Vertune"
                // Lets the window chrome follow dark mode instead of always being light.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSRequiresAquaSystemAppearance</key>
                        <false/>
                    """.trimIndent()
                }
            }
        }

        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}

// Checks search, yt-dlp, ffmpeg and the library database without opening a window (used by CI).
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    mainClass = "app.vertune.desktop.SmokeTestKt"
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.jvm.get().toInt())
    }
    val arch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x64"
    systemProperty("compose.application.resources.dir", file("resources/macos-$arch").absolutePath)
}

// The Java runtime that jlink builds has read-only licence files, which made
// `xattr -dr com.apple.quarantine` print "Permission denied" for each one. Make them writable.
val runtimeImageDir = layout.buildDirectory.dir("compose/tmp/main/runtime")
tasks.matching { it.name == "createRuntimeImage" }.configureEach {
    doLast {
        runtimeImageDir.get().asFile.walkTopDown().forEach { it.setWritable(true, true) }
    }
}
