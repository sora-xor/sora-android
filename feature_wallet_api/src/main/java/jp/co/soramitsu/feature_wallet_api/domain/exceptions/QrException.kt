package jp.co.soramitsu.feature_wallet_api.domain.exceptions

class QrException private constructor(
    val kind: Kind
) : RuntimeException() {

    companion object {

        fun userNotFoundError(): QrException {
            return QrException(Kind.USER_NOT_FOUND)
        }

        fun sendingToMyselfError(): QrException {
            return QrException(Kind.SENDING_TO_MYSELF)
        }

        fun decodeError(): QrException {
            return QrException(Kind.DECODE_ERROR)
        }
    }

    enum class Kind {
        USER_NOT_FOUND,
        SENDING_TO_MYSELF,
        DECODE_ERROR
    }
}
