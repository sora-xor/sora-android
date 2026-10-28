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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import jp.co.soramitsu.ui_core.theme.customTypography
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.neon.safemode.DeviceTransferNotice
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.BasicBannerCard

@Composable
fun BackupCard(
    onStartClicked: () -> Unit,
) {
    jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard {
        Column {
            androidx.compose.material.Text(stringResource(R.string.wallet_backup),
                style = androidx.compose.material.MaterialTheme.customTypography.headline2)
            androidx.compose.material.Text(stringResource(R.string.protect_loss_access_funds),
                modifier = Modifier.padding(vertical = 8.dp),
                style = androidx.compose.material.MaterialTheme.customTypography.textS)
            jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction(
                stringResource(R.string.backup_now), jp.co.soramitsu.common.presentation.compose.neon.NeonVariant.Safe,
                onClick = onStartClicked)
            DeviceTransferNotice(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Preview
@Composable
private fun PreviewBackupCard() {
    BackupCard(
        onStartClicked = {},
    )
}
