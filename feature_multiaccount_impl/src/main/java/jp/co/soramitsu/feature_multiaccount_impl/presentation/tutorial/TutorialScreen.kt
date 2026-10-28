/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.TutorialScreenState
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.common.presentation.compose.neon.OutlinedButton
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@ExperimentalUnitApi
@Composable
fun TutorialScreen(
    state: TutorialScreenState,
    onCreateAccount: () -> Unit,
    onImportAccount: () -> Unit,
    onGoogleSignin: () -> Unit,
    onTermsAndPrivacyClicked: (TermsAndPrivacyEnum) -> Unit,
    onFoundWallets: () -> Unit = {},
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
                    text = stringResource(id = R.string.neon_welcome_headline),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.displayL,
                    color = MaterialTheme.customColors.fgPrimary
                )


                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.tutorial_many_world_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )

                TutorialButtons(
                    modifier = Modifier.padding(top = Dimens.x3),
                    onCreateAccount = onCreateAccount,
                    onRecoveryAccount = onImportAccount,
                    onGoogleSignin = onGoogleSignin,
                    isGoogleSignInLoading = state.isGoogleSigninLoading,
                    isGoogleBackupAvailable = state.isGoogleBackupAvailable,
                    foundWalletsCount = state.foundWalletsCount,
                    onFoundWallets = onFoundWallets,
                )

                val annotatedLinkString: AnnotatedString = buildAnnotatedString {
                    val startingString =
                        stringResource(id = R.string.tutorial_terms_and_conditions_template_1) + "\n"
                    append(startingString)
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.customColors.fgPrimary,
                            textDecoration = TextDecoration.None
                        ),
                        start = 0, end = startingString.length
                    )

                    val termsWord = stringResource(id = R.string.tutorial_terms_and_conditions_3)
                    val startIndex = startingString.length
                    val endIndex = startIndex + termsWord.length
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.neon.link,
                            textDecoration = TextDecoration.Underline
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
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.customColors.fgPrimary,
                            textDecoration = TextDecoration.None
                        ),
                        start = endIndex, end = endIndex + andWord.length
                    )

                    val privacyWord = stringResource(id = R.string.tutorial_privacy_policy)
                    val privacyWordStartIndex =
                        startingString.length + termsWord.length + andWord.length
                    val privacyWordEndIndex =
                        startingString.length + termsWord.length + andWord.length + privacyWord.length
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.neon.link,
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
                        .padding(top = Dimens.x4).heightIn(min = 48.dp),
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

        NeumorphicSurface(
            shape = CircleShape,
            interactionSource = remember { MutableInteractionSource() },
            modifier = Modifier.size(112.dp).offset(y = (-56).dp)
                .border(2.dp, MaterialTheme.neon.brandRed, CircleShape),
        ) {
            Image(modifier = Modifier.size(80.dp),
                painter = painterResource(R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
                contentDescription = null)
        }
    }
}

@Composable
private fun TutorialButtons(
    modifier: Modifier = Modifier,
    onCreateAccount: () -> Unit,
    onGoogleSignin: () -> Unit,
    onRecoveryAccount: () -> Unit,
    isGoogleSignInLoading: Boolean = false,
    isGoogleBackupAvailable: Boolean = true,
    foundWalletsCount: Int = 0,
    onFoundWallets: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = CenterHorizontally
    ) {
        jp.co.soramitsu.common.presentation.compose.components.PreservedWalletDataButton()
        if (foundWalletsCount > 0) {
            // Keys of earlier wallets are still on this phone behind the PIN that was used here.
            // Offered first: it brings them back as they are, without typing a phrase.
            OutlinedButton(
                modifier = Modifier
                    .testTagAsId("FoundWallets")
                    .padding(top = Dimens.x1)
                    .fillMaxWidth(),
                text = stringResource(R.string.safe_mode_retained_title),
                onClick = onFoundWallets,
                size = Size.Large,
                order = Order.PRIMARY
            )
            ChoiceExplanation(R.string.onboarding_found_wallets_explanation)
        }

        NeonButton(
            modifier = Modifier.testTagAsId("CreateNewAccount").padding(top = Dimens.x1).fillMaxWidth(),
            text = stringResource(R.string.create_account_title),
            onClick = onCreateAccount, size = Size.Large, variant = NeonVariant.Primary,
            maxLines = 3, pulse = true,
        )
        ChoiceExplanation(R.string.wallet_create_explanation)
        NeonButton(
            modifier = Modifier.testTagAsId("ImportAccount").padding(top = Dimens.x1).fillMaxWidth(),
            text = stringResource(R.string.recovery_title),
            onClick = onRecoveryAccount, size = Size.Large, variant = NeonVariant.Secondary, maxLines = 3,
        )
        ChoiceExplanation(R.string.wallet_import_explanation)
        if (isGoogleBackupAvailable) {
            NeonButton(
                modifier = Modifier.testTagAsId("GoogleSignin").padding(top = Dimens.x1).fillMaxWidth(),
                text = stringResource(R.string.neon_restore_google),
                onClick = onGoogleSignin, size = Size.Large, variant = NeonVariant.Tertiary,
                maxLines = 3, enabled = !isGoogleSignInLoading, loading = isGoogleSignInLoading,
            )
            ChoiceExplanation(R.string.wallet_google_explanation)
        }

    }
}

@Composable
private fun ChoiceExplanation(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.customTypography.paragraphS,
        color = MaterialTheme.customColors.fgPrimary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.x1, bottom = Dimens.x2))
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
