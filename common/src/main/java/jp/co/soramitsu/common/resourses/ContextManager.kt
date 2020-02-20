/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.content.Context
import java.util.Locale

interface ContextManager {

    fun getContext(): Context

    fun setLocale(context: Context): Context

    fun getLocale(): Locale

    fun getLanguages(): LanguagesHolder
}