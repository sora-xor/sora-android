package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Image
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrievePainter

data class SoraCardIBANCardState(
    val iban: String
) {

    val headlineText: Text = Text.StringRes(
        id = R.string.sora_card_iban_headline
    )

    val actionIcon: Image = Image.ResImage(
        id = R.drawable.ic_rectangular_arrow_up
    )

    val ibanText: Text = Text.SimpleText(
        text = iban
    )
    
}

@Composable
fun SoraCardIBANCard(
    soraCardIBANCardState: SoraCardIBANCardState,
    onActionClick: () -> Unit
) {
    ContentCard(
        cornerRadius = Dimens.x4,
        onClick = remember { { /* DO NOTHING */ } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = Dimens.x2)
        ) {
            Row(
                modifier = Modifier
                    .padding(all = Dimens.x1)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentSize(),
                    text = soraCardIBANCardState.headlineText.retrieveString(),
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary,
                    textAlign = TextAlign.Center
                )
                Icon(
                    modifier = Modifier
                        .clickable { onActionClick.invoke() }
                        .wrapContentSize(),
                    painter = soraCardIBANCardState.actionIcon.retrievePainter(),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.fgSecondary
                )
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = Dimens.x1),
                text = soraCardIBANCardState.ibanText.retrieveString(),
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSoraCardIBANCard() {
    SoraCardIBANCard(
        soraCardIBANCardState = SoraCardIBANCardState(
            iban = "LT61 3250 0467 7252 5583"
        ),
        onActionClick = {}
    )
}