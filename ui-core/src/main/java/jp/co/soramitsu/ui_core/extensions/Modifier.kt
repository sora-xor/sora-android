package jp.co.soramitsu.ui_core.extensions

import android.annotation.SuppressLint
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.testTagAsId(tagWithPackageId: String?): Modifier {
    return tagWithPackageId?.let {
        this
            .semantics {
                testTagsAsResourceId = true
            }
            .testTag(tagWithPackageId)
    } ?: this
}