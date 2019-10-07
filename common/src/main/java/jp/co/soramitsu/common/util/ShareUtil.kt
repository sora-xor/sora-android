/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.content.Intent

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider.getUriForFile
import android.graphics.Bitmap
import jp.co.soramitsu.common.util.ext.saveToTempFile
import java.io.File

object ShareUtil {

    fun openShareDialog(context: AppCompatActivity, title: String, shareBody: String) {
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        context.startActivity(Intent.createChooser(sharingIntent, title))
    }

    fun openShareDialogWithBitmap(context: AppCompatActivity, title: String, shareBody: String, bitmap: Bitmap) {
        val mediaStorageDir: File? = bitmap.saveToTempFile(context)

        if (mediaStorageDir != null) {
            val imageUri = getUriForFile(context, "${context.packageName}.provider", mediaStorageDir)

            if (imageUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, shareBody)
                }

                context.startActivity(Intent.createChooser(intent, title))
            }
        }
    }
}