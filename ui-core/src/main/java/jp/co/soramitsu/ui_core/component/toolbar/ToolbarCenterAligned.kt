package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.ui.text.AnnotatedString
import jp.co.soramitsu.ui_core.component.text.FittedText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun RowScope.ToolbarCenterAlignedTop(
    state: BasicToolbarState,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    onMenuItemClickedTestTag: String? = null,
) {
    // As in the small toolbar: set smaller before it is cut short.
    FittedText(
        modifier = Modifier
            .wrapContentHeight()
            .weight(1f)
            .padding(start = state.navIcon?.let { Dimens.x2 } ?: 0.dp),
        text = AnnotatedString(state.title()),
        style = MaterialTheme.customTypography.headline2.copy(
            color = MaterialTheme.customColors.fgPrimary,
            textAlign = TextAlign.Center,
        ),
    )

    ToolbarActions(
        modifier = Modifier.wrapContentSize(),
        menu = state.menu,
        onMenuItemClicked = onMenuItemClicked,
        onMenuItemClickedTestTag = onMenuItemClickedTestTag,
    )
}

@Preview
@Composable
private fun PreviewToolbarCenterAligned() {
    val state = BasicToolbarState(
        title = "Title small",
        navIcon = painterResource(R.drawable.ic_burger),
        menu = listOf(
            Action.Info(),
        ),
        actionLabel = "Button",
    )
    val t = SoramitsuToolbarType.SmallCentered()
    val s1 = SoramitsuToolbarState(state, t)
    Column {
        SoramitsuToolbar(s1)
        SoramitsuToolbar(
            state = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = "qwe",
                    navIcon = null,
                ),
                type = t,
            ),
        )
        SoramitsuToolbar(
            state = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = "qwe",
                    navIcon = null,
                    actionLabel = "Action",
                ),
                type = t,
            ),
            onSearch = {},
            onAction = {},
        )
    }
}
