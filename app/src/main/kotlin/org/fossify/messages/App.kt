package org.fossify.messages

import android.app.Activity
import android.app.Application
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.dismissOpenToolbarPopups
import org.fossify.messages.helpers.GoogleBackup
import org.fossify.messages.extensions.rescheduleAllScheduledMessages
import org.fossify.messages.helpers.APP_SIDELOADING_STATUS_OK
import org.fossify.messages.helpers.MessagingCache

class App : FossifyApp() {
    private companion object {
        const val CRASH_TAG = "MessagesCrash"
    }
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        // a snapshot restored by Android's backup carries the user's settings; it has to be
        // applied before anything else reads them
        GoogleBackup.restoreIfPending(this)
        installCrashReporter()
        installPopupDismissal()
        // This fork is not Fossify's signed build, so commons' canary check would report it as a
        // corrupt version and send the user to Fossify's download page. Marking the build as
        // verified short-circuits that check, in the splash screen and everywhere else.
        config.appSideloadingStatus = APP_SIDELOADING_STATUS_OK
        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            listOf(
                ContactsContract.Contacts.CONTENT_URI,
                ContactsContract.Data.CONTENT_URI,
                ContactsContract.DisplayPhoto.CONTENT_URI
            ).forEach {
                try {
                    contentResolver.registerContentObserver(it, true, contactsObserver)
                } catch (_: Exception) {
                }
            }
        }

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
    }

    /**
     * The toolbar overflow popup outlives the activity while it is only paused, so it is still on
     * screen when the app is reopened later. Closing it here keeps every screen honest without
     * each activity having to remember.
     */
    private fun installPopupDismissal() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) = activity.dismissOpenToolbarPopups()

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * A crashing app that only shows the system dialog is unreadable for anyone debugging over a
     * screen recording, so the exception is also toasted and written to a log file first.
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val detail = "${throwable.javaClass.name}: ${throwable.message}"
            Log.e(CRASH_TAG, detail, throwable)
            runCatching { writeCrashReport(detail, throwable) }
            Handler(Looper.getMainLooper()).post { toast(detail, Toast.LENGTH_LONG) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(detail: String, throwable: Throwable) {
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "last-crash.txt")
        file.writeText("$detail\n\n${throwable.stackTraceToString()}")
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }
}
