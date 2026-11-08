package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun RowScope.ToolbarCenterImageTop(
    state: BasicToolbarState,
    actionTint: Color = MaterialTheme.customColors.fgPrimary,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    onMenuItemClickedTestTag: String? = null,
) {
    Image(
        modifier = Modifier.weight(1f),
        painter = state.title.toImage(),
        contentDescription = null,
    )
    ToolbarActions(
        menu = state.menu,
        onMenuItemClicked = onMenuItemClicked,
        onMenuItemClickedTestTag = onMenuItemClickedTestTag,
        actionTint = actionTint,
    )
}

@Preview
@Composable
private fun PreviewToolbarCenterAligned() {
    val state = BasicToolbarState(
        title = painterResource(R.drawable.ic_burger),
        navIcon = painterResource(R.drawable.ic_burger),
        menu = listOf(
            Action.Info(),
        ),
        actionLabel = "Button",
        contentPadding = PaddingValues(horizontal = Dimens.x2),
    )
    val t1 = SoramitsuToolbarType.ImageCentered()
    Column {
        SoramitsuToolbar(
            state = SoramitsuToolbarState(state, t1),
            tint = Color(0xdd345678),
        )
        SoramitsuToolbar(
            state = SoramitsuToolbarState(state, t1),
            tint = Color(0xdd345678),
            onSearch = {},
        )
    }
}
