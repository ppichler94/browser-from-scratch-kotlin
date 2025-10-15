import io.github.oshai.kotlinlogging.KotlinLogging

class HtmlTagParser(
    input: String,
) : BaseParser(input) {
    private val logger = KotlinLogging.logger {}

    fun name(): String {
        val start = index
        while (index < input.length) {
            if (input[index].isLetterOrDigit() || input[index] in ":/-") {
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

    fun value(): String {
        val start = index
        while (index < input.length) {
            if (input[index].isLetterOrDigit()) {
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

    fun quotedValue(): String {
        val start = index
        while (index < input.length) {
            if (input[index] != '"') {
                index++
            } else {
                break
            }
        }
        return input.substring(start, index++)
    }

    fun attribute(): Pair<String, String> {
        val name = name()
        if (checkLiteral('=')) {
            val value = if (checkLiteral('"')) quotedValue() else value()
            return Pair(name, value)
        }
        return Pair(name, "true")
    }

    fun body(): Map<String, String> =
        buildMap {
            while (index < input.length) {
                try {
                    val (key, value) = attribute()
                    if (key != "/") {
                        put(key, value)
                    }
                    whitespace()
                } catch (e: Exception) {
                    logger.warn { e.message }
                    if (ignoreUntil(" ") == ' ') {
                        whitespace()
                    } else {
                        break
                    }
                }
            }
        }

    fun tag(): Pair<String, Map<String, String>> {
        val name = name()
        whitespace()
        val body = body()
        return Pair(name, body)
    }
}
