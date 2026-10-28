package jp.co.soramitsu.demeter.data

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.sora.substrate.runtime.*
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.xsubstrate.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.bytes
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger

/** Real mainnet metadata and SCALE values, with only the node/DAO boundaries replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemeterStorageContractTest {
    private val calls = mockk<SubstrateCalls>()
    private val extrinsics = mockk<ExtrinsicManager>()
    private val runtime = mockk<RuntimeManager>()
    private val config = mockk<SoraConfigManager>()
    private val whitelist = mockk<WhitelistTokensManager>()
    private val db = mockk<AppDatabase>()
    private val dao = mockk<AssetDao>()
    private val assets = mockk<AssetsRepository>()
    private val polkaswap = mockk<PolkaswapRepository>()
    private val mapper = AssetLocalToAssetMapper(whitelist, config)
    private val subject = DemeterFarmingRepositoryImpl(calls, extrinsics, runtime, config, mapper, db, assets, polkaswap)
    private val base = TestTokens.xorToken.id
    private val target = TestTokens.valToken.id
    private val reward = TestTokens.pswapToken.id
    private val ownerBytes = ByteArray(32) { 3 }
    private val otherBytes = ByteArray(32) { 4 }
    private val owner = ownerBytes.toAddress(69.toShort())
    private val other = otherBytes.toAddress(69.toShort())
    private var currency = SoraCurrency("USD", "US Dollar", "$")
    private val rows = mutableMapOf<String, TokenLocal>()
    private var basics = emptyMap<String, String?>()
    private var rewards = emptyMap<String, String?>()
    private var userHex: String? = null
    private fun storage(which: Storage) = snapshot.metadata.module(Pallete.DEMETER_FARMING.palletName).storage(which.storageName)
    private fun key(which: Storage, vararg args: Any) = storage(which).storageKey(snapshot, *args)
    private fun encode(which: Storage, value: Any) = "0x" + storage(which).type.value!!.bytes(snapshot, value).joinToString("") { "%02x".format(it) }
    private fun atomic(value: String, precision: Int = 18) = BigDecimal(value).movePointRight(precision).toBigIntegerExact()
    private fun position(amount: String = "2", claim: String = "3", farm: Boolean = true, baseId: String = base, targetId: String = target, rewardId: String = reward) = Struct.Instance(mapOf(
        "baseAsset" to baseId.mapCodeToken(), "poolAsset" to targetId.mapCodeToken(), "rewardAsset" to rewardId.mapCodeToken(),
        "isFarm" to farm, "pooledTokens" to atomic(amount), "rewards" to atomic(claim, 12), "rewardPerTokenPaid" to BigInteger.ZERO))
    private fun farm(total: String = "10", removed: Boolean = false, isFarm: Boolean = true, multiplier: BigInteger = BigInteger.ONE) = Struct.Instance(mapOf(
        "baseAsset" to base.mapCodeToken(), "multiplier" to multiplier, "isCore" to true, "isFarm" to isFarm,
        "isRemoved" to removed, "depositFee" to atomic("0.01"), "totalTokensInPool" to atomic(total),
        "rewards" to BigInteger.ZERO, "rewardsToBeDistributed" to BigInteger.ZERO, "rewardPerToken" to BigInteger.ZERO))
    private fun rewardInfo(multiplier: BigInteger = BigInteger.ONE, allocation: String = "1", perBlock: String = "1") = Struct.Instance(mapOf(
        "teamAccount" to ownerBytes, "farmsTotalMultiplier" to multiplier, "stakingTotalMultiplier" to BigInteger.ONE,
        "tokenPerBlock" to atomic(perBlock, 12), "farmsAllocation" to atomic(allocation, 12),
        "stakingAllocation" to BigInteger.ZERO, "teamAllocation" to BigInteger.ZERO))
    private suspend fun seedFarm(total: String = "10", removed: Boolean = false, isFarm: Boolean = true) {
        basics = mapOf(key(Storage.POOLS, target.mapCodeToken(), reward.mapCodeToken()) to encode(Storage.POOLS, listOf(farm(total, removed, isFarm))))
        rewards = mapOf(key(Storage.TOKEN_INFOS, reward.mapCodeToken()) to encode(Storage.TOKEN_INFOS, rewardInfo()))
    }
    private fun equalDecimal(expected: String, actual: BigDecimal) = assertEquals(expected, 0, BigDecimal(expected).compareTo(actual))
    @Before fun setup() {
        rows[base] = TokenLocal(base, "XOR", "XOR", 18, true, "SORA", false)
        rows[target] = TokenLocal(target, "VAL", "VAL", 6, true, "SORA", false)
        rows[reward] = TokenLocal(reward, "PSWAP", "PSWAP", 12, true, "SORA", false)
        every { db.assetDao() } returns dao
        coEvery { runtime.getRuntimeSnapshot() } returns snapshot
        every { runtime.toSoraAddressOrNull(any()) } returns owner
        coEvery { config.getSelectedCurrency() } answers { currency }
        every { whitelist.getTokenIconUri(any()) } answers { "icon:${firstArg<String>()}" }
        coEvery { dao.getTokenLocal(any()) } answers { rows.getValue(firstArg()) }
        coEvery { dao.getToken(any(), any()) } answers {
            val id = firstArg<String>(); TokenWithFiatLocal(rows.getValue(id), FiatTokenPriceLocal(id, currency.code, if (id == reward) 3.0 else 2.0, 1, 2.0, 1, 2.0, 1))
        }
        coEvery { assets.isWhitelistedToken(any()) } returns true
        coEvery { calls.getBulk(key(Storage.POOLS)) } answers { basics }
        coEvery { calls.getBulk(key(Storage.TOKEN_INFOS)) } answers { rewards }
        coEvery { calls.getStorageHex(key(Storage.USER_INFOS, ownerBytes)) } answers { userHex }
        coEvery { calls.getStorageHex(key(Storage.USER_INFOS, otherBytes)) } answers { userHex }
        coEvery { polkaswap.getBasicPool(base, target) } returns BasicPoolData(TestTokens.xorToken, TestTokens.valToken, BigDecimal("100"), BigDecimal("200"), BigDecimal("100"), "reserve", null)
    }
    @Test fun `mainnet SCALE farm metadata produces TVL using LP base precision`() = runTest {
        seedFarm(); val pool = subject.getFarmedBasicPools().single()
        assertEquals(base, pool.tokenBase.id); assertEquals(target, pool.tokenTarget.id); assertEquals(reward, pool.tokenReward.id)
        equalDecimal("80", pool.tvl); assertEquals(1.0, pool.fee, 0.000001)
        assertEquals(19710000.0, pool.apr, 0.001)
    }
    @Test fun `mainnet SCALE positions keep independently scaled LP and reward amounts`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position()))
        val pool = subject.getFarmedPools(owner)!!.single()
        equalDecimal("2", pool.amount); equalDecimal("3", pool.amountReward)
        assertEquals(base, pool.tokenBase.id); assertEquals(target, pool.tokenTarget.id); assertEquals(reward, pool.tokenReward.id)
    }
    @Test fun `staked balance excludes farm positions and other base tokens`() = runTest {
        userHex = encode(Storage.USER_INFOS, listOf(position("2", farm = false, targetId = base), position("4"), position("6", farm = false)))
        equalDecimal("2", subject.getStakedFarmedAmountOfAsset(owner, base))
    }
    @Test fun `missing user storage is unavailable rather than a synthetic empty position`() = runTest {
        seedFarm(); assertNull(subject.getFarmedPools(owner)); equalDecimal("0", subject.getStakedFarmedAmountOfAsset(owner, base))
    }
    @Test fun `empty decoded positions are a valid empty wallet`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, emptyList<Any>())
        assertEquals(emptyList<Any>(), subject.getFarmedPools(owner))
    }
    @Test fun `zero pooled amount is not displayed as a farm position`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position("0")))
        assertTrue(subject.getFarmedPools(owner)!!.isEmpty())
    }
    @Test fun `staking positions are not presented as liquidity farms`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position(farm = false)))
        assertTrue(subject.getFarmedPools(owner)!!.isEmpty())
    }
    @Test fun `position without a matching base farm cannot adopt another APR`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position(targetId = base)))
        assertTrue(subject.getFarmedPools(owner)!!.isEmpty())
    }
    @Test fun `removed farm metadata is not offered to the user`() = runTest {
        seedFarm(removed = true); assertTrue(subject.getFarmedBasicPools().isEmpty())
    }
    @Test fun `staking metadata is excluded from liquidity farms`() = runTest {
        seedFarm(isFarm = false); assertTrue(subject.getFarmedBasicPools().isEmpty())
    }
    @Test fun `unwhitelisted target filters both pool and wallet positions`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position()))
        coEvery { assets.isWhitelistedToken(target) } returns false
        assertTrue(subject.getFarmedBasicPools().isEmpty()); assertTrue(subject.getFarmedPools(owner)!!.isEmpty())
    }
    @Test fun `unwhitelisted reward cannot be advertised as an eligible farm`() = runTest {
        seedFarm(); coEvery { assets.isWhitelistedToken(reward) } returns false
        assertTrue(subject.getFarmedBasicPools().isEmpty())
    }
    @Test fun `null bulk storage values are skipped without inventing farms`() = runTest {
        basics = mapOf(key(Storage.POOLS, target.mapCodeToken(), reward.mapCodeToken()) to null)
        rewards = mapOf(key(Storage.TOKEN_INFOS, reward.mapCodeToken()) to null)
        assertTrue(subject.getFarmedBasicPools().isEmpty())
    }
    @Test fun `missing reward emissions produce zero APR but preserve the pool`() = runTest {
        seedFarm(); rewards = emptyMap(); assertEquals(0.0, subject.getFarmedBasicPools().single().apr, 0.0)
    }
    @Test fun `zero reward multiplier cannot divide by zero`() = runTest {
        seedFarm(); rewards = mapOf(key(Storage.TOKEN_INFOS, reward.mapCodeToken()) to encode(Storage.TOKEN_INFOS, rewardInfo(BigInteger.ZERO)))
        assertEquals(0.0, subject.getFarmedBasicPools().single().apr, 0.0)
    }
    @Test fun `zero farm deposits have zero TVL and APR`() = runTest {
        seedFarm(total = "0"); val pool = subject.getFarmedBasicPools().single()
        equalDecimal("0", pool.tvl); assertEquals(0.0, pool.apr, 0.0)
    }
    @Test fun `missing liquidity pool preserves a farm with unavailable TVL`() = runTest {
        seedFarm(); coEvery { polkaswap.getBasicPool(base, target) } returns null
        val pool = subject.getFarmedBasicPools().single(); equalDecimal("0", pool.tvl); assertEquals(0.0, pool.apr, 0.0)
    }
    @Test fun `zero LP issuance preserves a new farm without division by zero`() = runTest {
        seedFarm(); coEvery { polkaswap.getBasicPool(base, target) } returns BasicPoolData(TestTokens.xorToken, TestTokens.valToken, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "reserve", null)
        val pool = subject.getFarmedBasicPools().single(); equalDecimal("0", pool.tvl); assertEquals(0.0, pool.apr, 0.0)
    }
    @Test fun `cancelled pool lookup cannot be converted into a cached empty list`() = runTest {
        seedFarm(); coEvery { polkaswap.getBasicPool(base, target) } throws CancellationException("screen closed")
        try { subject.getFarmedBasicPools(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun `wallet positions are cached per account rather than globally`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position("2")))
        equalDecimal("2", subject.getFarmedPools(owner)!!.single().amount)
        userHex = encode(Storage.USER_INFOS, listOf(position("7")))
        equalDecimal("7", subject.getFarmedPools(other)!!.single().amount)
        equalDecimal("2", subject.getFarmedPools(owner)!!.single().amount)
    }
    @Test fun `pool event invalidates cached basic and selected wallet positions after debounce`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position("2")))
        subject.getFarmedPools(owner)
        val events = MutableSharedFlow<String>(); every { calls.observeBulk(key(Storage.POOLS)) } returns events
        val seen = mutableListOf<String>(); val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribeFarms(owner).toList(seen) }
        events.emit("first"); advanceTimeBy(200); events.emit("last"); advanceTimeBy(299); runCurrent(); assertTrue(seen.isEmpty())
        userHex = encode(Storage.USER_INFOS, listOf(position("7"))); advanceTimeBy(1); runCurrent()
        assertEquals(listOf("last"), seen); equalDecimal("7", subject.getFarmedPools(owner)!!.single().amount)
        coVerify(exactly = 2) { calls.getBulk(key(Storage.POOLS)) }; job.cancel()
    }
    @Test fun `basic farm currency follows the selected currency on the next read`() = runTest {
        seedFarm(); assertEquals("$", subject.getFarmedBasicPools().single().tokenBase.fiatSymbol)
        currency = SoraCurrency("JPY", "Yen", "¥")
        assertEquals("¥", subject.getFarmedBasicPools().single().tokenBase.fiatSymbol)
    }
    private suspend fun feeCall(method: String, call: suspend () -> BigDecimal?) {
        val builder = mockk<jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder>()
        val pallet = slot<String>(); val operation = slot<String>(); val arguments = slot<Map<String, Any?>>()
        every { builder.call(capture(pallet), capture(operation), capture(arguments)) } returns builder
        coEvery { extrinsics.calcFee(owner, false, any()) } coAnswers {
            thirdArg<suspend jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder.() -> Unit>().invoke(builder)
            BigInteger("1234567")
        }
        equalDecimal("1.234567", call()!!)
        assertEquals("DemeterFarmingPlatform", pallet.captured); assertEquals(method, operation.captured)
        assertEquals(base.mapCodeToken(), arguments.captured["base_asset"])
        assertEquals(target.mapCodeToken(), arguments.captured["pool_asset"])
        assertEquals(reward.mapCodeToken(), arguments.captured["reward_asset"])
        assertEquals(true, arguments.captured["is_farm"])
        if (method != "get_rewards") assertEquals(atomic("1"), arguments.captured["pooled_tokens"])
    }
    @Test fun `deposit fee uses exact farm identifiers and the requested fee precision`() = runTest {
        feeCall("deposit") { subject.calcDepositFarmFee(owner, base, target, reward, true, 6) }
    }
    @Test fun `withdraw fee uses LP amount precision independent of target precision`() = runTest {
        feeCall("withdraw") { subject.calcWithdrawFarmFee(owner, base, target, reward, true, 6) }
    }
    @Test fun `claim fee does not include a fabricated pooled amount`() = runTest {
        feeCall("get_rewards") { subject.calcClaimDemeterRewards(owner, base, target, reward, true, 6) }
    }
    @Test fun `unavailable fees remain unavailable for all farm actions`() = runTest {
        coEvery { extrinsics.calcFee(owner, false, any()) } returns null
        assertNull(subject.calcDepositFarmFee(owner, base, target, reward, true, 18))
        assertNull(subject.calcWithdrawFarmFee(owner, base, target, reward, true, 18))
        assertNull(subject.calcClaimDemeterRewards(owner, base, target, reward, true, 18))
    }
    private suspend fun submitCall(method: String, farm: Boolean, action: suspend (jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair, jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity) -> jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus) {
        val keys = mockk<jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair>()
        val activity = mockk<jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity>()
        val status = jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus(true, "tx", "block")
        val builder = mockk<jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder>()
        val operation = slot<String>(); val arguments = slot<Map<String, Any?>>()
        every { builder.call("DemeterFarmingPlatform", capture(operation), capture(arguments)) } returns builder
        coEvery { extrinsics.submitAndWatchExtrinsic(owner, keys, false, activity, any()) } coAnswers {
            arg<suspend jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder.() -> Unit>(4).invoke(builder)
            status
        }
        assertSame(status, action(keys, activity)); assertEquals(method, operation.captured)
        assertEquals(base.mapCodeToken(), arguments.captured["base_asset"])
        assertEquals(target.mapCodeToken(), arguments.captured["pool_asset"])
        assertEquals(reward.mapCodeToken(), arguments.captured["reward_asset"])
        assertEquals(farm, arguments.captured["is_farm"])
        if (method != "get_rewards") assertEquals(BigInteger("1234567890123456789"), arguments.captured["pooled_tokens"])
        else assertFalse(arguments.captured.containsKey("pooled_tokens"))
    }
    @Test fun `deposit preserves wallet activity and full precision amount`() = runTest {
        submitCall("deposit", true) { keys, activity -> subject.depositDemeterFarm(owner, keys, base, target, reward, true, BigDecimal("1.234567890123456789"), activity) }
    }
    @Test fun `withdraw preserves staking mode and exact amount`() = runTest {
        submitCall("withdraw", false) { keys, activity -> subject.withdrawDemeterFarm(owner, keys, base, target, reward, false, BigDecimal("1.234567890123456789"), activity) }
    }
    @Test fun `claim preserves the reviewed activity and reward identifiers`() = runTest {
        submitCall("get_rewards", true) { keys, activity -> subject.claimDemeterRewards(owner, keys, base, target, reward, true, activity) }
    }
    @Test fun `wallet positions refresh token currency instead of returning stale fiat`() = runTest {
        seedFarm(); userHex = encode(Storage.USER_INFOS, listOf(position()))
        assertEquals("$", subject.getFarmedPools(owner)!!.single().tokenBase.fiatSymbol)
        currency = SoraCurrency("JPY", "Yen", "¥")
        assertEquals("¥", subject.getFarmedPools(owner)!!.single().tokenBase.fiatSymbol)
    }
    companion object {
        private fun repoFile(path: String): String = listOf(File("../$path"), File(path)).first(File::isFile).readText()
        private val snapshot: RuntimeSnapshot by lazy {
            val reader = RuntimeMetadataReader.read(repoFile("sorasubstrate/src/test/resources/sora2_metadata_mainnet_133").trim())
            val types = repoFile("common/src/production/assets/types_scalecodec_mobile.json")
            val parsed = TypeDefinitionParser.parseNetworkVersioning(
                tree = Gson().fromJson(types, TypeDefinitionsTree::class.java),
                typePreset = TypesParserV14.parse(reader.metadata[RuntimeMetadataSchemaV14.lookup], v14Preset()).typePreset,
                currentRuntimeVersion = 133, upto14 = false)
            val registry = TypeRegistry(parsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())
            RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(reader, registry))
        }
    }
}
