package jp.co.soramitsu.feature_main_impl.presentation.detail

import androidx.annotation.LayoutRes
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.presentation.view.openSoftKeyboard
import jp.co.soramitsu.common.util.KeyboardHelper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.presentation.util.VoteBottomSheetDialog
import javax.inject.Inject

abstract class BaseDetailFragment<V : BaseDetailViewModel>(@LayoutRes layoutRes: Int) :
    BaseFragment<V>(layoutRes),
    KeyboardHelper.KeyboardListener {
    abstract var debounceClickHandler: DebounceClickHandler

    private var voteDialog: VoteBottomSheetDialog? = null

    private var keyboardHelper: KeyboardHelper? = null

    @Inject
    lateinit var numbersFormatter: NumbersFormatter

    override fun onResume() {
        super.onResume()
        keyboardHelper = KeyboardHelper(requireView(), this)
    }

    override fun onPause() {
        super.onPause()
        keyboardHelper?.release()
        voteDialog?.dismiss()
    }

    override fun onKeyboardShow() {
        voteDialog?.showCloseKeyboard()
    }

    override fun onKeyboardHide() {
        voteDialog?.showOpenKeyboard()
    }

    protected fun openVotingSheet(
        maxAllowedVotes: Int,
        votableType: VoteBottomSheetDialog.VotableType,
        whenDone: (Long) -> Unit
    ) {
        voteDialog = VoteBottomSheetDialog(
            requireActivity(),
            votableType,
            maxAllowedVotes,
            { whenDone.invoke(it) },
            {
                if (keyboardHelper!!.isKeyboardShowing) {
                    hideSoftKeyboard(activity)
                } else {
                    openSoftKeyboard(it)
                }
            },
            numbersFormatter,
            debounceClickHandler
        )

        voteDialog!!.show()
    }
}
