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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.androidfoundation.format.nullZero
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.domain.lockedFree
import jp.co.soramitsu.common.domain.payableAsFee
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.subtractFee
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.transaction.TransactionOutcomeTexts
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.presentation.transaction.badge
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_assets_impl.presentation.states.SendState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal data class Sora2TransferFeePreviewRequest(
    val revision: Long,
    val walletId: String,
    val tokenId: String,
    val amount: BigDecimal,
) {
    fun matches(
        currentRevision: Long,
        currentWalletId: String?,
        currentTokenId: String?,
        currentAmount: BigDecimal?,
    ): Boolean =
        revision == currentRevision &&
            walletId == currentWalletId &&
            tokenId == currentTokenId &&
            currentAmount?.compareTo(amount) == 0
}

class TransferAmountViewModel @AssistedInject constructor(
    private val interactor: AssetsInteractor,
    private val walletRouter: WalletRouter,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
    private val clipboardManager: BasicClipboardManager,
    private val resourceManager: ResourceManager,
    avatarGenerator: AccountAvatarGenerator,
    @Assisted("recipientId") private val recipientId: String,
    @Assisted("assetId") private val assetId: String,
    @Assisted("initAmount") private val initialSendAmount: String?
) : BaseViewModel() {

    @AssistedFactory
    interface TransferAmountViewModelFactory {
        fun create(
            @Assisted("recipientId") recipientId: String,
            @Assisted("assetId") assetId: String,
            @Assisted("initAmount") initialSendAmount: String?
        ): TransferAmountViewModel
    }

    private val _transactionSuccessEvent = SingleLiveEvent<TransactionResultBadge>()

    /** Confirmed or submitted (still waiting); never raised for a transfer that failed. */
    val transactionSuccessEvent: LiveData<TransactionResultBadge> = _transactionSuccessEvent

    private val enteredFlow = MutableStateFlow(BigDecimal.ZERO)

    private var curAsset: Asset? = null
    private var feeAsset: Asset? = null
    private var fee: BigDecimal? = null
    private var feeWalletId: String? = null
    private var currentWalletId: String? = null
    private var feeReviewRevision = 0L
    private val assetsList = mutableListOf<Asset>()
    private var curTokenId: String = assetId
    private var hasXorReminderWarningBeenChecked = false

    /**
     * The transfer of this screen that reached the network. From then on this screen sends
     * nothing more: it is replaced by the transaction's page as soon as it is in front, and
     * until then Confirm stays off. A second tap would otherwise sign a second transfer, and
     * the duplicate question only covers a transfer that is still waiting.
     */
    private var sentTxHash: String? = null

    /**
     * A tapped Confirm is on its way and reports its own result. Set before the coroutine starts,
     * so a second tap is ignored whatever the button shows at that moment (it stays clickable
     * while its progress mark fades in). Without this a second tap started a second coroutine;
     * the duplicate guard stopped it from signing, but it cleared the progress mark and asked
     * "send again anyway?" about a transfer sent a moment ago.
     */
    private var confirmInFlight = false

    private val _sendState = MutableStateFlow(
        SendState(
            address = recipientId,
            icon = avatarGenerator.createAvatar(recipientId, 40),
        )
    )
    internal val sendState = _sendState.asStateFlow()

    override fun startScreen(): String = SendRoutes.start

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.common_enter_amount,
        )
        // Every collector below handles a failure of its own work: an exception that leaves
        // viewModelScope uncaught would end the app in the middle of a transfer.
        viewModelScope.launch {
            interactor.flowCurSoraAccount()
                .catch { onError(it) }
                .collectLatest { account ->
                    if (currentWalletId != account.substrateAddress) {
                        currentWalletId = account.substrateAddress
                        invalidateFeeReview()
                    }
                }
        }
        viewModelScope.launch {
            interactor.subscribeAssetsActiveOfCurAccount()
                .catch { onError(it) }
                .collectLatest { list ->
                    guarded {
                        assetsList.clear()
                        assetsList.addAll(list)
                        updateCurAsset()
                        feeAsset = list.find { it.token.id == SubstrateOptionsProvider.feeAssetId }
                            ?.also { asset ->
                                if (fee == null) {
                                    calcTransactionFee(
                                        asset,
                                        _sendState.value.input?.amount.orZero(),
                                    )
                                }
                            }
                        _sendState.value.input?.amount?.let {
                            checkEnteredAmount(it)
                        }
                    }
                }
        }
        viewModelScope.launch {
            enteredFlow
                .drop(1)
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    guarded {
                        feeAsset?.let { calcTransactionFee(it, amount) }
                        checkEnteredAmount(amount)
                        if (
                            _sendState.value.input?.token?.id ==
                            SubstrateOptionsProvider.feeAssetId ||
                            !hasXorReminderWarningBeenChecked
                        ) {
                            updateTransactionReminderWarningVisibility()
                            hasXorReminderWarningBeenChecked = true
                        }
                    }
                }
        }
    }

    private fun updateCurAsset() {
        curAsset = assetsList.find { it.token.id == curTokenId }?.also { asset ->
            if (_sendState.value.input == null) {
                _sendState.value = _sendState.value.copy(
                    input = AssetAmountInputState(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        amount = initialSendAmount?.toBigDecimalOrNull(),
                        amountFiat = "",
                        enabled = false,
                        error = false,
                        errorHint = ""
                    ),
                    lockedNote = lockedNoteOf(asset),
                )
            } else {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        amountFiat = asset.token.printFiat(
                            _sendState.value.input?.amount.orZero(),
                            numbersFormatter
                        )
                    ),
                    lockedNote = lockedNoteOf(asset),
                )
            }
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(_sendState.value.input) {
            if (this == null)
                return@with

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = fee.orZero(),
                xorChange = if (token.id == SubstrateOptionsProvider.feeAssetId) amount else null,
            )

            _sendState.value = _sendState.value.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        }

    fun onTokenChange(tokenId: String) {
        invalidateFeeReview()
        curTokenId = tokenId
        updateCurAsset()
        _sendState.value.input?.amount?.let {
            checkEnteredAmount(it)
            viewModelScope.launch {
                guarded {
                    feeAsset?.let { feeAsset ->
                        calcTransactionFee(feeAsset, it)
                        checkEnteredAmount(it)
                    }
                }
            }
        }
        hasXorReminderWarningBeenChecked = false
    }

    override fun onToolbarSearch(value: String) {
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        _sendState.value = _sendState.value.copy(
            searchFilter = _sendState.value.searchFilter.copy(
                filter = value,
            ),
        )
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            when (currentDestination) {
                SendRoutes.start -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.common_enter_amount,
                            searchEnabled = false,
                        )
                    )
                    _sendState.value = _sendState.value.copy(
                        input = _sendState.value.input?.copy(
                            readOnly = false,
                            amount = _sendState.value.input?.amount?.nullZero()
                        ),
                    )
                }

                SendRoutes.selectToken -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.choose_token,
                            searchEnabled = true,
                            searchPlaceholder = R.string.search_token_placeholder,
                            searchValue = _sendState.value.searchFilter.filter,
                        )
                    )
                }

                else -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.confirm_sending,
                            searchEnabled = false,
                        )
                    )
                    _sendState.value = _sendState.value.copy(
                        input = _sendState.value.input?.copy(
                            readOnly = true,
                        ),
                    )
                }
            }
        }
    }

    /**
     * The balance shown on this screen is what can be sent. When the chain holds part of the
     * free balance back (XOR locked by staking, voting or vesting), say how much.
     */
    private fun lockedNoteOf(asset: Asset): String? {
        val locked = asset.balance.lockedFree
        if (locked <= BigDecimal.ZERO) return null
        return resourceManager.getString(R.string.send_locked_balance_note).format(
            asset.token.printBalance(locked, numbersFormatter, AssetHolder.ROUNDING)
        )
    }

    private fun getAssetBalanceText(asset: Asset) = "%s (%s)".format(
        asset.printBalance(
            numbersFormatter,
            withSymbol = false,
            precision = AssetHolder.ROUNDING
        ),
        asset.printFiat(numbersFormatter)
    )

    private suspend fun calcTransactionFee(feeAsset: Asset, amount: BigDecimal) {
        val token = curAsset?.token ?: return
        val walletId = currentWalletId
            ?: interactor.getCurSoraAccount().substrateAddress.also {
                currentWalletId = it
            }
        val request = Sora2TransferFeePreviewRequest(
            revision = invalidateFeeReview(),
            walletId = walletId,
            tokenId = token.id,
            amount = amount,
        )
        val calculatedFee = try {
            interactor.calcTransactionFee(
                recipientId,
                token,
                amount,
                expectedWalletId = walletId,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (request.matchesCurrent()) {
                finishFeePreviewWithError(request, error)
            }
            return
        }
        if (!request.matchesCurrent()) return
        if (calculatedFee == null) {
            finishFeePreviewWithError(
                request,
                SoraException.businessError(ResponseCode.FEE_RATE_NOT_AVAILABLE),
            )
            return
        }
        fee = calculatedFee
        feeWalletId = walletId
        _sendState.value = _sendState.value.copy(
            feeLoading = false,
            fee = feeAsset.token.printBalance(
                calculatedFee,
                numbersFormatter,
                AssetHolder.ROUNDING,
            ),
            feeFiat = feeAsset.token.printFiat(calculatedFee, numbersFormatter),
            input = _sendState.value.input?.copy(
                enabled = true,
            ),
        )
    }

    /**
     * The fee could not be checked. Review stays off and the screen says why, with Try again.
     * Since 3.9.0 the fee, nonce, runtime and submission go to the reviewed SORA endpoint, not to
     * the node chosen in More > Nodes; when it can't be reached this names that calmly instead of
     * showing the generic "unexpected error" dialog on every amount change. Errors with their own
     * message (business errors) still show it.
     */
    private fun finishFeePreviewWithError(
        request: Sora2TransferFeePreviewRequest,
        error: Throwable,
    ) {
        if (!request.matchesCurrent()) return
        fee = null
        feeWalletId = null
        _sendState.value = _sendState.value.copy(
            feeLoading = false,
            fee = "",
            feeFiat = "",
            feeUnavailable = NetworkFeeUnavailable.from(error),
            reviewEnabled = false,
            input = _sendState.value.input?.copy(enabled = true),
        )
        if (error is SoraException && error.kind == SoraException.Kind.BUSINESS) {
            onError(error)
        } else {
            FirebaseWrapper.recordException(error)
        }
    }

    /** Try again on the fee notice: checks the fee for the current token and amount. */
    fun onRetryFee() {
        val feeAsset = feeAsset ?: return
        val amount = _sendState.value.input?.amount.orZero()
        viewModelScope.launch {
            guarded {
                calcTransactionFee(feeAsset, amount)
                checkEnteredAmount(amount)
            }
        }
    }

    private fun Sora2TransferFeePreviewRequest.matchesCurrent(): Boolean = matches(
        currentRevision = feeReviewRevision,
        currentWalletId = currentWalletId,
        currentTokenId = curAsset?.token?.id,
        currentAmount = _sendState.value.input?.amount.orZero(),
    )

    fun copyAddress() {
        clipboardManager.addToClipboard(recipientId)
        copiedToast.trigger()
    }

    fun onConfirmClick() = confirmTransfer(confirmedDuplicate = false)

    /**
     * Sends the reviewed transfer and shows exactly what is known afterwards. Confirmed and
     * still-waiting transfers open the transaction page, which carries the status and follows
     * it. A transfer that failed on chain, was refused by the node before broadcast, or could
     * not be prepared is explained in a dialog and the screen stays; it is never announced as
     * submitted.
     *
     * The result arrives after a wait of up to two minutes, so the app can be in the background
     * or behind the PIN lock by then. The toast and the dialogs are delivered when the screen is
     * shown again; the transaction page opens when it is in front (see [openTransactionPage]).
     */
    private fun confirmTransfer(confirmedDuplicate: Boolean) {
        if (sentTxHash != null || confirmInFlight) return
        val curAsset = curAsset ?: return
        val amount = _sendState.value.input?.amount ?: return
        val fee = fee ?: return
        val expectedWalletId = feeWalletId ?: return
        if (currentWalletId != expectedWalletId) {
            invalidateFeeReview()
            return
        }
        confirmInFlight = true
        viewModelScope.launch {
            try {
                sendTransfer(curAsset, amount, fee, expectedWalletId, confirmedDuplicate)
            } finally {
                // The result was shown. A transfer that reached the network keeps Confirm off
                // through sentTxHash; in every other case Confirm may be used again.
                confirmInFlight = false
            }
        }
    }

    private suspend fun sendTransfer(
        curAsset: Asset,
        amount: BigDecimal,
        fee: BigDecimal,
        expectedWalletId: String,
        confirmedDuplicate: Boolean,
    ) {
        _sendState.value = _sendState.value.copy(
            inProgress = true
        )
        var outcome: TransactionOutcome? = null
        var notSent: Throwable? = null
        try {
            outcome = interactor.observeTransfer(
                recipientId,
                curAsset.token,
                amount,
                fee,
                expectedWalletId,
                confirmedDuplicate,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: DuplicateWaitingTransactionException) {
            _sendState.value = _sendState.value.copy(inProgress = false)
            askBeforeSendingAgain(resourceManager) {
                confirmTransfer(confirmedDuplicate = true)
            }
            return
        } catch (t: Throwable) {
            notSent = t
        }
        _sendState.value = _sendState.value.copy(inProgress = false)
        when (outcome) {
            is TransactionOutcome.Confirmed,
            is TransactionOutcome.Waiting -> {
                sentTxHash = outcome.txHash
                _sendState.value = _sendState.value.copy(reviewEnabled = false)
                _transactionSuccessEvent.value = outcome.badge()
                openTransactionPage(outcome.txHash)
            }
            is TransactionOutcome.FailedOnChain -> {
                val text = TransactionOutcomeTexts.describe(resourceManager, outcome)
                alertDialogLiveData.setValue(text.title to text.message)
            }
            is TransactionOutcome.RefusedByNode ->
                showTransactionRefused(resourceManager, outcome)
            null -> {
                val error = checkNotNull(notSent)
                showTransactionNotSent(resourceManager, error)
                when {
                    error.isFeeChanged() -> showNewFee(amount)
                    error.isBalanceNoLongerEnough() -> showCurrentBalance()
                }
            }
        }
    }

    /**
     * Opens the transaction's page in place of this screen, only while this screen is in front.
     * A result that arrives while the app is in the background, or while the PIN lock is on
     * top, is held and the page opens on the next resume; nothing is navigated before that.
     */
    private fun openTransactionPage(txHash: String) = showWhenInFront {
        assetsRouter.showTxResult(txHash, TransactionScreen.SEND)
    }

    /**
     * The network fee is no longer the one that was reviewed, so nothing was sent. The dialog
     * says "check the new fee and confirm again": the reviewed fee is dropped and asked again
     * for the same token and amount. Confirm is off while that runs and comes back with the new
     * fee on screen. Without this the old fee stayed, and every further Confirm ended the same
     * way until the amount was edited.
     */
    private suspend fun showNewFee(amount: BigDecimal) {
        val feeAsset = feeAsset
        if (feeAsset == null) {
            invalidateFeeReview()
            return
        }
        // The fee that is no longer valid is not left on screen while the new one is asked.
        _sendState.value = _sendState.value.copy(fee = "", feeFiat = "")
        guarded {
            calcTransactionFee(feeAsset, amount)
            checkEnteredAmount(amount)
        }
    }

    /**
     * The last check before the transfer left the phone read the balance from the network, and
     * it no longer covers the amount and the fee, so nothing was sent. The figures on this screen
     * come from the local database and were out of date: an earlier transfer was included in the
     * meantime, or the funds moved from another device. The dialog says so; here the balances
     * are read again, and when they arrive the amount is checked against them (the balance
     * collector does that), which turns Confirm off and names the amount as too high. Without
     * this every further Confirm ended in the same dialog until the amount was edited.
     *
     * The read is not part of the Confirm that just ended: it can take as long as the network
     * takes, and Confirm must not be held back for it.
     */
    private fun showCurrentBalance() {
        viewModelScope.launch {
            try {
                interactor.updateBalanceVisibleAssets()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // The dialog was shown. A balance that cannot be read now is read again by the
                // app's own refresh; Confirm keeps checking against the network either way.
                FirebaseWrapper.recordException(error)
            }
        }
    }

    private fun Throwable.isFeeChanged(): Boolean = hasMessageInChain(FEE_CHANGED_CODE)

    /** Also when the XOR left for the fee turned out to be locked: the screen reads it again. */
    private fun Throwable.isBalanceNoLongerEnough(): Boolean =
        TransactionOutcomeTexts.BALANCE_NO_LONGER_ENOUGH_CODES.any { hasMessageInChain(it) } ||
            hasMessageInChain(TransactionOutcomeTexts.XOR_FEE_LOCKED_CODE)

    private fun Throwable.hasMessageInChain(code: String): Boolean {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current.message == code) return true
            current = current.cause?.takeIf { it !== current }
            depth += 1
        }
        return false
    }

    private fun checkEnteredAmount(amount: BigDecimal) {
        val fee = fee ?: return
        val feeAsset = feeAsset ?: return
        val curAsset = curAsset ?: return
        when {
            amount.isZero() -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    reviewEnabled = false,
                )
            }

            feeAsset.balance.transferable < fee -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.error_transaction_fee_title),
                    ),
                    reviewEnabled = false,
                )
            }

            // The fee is taken before the transfer, and the network does not let it come out of
            // frozen XOR even where a transfer could. Without this the amount passed here and
            // the node refused the transaction.
            feeAsset.balance.payableAsFee < fee -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.send_fee_locked_hint),
                    ),
                    reviewEnabled = false,
                )
            }

            curAsset.balance.transferable < amount -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable < amount + fee) -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable - amount - fee < SubstrateOptionsProvider.existentialDeposit.toBigDecimal()) -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.wallet_send_existential_warning_message),
                    ),
                    reviewEnabled = false,
                )
            }

            else -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    // A balance update after the transfer must not switch Confirm on again.
                    reviewEnabled = sentTxHash == null,
                )
            }
        }
    }

    fun onReviewClick() {
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = _sendState.value.input?.amount.orZero()
            )
        )
    }

    fun amountChanged(value: BigDecimal) {
        invalidateFeeReview()
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = value,
                amountFiat = _sendState.value.input?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        enteredFlow.value = value
    }

    fun optionSelected(percent: Int) {
        val fee = fee ?: return
        val curAsset = curAsset ?: return
        var amount = PolkaswapFormulas.calculateAmountByPercentage(
            curAsset.balance.transferable,
            percent.toDouble(),
            curAsset.token.precision
        )
        if (curAsset.token.id == SubstrateOptionsProvider.feeAssetId && amount > BigDecimal.ZERO) {
            amount = subtractFee(amount, curAsset.balance.transferable, fee)
        }
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = amount,
                amountFiat = _sendState.value.input?.token?.printFiat(
                    amount,
                    numbersFormatter
                ).orEmpty()
            )
        )
        invalidateFeeReview()
        enteredFlow.value = amount
    }

    private fun invalidateFeeReview(): Long {
        feeReviewRevision += 1L
        fee = null
        feeWalletId = null
        _sendState.value = _sendState.value.copy(
            feeLoading = true,
            feeUnavailable = null,
            reviewEnabled = false,
        )
        return feeReviewRevision
    }

    private companion object {
        // requireExactSora2TransferFee: the fee at signing is not the reviewed one.
        const val FEE_CHANGED_CODE = "SORA2_TRANSFER_FEE_CHANGED"
        const val MAX_CAUSE_DEPTH = 16
    }
}
