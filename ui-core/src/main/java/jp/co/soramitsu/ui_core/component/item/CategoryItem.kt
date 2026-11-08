package jp.co.soramitsu.ui_core.component.item

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.modifier.applyIf
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CategoryItem(
    modifier: Modifier = Modifier,
    title: String,
    cornerRadius: Dp = 36.dp,
    elevation: Dp = 32.dp,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.customColors.fgSecondary,
    subtitleIcon: Int? = null,
    icon: Int? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .applyIf(!isSystemInDarkTheme()) {
                shadow(
                    elevation = elevation,
                    ambientColor = Color(0xFF999999),
                    spotColor = Color(0xFF999999),
                    shape = RoundedCornerShape(cornerRadius)
                )
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.customColors.bgSurface)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Dimens.x2, horizontal = Dimens.x3)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.customTypography.headline2,
                color = MaterialTheme.customColors.fgPrimary
            )

            subtitle?.let {
                Row(
                    modifier = Modifier.padding(top = Dimens.x1_2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    subtitleIcon?.let {
                        Icon(
                            modifier = Modifier.padding(end = Dimens.x1_2),
                            painter = painterResource(subtitleIcon),
                            tint = Color.Unspecified,
                            contentDescription = null,
                        )
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.customTypography.textSBold,
                        color = subtitleColor,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }

        icon?.let {
            Icon(
                modifier = Modifier.padding(end = Dimens.x3),
                painter = painterResource(icon),
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = null
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewCategoryItem() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage)
            .padding(Dimens.x2),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            CategoryItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x1),
                title = "Account Settings",
                subtitle = "Manage accounts, backup passphrase",
                subtitleIcon = R.drawable.ic_indicator,
                icon = R.drawable.ic_plus,
                onClick = {}
            )

            CategoryItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x1),
                title = "App Settings",
                subtitle = "Nodes; Language; Appearance",
                icon = R.drawable.ic_plus,
                onClick = {}
            )

            CategoryItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.x1),
                title = "Login & Security",
                subtitle = "PIN and Biometric auth PIN and Biometric auth",
                subtitleColor = MaterialTheme.customColors.statusError,
                icon = R.drawable.ic_plus,
                onClick = {}
            )
        }
    }
}
