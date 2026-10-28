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

package jp.co.soramitsu.common.util.ext

fun Char.isUnicodeMark(): Boolean {
    val type = Character.getType(this).toByte()
    return type == Character.ENCLOSING_MARK || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
}

fun Char.isValidNameChar(): Boolean {
    return this.isUnicodeMark() || this.isLetter() || this.isEmoji() || this.isDigit()
}

fun Char.isEmoji(): Boolean {
    val type = Character.getType(this).toByte()
    return type == Character.SURROGATE || type == Character.NON_SPACING_MARK || type == Character.OTHER_SYMBOL
}
