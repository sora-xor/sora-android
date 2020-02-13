package jp.co.soramitsu.feature_did_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.JsonObject
import io.reactivex.Single
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import jp.co.soramitsu.core_network_api.data.response.BaseResponse
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidDatasource
import jp.co.soramitsu.feature_did_impl.data.network.DidNetworkApi
import jp.co.soramitsu.feature_did_impl.data.network.response.GetDdoResponse
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.DID
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyPair
import java.security.PublicKey

@RunWith(MockitoJUnitRunner::class)
class DidRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var didApi: DidNetworkApi
    @Mock private lateinit var didDatasource: DidDatasource
    @Mock private lateinit var authHolder: AuthHolder
    @Mock private lateinit var mnemonicProvider: MnemonicProvider
    @Mock private lateinit var cryptoAssistant: CryptoAssistant
    @Mock private lateinit var didProvider: DidProvider

    private lateinit var didRepository: DidRepositoryImpl

    @Before fun setUp() {
        didRepository = DidRepositoryImpl(didApi, didDatasource, authHolder, mnemonicProvider, cryptoAssistant, didProvider)
    }

    @Test fun `register user ddo called with cached keypair`() {
        val keyPair = mock(KeyPair::class.java)
        val mnemonic = "mnemonic"
        given(didDatasource.retrieveMnemonic()).willReturn(mnemonic)
        given(didDatasource.retrieveKeys()).willReturn(keyPair)

        didRepository.registerUserDdo()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(authHolder).setKeyPair(keyPair)
    }

    @Test fun `register user ddo called`() {
        val projectName = "SORA"
        val purpose = "iroha keypair"
        val keyPair = mock(KeyPair::class.java)
        val publicKey = mock(PublicKey::class.java)
        val did = mock(DID::class.java)
        val ddo = mock(DDO::class.java)
        val emptyMnemonic = ""
        val mnemonic = "mnemonic"
        val json = "{}"
        val randomBytes = "randomrandomrandomrandomrandomrandom".toByteArray()
        val hexRandom = "72616e646f6d72616e646f6d72616e646f6d72616e646f6d72616e646f6d72616e646f6d"
        given(keyPair.public).willReturn(publicKey)
        given(publicKey.encoded).willReturn(randomBytes)
        given(didDatasource.retrieveMnemonic()).willReturn(emptyMnemonic)
        given(cryptoAssistant.getSecureRandom(anyInt())).willReturn(Single.just(randomBytes))
        given(mnemonicProvider.generateMnemonic(randomBytes)).willReturn(Single.just(mnemonic))
        given(mnemonicProvider.getBytesFromMnemonic(mnemonic)).willReturn(Single.just(randomBytes))
        given(cryptoAssistant.generateScryptSeed(randomBytes, projectName, purpose, "")).willReturn(Single.just(randomBytes))
        given(cryptoAssistant.generateKeys(randomBytes)).willReturn(Single.just(keyPair))
        given(didProvider.generateDID(hexRandom.substring(0, 20))).willReturn(did)
        given(didProvider.generateDDO(did, randomBytes)).willReturn(Single.just(ddo))
        given(cryptoAssistant.signDDO(keyPair, ddo)).willReturn(Single.just(ddo))
        given(didProvider.ddoToJson(ddo)).willReturn(json)
        given(didApi.postDdo(anyNonNull())).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        didRepository.registerUserDdo()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(authHolder).setKeyPair(keyPair)
        verify(didDatasource).saveDdo(ddo)
        verify(didDatasource).saveKeys(keyPair)
        verify(didDatasource).saveMnemonic(mnemonic)
    }

    @Test fun `recover mnemonic called`() {
        val projectName = "SORA"
        val purpose = "iroha keypair"
        val mnemonic = "mnemonic"
        val json = "{}"
        val randomBytes = "randomrandomrandomrandomrandomrandom".toByteArray()
        val hexRandom = "72616e646f6d72616e646f6d72616e646f6d72616e646f6d72616e646f6d72616e646f6d"
        val keyPair = mock(KeyPair::class.java)
        val publicKey = mock(PublicKey::class.java)
        val ddoJson = mock(JsonObject::class.java)
        val ddo = mock(DDO::class.java)
        val did = mock(DID::class.java)
        val didString = "some:did"
        given(ddoJson.toString()).willReturn(json)
        given(didProvider.jsonToDdo(json)).willReturn(ddo)
        given(did.toString()).willReturn(didString)
        given(keyPair.public).willReturn(publicKey)
        given(publicKey.encoded).willReturn(randomBytes)
        given(mnemonicProvider.getBytesFromMnemonic(mnemonic)).willReturn(Single.just(randomBytes))
        given(cryptoAssistant.generateScryptSeed(randomBytes, projectName, purpose, "")).willReturn(Single.just(randomBytes))
        given(cryptoAssistant.generateKeys(randomBytes)).willReturn(Single.just(keyPair))
        given(didProvider.generateDID(hexRandom.substring(0, 20))).willReturn(did)
        given(didApi.getDdo(didString)).willReturn(Single.just(GetDdoResponse(StatusDto("Ok", ""), ddoJson)))
        given(cryptoAssistant.getProofKeyFromDdo(ddo)).willReturn(randomBytes)

        didRepository.recoverAccount(mnemonic)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(didDatasource).saveMnemonic(mnemonic)
        verify(didDatasource).saveKeys(keyPair)
        verify(didDatasource).saveDdo(ddo)
        verify(authHolder).setKeyPair(keyPair)
    }

    @Test fun `restore auth called`() {
        val keyPair = mock(KeyPair::class.java)
        given(didDatasource.retrieveKeys()).willReturn(keyPair)

        didRepository.restoreAuth()

        verify(authHolder).setKeyPair(keyPair)
    }

    @Test fun `save mnemonic called`() {
        val mnemonic = "mnemonic"

        didRepository.saveMnemonic(mnemonic)

        verify(didDatasource).saveMnemonic(mnemonic)
    }

    @Test fun `retrieve mnemonic called`() {
        val mnemonic = "mnemonic"
        given(didDatasource.retrieveMnemonic()).willReturn(mnemonic)

        didRepository.retrieveMnemonic()
            .test()
            .assertResult(mnemonic)
    }

    @Test fun `retrieve keypair called`() {
        val keyPair = mock(KeyPair::class.java)
        given(didDatasource.retrieveKeys()).willReturn(keyPair)

        didRepository.retrieveKeypair()
            .test()
            .assertResult(keyPair)
    }

    @Test fun `retrieve did called`() {
        val did = mock(DID::class.java)
        val didString = "did:did"
        val ddo = mock(DDO::class.java)
        given(didDatasource.retrieveDdo()).willReturn(ddo)
        given(ddo.id).willReturn(did)
        given(did.toString()).willReturn(didString)

        didRepository.retrieveDid()
            .test()
            .assertResult(didString)
    }

    @Test fun `get iroha username called`() {
        val did = mock(DID::class.java)
        val didString = "did:did"
        val irohaUsername = "did_did"
        val ddo = mock(DDO::class.java)
        given(didDatasource.retrieveDdo()).willReturn(ddo)
        given(ddo.id).willReturn(did)
        given(did.toString()).willReturn(didString)

        didRepository.getIrohaUserName()
            .test()
            .assertResult(irohaUsername)
    }

    @Test fun `get accountId username called`() {
        val did = mock(DID::class.java)
        val didString = "did:did"
        val accountId = "did_did@sora"
        val ddo = mock(DDO::class.java)
        given(didDatasource.retrieveDdo()).willReturn(ddo)
        given(ddo.id).willReturn(did)
        given(did.toString()).willReturn(didString)

        didRepository.getAccountId()
            .test()
            .assertResult(accountId)
    }
}