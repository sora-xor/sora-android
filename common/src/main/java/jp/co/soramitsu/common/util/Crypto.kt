package jp.co.soramitsu.common.util

import java.security.Security

object Crypto {

    init {
        Security.insertProviderAt(org.spongycastle.jce.provider.BouncyCastleProvider(), 1)
    }
}