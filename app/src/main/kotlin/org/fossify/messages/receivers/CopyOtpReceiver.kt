package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.messages.helpers.OTP_CODE

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(OTP_CODE) ?: return
        context.copyToClipboard(code)
    }
}
