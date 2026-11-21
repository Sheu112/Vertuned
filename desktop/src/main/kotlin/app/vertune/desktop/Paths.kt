package app.vertune.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Where Vertune keeps its data on this computer, and where its helper programs live. */
object Paths {
    private val os = System.getProperty("os.name").lowercase()
    val isMac = "mac" in os
    val isWindows = "windows" in os

    /** The computer's name for itself in the app's wording ("this Mac", "this PC"). */
    val deviceName get() = if (isWindows) "PC" else "Mac"

    private val home get() = System.getProperty("user.home")

    val dataDir: File by lazy {
        val dir = when {
            isMac -> File(home, "Library/Application Support/Vertune")
            // %APPDATA%\Vertune, where Windows apps keep their settings and libraries.
            isWindows -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, "Vertune")
            else -> File(home, ".vertune")
        }
        dir.apply { mkdirs() }
    }

    /** Where big, re-downloadable files (the song cache) go. */
    val cacheDir: File by lazy {
        when {
            isMac -> File(home, "Library/Caches/Vertune")
            // %LOCALAPPDATA%\Vertune\Cache: not copied between PCs with the user's roaming profile.
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: File(home, "AppData/Local").path, "Vertune/Cache")
            else -> File(dataDir, "cache")
        }
    }

    val databaseFile get() = File(dataDir, "library.db")
    val logFile get() = File(dataDir, "vertune.log")
    val imageCacheDir get() = File(dataDir, "image-cache")
    val quickPicksFile get() = File(dataDir, "quick-picks.json")

    /**
     * The helper programs (yt-dlp, ffmpeg, quickjs) are shipped inside the app bundle. They are
     * copied into the data folder before use: the copy is not marked as "downloaded from the
     * internet" by macOS, and yt-dlp can be updated there (the install folder on Windows may be
     * read-only).
     */
    val toolsDir: File by lazy { File(dataDir, "tools").apply { mkdirs() } }

    /** A program's file name on this system (Windows programs end in .exe). */
    fun exeName(name: String) = if (isWindows) "$name.exe" else name

    private val bundledResources: File? by lazy {
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
    }

    fun tool(name: String): String? {
        bundledResources?.let { res ->
            // A folder (yt-dlp's faster-starting version), or a single program file.
            val bundled = listOf(File(res, name), File(res, exeName(name))).firstOrNull { it.exists() }
            if (bundled != null) return installTool(bundled).absolutePath
        }
        // Development runs: use whatever is on the PATH.
        return System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, exeName(name)) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    /** Copies a bundled tool (a file, or a folder with an executable of the same name) once per app version. */
    @Synchronized
    private fun installTool(source: File): File {
        val marker = File(toolsDir, "${source.name}.version")
        val target = File(toolsDir, source.name)
        val stamp = "${source.length()}-${source.lastModified()}-${AppInfo.VERSION}"
        val executable = if (source.isDirectory) File(target, exeName(source.name)) else target

        if (!executable.exists() || runCatching { marker.readText() }.getOrNull() != stamp) {
            target.deleteRecursively()
            source.walkTopDown().forEach { file ->
                val dest = File(target, file.relativeTo(source).path)
                if (file.isDirectory) dest.mkdirs()
                else {
                    dest.parentFile?.mkdirs()
                    Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (file.canExecute()) dest.setExecutable(true)
                }
            }
            executable.setExecutable(true)
            marker.writeText(stamp)
        }
        return executable
    }
}

object AppInfo {
    const val NAME = "Vertune"
    /** Set by the build (see desktop/build.gradle.kts); "dev" when run straight from Gradle. */
    val VERSION: String = System.getProperty("vertune.version") ?: "dev"
}
