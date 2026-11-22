package jp.co.soramitsu.ui_core.component.card

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.litPlate
import jp.co.soramitsu.ui_core.modifier.applyIf
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MaterialTheme.borderRadius.ml,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // A sheet of frosted glass with small corners: a custom colour is a sheet of that colour.
    val radius = cornerRadius.coerceAtMost(Lit.SheetRadius)
    val shape = RoundedCornerShape(radius)
    val plain = backgroundColor == MaterialTheme.customColors.bgSurface
    CompositionLocalProvider(LocalContentColor provides contentColorFor(backgroundColor)) {
        Box(
            modifier = modifier
                .litPlate(shape, face = backgroundColor.takeUnless { plain })
                .clip(shape)
                .applyIf(onClick != null) {
                    clickable(onClick = onClick!!)
                },
            propagateMinConstraints = true,
        ) {
            Box(modifier = Modifier.padding(innerPadding)) {
                content()
            }
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
fun PreviewCardWithShadow01() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.customColors.bgPage)
            .padding(24.dp)
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            backgroundColor = Color.Green,
            innerPadding = PaddingValues(30.dp)
        ) {
            Text(
                text = "Card with shadow\nText",
                style = MaterialTheme.customTypography.headline2
            )
        }
        Spacer(modifier = Modifier.size(30.dp))
        Text(
            text = "Card with shadow\nText",
            style = MaterialTheme.customTypography.headline2
        )
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun PreviewCardWithShadow02() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.customColors.bgPage)
            .padding(24.dp)
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            innerPadding = PaddingValues(30.dp)
        ) {
            Text(
                text = "Card with shadow\nText",
                style = MaterialTheme.customTypography.headline2
            )
        }
        Spacer(modifier = Modifier.size(30.dp))
        Text(
            text = "Card with shadow\nText",
            style = MaterialTheme.customTypography.headline2
        )
    }
}
