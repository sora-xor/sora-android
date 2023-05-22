/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.AccountWithIcon
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun ContactsScreen(
    inputTextState: InputTextState,
    onValueChanged: (TextFieldValue) -> Unit,
    hint: Int,
    accounts: List<ContactsListItem>,
    isSearchEntered: Boolean = false,
    isMyAddress: Boolean,
    onScanClick: () -> Unit,
    onCloseSearchClick: () -> Unit,
    onAccountClick: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    InputText(
        modifier = Modifier
            .focusRequester(focusRequester = focusRequester)
            .background(
                color = MaterialTheme.customColors.bgSurface,
                shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
            )
            .fillMaxWidth()
            .wrapContentHeight(),
        maxLines = 1,
        singleLine = true,
        state = inputTextState,
        onValueChange = onValueChanged,
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        onAction = if (isSearchEntered) {
            onCloseSearchClick
        } else {
            onScanClick
        },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
    )
    AnimatedVisibility(visible = isMyAddress) {
        Text(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(horizontal = Dimens.x2),
            text = stringResource(id = R.string.invoice_scan_error_match),
            style = MaterialTheme.customTypography.textXS,
            color = MaterialTheme.customColors.statusError,
        )
    }
    Divider(thickness = Dimens.x2, color = Color.Transparent)
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(scroll)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                text = stringResource(id = hint).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
            )
            Divider(thickness = Dimens.x2, color = Color.Transparent)
            accounts.forEachIndexed { index, item ->
                AccountWithIcon(
                    address = item.account,
                    accountIcon = item.icon,
                    onClick = { onAccountClick.invoke(item.account) },
                    onLongClick = {}
                )
                if (index < accounts.lastIndex) {
                    Divider(thickness = Dimens.x2, color = Color.Transparent)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewContactsScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(12.dp)
    ) {
        ContactsScreen(
            inputTextState = InputTextState(
                value = TextFieldValue("cnVko"),
                label = "Search or add a new address",
                trailingIcon = R.drawable.ic_scan_wrapped,
            ),
            onValueChanged = {},
            hint = R.string.address_not_found_1,
            isMyAddress = true,
            accounts = listOf(
                ContactsListItem(
                    "ansklksjalfk",
                    previewDrawable,
                ),
                ContactsListItem(
                    "moribm",
                    previewDrawable,
                ),
                ContactsListItem(
                    "anskwoiefonjsnvlsdkskjvnskjdnlsnvkjsnvjflnvkjnwndvklksjalfk",
                    previewDrawable,
                ),
            ),
            onScanClick = {},
            onCloseSearchClick = {},
            onAccountClick = {},
        )
    }
}
