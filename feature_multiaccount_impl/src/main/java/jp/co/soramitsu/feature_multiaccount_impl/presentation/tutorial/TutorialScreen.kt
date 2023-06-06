/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.OutlinedButton
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.extensions.ripple
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@ExperimentalUnitApi
@Composable
internal fun TutorialScreen(
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
                    onGoogleSignin = onGoogleSignin
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
    onRecoveryAccount: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = CenterHorizontally
    ) {
        FilledButton(
            modifier = Modifier
                .testTagAsId("CreateNewAccount")
                .padding(top = Dimens.x1)
                .fillMaxWidth(),
            text = "Google signin",
            onClick = onGoogleSignin,
            size = Size.Large,
            order = Order.PRIMARY
        )


        OutlinedButton(
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
        {},
        {},
        {},
        {}
    )
}
