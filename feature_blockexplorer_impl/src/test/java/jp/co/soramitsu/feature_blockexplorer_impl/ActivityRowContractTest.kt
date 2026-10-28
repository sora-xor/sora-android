package jp.co.soramitsu.feature_blockexplorer_impl

import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel.EventTxUiModel.*
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory.TransactionMappersImpl
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.util.Date
import java.util.Locale

class ActivityRowContractTest {
    private val resources = mockk<ResourceManager>()
    private val dates = mockk<DateTimeFormatter>()
    private val subject = TransactionMappersImpl(resources, NumbersFormatter(), dates)
    private val base = TransactionBase("tx-hash", "block-hash", BigDecimal("0.01"), TransactionStatus.COMMITTED, 1000000L)
    private val xor = Token("xor", "Sora", "XOR", 18, true, "xor-icon", 2.0, 0.0, "$")
    private val valToken = Token("val", "Validator", "VAL", 6, true, "val-icon", 4.0, 0.0, "$")
    private val reward = Token("reward", "Reward", "PSWAP", 12, true, "reward-icon", 3.0, 0.0, "$")
    private lateinit var previousLocale: Locale
    @Before fun setup() {
        previousLocale = Locale.getDefault(); Locale.setDefault(Locale.US)
        every { dates.formatTimeWithoutSeconds(Date(base.timestamp)) } returns "12:56"
        every { resources.getString(R.string.activity_pool_title) } returns "Pool"
        every { resources.getQuantityString(R.plurals.referral_invitations, 1) } returns "invitation"
    }
    @After fun cleanup() { Locale.setDefault(previousLocale) }
    private fun checkIdentity(row: EventUiModel.EventTxUiModel) {
        assertEquals(base.txHash, row.txHash); assertEquals(base.timestamp, row.timestamp); assertEquals(base.status, row.status)
    }
    @Test fun `incoming transfer shows the sender plus sign token and fiat`() {
        val row = subject.mapTransaction(Transaction.Transfer(base, BigDecimal("1.25"), "sender", TransactionTransferType.INCOMING, valToken), "me") as EventTransferInUiModel
        checkIdentity(row); assertEquals("sender", row.peerAddress); assertEquals("val-icon", row.tokenIcon)
        assertEquals("+1.25 VAL", row.amountFormatted); assertEquals("~$5", row.fiatFormatted); assertEquals("12:56", row.dateTime)
    }
    @Test fun `outgoing transfer preserves recipient without presenting an incoming amount`() {
        val row = subject.mapTransaction(Transaction.Transfer(base, BigDecimal("2.5"), "recipient", TransactionTransferType.OUTGOING, xor), "me") as EventTransferOutUiModel
        checkIdentity(row); assertEquals("recipient", row.peerAddress); assertEquals("xor-icon", row.tokenIcon)
        assertEquals("2.5 XOR", row.amountFormatted); assertEquals("~$5", row.fiatFormatted); assertEquals("12:56", row.dateTime)
    }
    @Test fun `swap keeps distinct input and output amounts and token identities`() {
        val row = subject.mapTransaction(Transaction.Swap(base, xor, valToken, BigDecimal("2"), BigDecimal("3.25"), Market.XYK), "me") as EventLiquiditySwapUiModel
        checkIdentity(row); assertEquals("xor-icon", row.iconFrom); assertEquals("val-icon", row.iconTo)
        assertEquals("2 XOR -> ", row.amountFrom); assertEquals("3.25 VAL", row.amountTo)
        assertEquals("XOR", row.tickerFrom); assertEquals("VAL", row.tickerTo); assertEquals("12:56", row.dateTime)
    }
    @Test fun `liquidity deposit does not reverse token amounts or imply received balances`() {
        val row = subject.mapTransaction(Transaction.Liquidity(base, xor, valToken, BigDecimal("2"), BigDecimal("9"), TransactionLiquidityType.ADD), "me") as EventLiquidityAddUiModel
        checkIdentity(row); assertTrue(row.add); assertEquals("2 XOR", row.amount1); assertEquals("9 VAL", row.amount2)
        assertEquals("xor-icon", row.icon1); assertEquals("val-icon", row.icon2); assertEquals("Pool", row.type)
        assertEquals("XOR", row.ticker1); assertEquals("VAL", row.ticker2); assertEquals("12:56", row.dateTime)
    }
    @Test fun `liquidity withdrawal marks both amounts as received`() {
        val row = subject.mapTransaction(Transaction.Liquidity(base, xor, valToken, BigDecimal("2"), BigDecimal("9"), TransactionLiquidityType.WITHDRAW), "me") as EventLiquidityAddUiModel
        checkIdentity(row); assertFalse(row.add); assertEquals("+2 XOR", row.amount1); assertEquals("+9 VAL", row.amount2)
    }
    @Test fun `claim activity names and depicts the actual reward token`() {
        val row = subject.mapTransaction(Transaction.DemeterFarming(base, DemeterType.REWARD, BigDecimal("7.5"), xor, valToken, reward), "me") as EventDemeterRewardUiModel
        checkIdentity(row); assertEquals("reward-icon", row.token); assertEquals("7.5 PSWAP", row.amountFormatted)
    }
    @Test fun `farm deposit keeps the pair and reward identity`() {
        val row = subject.mapTransaction(Transaction.DemeterFarming(base, DemeterType.STAKE, BigDecimal("3.25"), xor, valToken, reward), "me") as EventDemeterStakeUiModel
        checkIdentity(row); assertTrue(row.stake); assertEquals("XOR-VAL", row.symbols); assertEquals("3.25", row.amountFormatted)
        assertEquals("xor-icon", row.tokenBase); assertEquals("val-icon", row.tokenTarget); assertEquals("reward-icon", row.tokenReward)
    }
    @Test fun `farm withdrawal is distinguished from a stake`() {
        val row = subject.mapTransaction(Transaction.DemeterFarming(base, DemeterType.UNSTAKE, BigDecimal.ONE, xor, valToken, reward), "me") as EventDemeterStakeUiModel
        checkIdentity(row); assertFalse(row.stake); assertEquals("1", row.amountFormatted)
    }
    @Test fun `ethereum transfer retains bridge request and sidechain destination`() {
        val row = subject.mapTransaction(Transaction.EthTransfer(base, BigDecimal("2"), xor, valToken, "bridge-request", "0xDestination"), "me") as EventEthTransfer
        checkIdentity(row); assertEquals("bridge-request", row.requestHash); assertEquals("0xDestination", row.sidechainAddress)
        assertEquals("xor-icon", row.tokenUri); assertEquals("val-icon", row.ethTokenUri)
        assertEquals("2 XOR", row.amountFormatted); assertEquals("~$4", row.fiatFormatted); assertEquals("12:56", row.dateTime)
    }
    @Test fun `income retains the peer and token amount`() {
        val row = subject.mapTransaction(Transaction.AdarIncome(base, BigDecimal("4"), "income-source", reward), "me") as EventAdarIncomeUiModel
        checkIdentity(row); assertEquals("income-source", row.peerAddress); assertEquals("reward-icon", row.tokenUri); assertEquals("4 PSWAP", row.amountFormatted)
    }
    @Test fun `bonded invitations show the current wallet and an outgoing quantity`() {
        val row = subject.mapTransaction(Transaction.ReferralBond(base, BigDecimal("5"), xor), "selected-wallet") as EventReferralProgramUiModel
        checkIdentity(row); assertFalse(row.plusAmount); assertEquals(R.string.wallet_bonded, row.title)
        assertEquals("selected-wallet", row.description); assertEquals("5 XOR", row.amountFormatted); assertEquals("12:56", row.dateTime)
    }
    @Test fun `unbonded invitations show an incoming quantity`() {
        val row = subject.mapTransaction(Transaction.ReferralUnbond(base, BigDecimal("5"), xor), "selected-wallet") as EventReferralProgramUiModel
        checkIdentity(row); assertTrue(row.plusAmount); assertEquals(R.string.wallet_unbonded, row.title)
        assertEquals("selected-wallet", row.description); assertEquals("+5 XOR", row.amountFormatted); assertEquals("xor-icon", row.tokenIcon)
    }
    @Test fun `setting my referrer does not invent a token payment`() {
        val row = subject.mapTransaction(Transaction.ReferralSetReferrer(base, "referrer", true, xor), "me") as EventReferralProgramUiModel
        checkIdentity(row); assertEquals(R.string.referrer_set, row.title); assertEquals("referrer", row.description)
        assertFalse(row.plusAmount); assertEquals("--", row.amountFormatted)
    }
    @Test fun `a friend accepting an invitation shows one invitation consumed`() {
        val row = subject.mapTransaction(Transaction.ReferralSetReferrer(base, "friend", false, xor), "me") as EventReferralProgramUiModel
        checkIdentity(row); assertEquals(R.string.activity_referral_title, row.title); assertEquals("friend", row.description)
        assertFalse(row.plusAmount); assertEquals("-1 invitation", row.amountFormatted)
    }
    @Test fun `failed financial activity keeps its rejected status`() {
        val rejected = TransactionBase(base.txHash, base.blockHash, base.fee, TransactionStatus.REJECTED, base.timestamp)
        val transactions = listOf(Transaction.Transfer(rejected, BigDecimal.ONE, "peer", TransactionTransferType.OUTGOING, xor),
            Transaction.DemeterFarming(rejected, DemeterType.REWARD, BigDecimal.ONE, xor, valToken, reward),
            Transaction.Liquidity(rejected, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, TransactionLiquidityType.ADD),
            Transaction.Swap(rejected, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, Market.SMART))
        transactions.forEach { assertEquals(TransactionStatus.REJECTED, subject.mapTransaction(it, "me").status) }
    }
}
