/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment

fun Fragment.showBrowser(link: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
    startActivity(intent)
}