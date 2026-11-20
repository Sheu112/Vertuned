package app.vertune.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Where Vertune keeps its data on this computer, and where its helper programs live. */
object Paths {
    private val os = System.getProperty("os.name").lowercase()
    val isMac = "mac" in os

    val dataDir: File by lazy {
        val home = System.getProperty("user.home")
        val dir = if (isMac) File(home, "Library/Application Support/Vertune") else File(home, ".vertune")
        dir.apply { mkdirs() }
    }

    val databaseFile get() = File(dataDir, "library.db")
    val logFile get() = File(dataDir, "vertune.log")

    /**
     * The helper programs (yt-dlp, ffmpeg, quickjs) are shipped inside the app bundle. They are
     * copied into the data folder before use: the copy is not marked as "downloaded from the
     * internet" by macOS, and yt-dlp can update itself there.
     */
    private val toolsDir: File by lazy { File(dataDir, "tools").apply { mkdirs() } }

    private val bundledResources: File? by lazy {
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
    }

    fun tool(name: String): String? {
        bundledResources?.let { res ->
            val bundled = File(res, name)
            if (bundled.exists()) return installTool(bundled).absolutePath
        }
        // Development runs: use whatever is on the PATH.
        return System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, name) }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    /** Copies a bundled tool (a file, or a folder with an executable of the same name) once per app version. */
    @Synchronized
    private fun installTool(source: File): File {
        val marker = File(toolsDir, "${source.name}.version")
        val target = File(toolsDir, source.name)
        val stamp = "${source.length()}-${source.lastModified()}-${AppInfo.VERSION}"
        val executable = if (source.isDirectory) File(target, source.name) else target

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
    const val VERSION = "1.0.0"
}
