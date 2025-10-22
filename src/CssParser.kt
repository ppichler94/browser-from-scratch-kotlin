import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val logger = KotlinLogging.logger {}
    typealias CssRule = Pair<Selector, Map<String, String>>

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
                    if (input[index] == '}') {
                        break
                    }
                } catch (e: Exception) {
                    logger.debug { e.message }
                    if (ignoreUntil(";}") == ';') {
                        literal(';')
                        whitespace()
                    } else {
                        break
                    }
                }
            }
        }

    fun selector(): Selector {
        var out: Selector = selector(word().lowercase())
        whitespace()
        while (index < input.length && input[index] != '{') {
            val tag = word().lowercase()
            val descendant = selector(tag)
            out = DescendantSelector(out, descendant)
            whitespace()
        }
        return out
    }

    private fun selector(name: String): Selector =
        when {
            name.startsWith(".") -> ClassSelector(name.substring(1))
            name.contains(".") -> {
                val names = name.split(".")
                val tag = TagSelector(names[0])
                val classes = names.drop(1).map { ClassSelector(it) }
                SequenceSelector(listOf(tag) + classes)
            }
            else -> TagSelector(name)
        }

    fun parse(): List<CssRule> =
        buildList {
            while (index < input.length) {
                try {
                    whitespace()
                    val selector = selector()
                    literal('{')
                    whitespace()
                    val body = body()
                    whitespace()
                    literal('}')
                    add(Pair(selector, body))
                } catch (e: Exception) {
                    logger.debug { e.message }
                    if (ignoreUntil("}") == '}') {
                        literal('}')
                        whitespace()
                    } else {
                        break
                    }
                }
            }
        }
}
