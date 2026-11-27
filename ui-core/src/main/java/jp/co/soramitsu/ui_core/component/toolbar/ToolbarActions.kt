package jp.co.soramitsu.ui_core.component.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.extensions.testTagAsId
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ToolbarActions(
    modifier: Modifier = Modifier,
    actionLabel: Any? = null,
    onAction: (() -> Unit)? = null,
    onActionTestTag: String? = null,
    menu: List<Action>? = null,
    onMenuItemClicked: ((Action) -> Unit)? = null,
    actionTint: Color = MaterialTheme.customColors.fgPrimary,
    onMenuItemClickedTestTag: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier.padding(horizontal = Dimens.x1_2)
            ) {
                ToolbarActionButton(
                    modifier = Modifier
                        .testTagAsId(onActionTestTag)
                        .padding(horizontal = Dimens.x1),
                    label = actionLabel.toTitle(),
                    onClick = onAction
                )
            }
        }

        if (menu != null) {
            menu.forEach { action ->
                IconButton(
                    modifier = Modifier.size(40.dp).testTagAsId(onMenuItemClickedTestTag),
                    onClick = { onMenuItemClicked?.invoke(action) }
                ) {
                    Icon(
                        painter = painterResource(action.icon),
                        contentDescription = null,
                        tint = actionTint,
                    )
                }
                Spacer(modifier = Modifier.size(Dimens.x1))
            }
        } else if (actionLabel == null) {
            Box(
                modifier = Modifier.size(Dimens.x6)
            )
        }
    }
}

@Composable
fun ToolbarActionButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(
            modifier = Modifier.padding(Dimens.x1),
            text = label,
            style = MaterialTheme.customTypography.buttonM,
            color = MaterialTheme.customColors.accentPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun PreviewToolbarActions() {
    Box(
        modifier = Modifier.background(MaterialTheme.customColors.bgPage),
    ) {
        ToolbarActions(
            actionLabel = "Button",
            onAction = {},
            menu = listOf(
                Action.Info(),
                Action.Share(),
                Action.Scan(),
            ),
            onMenuItemClicked = {},
            onMenuItemClickedTestTag = "",
        )
    }
}
