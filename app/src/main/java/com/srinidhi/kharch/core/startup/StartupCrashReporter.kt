package com.srinidhi.kharch.core.startup

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * On-device-only crash reporter for the "app doesn't open" case.
 *
 * P1: nothing leaves the device. No Crashlytics, no Analytics, no upload.
 * The stack trace is written to a private file (`filesDir/startup-crash.txt`)
 * that the user can copy from Settings → Data, or read via `adb`.
 * No `android.util.Log` is used (detekt forbids it).
 */
object StartupCrashReporter {

    private const val FILE_NAME = "startup-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter()
                PrintWriter(trace).use { writer -> throwable.printStackTrace(writer) }
                val report = buildString {
                    appendLine("Kharch startup crash report")
                    appendLine("time=" + Instant.now().toString())
                    appendLine("thread=" + thread.name)
                    appendLine("----")
                    append(trace.toString())
                }
                File(appContext.filesDir, FILE_NAME).writeText(report)
            }
            previous?.uncaughtException(thread, throwable) ?: run {
                // No previous handler — rethrow so the process still crashes
                // (the report above is already persisted).
                throw throwable
            }
        }
    }

    /** Reads the persisted report, or null if there is no recorded crash. */
    fun readLastReport(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }
}
