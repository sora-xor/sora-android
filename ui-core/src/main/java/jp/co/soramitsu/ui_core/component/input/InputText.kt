package jp.co.soramitsu.ui_core.component.input

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.toolbar.toTitle
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.darkColors
import jp.co.soramitsu.ui_core.theme.defaultCustomTypography
import jp.co.soramitsu.ui_core.theme.lightColors
import jp.co.soramitsu.ui_core.theme.opacity

@Composable
fun InputText(
    modifier: Modifier = Modifier,
    state: InputTextState,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = false,
    onAction: (() -> Unit)? = null,
    focusRequester: FocusRequester = FocusRequester(),
    onFocusChanged: ((FocusState) -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    val enabledBackgroundColor = MaterialTheme.customColors.bgSurface
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
    val extraColors = InputTextDefaults.inputTextBorderColors(
        borderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.customColors.fgPrimary,
        unfocusedBorderColor = MaterialTheme.customColors.fgOutline,
        errorBorderColor = MaterialTheme.customColors.statusError,
        successBorderColor = MaterialTheme.customColors.statusSuccess,
        descriptionTextColor = MaterialTheme.customColors.fgSecondary,
        disabledDescriptionTextColor = disabledColor,
        errorDescriptionTextColor = MaterialTheme.customColors.statusError,
        successDescriptionTextColor = MaterialTheme.customColors.statusSuccess
    )

    val focused = remember { mutableStateOf(false) }

    Column {
        TextField(
            modifier = modifier
                .defaultMinSize(minHeight = Dimens.InputHeight)
                .clip(RoundedCornerShape(MaterialTheme.borderRadius.ml))
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused.value = it.isFocused
                    onFocusChanged?.invoke(it)
                }
                .border(
                    border = BorderStroke(1.dp, extraColors.borderColor(state, focused.value)),
                    shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                ),
            enabled = state.enabled,
            maxLines = maxLines,
            singleLine = singleLine,
            colors = TextFieldDefaults.textFieldColors(
                textColor = MaterialTheme.customColors.fgPrimary,
                disabledTextColor = disabledColor,
                placeholderColor = MaterialTheme.customColors.fgPrimary,
                disabledPlaceholderColor = disabledColor,
                cursorColor = MaterialTheme.customColors.fgPrimary,
                backgroundColor = backgroundColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.customColors.fgSecondary,
                unfocusedLabelColor = MaterialTheme.customColors.fgSecondary,
                disabledLabelColor = disabledColor,
                leadingIconColor = MaterialTheme.customColors.fgSecondary,
                disabledLeadingIconColor = disabledColor,
                trailingIconColor = MaterialTheme.customColors.fgSecondary,
                disabledTrailingIconColor = disabledColor,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            value = state.value,
            visualTransformation = visualTransformation,
            onValueChange = {
                if (singleLine) {
                    onValueChange(it.copy(text = it.text.replace("\n", " ")))
                } else {
                    onValueChange(it)
                }
            },
            placeholder = {
                state.placeholder?.let { placeholder ->
                    Text(
                        text = placeholder,
                        style = MaterialTheme.customTypography.textM
                    )
                }
            },
            label = state.label?.let { string ->
                {
                    Text(
                        text = string.toTitle()
                    )
                }
            },
            leadingIcon = state.leadingIcon?.let { icon ->
                {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null
                    )
                }
            },
            trailingIcon = state.trailingIcon?.let { icon ->
                {
                    Icon(
                        modifier = Modifier
                            .padding(end = Dimens.x1)
                            .clickable {
                                onAction?.invoke()
                            },
                        painter = painterResource(icon),
                        contentDescription = null
                    )
                }
            }
        )

        state.descriptionText?.let { text ->
            Text(
                modifier = Modifier.padding(vertical = Dimens.x1_2, horizontal = Dimens.x2),
                text = text.toTitle(),
                color = extraColors.descriptionTextColor(state),
                style = MaterialTheme.customTypography.textXS
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewInputText() {
    AppTheme(
        lightColors = lightColors(),
        darkColors = darkColors(),
        typography = defaultCustomTypography(),
    ) {
        val state = remember {
            mutableStateOf(
                InputTextState(
                    descriptionText = "Description Text",
                    label = "Label",
                    placeholder = "Input Text",
                    leadingIcon = R.drawable.ic_plus,
                    trailingIcon = R.drawable.ic_plus,
                )
            )
        }
        val focusRequester = remember { mutableStateOf(FocusRequester()) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.customColors.bgPage)
                .clickable {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
        ) {
            InputText(
                modifier = Modifier.fillMaxWidth(),
                state = state.value,
                onValueChange = {
                    state.value = state.value.copy(value = it)
                },
                focusRequester = focusRequester.value,
                onFocusChanged = {

                },
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                maxLines = 5,
                singleLine = false
            )

            Row {
                Button(
                    modifier = Modifier.padding(Dimens.x1),
                    onClick = {
                        state.value = state.value.copy(enabled = !state.value.enabled)
                    }
                ) {
                    Text("Enable")
                }

                Button(
                    modifier = Modifier.padding(Dimens.x1),
                    onClick = {

                    }
                ) {
                    Text("Focus")
                }

                Button(
                    modifier = Modifier.padding(Dimens.x1),
                    onClick = {
                        state.value = state.value.copy(error = !state.value.error)
                    }
                ) {
                    Text("Error")
                }

                Button(
                    modifier = Modifier.padding(Dimens.x1),
                    onClick = {
                        state.value = state.value.copy(success = !state.value.success)
                    }
                ) {
                    Text("Success")
                }
            }
        }
    }
}
