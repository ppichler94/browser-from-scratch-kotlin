sealed class Node {
    abstract val children: MutableList<Node>
}

data class Text(
    val content: String,
    val parent: Node? = null,
    override val children: MutableList<Node> = mutableListOf(),
) : Node()

data class Element(
    val tag: String,
    val parent: Node? = null,
    val attributes: Map<String, String> = mapOf(),
    override val children: MutableList<Node> = mutableListOf(),
) : Node()

class HtmlParser(
    private val html: String,
) {
    private val unfinished = mutableListOf<Node>()
    private val selfClosingTags =
        setOf(
            "area",
            "base",
            "br",
            "col",
            "embed",
            "hr",
            "img",
            "input",
            "link",
            "meta",
            "param",
            "source",
            "track",
            "wbr",
        )

    fun parse(): Node {
        var text = ""
        var entity = ""
        var inTag = false
        var inEntity = false

        for (char in html) {
            if (char == '<') {
                inTag = true
                if (text.isNotEmpty()) {
                    addText(text)
                }
                text = ""
            } else if (char == '>') {
                inTag = false
                addTag(text)
                text = ""
            } else if (!inTag && char == '&') {
                inEntity = true
            } else if (inEntity) {
                if (char == ';') {
                    text += getEntity(entity)
                    inEntity = false
                    entity = ""
                } else {
                    entity += char
                }
            } else {
                text += char
            }
        }
        if (!inTag && text.isNotEmpty()) {
            addText(text)
        }

        return finish()
    }

    private fun addText(text: String) {
        if (text.isBlank()) {
            return
        }
        val parent = unfinished.last()
        val node = Text(text, parent)
        parent.children.add(node)
    }

    private fun addTag(tag: String) {
        if (tag.startsWith("!")) {
            return
        }
        val (tag, attrs) = getAttributes(tag)
        if (tag.startsWith("/")) {
            if (unfinished.size == 1) {
                return
            }
            val node = unfinished.removeLast()
            val parent = unfinished.last()
            parent.children.add(node)
        } else if (tag in selfClosingTags) {
            val parent = unfinished.last()
            val node = Element(tag, parent, attrs)
            parent.children.add(node)
        } else {
            val parent = unfinished.lastOrNull()
            val node = Element(tag, parent, attrs)
            unfinished.add(node)
        }
    }

    private fun getAttributes(tag: String): Pair<String, Map<String, String>> {
        val parts = tag.split("\\s+".toRegex())
        val tag = parts[0].lowercase()
        val attrs = mutableMapOf<String, String>()
        parts.drop(1).forEach { part ->
            if ("=" in part) {
                val (key, value) = part.split("=", limit = 2)
                attrs[key] = value
            } else {
                attrs[part] = ""
            }
        }
        return Pair(tag, attrs)
    }

    private fun finish(): Node {
        while (unfinished.size > 1) {
            val node = unfinished.removeLast()
            val parent = unfinished.last()
            parent.children.add(node)
        }
        return unfinished.removeLast()
    }

    private fun getEntity(name: String): String =
        when (name) {
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            "quot" -> "\""
            else -> name
        }
}
