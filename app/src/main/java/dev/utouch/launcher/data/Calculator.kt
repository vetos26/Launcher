package dev.utouch.launcher.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Tiny expression evaluator behind the search field: `2+2*7`, `(340-20)/4`, `15%`.
 * Returns null when the input is not a complete arithmetic expression.
 */
object Calculator {

    fun evaluate(input: String): String? {
        val expression = input.trim()
        if (expression.length < 2) return null
        if (expression.none { it.isDigit() }) return null
        if (expression.none { it in "+-*/%()" }) return null

        val value = runCatching { Parser(expression).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun format(value: Double): String {
        if (abs(value) < 1e15 && abs(value - floor(value)) < 1e-9) {
            return value.roundToLong().toString()
        }
        return String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            require(position == text.length) { "trailing input" }
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * factor()
                    consume('/') -> value / factor()
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            var sign = 1.0
            if (consume('-')) sign = -1.0 else consume('+')
            var value = primary()
            skipSpaces()
            if (consume('%')) value /= 100.0
            return sign * value
        }

        private fun primary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')')) { "missing )" }
                return value
            }

            val start = position
            while (position < text.length &&
                (text[position].isDigit() || text[position] == '.' || text[position] == ',')
            ) {
                position++
            }
            require(position > start) { "number expected" }
            return text.substring(start, position).replace(',', '.').toDouble()
        }

        private fun consume(char: Char): Boolean {
            skipSpaces()
            if (position < text.length && text[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < text.length && text[position].isWhitespace()) position++
        }
    }
}
