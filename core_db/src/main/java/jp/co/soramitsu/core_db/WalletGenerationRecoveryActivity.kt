package jp.co.soramitsu.core_db

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import jp.co.soramitsu.common.R
import java.text.DateFormat
import java.util.Date

/** A generation is switched only after device authentication and a cold process restart.
 * The current generation is journalled and preserved before the selected one is activated. */
class WalletGenerationRecoveryActivity : Activity() {
    private var selected: String? = null
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        selected = savedInstanceState?.getString("selected")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        layout.addView(TextView(this).apply { setText(R.string.wallet_preserved_data_body) })
        work.launch {
            val inventory = withContext(Dispatchers.IO) {
                val reconciled = WalletUpgradeBackup.reconcileLegacyPreservedGenerations(this@WalletGenerationRecoveryActivity).isSuccess
                val inventory = runCatching { WalletUpgradeBackup.preservedGenerationInventory(this@WalletGenerationRecoveryActivity) }
                    .getOrDefault(WalletUpgradeBackup.PreservedGenerationInventory(emptyList(), true))
                inventory.copy(hasUnavailable = inventory.hasUnavailable || !reconciled)
            }
            if (inventory.hasUnavailable) {
                layout.addView(TextView(this@WalletGenerationRecoveryActivity).apply { setText(R.string.wallet_preserved_data_partial) })
            }
            inventory.available.forEach { generation ->
                layout.addView(Button(this@WalletGenerationRecoveryActivity).apply {
                    text = DateFormat.getDateTimeInstance().format(Date(generation.createdAtMillis)) +
                        if (generation.scope == SetUpAgainScope.DATABASE_ONLY) " · " + getString(R.string.wallet_preserved_database_label) else ""
                    setOnClickListener {
                        AlertDialog.Builder(this@WalletGenerationRecoveryActivity)
                            .setTitle(R.string.wallet_preserved_data)
                            .setMessage(if (generation.scope == SetUpAgainScope.DATABASE_ONLY) R.string.wallet_preserved_database_confirm else R.string.wallet_preserved_data_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.wallet_preserved_data_use) { _, _ -> authenticate(generation.id) }
                            .show()
                    }
                })
            }
        }
        layout.addView(Button(this).apply {
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    private fun authenticate(id: String) {
        selected = id
        val manager = getSystemService(KeyguardManager::class.java)
        val request = manager.createConfirmDeviceCredentialIntent(getString(R.string.wallet_preserved_data), getString(R.string.wallet_preserved_data_confirm))
        if (request == null) showFailure(R.string.wallet_preserved_data_screen_lock)
        else startActivityForResult(request, 41)
    }

    @Deprecated("Android device-credential result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK) { selected = null; return }
        val id = selected ?: return
        selected = null
        work.launch {
            val request = withContext(Dispatchers.IO) {
                WalletUpgradeBackup.requestUsePreservedGeneration(this@WalletGenerationRecoveryActivity, id)
            }
            if (request.isFailure) {
                showFailure(R.string.wallet_preserved_data_failed)
                return@launch
            }
            val launch = packageManager.getLaunchIntentForPackage(packageName)?.component
            if (launch == null) { showFailure(R.string.wallet_preserved_data_failed); return@launch }
            startActivity(Intent.makeRestartActivityTask(launch))
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }

    private fun showFailure(message: Int) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }
}
