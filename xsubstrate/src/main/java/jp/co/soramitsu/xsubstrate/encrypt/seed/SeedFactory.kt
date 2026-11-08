package jp.co.soramitsu.xsubstrate.encrypt.seed

import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xsubstrate.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory

interface SeedFactory {
    class Result(val seed: ByteArray, val mnemonic: Mnemonic)

    fun createSeed(
        length: Mnemonic.Length,
        password: String?,
    ): Result

    fun deriveSeed(
        mnemonicWords: String,
        password: String?,
    ): Result
}

fun SeedFactory.createSeed32(
    length: Mnemonic.Length,
    password: String?,
) = cropSeedTo32Bytes(createSeed(length, password))

fun SubstrateSeedFactory.deriveSeed32(
    mnemonicWords: String,
    password: String?,
) = cropSeedTo32Bytes(deriveSeed(mnemonicWords, password))

fun EthereumSeedFactory.deriveSeed32(
    mnemonicWords: String,
    password: String?,
) = deriveSeed(mnemonicWords, password)

private const val CROP_SIZE = 32

private fun cropSeedTo32Bytes(seedResult: SeedFactory.Result): SeedFactory.Result {
    return SeedFactory.Result(seed = seedResult.seed.copyOfRange(0, CROP_SIZE), seedResult.mnemonic)
}
