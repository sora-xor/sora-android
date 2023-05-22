/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import android.database.Cursor

inline fun <T> Cursor.map(iteration: Cursor.() -> T): List<T> {
    val result = mutableListOf<T>()

    while (moveToNext()) {
        result.add(iteration())
    }

    return result
}
