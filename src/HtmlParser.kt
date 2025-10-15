sealed class Node {
    abstract val children: MutableList<Node>
}

data class Text(
    val content: String,
    val parent: Node? = null,
    override val children: MutableList<Node> = mutableListOf(),
) : Node() {
    override fun toString() = content
}

data class Element(
    val tag: String,
    val parent: Node? = null,
    val attributes: Map<String, String> = mapOf(),
    override val children: MutableList<Node> = mutableListOf(),
) : Node() {
    override fun toString() = "<$tag $attributes>"

    val style: Map<String, String>

    init {
        if ("style" in attributes) {
            val pairs = CssParser(attributes["style"]!!).body()
            style = pairs.toMap()
        } else {
            style = mapOf()
        }
    }
}

open class HtmlParser(
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

    open fun parse(): Node {
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

    protected open fun addText(text: String) {
        if (text.isBlank()) {
            return
        }
        val parent = unfinished.last()
        val node = Text(text, parent)
        parent.children.add(node)
    }

    protected open fun addTag(tag: String) {
        if (tag.startsWith("!")) {
            return
        }
        val (tag, attrs) = HtmlTagParser(tag).tag()
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

    protected open fun finish(): Node {
        while (unfinished.size > 1) {
            val node = unfinished.removeLast()
            val parent = unfinished.last()
            parent.children.add(node)
        }
        return unfinished.removeLast()
    }

    protected fun getEntity(name: String): String =
        when (name) {
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            "quot" -> "\""
            else -> name
        }
}

class ViewSourceHtmlParser(
    html: String,
) : HtmlParser(html) {
    private val root = Element("html")

    override fun addText(text: String) {
        if (text.isBlank()) {
            return
        }
        val element = Element("b", root, mapOf(), mutableListOf(Text(text)))
        root.children.add(element)
    }

    override fun addTag(tag: String) {
        root.children.add(Text("<$tag>", root))
    }

    override fun finish(): Node = root
}
