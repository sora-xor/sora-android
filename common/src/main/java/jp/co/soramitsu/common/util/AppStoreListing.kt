/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.util.ext.safeStartActivity

/**
 * Where the user gets a newer version of this app. Used where the app has to say "please update
 * the app", so that the sentence comes with a button that leads there.
 */
object AppStoreListing {

    /** The store app's page for [packageName]. */
    fun storeUri(packageName: String): Uri = "market://details?id=$packageName".toUri()

    /** The same page on the web, for a phone without a store app. */
    fun webUri(packageName: String): Uri =
        "https://play.google.com/store/apps/details?id=$packageName".toUri()

    /**
     * Opens this app's page in the store app, or in the browser when the phone has no store
     * app. Nothing is sent anywhere but the package name, which is public.
     */
    fun open(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(viewIntent(storeUri(packageName)))
        } catch (_: ActivityNotFoundException) {
            context.safeStartActivity(
                viewIntent(webUri(packageName)),
                ResponseCode.NOW_BROWSER_FOUND,
            )
        }
    }

    private fun viewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
