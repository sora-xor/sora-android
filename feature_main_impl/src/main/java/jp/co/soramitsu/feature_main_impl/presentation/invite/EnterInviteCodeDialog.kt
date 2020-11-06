package jp.co.soramitsu.feature_main_impl.presentation.invite

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.feature_main_impl.R
import kotlinx.android.synthetic.main.dialog_enter_invite_code.view.invitationEt

class EnterInviteCodeDialog(
    context: Context,
    inviteCodeEnteredCallback: (String) -> Unit
) {

    private val dialog: AlertDialog

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_enter_invite_code, null)

        dialog = AlertDialog.Builder(context).setView(view)
            .setTitle(R.string.invite_enter_invitation_code)
            .setNegativeButton(R.string.common_cancel) { _, _ ->
            }
            .setPositiveButton(R.string.common_save) { _, _ ->
                inviteCodeEnteredCallback(view.invitationEt.text.toString())
            }
            .create()
    }

    fun show() {
        dialog.show()
    }
}