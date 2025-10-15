import kotlin.math.max
import kotlin.math.min

open class BaseParser(
    protected val input: String,
) {
    protected var index = 0

    fun whitespace() {
        while (index < input.length && input[index].isWhitespace()) {
            index++
        }
    }

    fun literal(literal: Char) {
        require(index < input.length && input[index] == literal) {
            "Parsing error: literal $literal expected at index $index ${inputAtIndex()}"
        }
        index++
    }

    fun ignoreUntil(chars: String): Char? {
        while (index < input.length) {
            if (input[index] in chars) {
                return input[index]
            }
            index++
        }
        return null
    }

    protected fun inputAtIndex(): String {
        val start = max(0, index - 10)
        val end = min(input.length, index + 10)
        return "\n${input.substring(start, end)}\n${" ".repeat(index - start) + "^"}"
    }

    protected fun checkLiteral(literal: Char): Boolean {
        if (index < input.length && input[index] == literal) {
            index++
            return true
        }
        return false
    }
}

class CssParser(
    input: String,
) : BaseParser(input) {
    fun word(): String {
        val start = index
        while (index < input.length) {
            if (input[index].isLetterOrDigit() || input[index] in "#-.%") {
                index++
            } else {
                break
            }
        }
        require(index > start) {
            "Parsing error: word expected at index $index ${inputAtIndex()}"
        }
        return input.substring(start, index)
    }

    fun pair(): Pair<String, String> {
        val prop = word().lowercase()
        whitespace()
        literal(':')
        whitespace()
        val value = word()
        return Pair(prop, value)
    }

    fun body(): Map<String, String> =
        buildMap {
            while (index < input.length) {
                try {
                    val (prop, value) = pair()
                    put(prop, value)
                    whitespace()
                    literal(';')
                    whitespace()
                } catch (e: Exception) {
                    println(e.message) // todo: use logger
                    if (ignoreUntil(";") == ';') {
                        literal(';')
                        whitespace()
                    } else {
                        break
                    }
                }
            }
        }
}
