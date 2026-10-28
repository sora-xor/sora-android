package jp.co.soramitsu.common.presentation.compose.components

import android.content.Intent
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import java.io.File

/** Opens the local, authenticated generation switcher from onboarding and settings. */
@Composable
fun PreservedWalletDataButton() {
    val context = LocalContext.current
    val available = remember(context) {
        runCatching {
            context.noBackupFilesDir.listFiles()?.any {
                it.name.startsWith("wallet-quarantine-") && it.name.contains("-set-up-again")
            } ?: false
        }.getOrDefault(true)
    }
    if (available) {
        TextButton(onClick = {
            context.startActivity(Intent().setClassName(context.packageName, "jp.co.soramitsu.core_db.WalletGenerationRecoveryActivity"))
        }) {
            Text(stringResource(R.string.wallet_preserved_data))
        }
    }
}
