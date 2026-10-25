package com.roger.circuitcaller.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Installs a downloaded APK over this app with the PackageInstaller session API. */
object UpdateInstaller {

    /** Where cached downloads live; cleared on every app start. */
    fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    /** False until the user allows "Install unknown apps" for Circuit Caller. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Writes [apk] into a new session and commits it. Android then asks the user to confirm
     * through [InstallResultReceiver]; on success the app process is replaced.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver(context, sessionId).intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    private fun statusReceiver(context: Context, sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
        // Must be mutable: the installer adds the status and confirmation intent as extras.
        // With FLAG_IMMUTABLE the confirmation prompt never appears. Below API 31 every
        // PendingIntent is mutable and FLAG_MUTABLE doesn't exist.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}

/** Install failures, for the UI to show as a snackbar. Success needs no message: the app restarts. */
object InstallEvents {
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun reportFailure(message: String) {
        _failure.value = message
    }

    fun consumeFailure() {
        _failure.value = null
    }
}

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    InstallEvents.reportFailure("Android didn't show the update prompt.")
                } else {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallEvents.reportFailure("Update cancelled")

            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                InstallEvents.reportFailure(
                    "Update failed" + if (detail.isNullOrBlank()) " (status $status)" else ": $detail"
                )
            }
        }
    }
}
