package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class PrefsWalletDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var serializer: Serializer

    private lateinit var prefsWalletDatasource: PrefsWalletDatasource
    private val emptyJson = "{}"

    @Before fun setUp() {
        prefsWalletDatasource = PrefsWalletDatasource(preferences, serializer)
    }

    @Test fun `save balance called`() {
        val keyBalance = "key_balance"
        val assets = arrayOf(Asset(BigDecimal.TEN, "xor#sora"))
        given(serializer.serialize(assets)).willReturn(emptyJson)

        prefsWalletDatasource.saveBalance(assets)

        verify(preferences).putString(keyBalance, emptyJson)
    }

    @Test fun `retrieve balance called`() {
        val keyBalance = "key_balance"
        val assets = arrayOf(Asset(BigDecimal.TEN, "xor#sora"))
        given(preferences.getString(keyBalance)).willReturn(emptyJson)
        given(serializer.deserialize<Array<Asset>>(emptyJson, object : TypeToken<Array<Asset>>() {}.type)).willReturn(assets)

        assertEquals(assets, prefsWalletDatasource.retrieveBalance())
    }

    @Test fun `retrieve balance called if no cached`() {
        val keyBalance = "key_balance"
        given(preferences.getString(keyBalance)).willReturn("")

        assertNull(prefsWalletDatasource.retrieveBalance())
    }

    @Test fun `save contacts called`() {
        val keyContacts = "key_contacts"
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        given(serializer.serialize(accounts)).willReturn(emptyJson)

        prefsWalletDatasource.saveContacts(accounts)

        verify(preferences).putString(keyContacts, emptyJson)
    }

    @Test fun `retrieve contacts called`() {
        val keyContacts = "key_contacts"
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        given(preferences.getString(keyContacts)).willReturn(emptyJson)
        given(serializer.deserialize<List<Account>>(emptyJson, object : TypeToken<List<Account>>() {}.type)).willReturn(accounts)

        assertEquals(accounts, prefsWalletDatasource.retrieveContacts())
    }

    @Test fun `retrieve contacts called if no cached`() {
        val keyContacts = "key_contacts"
        given(preferences.getString(keyContacts)).willReturn("")

        assertNull(prefsWalletDatasource.retrieveContacts())
    }

    @Test fun `save transfer meta called`() {
        val keyTransferMetaFeeRate = "key_transfer_meta_rate"
        val keyTransferMetaFeeType = "key_transfer_meta_type"
        val transferMeta = TransferMeta(2.0, FeeType.FIXED)

        prefsWalletDatasource.saveTransferMeta(transferMeta)

        verify(preferences).putDouble(keyTransferMetaFeeRate, transferMeta.feeRate)
        verify(preferences).putString(keyTransferMetaFeeType, transferMeta.feeType.toString())
    }

    @Test fun `retrieve transfer meta called`() {
        val keyTransferMetaFeeRate = "key_transfer_meta_rate"
        val keyTransferMetaFeeType = "key_transfer_meta_type"
        val transferMeta = TransferMeta(2.0, FeeType.FIXED)
        given(preferences.getDouble(keyTransferMetaFeeRate, -1.0)).willReturn(transferMeta.feeRate)
        given(preferences.getString(keyTransferMetaFeeType)).willReturn(transferMeta.feeType.toString())

        assertEquals(transferMeta, prefsWalletDatasource.retrieveTransferMeta())
    }

    @Test fun `retrieve transfer meta called if no cached`() {
        val keyTransferMetaFeeRate = "key_transfer_meta_rate"
        given(preferences.getDouble(keyTransferMetaFeeRate, -1.0)).willReturn(-1.0)

        assertNull(prefsWalletDatasource.retrieveTransferMeta())
    }
}
