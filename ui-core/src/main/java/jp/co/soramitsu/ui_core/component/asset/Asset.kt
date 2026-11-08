package jp.co.soramitsu.ui_core.component.asset

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun String.changePriceColor() = when {
    startsWith("+") -> MaterialTheme.customColors.statusSuccess
    startsWith("-") -> MaterialTheme.customColors.statusError
    else -> MaterialTheme.customColors.fgPrimary
}

@Composable
fun Asset(
    modifier: Modifier = Modifier,
    icon: Uri,
    name: String,
    balance: String,
    symbol: String,
    onClick: () -> Unit,
    fiat: String? = null,
    change: String? = null
) {
    AssetInfo(
        modifier,
        icon,
        name,
        balance,
        symbol,
        onClick = onClick,
        rightContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                fiat?.let {
                    Text(
                        text = fiat,
                        style = MaterialTheme.customTypography.textM,
                        color = MaterialTheme.customColors.fgPrimary
                    )
                }

                change?.let {
                    Text(
                        modifier = Modifier.padding(start = 1.dp).wrapContentSize(),
                        textAlign = TextAlign.End,
                        text = change,
                        style = MaterialTheme.customTypography.textXSBold,
                        color = it.changePriceColor()
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun PreviewAsset() {
    Surface(
        modifier = Modifier.background(MaterialTheme.customColors.bgPage)
    ) {
        Asset(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            icon = Uri.parse("file:///android_asset/ic_0x0033.png"),
//            icon = Uri.parse("file:///android_asset/ic_0x004c.svg"),
            name = "SORA Synthetic USD",
            balance = "1,697.98",
            symbol = "XSTUSD",
            fiat = "$1,677.98",
            change = "0.07 %",
            onClick = {}
        )
    }
}
