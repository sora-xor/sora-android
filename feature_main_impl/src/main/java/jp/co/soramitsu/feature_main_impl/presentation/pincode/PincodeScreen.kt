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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.base.ProgressDialog
import jp.co.soramitsu.common.util.ext.shake
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun PincodeScreen(
    pinCodeScreenState: PinCodeScreenState,
    onNumClick: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onWrongPinAnimationEnd: () -> Unit,
    onSwitchNode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.customColors.bgPage)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!pinCodeScreenState.isLengthInfoAlertVisible) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f, true),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(bottom = Dimens.x2),
                    textAlign = TextAlign.Center,
                    text = pinCodeScreenState.toolbarTitleString,
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline2
                )

                DotsProgress(
                    modifier = Modifier.shake(
                        enabled = pinCodeScreenState.enableShakeAnimation,
                        onAnimationEnd = onWrongPinAnimationEnd
                    ),
                    maxDotsCount = pinCodeScreenState.maxDotsCount,
                    checkedDotsCount = pinCodeScreenState.checkedDotsCount
                )
            }
            PincodeInputView(
                onNumClick = onNumClick,
                isDeleteVisible = pinCodeScreenState.isBackButtonVisible,
                onDeleteClick = onDeleteClick,
                isBiometricVisible = pinCodeScreenState.isBiometricButtonVisible,
                onBiometricClick = onBiometricClick,
            )
        }
        if (pinCodeScreenState.migrating && pinCodeScreenState.isConnected) {
            ProgressDialog()
        }
        if (pinCodeScreenState.migrating && !pinCodeScreenState.isConnected) {
            PinCodeNodeConnection(onSwitchNode)
        }
    }
}

@Composable
private fun DotsProgress(modifier: Modifier = Modifier, maxDotsCount: Int, checkedDotsCount: Int) {
    Row(
        modifier = modifier
    ) {
        repeat(checkedDotsCount) {
            Dot(true)
        }
        repeat(maxDotsCount - checkedDotsCount) {
            Dot(false)
        }
    }
}

@Composable
private fun Dot(isChecked: Boolean = false) {
    Icon(
        modifier = Modifier
            .padding(horizontal = Dimens.x1_2)
            .size(Dimens.x3),
        painter = painterResource(id = R.drawable.ic_dot_unchecked),
        tint = if (isChecked) {
            MaterialTheme.customColors.accentPrimary
        } else {
            MaterialTheme.customColors.accentSecondaryContainer
        },
        contentDescription = ""
    )
}

@Composable
private fun PincodeInputView(
    modifier: Modifier = Modifier,
    onNumClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    isDeleteVisible: Boolean = false,
    onBiometricClick: () -> Unit,
    isBiometricVisible: Boolean = false
) {
    Column(
        modifier = modifier.padding(bottom = Dimens.x4),
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(modifier = Modifier.padding(vertical = Dimens.x1)) {
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "1", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "2", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "3", onNumClick)
        }
        Row(modifier = Modifier.padding(vertical = Dimens.x1)) {
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "4", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "5", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "6", onNumClick)
        }
        Row(modifier = Modifier.padding(vertical = Dimens.x1)) {
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "7", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "8", onNumClick)
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "9", onNumClick)
        }
        Row(modifier = Modifier.padding(vertical = Dimens.x1)) {
            if (isBiometricVisible) {
                PinIconButton(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x1)
                        .testTagAsId("Biometric"),
                    R.drawable.ic_pin_biometric,
                    onBiometricClick
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x1)
                        .size(Dimens.x9)
                )
            }
            PinTextButton(modifier = Modifier.padding(horizontal = Dimens.x1), "0", onNumClick)
            if (isDeleteVisible) {
                PinIconButton(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x1)
                        .testTagAsId("Biometric"),
                    R.drawable.ic_neu_chevron_back,
                    onDeleteClick
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x1)
                        .size(Dimens.x9)
                )
            }
        }
    }
}

@Composable
private fun PinTextButton(modifier: Modifier = Modifier, text: String, onClick: (String) -> Unit) {
    Button(
        modifier = modifier
            .size(Dimens.x9)
            .testTagAsId(text),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.customColors.bgSurface),
        shape = CircleShape,
        onClick = { onClick(text) }
    ) {
        Text(
            text = text,
            color = MaterialTheme.customColors.fgSecondary,
            style = MaterialTheme.customTypography.displayL
        )
    }
}

@Composable
private fun PinIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    onIconClicked: () -> Unit
) {
    Button(
        modifier = modifier.size(Dimens.x9),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.customColors.bgSurface),
        shape = CircleShape,
        onClick = onIconClicked
    ) {
        Icon(
            painter = painterResource(id = icon),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = ""
        )
    }
}

@Preview
@Composable
fun PincodeScreenPreview() {
    PincodeScreen(
        PinCodeScreenState(
            toolbarTitleString = "Enter your pincode",
            checkedDotsCount = 3,
            migrating = false,
            isConnected = false,
        ),
        {},
        {},
        {},
        {},
        {},
    )
}
