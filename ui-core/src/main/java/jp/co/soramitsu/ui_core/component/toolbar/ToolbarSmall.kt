package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ToolbarSmallTop(
    modifier: Modifier = Modifier,
    state: BasicToolbarState,
    onAction: (() -> Unit)? = null,
    onActionTestTag: String? = null,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    onMenuItemClickedTestTag: String? = null,
    ) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f),
            text = state.title(),
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        ToolbarActions(
            modifier = Modifier.wrapContentSize(),
            actionLabel = state.actionLabel,
            onAction = onAction,
            onActionTestTag = onActionTestTag,
            menu = state.menu,
            onMenuItemClicked = onMenuItemClicked,
            onMenuItemClickedTestTag = onMenuItemClickedTestTag,
        )
    }
}

@Preview
@Composable
private fun PreviewToolbarSmallTop() {
    ToolbarSmallTop(
        state = BasicToolbarState(
            title = "qwe",
            navIcon = null,
            actionLabel = "Button",
            menu = listOf(Action.Info()),
        ),
        onAction = {},
    )
}

@Preview
@Composable
private fun PreviewToolbarSmall() {
    val state1 = BasicToolbarState(
        title = "Title small",
        navIcon = painterResource(R.drawable.ic_burger),
        menu = listOf(
            Action.Info(),
        ),
        actionLabel = "Button",
        contentPadding = PaddingValues(horizontal = Dimens.x2),
    )
    val t = SoramitsuToolbarType.Small()
    val state2 = BasicToolbarState(
        title = "Title small foo bar text more letters",
        navIcon = null,
        menu = listOf(
            Action.Info(),
            Action.Close(),
            Action.Plus(),
        ),
        actionLabel = "Button",
        searchValue = "qwe",
    )
    val s1 = SoramitsuToolbarState(state1, t)
    val s2 = SoramitsuToolbarState(
        state2, t
    )
    Column {
        SoramitsuToolbar(
            state = s1,
            backgroundColor = Color(0xaabb22ee),
        )
        SoramitsuToolbar(
            state = s2,
            backgroundColor = Color(0xaa1122ee),
            onAction = {},
        )
        SoramitsuToolbar(
            state = s1,
            backgroundColor = Color(0xaabb22ee),
            onSearch = {},
        )
        SoramitsuToolbar(
            state = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = "Title",
                    actionLabel = "Action button",
                    navIcon = null,
                ),
                type = SoramitsuToolbarType.Small(),
            ),
            onAction = {},
        )
        SoramitsuToolbar(
            state = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = "Title",
                    actionLabel = "Action button",
                    navIcon = null,
                ),
                type = SoramitsuToolbarType.Small(),
            ),
            onAction = {},
        )
    }
}
