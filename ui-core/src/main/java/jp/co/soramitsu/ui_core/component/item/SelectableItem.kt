package jp.co.soramitsu.ui_core.component.item

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.menu.CustomDropMenu
import jp.co.soramitsu.ui_core.component.menu.MenuItem
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun SelectableItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    leftIcon: Drawable? = null,
    title: String,
    subtitle: String,
    isMenuIconVisible: Boolean,
    onClick: () -> Unit,
    menuItems: List<MenuItem>,
    onMenuItemClick: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Dimens.x3),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_rounded),
                    tint = MaterialTheme.customColors.statusSuccess,
                    contentDescription = null
                )
            }
        }

        if (leftIcon != null) {
            Image(
                modifier = Modifier
                    .padding(start = Dimens.x2),
                bitmap = leftIcon.toBitmap().asImageBitmap(),
                contentDescription = null
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.x2)
        ) {
            Text(
                text = title,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isMenuIconVisible) {
            val expanded = remember { mutableStateOf(false) }

            Box {
                IconButton(onClick = { expanded.value = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_vertical),
                        tint = MaterialTheme.customColors.accentTertiary,
                        contentDescription = null
                    )
                }

                CustomDropMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false },
                    items = menuItems,
                    clickHandler = {
                        expanded.value = false
                        onMenuItemClick(it)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun previewNodeItem() {
    SelectableItem(
        title = "SORA based on SORA Parliament Ministry of Finance",
        subtitle = "wss://ws.mof.sora.org",
        isMenuIconVisible = true,
        isSelected = true,
        onClick = {},
        menuItems = emptyList(),
        onMenuItemClick = {}
        )
}