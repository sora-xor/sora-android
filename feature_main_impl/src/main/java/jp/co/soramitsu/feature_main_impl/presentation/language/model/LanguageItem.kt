package jp.co.soramitsu.feature_main_impl.presentation.language.model

data class LanguageItem(
    val iso: String,
    val displayName: String,
    val nativeDisplayName: String,
    val isSelected: Boolean
)