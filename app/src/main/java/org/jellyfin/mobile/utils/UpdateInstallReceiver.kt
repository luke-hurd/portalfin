package org.jellyfin.mobile.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import timber.log.Timber

/**
 * Receives the real result of a [PackageInstaller] self-update session (see
 * [AppUpdater]). This is what lets us show an accurate message instead of relying
 * on the system installer screen, which on the Portal falsely reports "App not
 * installed" even when the update succeeded.
 *
 * Three outcomes:
 *  - PENDING_USER_ACTION: Android wants the user to confirm — we launch its intent.
 *  - SUCCESS: toast confirming the update.
 *  - everything else: failure toast with the reported reason.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't launch install confirmation")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> toast(context, "portalfin updated. Reopen to use the new version.")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.w("Update install failed: status=$status message=$message")
                toast(context, "Update failed${message?.let { ": $it" } ?: ""}")
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "org.jellyfin.mobile.action.INSTALL_STATUS"
    }
}
