package jp.co.soramitsu.common.util

import androidx.compose.ui.Modifier
import jp.co.soramitsu.androidfoundation.compose.testTagAsId

const val PACKAGE_ID = "jp.co.soramitsu.sora.develop"

fun Modifier.testTagAsId(tag: String): Modifier = this.testTagAsId("$PACKAGE_ID:id", tag)
