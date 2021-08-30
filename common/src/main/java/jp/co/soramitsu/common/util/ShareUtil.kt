package jp.co.soramitsu.common.util

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

object ShareUtil {

    fun openShareDialog(context: AppCompatActivity, title: String, shareBody: String) {
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        context.startActivity(Intent.createChooser(sharingIntent, title))
    }
}
