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

import java.util.Calendar

object DateTimeUtils {
    fun isSameMonth(d1: Long, d2: Long): Boolean {
        val date1 = Calendar.getInstance().apply { timeInMillis = d1 }
        val date2 = Calendar.getInstance().apply { timeInMillis = d2 }
        return isSameMonth(date1, date2)
    }
    fun isSameDay(d1: Long, d2: Long): Boolean {
        val date1 = Calendar.getInstance().apply { timeInMillis = d1 }
        val date2 = Calendar.getInstance().apply { timeInMillis = d2 }
        return isSameMonth(date1, date2) &&
            date1.get(Calendar.DAY_OF_MONTH) == date2.get(Calendar.DAY_OF_MONTH)
    }

    private fun isSameMonth(date1: Calendar, date2: Calendar): Boolean =
        date1.get(Calendar.ERA) == date2.get(Calendar.ERA) &&
            date1.get(Calendar.YEAR) == date2.get(Calendar.YEAR) &&
            date1.get(Calendar.MONTH) == date2.get(Calendar.MONTH)
}
