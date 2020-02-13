package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.feature_wallet_impl.R

class ChooserDialog(
    context: Context,
    titleResource: Int,
    elementsResource: Int,
    chooseCameraClickListener: () -> Unit,
    chooseGalleryClickListener: () -> Unit
) {

    private val instance: Dialog

    init {
        instance = AlertDialog.Builder(context)
            .setTitle(titleResource)
            .setItems(R.array.contacts_scan_qr_variants) { _, item ->
                when (item) {
                    0 -> chooseCameraClickListener()
                    1 -> chooseGalleryClickListener()
                }
            }
            .setCancelable(true)
            .create()
    }

    fun show() {
        instance.show()
    }

    fun dismiss() {
        instance.dismiss()
    }
}