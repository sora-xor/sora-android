package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ToolbarLargeBottom(
    modifier: Modifier = Modifier,
    state: BasicToolbarState,
) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = Dimens.x4,
                bottom = Dimens.x2,
                start = Dimens.x2,
            ),
        text = state.title(),
        style = MaterialTheme.customTypography.displayL,
        color = MaterialTheme.customColors.fgPrimary,
    )
}

@Composable
internal fun ToolbarLargeTop(
    modifier: Modifier = Modifier,
    state: BasicToolbarState,
    onAction: (() -> Unit)? = null,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    onMenuItemClickedTestTag: String? = null,
    ) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarActions(
            actionLabel = state.actionLabel,
            onAction = onAction,
            menu = state.menu,
            onMenuItemClicked = onMenuItemClicked,
            onMenuItemClickedTestTag = onMenuItemClickedTestTag,
        )
    }
}

@Preview
@Composable
private fun PreviewToolbarLarge() {
    val state = BasicToolbarState(
        title = "Title small",
        navIcon = painterResource(R.drawable.ic_burger),
        menu = listOf(
            Action.Info(),
        ),
        actionLabel = "Button",
    )
    val t1 = SoramitsuToolbarType.Large()
    SoramitsuToolbar(
        SoramitsuToolbarState(state, t1),
        onAction = {},
    )
}
