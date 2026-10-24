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
        requireCanonicalIntegerNumbers = false,
    )
}

/** Nexus wire quantities are strings; every JSON NUMBER must be one exact 64-bit integer token. */
fun requireStrictNexusJsonDocument(rawJson: String) {
    // RFC 8259 permits parsers to ignore a leading BOM for interoperability,
    // but emitters must not produce one. Security-sensitive Nexus admission is
    // byte-contractual across iOS and Android, so accepting a platform-specific
    // normalization here would make the two clients disagree on signed input.
    if (rawJson.startsWith('\uFEFF')) invalidJson()
    requireStrictJsonDocumentWithoutDuplicateKeys(
        rawJson = rawJson,
        maximumDepth = MAXIMUM_STRICT_JSON_DEPTH,
        maximumTokens = MAXIMUM_STRICT_JSON_TOKENS,
        requireCanonicalIntegerNumbers = true,
    )
}

internal fun requireStrictJsonDocumentWithoutDuplicateKeys(
    rawJson: String,
    maximumDepth: Int,
    maximumTokens: Int,
    requireCanonicalIntegerNumbers: Boolean = false,
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
                requireCanonicalIntegerNumbers = requireCanonicalIntegerNumbers,
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
    requireCanonicalIntegerNumbers: Boolean,
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
                    requireCanonicalIntegerNumbers = requireCanonicalIntegerNumbers,
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
                    requireCanonicalIntegerNumbers = requireCanonicalIntegerNumbers,
                )
            }
            reader.endArray()
        }

        JsonToken.STRING -> requireWellFormedUtf16(reader.nextString())
        JsonToken.NUMBER -> {
            val token = reader.nextString()
            if (requireCanonicalIntegerNumbers && !isCanonical64BitInteger(token)) {
                invalidJson()
            }
        }
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull()
        else -> invalidJson()
    }
}

private fun isCanonical64BitInteger(value: String): Boolean =
    if (value.startsWith("-")) {
        value.toLongOrNull()?.let { value == it.toString() } == true
    } else {
        value.toULongOrNull()?.let { value == it.toString() } == true
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
