package jp.co.soramitsu.ui_core.component.searchview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.opacity

/**
 * sora experimental api
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchView(
    modifier: Modifier = Modifier,
    state: SearchViewState,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onAction: (() -> Unit)? = null,
    onFocusChanged: ((FocusState) -> Unit)? = null,
) {
    val enabledBackgroundColor =
        Color(0xff767680).withOpacity(opacity = MaterialTheme.opacity.actionBgPressed)
    val disabledBackgroundColor =
        MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionBgDisabled)
    val disabledColor =
        MaterialTheme.customColors.fgPrimary.withOpacity(MaterialTheme.opacity.actionFgDisabled)
    val backgroundColor = remember(state.enabled) {
        if (state.enabled) {
            enabledBackgroundColor
        } else {
            disabledBackgroundColor
        }
    }
    val textColor = Color(0xff3c3c43).withOpacity(opacity = 0.6f)
    var showClearButton by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    TextField(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.x2))
            .focusRequester(focusRequester)
            .onFocusChanged {
                onFocusChanged?.invoke(it)
                showClearButton = it.isFocused
                if (it.isFocused.not()) {
                    keyboardController?.hide()
                }
            },
        enabled = state.enabled,
        maxLines = 1,
        singleLine = true,
        colors = TextFieldDefaults.textFieldColors(
            textColor = textColor,
            disabledTextColor = disabledColor,
            placeholderColor = textColor,
            disabledPlaceholderColor = disabledColor,
            cursorColor = MaterialTheme.customColors.fgPrimary,
            backgroundColor = backgroundColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.customColors.fgSecondary,
            unfocusedLabelColor = MaterialTheme.customColors.fgSecondary,
            disabledLabelColor = disabledColor,
            leadingIconColor = textColor,
            disabledLeadingIconColor = disabledColor,
            trailingIconColor = textColor,
            disabledTrailingIconColor = disabledColor,
        ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        ),
        value = state.value,
        visualTransformation = visualTransformation,
        onValueChange = {
            onValueChange(it.copy(text = it.text.replace("\n", " ")))
        },
        textStyle = MaterialTheme.customTypography.paragraphM,
        placeholder = {
            state.placeholder?.let { placeholder ->
                Text(
                    text = placeholder,
                    style = MaterialTheme.customTypography.paragraphM
                )
            }
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = showClearButton) {
                IconButton(onClick = { onAction?.invoke() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cross),
                        contentDescription = null
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun PreviewSearchView() {
    val state = remember {
        mutableStateOf(
            SearchViewState(
                placeholder = "Search assets",
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgSurface)
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
    ) {
        SearchView(
            modifier = Modifier.fillMaxWidth(),
            state = state.value,
            onValueChange = {
                state.value = state.value.copy(value = it)
            },
        )

        Button(
            modifier = Modifier.padding(Dimens.x1),
            onClick = {
                state.value = state.value.copy(enabled = !state.value.enabled)
            }
        ) {
            Text("Enable")
        }
    }
}
