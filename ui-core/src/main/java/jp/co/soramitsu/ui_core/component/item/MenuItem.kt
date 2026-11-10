package jp.co.soramitsu.ui_core.component.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun MenuItem(
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Dimens.x2, horizontal = Dimens.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        icon?.let {
            Icon(
                modifier = Modifier
                    .size(Dimens.IconSizeLarge)
                    .padding(end = Dimens.x1),
                painter = icon,
                contentDescription = null
            )
        }

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.x1),
            text = label,
            style = MaterialTheme.customTypography.textM,
            color = MaterialTheme.customColors.fgPrimary
        )

        Icon(
            modifier = Modifier
                .size(Dimens.IconSizeLarge),
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.customColors.fgSecondary
        )
    }
}

@Composable
@Preview
private fun PreviewMenuItem() {
    Column(
        modifier = Modifier
            .padding(14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.borderRadius.s))
            .background(MaterialTheme.customColors.bgSurface)
    ) {
        MenuItem(label = "General Terms of Use", onClick = {})
        MenuItem(label = "General Terms of Use", onClick = {})
        MenuItem(label = "General Terms of Use", onClick = {})
    }
}
