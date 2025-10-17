import io.github.oshai.kotlinlogging.KotlinLogging

sealed class Node {
    abstract val children: MutableList<Node>
    val style = mutableMapOf<String, String>()
    abstract val parent: Node?
}

fun Node.treeToList(): List<Node> = children.flatMap { it.treeToList() } + listOf(this)

data class Text(
    val content: String,
    override val parent: Node? = null,
    override val children: MutableList<Node> = mutableListOf(),
) : Node() {
    override fun toString() = content
}

data class Element(
    val tag: String,
    override val parent: Node? = null,
    val attributes: Map<String, String> = mapOf(),
    override val children: MutableList<Node> = mutableListOf(),
) : Node() {
    override fun toString() = "<$tag $attributes>"
}

open class HtmlParser(
    private val html: String,
) {
    private val logger = KotlinLogging.logger {}
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
        logger.info { "Parsing HTML..." }
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

    companion object {
        private val INHERITED_PROPERTIES =
            mutableMapOf(
                "font-size" to "16px",
                "font-style" to "normal",
                "font-weight" to "normal",
                "color" to "black",
            )

        fun style(
            node: Node,
            rules: List<CssParser.CssRule>,
        ) {
            INHERITED_PROPERTIES.forEach { (property, value) ->
                if (node.parent != null) {
                    node.style[property] = node.parent!!.style[property] ?: value
                } else {
                    node.style[property] = value
                }
            }
            rules
                .filter { (selector, _) -> selector.matches(node) }
                .forEach { (_, body) ->
                    node.style.putAll(body)
                }

            if (node is Element && "style" in node.attributes) {
                val pairs = CssParser(node.attributes["style"]!!).body()
                node.style.putAll(pairs)
            }

            if (node.style["font-size"]?.endsWith("%") == true) {
                val parentFontSize =
                    if (node.parent is Element) {
                        node.parent!!.style["font-size"]
                    } else {
                        INHERITED_PROPERTIES["font-size"]
                    }
                val nodePct = node.style["font-size"]!!.removeSuffix("%").toFloat() / 100
                val parentPx = parentFontSize!!.removeSuffix("px").toFloat()
                node.style["font-size"] = "${parentPx * nodePct}px"
            }

            node.children.forEach { style(it, rules) }
        }
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
