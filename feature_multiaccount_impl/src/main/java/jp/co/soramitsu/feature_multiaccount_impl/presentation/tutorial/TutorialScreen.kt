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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.TutorialScreenState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.OutlinedButton
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@ExperimentalUnitApi
@Composable
internal fun TutorialScreen(
    state: TutorialScreenState,
    onCreateAccount: () -> Unit,
    onImportAccount: () -> Unit,
    onGoogleSignin: () -> Unit,
    onTermsAndPrivacyClicked: (TermsAndPrivacyEnum) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxHeight(),
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
                    text = stringResource(id = R.string.tutorial_many_world),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.displayL,
                    color = MaterialTheme.customColors.fgPrimary
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.x2),
                    text = stringResource(id = R.string.asset_sora_fullname),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.displayL,
                    color = MaterialTheme.customColors.accentPrimary
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.tutorial_many_world_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.paragraphS,
                )

                TutorialButtons(
                    modifier = Modifier.padding(top = Dimens.x3),
                    onCreateAccount = onCreateAccount,
                    onRecoveryAccount = onImportAccount,
                    onGoogleSignin = onGoogleSignin,
                    isGoogleSignInLoading = state.isGoogleSigninLoading
                )

                val annotatedLinkString: AnnotatedString = buildAnnotatedString {
                    val startingString =
                        stringResource(id = R.string.tutorial_terms_and_conditions_template_1) + "\n"
                    append(startingString)

                    val termsWord = stringResource(id = R.string.tutorial_terms_and_conditions_3)
                    val startIndex = startingString.length
                    val endIndex = startIndex + termsWord.length
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.customColors.accentPrimary,
                            textDecoration = TextDecoration.None
                        ),
                        start = startIndex, end = endIndex
                    )

                    addStringAnnotation(
                        tag = "",
                        annotation = TermsAndPrivacyEnum.TERMS.toString(),
                        start = startIndex,
                        end = endIndex
                    )

                    append(termsWord)

                    val andWord = " ${stringResource(id = R.string.common_and)} "
                    append(andWord)

                    val privacyWord = stringResource(id = R.string.tutorial_privacy_policy)
                    val privacyWordStartIndex =
                        startingString.length + termsWord.length + andWord.length
                    val privacyWordEndIndex =
                        startingString.length + termsWord.length + andWord.length + privacyWord.length
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.customColors.accentPrimary,
                            textDecoration = TextDecoration.None
                        ),
                        start = privacyWordStartIndex, end = privacyWordEndIndex
                    )

                    addStringAnnotation(
                        tag = "",
                        annotation = TermsAndPrivacyEnum.PRIVACY.toString(),
                        start = privacyWordStartIndex,
                        end = privacyWordEndIndex
                    )

                    append(privacyWord)
                }

                ClickableText(
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .padding(top = Dimens.x4),
                    style = MaterialTheme.customTypography.textXS.copy(textAlign = TextAlign.Center),
                    text = annotatedLinkString,
                    onClick = {
                        annotatedLinkString.getStringAnnotations(it, it).firstOrNull()?.let {
                            onTermsAndPrivacyClicked(TermsAndPrivacyEnum.valueOf(it.item))
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

@Composable
private fun TutorialButtons(
    modifier: Modifier = Modifier,
    onCreateAccount: () -> Unit,
    onGoogleSignin: () -> Unit,
    onRecoveryAccount: () -> Unit,
    isGoogleSignInLoading: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = CenterHorizontally
    ) {
//        LoaderWrapper(
//            modifier = Modifier
//                .padding(top = Dimens.x1)
//                .fillMaxWidth(),
//            loading = isGoogleSignInLoading,
//            loaderSize = Size.Large,
//        ) { modifier, elevation ->
//            Button(
//                modifier = modifier
//                    .testTagAsId("GoogleSignin")
//                    .height(Dimens.x7)
//                    .fillMaxWidth(),
//                colors = ButtonDefaults.buttonColors(
//                    backgroundColor = Color(0xFF3579F7),
//                    contentColor = Color.White,
//                ),
//                shape = RoundedCornerShape(MaterialTheme.borderRadius.ml),
//                onClick = onGoogleSignin,
//            ) {
//                Image(
//                    modifier = Modifier.padding(end = Dimens.x1),
//                    painter = painterResource(id = R.drawable.ic_google_white),
//                    contentDescription = stringResource(id = R.string.onboarding_continue_with_google)
//                )
//
//                Text(
//                    style = MaterialTheme.customTypography.buttonM,
//                    text = stringResource(id = R.string.onboarding_continue_with_google)
//                )
//            }
//        }

        FilledButton(
            modifier = Modifier
                .testTagAsId("CreateNewAccount")
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = stringResource(R.string.create_account_title),
            onClick = onCreateAccount,
            size = Size.Large,
            order = Order.PRIMARY
        )

        TextButton(
            modifier = Modifier
                .testTagAsId("ImportAccount")
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = stringResource(R.string.recovery_title),
            onClick = onRecoveryAccount,
            size = Size.Large,
            order = Order.PRIMARY
        )
    }
}

@OptIn(ExperimentalUnitApi::class)
@Preview
@Composable
fun PreviewTutorialScreen() {
    TutorialScreen(
        state = TutorialScreenState(),
        {},
        {},
        {},
        {}
    )
}
