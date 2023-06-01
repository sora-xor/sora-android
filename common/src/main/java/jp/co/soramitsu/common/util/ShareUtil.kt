/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.util.ext.safeStartActivity

object ShareUtil {

    private const val mimeText = "text/plain"

    fun Context.openAppSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    fun shareText(c: Context, title: String, body: String) {
        val intent = ShareCompat.IntentBuilder(c)
            .setType(mimeText)
            .setText(body)
            .setChooserTitle(title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(intent, title))
    }

    fun shareFile(context: Context, title: String, file: Uri) {
        val mime = context.contentResolver.getType(file)
        val intent = ShareCompat.IntentBuilder(context)
            .setType(mime)
            .setStream(file)
            .setChooserTitle(title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareImageFile(context: Context, title: String, file: Uri, description: String) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType("image/*")
            .setStream(file)
            .setText(description)
            .setChooserTitle(title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun sendEmail(context: Context, targetEmail: String, title: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            putExtra(Intent.EXTRA_EMAIL, targetEmail)
            type = "message/rfc822"
            data = Uri.parse("mailto:$targetEmail")
        }
        context.startActivity(Intent.createChooser(emailIntent, title))
    }

    fun shareInBrowser(fragment: Fragment, link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
        fragment.safeStartActivity(intent, ResponseCode.NOW_BROWSER_FOUND)
    }

    fun shareInBrowser(context: Context, link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
        context.safeStartActivity(intent, ResponseCode.NOW_BROWSER_FOUND)
    }
}
