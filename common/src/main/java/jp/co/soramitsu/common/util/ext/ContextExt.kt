package jp.co.soramitsu.common.util.ext

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.createSendEmailIntent(targetEmail: String, title: String) {
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        putExtra(Intent.EXTRA_EMAIL, targetEmail)
        type = "message/rfc822"
        data = Uri.parse("mailto:$targetEmail")
    }
    startActivity(Intent.createChooser(emailIntent, title))
}