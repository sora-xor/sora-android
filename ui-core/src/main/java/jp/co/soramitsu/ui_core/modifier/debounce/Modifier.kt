package jp.co.soramitsu.ui_core.modifier.debounce

import android.util.Log
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

fun Modifier.onClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    debounceHandler: DebounceHandler,
    onClick: () -> Unit,
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
        properties["debounceHandler"] = debounceHandler
    }
) {
    Modifier.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = {
            debounceHandler.debounceClicks {
                onClick.invoke()
            }
        },
        role = role,
        indication = LocalIndication.current,
        interactionSource = remember { MutableInteractionSource() }
    )
}

@OptIn(DelicateCoroutinesApi::class)
@Preview
@Composable
private fun DebounceClicker() {
    val debounceHandler = remember {
        DebounceHandler(GlobalScope, debounce = 300)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.x2),
    ) {
        Text(
            modifier = Modifier
                .onClick(
                    onClick = {
                        Log.i("DEBOUNCE", "Text was clicked")
                    },
                    debounceHandler = DebounceHandler(GlobalScope, debounce = 300)
                )
                .padding(Dimens.x1),
            style = MaterialTheme.customTypography.buttonM,
            text = "Text"
        )

        Button(
            modifier = Modifier.padding(Dimens.x1),
            onClick = {
                debounceHandler.debounceClicks {
                    Log.i("DEBOUNCE", "Button was clicked")
                }
            }
        ) {
            Text(
                style = MaterialTheme.customTypography.buttonM,
                text = "Button"
            )
        }
    }
}
