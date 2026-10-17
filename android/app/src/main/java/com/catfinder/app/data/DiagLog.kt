package com.catfinder.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log: timestamped lines appended to a file in the app's files dir,
 * so it survives the app being killed. Rotates once at [MAX_BYTES]; the export
 * joins the previous and the current file.
 */
object DiagLog {
    private const val MAX_BYTES = 4L * 1024 * 1024
    private const val TAIL_LINES = 200

    private var file: File? = null
    private var writer: FileWriter? = null
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val recent = ArrayDeque<String>()

    private val _tail = MutableStateFlow<List<String>>(emptyList())
    /** The newest lines, oldest first, for the settings screen. */
    val tail: StateFlow<List<String>> = _tail.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "diag.log")
        writer = FileWriter(file, true)
        line("---- app start")
    }

    @Synchronized
    fun line(text: String) {
        val l = "${format.format(Date())} $text"
        recent.addLast(l)
        while (recent.size > TAIL_LINES) recent.removeFirst()
        _tail.value = recent.toList()
        val f = file ?: return
        runCatching {
            writer?.apply { write(l); write("\n"); flush() }
            if (f.length() > MAX_BYTES) {
                writer?.close()
                f.renameTo(File(f.parentFile, "diag.1.log"))
                writer = FileWriter(f, true)
            }
        }
    }

    @Synchronized
    fun clear() {
        val f = file ?: return
        writer?.close()
        File(f.parentFile, "diag.1.log").delete()
        f.delete()
        writer = FileWriter(f, true)
        recent.clear()
        _tail.value = emptyList()
        line("---- log cleared")
    }

    /** Copies the whole log into [dir] for sharing. */
    @Synchronized
    fun export(dir: File): File {
        val f = file ?: throw IllegalStateException("log not initialised")
        writer?.flush()
        dir.mkdirs()
        val out = File(dir, "catfinder-diag.txt")
        out.outputStream().use { o ->
            for (part in listOf(File(f.parentFile, "diag.1.log"), f)) {
                if (part.exists()) part.inputStream().use { it.copyTo(o) }
            }
        }
        return out
    }
}
