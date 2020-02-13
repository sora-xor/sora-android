package jp.co.soramitsu.feature_wallet_impl.presentation.contacts.adapter

data class ContactMenuItem(
    val iconRes: Int,
    val nameRes: Int,
    val type: Type
) {
    enum class Type {
        SCAN_QR_CODE
    }
}