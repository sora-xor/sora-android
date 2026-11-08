package jp.co.soramitsu.ui_core.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CustomDropMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<MenuItem>,
    clickHandler: (Int) -> Unit,
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            medium = RoundedCornerShape(MaterialTheme.borderRadius.xl)
        )
    ) {
        DropdownMenu(
            modifier = Modifier.background(MaterialTheme.customColors.bgSurface),
            expanded = expanded,
            onDismissRequest = onDismissRequest
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    onClick = { clickHandler(index) }
                ) {
                    Text(
                        text = item.title,
                        color = item.color ?: MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textM
                    )
                }
            }
        }
    }
}

data class MenuItem(val title: String, val color: Color? = null)

@Preview
@Composable
fun previewDropdownMenu() {
    CustomDropMenu(
        expanded = true,
        onDismissRequest = {  },
        items = listOf(
            MenuItem("First"),
            MenuItem("Second", color = MaterialTheme.customColors.statusError)
        ),
        clickHandler = {}
    )
}