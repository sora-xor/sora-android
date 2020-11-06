package jp.co.soramitsu.feature_ethereum_impl.data.repository

import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.spongycastle.util.encoders.Hex
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class Bip3244Test {

    private val mnemonic = "agent plunge forget key stadium bridge garlic board acid volcano patrol sick"
    private val hexseed = "e175fb39d2f2688495d74462ea2596c3fe66a1213a72acd74021c8ac145e7addfe8f9c4c4ce12ec2a03adad3f86cbbb11c33efb51d44f84a6b82c7a5eb65d233"
    private val privateKey = BigInteger("42478643bce06b7d98415ab869afc109e49ade02dcc1dfef7b5ca47ce799b6fe", 16)
    private val address = "0x736d66B3313BD8c7d91a08423a0354E4EF57219b"
    private val publicKey = BigInteger("a499871e2f0ed30cfd2ba035f35e7cccf094d01b7207e275d86dcdf538e41a512dfef76822fc747eafe3cd825b1a05d3ecd0e995acfd9e76edaaab53baf8c198", 16)

    @Test fun `bip32 and 44 protocols are implemented right`() {
        val path = intArrayOf(44 or Bip32ECKeyPair.HARDENED_BIT, 60 or Bip32ECKeyPair.HARDENED_BIT, 0 or Bip32ECKeyPair.HARDENED_BIT, 0, 0)
        val seed = MnemonicUtils.generateSeed(mnemonic, "")

        assertEquals(Hex.toHexString(seed), hexseed)

        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val bip44Keypair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)
        val credentials = Credentials.create(bip44Keypair)

        assertEquals(publicKey, credentials.ecKeyPair.publicKey)
        assertEquals(privateKey, credentials.ecKeyPair.privateKey)
        assertEquals(address.toLowerCase(), credentials.address)
    }
}