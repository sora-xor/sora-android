package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.extensions.testTagAsId
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

private const val ANIMATION_DURATION = 250

@Composable
internal fun Any.toTitle(): String {
    return if (this is String) this else stringResource(this as Int)
}

@Composable
internal fun Any.toImage(): Painter {
    return if (this is Painter) this else painterResource(this as Int)
}

@Composable
internal fun BasicToolbarState.title(): String {
    return if (titleArgs == null) {
        title.toTitle()
    } else {
        stringResource(id = title as Int, *titleArgs)
    }
}

data class BasicToolbarState(
    val title: Any,
    val titleArgs: Array<Any>? = null,
    val navIcon: Any?,
    val actionLabel: Any? = null,
    val menu: List<Action>? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.x1),
    val visibility: Boolean = true,
    val searchValue: String? = null,
    val searchPlaceholder: Any? = null,
    val searchEnabled: Boolean = false,
)

data class SoramitsuToolbarState(
    val basic: BasicToolbarState,
    val type: SoramitsuToolbarType,
)

sealed class SoramitsuToolbarType(
    val height: Dp,
) {
    class Small : SoramitsuToolbarType(Dimens.x8)

    class Medium : SoramitsuToolbarType(112.dp)

    class Large : SoramitsuToolbarType(152.dp)

    class SmallCentered : SoramitsuToolbarType(Dimens.x8)

    class ImageCentered : SoramitsuToolbarType(Dimens.x8)
}

@Composable
fun SoramitsuToolbar(
    state: SoramitsuToolbarState,
    backgroundColor: Color = MaterialTheme.customColors.bgPage,
    tint: Color = MaterialTheme.customColors.fgPrimary,
    elevation: Dp = AppBarDefaults.TopAppBarElevation,
    onNavigate: (() -> Unit)? = null,
    onNavigateTestTag: String? = null,
    onAction: (() -> Unit)? = null,
    onActionTestTag: String? = null,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    onMenuItemClickedTestTag: String? = null,
    onSearch: ((String) -> Unit)? = null,
    onSearchTestTag: String? = null,
) {
    if (state.basic.visibility) {
        TopAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(state.type.height),
            backgroundColor = backgroundColor,
            elevation = elevation,
            contentPadding = state.basic.contentPadding,
        ) {
            BasicBar(
                state = state,
                onNavigate = onNavigate,
                onNavigateTestTag = onNavigateTestTag,
                topRowContent = {
                    when (state.type) {
                        is SoramitsuToolbarType.ImageCentered -> {
                            ToolbarCenterImageTop(
                                state = state.basic,
                                tint,
                                onMenuItemClicked,
                                onMenuItemClickedTestTag
                            )
                        }

                        is SoramitsuToolbarType.Large -> {
                            ToolbarLargeTop(
                                state = state.basic,
                                onAction = onAction,
                                onMenuItemClicked = onMenuItemClicked,
                                onMenuItemClickedTestTag = onMenuItemClickedTestTag
                            )
                        }

                        is SoramitsuToolbarType.Medium -> {
                            ToolbarMediumTop(
                                modifier = it,
                                state = state.basic,
                                onAction = onAction,
                                onActionTestTag = onActionTestTag,
                                onMenuItemClicked = onMenuItemClicked,
                                onMenuItemClickedTestTag = onMenuItemClickedTestTag
                            )
                        }

                        is SoramitsuToolbarType.Small -> {
                            ToolbarSmallTop(
                                modifier = it,
                                state = state.basic,
                                onAction,
                                onActionTestTag,
                                onMenuItemClicked,
                                onMenuItemClickedTestTag
                            )
                        }

                        is SoramitsuToolbarType.SmallCentered -> {
                            ToolbarCenterAlignedTop(
                                state = state.basic,
                                onMenuItemClicked,
                                onMenuItemClickedTestTag
                            )
                        }
                    }
                },
                bottomRowContent = {
                    when (state.type) {
                        is SoramitsuToolbarType.Medium -> {
                            ToolbarMediumBottom(state = state.basic)
                        }

                        is SoramitsuToolbarType.Large -> {
                            ToolbarLargeBottom(state = state.basic)
                        }

                        else -> {}
                    }
                },
                onSearch = onSearch,
                onSearchTestTag = onSearchTestTag,
                actionTint = tint,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
private fun BasicBar(
    state: SoramitsuToolbarState,
    onNavigate: (() -> Unit)? = null,
    onNavigateTestTag: String? = null,
    topRowContent: @Composable (Modifier) -> Unit,
    bottomRowContent: @Composable() ((Modifier) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onSearchTestTag: String? = null,
    actionTint: Color = MaterialTheme.customColors.fgPrimary,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
    ) {
        val searchMode = remember(state.basic.searchEnabled) {
            mutableStateOf(state.basic.searchValue.isNullOrEmpty().not())
        }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current

        val searchValue = remember { mutableStateOf(state.basic.searchValue.orEmpty()) }

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth(),
            visible = searchMode.value.not() || state.basic.searchEnabled.not(),
            enter = fadeIn(tween(ANIMATION_DURATION, ANIMATION_DURATION)),
            exit = fadeOut(tween(ANIMATION_DURATION)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val navIconLocal = state.basic.navIcon
                if (navIconLocal != null && onNavigate != null) {
                    IconButton(
                        modifier = Modifier
                            .testTagAsId(onNavigateTestTag)
                            .size(40.dp),
                        onClick = onNavigate,
                    ) {
                        Icon(
                            painter = navIconLocal.toImage(),
                            contentDescription = null,
                            tint = MaterialTheme.customColors.fgPrimary,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(Dimens.x1)
                    )
                }

                topRowContent(
                    Modifier
                        .weight(1f)
                        .wrapContentHeight()
                )
                if (onSearch != null && state.basic.searchEnabled) {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = { searchMode.value = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = actionTint,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth(),
            visible = searchMode.value && state.basic.searchEnabled,
            enter = fadeIn(tween(ANIMATION_DURATION, ANIMATION_DURATION)),
            exit = fadeOut(tween(ANIMATION_DURATION)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        searchMode.value = false
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_left),
                        contentDescription = null,
                        tint = MaterialTheme.customColors.fgPrimary,
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                val colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.customColors.fgPrimary,
                    placeholderColor = MaterialTheme.customColors.fgSecondary,
                    cursorColor = MaterialTheme.customColors.fgPrimary,
                    focusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
                    unfocusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
                    backgroundColor = Color.Transparent,
                )
                BasicTextField(
                    modifier = Modifier
                        .testTagAsId(onSearchTestTag)
                        .weight(1f)
                        .padding(end = Dimens.x1)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused.not()) keyboardController?.hide()
                        }
                        .indicatorLine(
                            enabled = true,
                            isError = false,
                            interactionSource,
                            colors,
                            focusedIndicatorLineThickness = 1.dp,
                            unfocusedIndicatorLineThickness = 1.dp
                        ),
                    value = searchValue.value,
                    onValueChange = {
                        searchValue.value = it
                        onSearch?.invoke(it)
                    },
                    singleLine = true,
                    maxLines = 1,
                    textStyle = MaterialTheme.customTypography.textL.copy(color = MaterialTheme.customColors.fgPrimary),
                    cursorBrush = SolidColor(MaterialTheme.customColors.fgPrimary),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource
                ) { innerTextField ->
                    TextFieldDefaults.TextFieldDecorationBox(
                        value = searchValue.value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        placeholder = {
                            Text(
                                text = state.basic.searchPlaceholder?.toTitle() ?: "",
                                modifier = Modifier.wrapContentHeight(),
                                style = MaterialTheme.customTypography.paragraphS,
                                color = MaterialTheme.customColors.fgTertiary,
                                maxLines = 1,
                            )
                        },
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                IconButton(
                    onClick = {
                        searchValue.value = ""
                        onSearch?.invoke("")
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cross_24),
                        contentDescription = null,
                        tint = MaterialTheme.customColors.fgPrimary,
                    )
                }
            }
        }
        if (bottomRowContent != null) {
            bottomRowContent(Modifier.fillMaxWidth())
        }
    }
}
