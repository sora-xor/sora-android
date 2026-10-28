/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.util.ext.safeStartActivity

object ShareUtil {

    fun shareInBrowser(fragment: Fragment, link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
        fragment.safeStartActivity(intent, ResponseCode.NOW_BROWSER_FOUND)
    }

    fun shareInBrowser(context: Context, link: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
        context.safeStartActivity(intent, ResponseCode.NOW_BROWSER_FOUND)
    }
}
