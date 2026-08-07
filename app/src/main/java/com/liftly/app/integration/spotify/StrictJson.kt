package com.liftly.app.integration.spotify

internal sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val rawValue: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal class StrictJson private constructor(private val source: String) {
    private var position = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val result = readValue(depth = 0)
        skipWhitespace()
        require(position == source.length) { "Trailing JSON content." }
        return result
    }

    private fun readValue(depth: Int): JsonValue {
        require(depth <= MAX_DEPTH) { "JSON nesting is too deep." }
        require(position < source.length) { "Unexpected end of JSON." }
        return when (source[position]) {
            '{' -> readObject(depth + 1)
            '[' -> readArray(depth + 1)
            '"' -> JsonValue.StringValue(readString())
            't' -> readLiteral("true", JsonValue.BooleanValue(true))
            'f' -> readLiteral("false", JsonValue.BooleanValue(false))
            'n' -> readLiteral("null", JsonValue.NullValue)
            '-', in '0'..'9' -> JsonValue.NumberValue(readNumber())
            else -> error("Unexpected JSON token.")
        }
    }

    private fun readObject(depth: Int): JsonValue.ObjectValue {
        position++
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonValue.ObjectValue(values)
        while (true) {
            require(position < source.length && source[position] == '"') { "Object key must be a string." }
            val key = readString()
            require(key !in values) { "Duplicate JSON key." }
            skipWhitespace()
            require(consume(':')) { "Missing colon after object key." }
            skipWhitespace()
            values[key] = readValue(depth)
            skipWhitespace()
            if (consume('}')) break
            require(consume(',')) { "Missing comma in object." }
            skipWhitespace()
        }
        return JsonValue.ObjectValue(values)
    }

    private fun readArray(depth: Int): JsonValue.ArrayValue {
        position++
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonValue.ArrayValue(values)
        while (true) {
            values += readValue(depth)
            skipWhitespace()
            if (consume(']')) break
            require(consume(',')) { "Missing comma in array." }
            skipWhitespace()
        }
        return JsonValue.ArrayValue(values)
    }

    private fun readString(): String {
        require(consume('"')) { "Missing opening quote." }
        val result = StringBuilder()
        while (position < source.length) {
            val char = source[position++]
            when {
                char == '"' -> return result.toString()
                char == '\\' -> result.append(readEscape())
                char.code < 0x20 -> error("Unescaped control character in string.")
                else -> result.append(char)
            }
        }
        error("Unterminated JSON string.")
    }

    private fun readEscape(): Char {
        require(position < source.length) { "Unterminated JSON escape." }
        return when (val escaped = source[position++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(position + 4 <= source.length) { "Incomplete Unicode escape." }
                val code = source.substring(position, position + 4).toIntOrNull(16)
                    ?: error("Invalid Unicode escape.")
                position += 4
                code.toChar()
            }
            else -> error("Invalid JSON escape.")
        }
    }

    private fun readNumber(): String {
        val start = position
        consume('-')
        require(position < source.length) { "Invalid JSON number." }
        if (consume('0')) {
            require(position >= source.length || source[position] !in '0'..'9') { "Leading zero in JSON number." }
        } else {
            require(position < source.length && source[position] in '1'..'9') { "Invalid JSON number." }
            while (position < source.length && source[position] in '0'..'9') position++
        }
        if (consume('.')) {
            require(position < source.length && source[position] in '0'..'9') { "Invalid fraction." }
            while (position < source.length && source[position] in '0'..'9') position++
        }
        if (position < source.length && source[position] in charArrayOf('e', 'E')) {
            position++
            if (position < source.length && source[position] in charArrayOf('+', '-')) position++
            require(position < source.length && source[position] in '0'..'9') { "Invalid exponent." }
            while (position < source.length && source[position] in '0'..'9') position++
        }
        return source.substring(start, position)
    }

    private fun <T : JsonValue> readLiteral(token: String, result: T): T {
        require(source.regionMatches(position, token, 0, token.length)) { "Invalid JSON literal." }
        position += token.length
        return result
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in charArrayOf(' ', '\t', '\r', '\n')) position++
    }

    private fun consume(expected: Char): Boolean {
        if (position >= source.length || source[position] != expected) return false
        position++
        return true
    }

    companion object {
        private const val MAX_DEPTH = 16

        fun parse(source: String): JsonValue = StrictJson(source).parse()
    }
}
