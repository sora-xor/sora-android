package jp.co.soramitsu.ui_core.component.asset

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.component.text.FittedText
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
    change: String? = null,
    tokenBadge: (@Composable () -> Unit)? = null,
) {
    AssetInfo(
        modifier,
        icon,
        name,
        balance,
        symbol,
        onClick = onClick,
        tokenBadge = tokenBadge,
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

/**
 * A row for something held: the amount held ("12.5 XOR") is the row's figure, on top at the end in the larger type;
 * its value and 24 h change are the small line under it, and are left out when there are none (no price, a testnet).
 * [subtitle] is the line under the name: the symbol and any notes on the holding.
 */
@Composable
fun AssetHolding(
    modifier: Modifier = Modifier,
    icon: Uri,
    name: String,
    subtitle: String,
    amount: String,
    onClick: () -> Unit,
    fiat: String = "",
    change: String = "",
    tokenBadge: (@Composable () -> Unit)? = null,
) {
    AssetInfo(
        modifier,
        icon,
        name,
        balance = "",
        symbol = subtitle,
        onClick = onClick,
        tokenBadge = tokenBadge,
        rightContent = {
            // The amount may take up to two thirds of the room beside the icon: past that the name keeps the rest, and
            // the amount is set smaller and then wraps, never cut.
            Column(
                modifier = Modifier.weight(2f, fill = false),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                FittedText(
                    text = AnnotatedString(amount),
                    style = MaterialTheme.customTypography.textM.copy(
                        color = MaterialTheme.customColors.fgPrimary,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = AMOUNT_LINES,
                )
                val value = listOf(fiat, change).filter(String::isNotBlank)
                if (value.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fiat.isNotBlank()) {
                            Text(
                                text = fiat,
                                style = MaterialTheme.customTypography.textXSBold,
                                color = MaterialTheme.customColors.fgSecondary,
                            )
                        }
                        if (value.size == 2) Spacer(Modifier.width(4.dp))
                        if (change.isNotBlank()) {
                            Text(
                                text = change,
                                style = MaterialTheme.customTypography.textXSBold,
                                color = change.changePriceColor(),
                            )
                        }
                    }
                }
            }
        }
    )
}

/** An amount sets smaller first, then takes as many lines as this before anything could be cut. */
private const val AMOUNT_LINES = 3

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
