/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.highlightWordsCompose
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@ExperimentalUnitApi
@Composable
internal fun ClaimScreen(
    claimState: ClaimState,
    onSubmitClicked: () -> Unit,
    onContactUsClicked: () -> Unit
) {
    Box(
        contentAlignment = Alignment.TopCenter
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.x2,
                    end = Dimens.x2,
                    bottom = Dimens.x2,
                ),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        top = Dimens.x7,
                        bottom = Dimens.x3,
                        start = Dimens.x3,
                        end = Dimens.x3
                    )
                    .fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.claim_welcome_to_v1),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.displayL,
                    color = MaterialTheme.customColors.fgPrimary
                )

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.sora_2),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.displayL,
                    color = MaterialTheme.customColors.accentPrimary
                )
                if (claimState.loading) {
                    Text(
                        modifier = Modifier
                            .padding(top = Dimens.x2)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        text = stringResource(id = R.string.claim_subtitle_confirmed),
                        style = MaterialTheme.customTypography.headline3,
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.x2),
                        textAlign = TextAlign.Center,
                        text = stringResource(id = R.string.claim_subtitle_confirmed_v2),
                        style = MaterialTheme.customTypography.paragraphS,
                    )
                } else {
                    Text(
                        modifier = Modifier
                            .padding(top = Dimens.x2)
                            .fillMaxWidth(),
                        text = stringResource(id = R.string.claim_subtitle_v2),
                        style = MaterialTheme.customTypography.paragraphS,
                    )
                }
                LoaderWrapper(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x3),
                    loading = claimState.loading,
                    loaderSize = Size.Large
                ) { modifier, elevation ->
                    FilledButton(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.x3),
                        size = Size.Large,
                        order = Order.PRIMARY,
                        text = stringResource(id = R.string.common_confirm),
                        onClick = onSubmitClicked
                    )
                }

                val contactsUsAnnotation = "ContactsUs"
                val annotatedLinkString: AnnotatedString = stringResource(id = R.string.claim_contact_us)
                    .highlightWordsCompose(
                        colors = listOf(MaterialTheme.customColors.accentPrimary.toArgb()),
                        clickableAnnotation = listOf(contactsUsAnnotation),
                        underlined = true
                    )

                ClickableText(
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .padding(top = Dimens.x2),
                    style = MaterialTheme.customTypography.textXS.copy(textAlign = TextAlign.Center),
                    text = annotatedLinkString,
                    onClick = {
                        annotatedLinkString.getStringAnnotations(it, it).firstOrNull()?.let {
                            if (it.item == contactsUsAnnotation) {
                                onContactUsClicked()
                            }
                        }
                    }
                )
            }
        }

        Image(
            modifier = Modifier
                .size(112.dp)
                .offset(y = (-56).dp),
            alignment = Alignment.TopCenter,
            painter = painterResource(id = R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
            contentDescription = null,
        )
    }
}
