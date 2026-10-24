package jp.co.soramitsu.feature_blockexplorer_api.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Preserves PI's unsigned integer wire values without routing them through a floating-point or
 * fixed-width numeric representation.
 *
 * The deployed health schema emits its four mandatory worker checkpoint values as JSON integer
 * tokens, while fixtures and the app-private cache may contain JSON strings. All seven PI health
 * integer fields use this serializer so optional aliases and error timestamps also retain exact
 * numeric-cache compatibility. Both forms are admitted only when their decoded value is the
 * canonical unsigned decimal lexeme. Encoding always uses a JSON string so cache bytes are
 * deterministic and remain precision-safe across serializer upgrades.
 */
internal object NullableCanonicalUnsignedIntegerLexemeSerializer : KSerializer<String?> {
    private val canonicalUnsignedInteger = Regex("^(?:0|[1-9][0-9]*)$")

    override val descriptor: SerialDescriptor = String.serializer().nullable.descriptor

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException(ERROR_CODE)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonPrimitive -> requireCanonical(element.content)
            else -> throw SerializationException(ERROR_CODE)
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException(ERROR_CODE)
        jsonEncoder.encodeJsonElement(
            value?.let { JsonPrimitive(requireCanonical(it)) } ?: JsonNull
        )
    }

    private fun requireCanonical(value: String): String {
        if (value.length > MAXIMUM_WIRE_BYTES || !canonicalUnsignedInteger.matches(value)) {
            throw SerializationException(ERROR_CODE)
        }
        return value
    }

    internal const val MAXIMUM_WIRE_BYTES = 4_096
    internal const val ERROR_CODE = "PI_INDEXER_INTEGER_LEXEME_INVALID"
}
