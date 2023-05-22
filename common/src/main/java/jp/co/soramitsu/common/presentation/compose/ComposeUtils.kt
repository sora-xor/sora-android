/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose

import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.ui.text.input.TextFieldValue

fun TextFieldValue?.orEmpty() = this ?: TextFieldValue("")

val previewDrawable: Drawable by lazy { ShapeDrawable(OvalShape()) }
