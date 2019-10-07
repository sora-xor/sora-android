/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.date

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateFormatter {

    companion object {

        val YYYY_DD_MM_HH_MM_SS = "yyyy-MM-dd HH:mm:ss"
        val DD_MM_YYYY = "dd.MM.yyyy"
        val DD_MMMM = "dd MMMM"

        @JvmStatic
        fun format(date: Date, dateFormat: String): String {
            val format = SimpleDateFormat(dateFormat, Locale.ENGLISH)
            return format.format(date)
        }
    }
}