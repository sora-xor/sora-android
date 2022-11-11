/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.components.ProgressContainedButton
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.NeuColorsCompat
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuMedium15
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular16
import jp.co.soramitsu.feature_referral_impl.R
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ReferralMainBottomSheet(
    referralProgramState: ReferralProgramState,
    viewModel: ReferralViewModel,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val (sheet, sheetMethod) = remember { mutableStateOf(DetailedBottomSheet.REQUEST_REFERRER) }

    fun openSheet(state: DetailedBottomSheet) {
        coroutineScope.launch {
            sheetMethod.invoke(state)
            viewModel.onSheetOpen(state)
            modalBottomSheetState.show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.hideSheet.onEach {
            if (modalBottomSheetState.isVisible) modalBottomSheetState.hide()
        }.collect()
    }
    ModalBottomSheetLayout(
        sheetState = modalBottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        sheetBackgroundColor = NeuColorsCompat.neuBackgroundAbove,
        sheetContent = {
            when (sheet) {
                DetailedBottomSheet.SHOW_REFERRER -> {
                    referralProgramState.common.referrer?.let { referrer ->
                        SheetContentReferralCommon(
                            text = stringResource(id = R.string.referral_your_referrer),
                            content = { SheetContentReferrer(referrer) }
                        )
                    }
                }
                DetailedBottomSheet.REQUEST_REFERRER -> {
                    SheetContentReferralCommon(
                        text = stringResource(id = R.string.referral_enter_link_title),
                        content = {
                            SheetContentReferrerRequest(
                                activate = referralProgramState.common.activate,
                                progress = referralProgramState.common.progress,
                                onLinkChange = { viewModel.onLinkChange(it) },
                                onLinkActivate = { viewModel.onActivateLinkClick(it) },
                            )
                        }
                    )
                }
                DetailedBottomSheet.BOND -> {
                    SheetContentReferralCommon(
                        text = stringResource(id = R.string.referral_start_inviting),
                        content = {
                            SheetContentBondXor(
                                commonState = referralProgramState.common,
                                state = referralProgramState.bondState,
                                onBondInvitationsCountChange = { viewModel.onBondValueChange(it) },
                                onBondMinus = { viewModel.onBondMinus() },
                                onBondPlus = { viewModel.onBondPlus() },
                                onBondClick = { viewModel.onBondButtonClick() }
                            )
                        }
                    )
                }
                DetailedBottomSheet.UNBOND -> {
                    SheetContentReferralCommon(
                        text = stringResource(id = R.string.referral_unbond_button_title),
                        content = {
                            SheetContentUnbondXor(
                                commonState = referralProgramState.common,
                                state = referralProgramState.bondState,
                                onUnbondInvitationsCountChange = {
                                    viewModel.onUnbondValueChange(it)
                                },
                                onUnbondMinus = { viewModel.onUnbondMinus() },
                                onUnbondPlus = { viewModel.onUnbondPlus() },
                                onUnbondClick = { viewModel.onUnbondButtonClick() }
                            )
                        }
                    )
                }
            }
        }
    ) {
        when (referralProgramState.screen) {
            is ReferralProgramStateScreen.Initial -> {
                ReferralStartScreen(
                    state = referralProgramState,
                    onFirstClick = {
                        coroutineScope.launch {
                            openSheet(DetailedBottomSheet.BOND)
                        }
                    },
                    onSecondClick = {
                        coroutineScope.launch {
                            openSheet(if (referralProgramState.common.referrer != null) DetailedBottomSheet.SHOW_REFERRER else DetailedBottomSheet.REQUEST_REFERRER)
                        }
                    }
                )
            }
            is ReferralProgramStateScreen.ReferralProgramData -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(ThemeColors.Background)
                        .padding(bottom = Dimens.x4),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvailableInvitations(
                        invitations = referralProgramState.screen.invitations,
                        link = referralProgramState.screen.link,
                        bonded = referralProgramState.screen.bonded,
                        onBondClick = {
                            coroutineScope.launch {
                                openSheet(DetailedBottomSheet.BOND)
                            }
                        },
                        onUnbondClick = {
                            coroutineScope.launch {
                                openSheet(DetailedBottomSheet.UNBOND)
                            }
                        },
                        onShareLinkClick = { viewModel.onShareLink() }
                    )
                    YourReferrer(referralProgramState.common.referrer) {
                        coroutineScope.launch {
                            openSheet(DetailedBottomSheet.REQUEST_REFERRER)
                        }
                    }
                    YourReferralsCard(
                        referralsModel = referralProgramState.screen.referrals,
                        onHeaderClick = { viewModel.toggleReferralsCard() }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SheetContentReferrerRequest(
    activate: Boolean,
    progress: Boolean,
    onLinkChange: (String) -> Unit,
    onLinkActivate: (String) -> Unit,
) {
    val link = remember { mutableStateOf("") }
    Text(
        text = stringResource(id = R.string.referral_referrer_description),
        style = neuMedium15,
        modifier = Modifier.padding(bottom = 16.dp),
        color = NeuColorsCompat.color281818
    )
    NeuCardPressed(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        TextField(
            value = link.value,
            onValueChange = {
                link.value = it
                onLinkChange.invoke(it)
            },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.referral_referral_link),
                    style = neuRegular16,
                    maxLines = 1,
                    color = NeuColorsCompat.neuTintLight
                )
            },
            modifier = Modifier.fillMaxSize(),
            textStyle = neuRegular16,
            singleLine = true,
            maxLines = 1,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
    ProgressContainedButton(
        label = stringResource(id = R.string.referral_activate_button_title),
        modifier = Modifier.padding(top = 16.dp),
        enabled = activate,
        progress = progress,
    ) {
        onLinkActivate.invoke(link.value)
    }
}

@Preview
@Composable
private fun SheetContentReferrerRequestPreview() {
    SheetContentReferrerRequest(activate = false, progress = false, {}, {})
}
