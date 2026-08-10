package jp.co.soramitsu.common.network

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/**
 * Performs a bounded, RFC-strict pass over security-sensitive JSON before a model decoder can
 * collapse duplicate object names. The admitted document is deliberately not materialized here.
 */
fun requireStrictJsonDocumentWithoutDuplicateKeys(rawJson: String) {
    requireStrictJsonDocumentWithoutDuplicateKeys(
        rawJson = rawJson,
        maximumDepth = MAXIMUM_STRICT_JSON_DEPTH,
        maximumTokens = MAXIMUM_STRICT_JSON_TOKENS,
    )
}

internal fun requireStrictJsonDocumentWithoutDuplicateKeys(
    rawJson: String,
    maximumDepth: Int,
    maximumTokens: Int,
) {
    require(maximumDepth > 0 && maximumTokens > 0) {
        "BOUNDED_HTTP_JSON_LIMIT_INVALID"
    }
    val tokenCount = intArrayOf(0)
    try {
        JsonReader(StringReader(rawJson)).use { reader ->
            reader.strictness = Strictness.STRICT
            readStrictJsonValue(
                reader = reader,
                depth = 1,
                maximumDepth = maximumDepth,
                maximumTokens = maximumTokens,
                tokenCount = tokenCount,
            )
            if (reader.peek() != JsonToken.END_DOCUMENT) invalidJson()
        }
    } catch (error: BoundedHttpTextException) {
        throw error
    } catch (_: Exception) {
        invalidJson()
    }
}

private fun readStrictJsonValue(
    reader: JsonReader,
    depth: Int,
    maximumDepth: Int,
    maximumTokens: Int,
    tokenCount: IntArray,
) {
    if (depth > maximumDepth) jsonTooComplex()
    admitJsonToken(tokenCount, maximumTokens)
    when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val names = mutableSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                admitJsonToken(tokenCount, maximumTokens)
                val name = reader.nextName()
                requireWellFormedUtf16(name)
                if (!names.add(name)) invalidJson()
                readStrictJsonValue(
                    reader = reader,
                    depth = depth + 1,
                    maximumDepth = maximumDepth,
                    maximumTokens = maximumTokens,
                    tokenCount = tokenCount,
                )
            }
            reader.endObject()
        }

        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            while (reader.hasNext()) {
                readStrictJsonValue(
                    reader = reader,
                    depth = depth + 1,
                    maximumDepth = maximumDepth,
                    maximumTokens = maximumTokens,
                    tokenCount = tokenCount,
                )
            }
            reader.endArray()
        }

        JsonToken.STRING -> requireWellFormedUtf16(reader.nextString())
        JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull()
        else -> invalidJson()
    }
}

/**
 * JSON string escapes can encode isolated UTF-16 surrogates even when the transport bytes were
 * valid UTF-8. Reject them before a platform decoder can normalize or replace them differently.
 */
private fun requireWellFormedUtf16(value: String) {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            Character.isHighSurrogate(character) -> {
                if (
                    index + 1 >= value.length ||
                    !Character.isLowSurrogate(value[index + 1])
                ) {
                    invalidJson()
                }
                index += 2
            }

            Character.isLowSurrogate(character) -> invalidJson()
            else -> index += 1
        }
    }
}

private fun admitJsonToken(tokenCount: IntArray, maximumTokens: Int) {
    tokenCount[0] += 1
    if (tokenCount[0] > maximumTokens) jsonTooComplex()
}

private fun invalidJson(): Nothing =
    throw BoundedHttpTextException("BOUNDED_HTTP_JSON_INVALID")

private fun jsonTooComplex(): Nothing =
    throw BoundedHttpTextException("BOUNDED_HTTP_JSON_COMPLEXITY")

private const val MAXIMUM_STRICT_JSON_DEPTH = 64
private const val MAXIMUM_STRICT_JSON_TOKENS = 500_000
