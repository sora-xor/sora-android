package jp.co.soramitsu.ui_core.component.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.modifier.applyIf
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun ContentCardEndless(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MaterialTheme.borderRadius.xl,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.material.Card(
        modifier = modifier
            .applyIf(onClick != null) {
                clickable { onClick?.invoke() }
            },
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        elevation = if (isSystemInDarkTheme()) 0.dp else 10.dp,
        backgroundColor = MaterialTheme.customColors.bgSurface,
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    Box(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        ContentCardEndless(
            modifier = Modifier.padding(8.dp),
            innerPadding = PaddingValues(18.dp),
        ) {
            Text(
                text = "df\nfgfg",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}