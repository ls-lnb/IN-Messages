package org.fossify.messages.helpers

import android.app.backup.BackupManager
import android.content.Context
import org.fossify.commons.extensions.toast
import org.fossify.messages.R

/**
 * Android's own backup, off unless the user asks for it.
 *
 * The manifest keeps `allowBackup` on, but its rules include a single preferences file and
 * nothing else, so nothing leaves the device until that file carries something. Turning the
 * switch on writes this app's settings into it - the same JSON the manual export produces -
 * and asks the backup manager to pick it up; turning it off empties the file again.
 *
 * Only settings travel this way: sender groups, colours, OTP keywords and the rest of the
 * preferences. Messages are left out on purpose, both because they are large and because the
 * manual export already covers them.
 */
object GoogleBackup {
    private const val PREFS_NAME = "backup_opt_in"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PAYLOAD = "payload"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    /** Snapshots the current settings into the backed-up file. */
    fun enable(context: Context) {
        val json = SettingsBackup.exportToJson(context)
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PAYLOAD, json)
            .apply()
        requestBackup(context)
    }

    /** Empties the file, so the next backup pass clears what was stored. */
    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        requestBackup(context)
    }

    /**
     * Applies a restored snapshot, if the file arrived with one. Returns true when settings were
     * written, so the caller can react. The payload is then re-snapshotted from the restored
     * settings, which keeps the stored copy current instead of leaving it consumed.
     */
    fun restoreIfPending(context: Context): Boolean {
        val prefs = prefs(context)
        val json = prefs.getString(KEY_PAYLOAD, null) ?: return false
        val restored = runCatching { SettingsBackup.importFromJson(context, json) }.isSuccess
        if (!restored) {
            prefs.edit().remove(KEY_PAYLOAD).apply()
            return false
        }

        val stillEnabled = prefs.getBoolean(KEY_ENABLED, false)
        if (stillEnabled) {
            enable(context)
        } else {
            prefs.edit().remove(KEY_PAYLOAD).apply()
        }
        context.toast(R.string.settings_restored_from_backup)
        return true
    }

    private fun requestBackup(context: Context) {
        runCatching { BackupManager(context).dataChanged() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
