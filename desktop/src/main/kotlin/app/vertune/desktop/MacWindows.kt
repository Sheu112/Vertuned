package app.vertune.desktop

import java.awt.Component
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BOOLEAN
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A few macOS window tricks Java can't do on its own, done by talking to AppKit directly
 * (Java's foreign function API, no extra libraries):
 * - let a window float over every Space and over full-screen apps (for the mini player);
 * - hide or show the Dock icon (macOS only lets an app's windows sit over another app's
 *   full-screen Space when the app has no Dock icon).
 * Everything fails quietly: if something isn't available, the window just behaves normally.
 */
object MacWindows {
    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    private class Native {
        val linker: Linker = Linker.nativeLinker()
        val objc: SymbolLookup = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
        val system: SymbolLookup = SymbolLookup.libraryLookup("/usr/lib/libSystem.B.dylib", Arena.global())

        val getClass = linker.downcallHandle(objc.find("objc_getClass").get(), FunctionDescriptor.of(ADDRESS, ADDRESS))
        val selector = linker.downcallHandle(objc.find("sel_registerName").get(), FunctionDescriptor.of(ADDRESS, ADDRESS))
        private val msgSend = objc.find("objc_msgSend").get()
        val sendForObject = linker.downcallHandle(msgSend, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        val sendLong = linker.downcallHandle(msgSend, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG))
        val sendBool = linker.downcallHandle(msgSend, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_BOOLEAN))

        private val dispatchAsync = linker.downcallHandle(
            system.find("dispatch_async_f").get(),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS)
        )
        private val mainQueue: MemorySegment = system.find("_dispatch_main_q").get()
        private val callback: MemorySegment = linker.upcallStub(
            MethodHandles.lookup().findStatic(
                MacWindows::class.java, "runPending", MethodType.methodType(Void.TYPE, MemorySegment::class.java)
            ),
            FunctionDescriptor.ofVoid(ADDRESS),
            Arena.global()
        )

        fun onMainThread() {
            dispatchAsync.invokeWithArguments(mainQueue, MemorySegment.NULL, callback)
        }
    }

    private val native: Native? by lazy { if (isMac) runCatching { Native() }.onFailure { println("MacWindows unavailable: $it") }.getOrNull() else null }
    private val pending = ConcurrentLinkedQueue<() -> Unit>()

    /** Called by AppKit on its main thread; runs the queued work. */
    @JvmStatic
    fun runPending(@Suppress("UNUSED_PARAMETER") context: MemorySegment) {
        while (true) {
            val work = pending.poll() ?: break
            try { work() } catch (t: Throwable) { println("MacWindows: ${t.message}") }
        }
    }

    private fun onMain(work: Native.() -> Unit) {
        val n = native ?: return
        pending += { n.work() }
        runCatching { n.onMainThread() }.onFailure { println("MacWindows: $it") }
    }

    private fun Native.cString(s: String): MemorySegment = Arena.global().allocateFrom(s)
    private fun Native.sel(name: String) = selector.invokeWithArguments(cString(name)) as MemorySegment
    private fun Native.cls(name: String) = getClass.invokeWithArguments(cString(name)) as MemorySegment
    private fun Native.nsApp() = sendForObject.invokeWithArguments(cls("NSApplication"), sel("sharedApplication")) as MemorySegment

    /** The AppKit window behind a Java window (needs the --add-opens flags in build.gradle.kts). */
    private fun nsWindow(window: java.awt.Window): MemorySegment? = runCatching {
        val accessor = Class.forName("sun.awt.AWTAccessor").getMethod("getComponentAccessor").invoke(null)
        val peer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
            .getMethod("getPeer", Component::class.java).invoke(accessor, window) ?: return null
        val platformWindow = peer.javaClass.getMethod("getPlatformWindow").invoke(peer) ?: return null
        val field = Class.forName("sun.lwawt.macosx.CFRetainedResource").getDeclaredField("ptr").apply { isAccessible = true }
        val ptr = field.getLong(platformWindow)
        if (ptr == 0L) null else MemorySegment.ofAddress(ptr)
    }.onFailure { println("MacWindows: can't reach the window: $it") }.getOrNull()

    /** Keeps [window] above other apps on every Space, including full-screen ones. */
    fun floatEverywhere(window: java.awt.Window) {
        val ns = nsWindow(window) ?: return
        onMain {
            sendLong.invokeWithArguments(ns, sel("setCollectionBehavior:"), CAN_JOIN_ALL_SPACES or FULL_SCREEN_AUXILIARY)
            sendLong.invokeWithArguments(ns, sel("setLevel:"), STATUS_WINDOW_LEVEL)
            sendBool.invokeWithArguments(ns, sel("setHidesOnDeactivate:"), false)
        }
    }

    /** Shows or hides the Dock icon (and the menu bar while the app is in front). */
    fun setDockIconVisible(visible: Boolean) = onMain {
        val app = nsApp()
        sendLong.invokeWithArguments(app, sel("setActivationPolicy:"), if (visible) POLICY_REGULAR else POLICY_ACCESSORY)
        if (visible) sendBool.invokeWithArguments(app, sel("activateIgnoringOtherApps:"), true)
    }

    private const val CAN_JOIN_ALL_SPACES = 1L shl 0
    private const val FULL_SCREEN_AUXILIARY = 1L shl 8
    private const val STATUS_WINDOW_LEVEL = 25L
    private const val POLICY_REGULAR = 0L
    private const val POLICY_ACCESSORY = 1L
}
