import java.awt.Color
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics

abstract class Layout {
    val children: MutableList<Layout> = mutableListOf()
    abstract val parent: Layout?
    abstract val node: Node

    abstract fun layout(frameWidth: Int)

    abstract fun paint(): List<DrawCommand>

    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
}

class LineLayout(
    override val node: Node,
    override val parent: Layout?,
    private val previous: Layout?,
) : Layout() {
    override fun layout(frameWidth: Int) {
        width = parent?.width ?: (frameWidth - x)
        x = parent?.x ?: 0

        y =
            if (previous != null) {
                previous.y + previous.height
            } else {
                parent?.y ?: 0
            }

        children.forEach { it.layout(frameWidth) }

        val maxAscent = children.maxOfOrNull { (it as TextLayout).metrics.ascent } ?: 0f
        val baseline = y + (1.25f * maxAscent).toInt()
        children.forEach { (it as TextLayout).y = baseline - it.metrics.ascent.toInt() }
        val maxDescent = children.maxOfOrNull { (it as TextLayout).metrics.descent } ?: 0f

        height = (1.25f * (maxAscent + maxDescent)).toInt()
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "Line(node=$node, x=$x, y=$y, width=$width, height=$height)"
}

class TextLayout(
    private val word: String,
    override val node: Node,
    override val parent: Layout?,
    private val previous: TextLayout?,
) : Layout() {
    override fun layout(frameWidth: Int) {
        var style = 0
        if (node.style["font-weight"] == "bold") {
            style += Font.BOLD
        }
        if (node.style["font-style"] == "italic") {
            style += Font.ITALIC
        }
        val size = node.style["font-size"]?.removeSuffix("px")?.toIntOrNull() ?: 12
        val fontName = node.style["font-family"] ?: "SansSerif"
        font = Font(fontName, style, size)

        width = font.getStringBounds(word, fontRenderContext).width.toInt()
        x =
            if (previous != null) {
                val space =
                    previous.font
                        .getStringBounds(" ", fontRenderContext)
                        .width
                        .toInt()
                previous.x + previous.width + space
            } else {
                parent?.x ?: 0
            }
        height = font.getLineMetrics(word, fontRenderContext).height.toInt()
    }

    override fun paint(): List<DrawCommand> {
        val color = node.style["color"] ?: "black"
        val colorCode = if (color.startsWith("#")) color else colorCode(color)
        return listOf(DrawText(y, x, word, font, Color.decode(colorCode)))
    }

    val metrics: LineMetrics get() = font.getLineMetrics(word, fontRenderContext)
    lateinit var font: Font
        private set

    override fun toString(): String = "Text(node=$node, word='$word', x=$x, y=$y, width=$width, height=$height)"

    companion object {
        private val fontRenderContext = FontRenderContext(null, true, true)
    }
}

open class BlockLayout(
    override val node: Node,
    override val parent: Layout?,
    private val previous: Layout?,
) : Layout() {
    var cursorX = 0
    val fontRenderContext = FontRenderContext(null, true, true)

    override fun layout(frameWidth: Int) {
        if (node is Element && (node as Element).tag == "head") {
            return
        }
        x = parent?.x ?: 0
        width = parent?.width ?: (frameWidth - x)
        y =
            if (previous != null) {
                previous.y + previous.height
            } else {
                parent?.y ?: 0
            }

        val mode = layoutMode()
        when (mode) {
            LayoutMode.BLOCK -> {
                var previous: Layout? = null
                groupInlineElements().forEach {
                    val next =
                        if (it.size > 1) {
                            AnonymousBlockBoxLayout(it, this, previous)
                        } else {
                            BlockLayout(it.first(), this, previous)
                        }
                    children.add(next)
                    previous = next
                }
            }
            LayoutMode.INLINE -> {
                newLine(node)
                recurse(node)
            }
        }

        children.forEach { it.layout(width) }

        height = children.sumOf { it.height }
    }

    private fun groupInlineElements(): List<List<Node>> {
        val result = mutableListOf<List<Node>>()
        var current = mutableListOf<Node>()
        node.children.forEach {
            if (it is Element && it.tag in BLOCK_ELEMENTS) {
                if (current.isNotEmpty()) {
                    result.add(current)
                }
                result.add(listOf(it))
                current = mutableListOf()
            } else {
                current.add(it)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current)
        }
        return result
    }

    private fun layoutMode(): LayoutMode {
        if (node is Text) {
            return LayoutMode.INLINE
        }
        if (node.children.any { it is Element && it.tag in BLOCK_ELEMENTS }) {
            return LayoutMode.BLOCK
        }
        if (node.children.isNotEmpty()) {
            return LayoutMode.INLINE
        }
        return LayoutMode.BLOCK
    }

    fun recurse(node: Node) {
        if (node is Text) {
            text(node)
        } else if (node is Element) {
            if (node.tag == "br") {
                newLine(node)
            }
            node.children.forEach { recurse(it) }
        }
    }

    fun text(node: Text) {
        var style = 0
        if (node.style["font-weight"] == "bold") {
            style += Font.BOLD
        }
        if (node.style["font-style"] == "italic") {
            style += Font.ITALIC
        }
        val size = node.style["font-size"]?.removeSuffix("px")?.toIntOrNull() ?: 12
        val fontName = node.style["font-family"] ?: "SansSerif"
        val font = Font(fontName, style, size)
        node.content.split("\\s+".toRegex()).forEach { word(it, font, node) }
    }

    fun word(
        word: String,
        font: Font,
        node: Text,
    ) {
        val width = font.getStringBounds(word, fontRenderContext).width.toInt()
        if (cursorX + width > this.width) {
            newLine(node)
        }
        val line = children.last()
        val previousWord = line.children.lastOrNull() as TextLayout?
        val text = TextLayout(word, node, line, previousWord)
        line.children.add(text)
        cursorX += width + font.getStringBounds(" ", fontRenderContext).width.toInt()
    }

    fun newLine(node: Node) {
        cursorX = 0
        val lastLine = children.lastOrNull()
        val newLine = LineLayout(node, this, lastLine)
        children.add(newLine)
    }

    override fun paint(): List<DrawCommand> =
        buildList {
            val bgcolor = if (node is Element) node.style["background-color"] ?: "transparent" else "transparent"
            if (bgcolor != "transparent") {
                val x2 = x + width
                val y2 = y + height
                val bgColorCode = if (bgcolor.startsWith("#")) bgcolor else colorCode(bgcolor)
                add(DrawRect(y, x, y2, x2, Color.decode(bgColorCode)))
            }
            if (node is Element && (node as Element).tag == "li") {
                add(DrawRect(y + 6, x + 2, y + 14, x + 8, Color.LIGHT_GRAY))
            }
        }

    override fun toString(): String = "Block(x=$x, y=$y, width=$width, height=$height)"

    companion object {
        protected const val HSTEP = 13
        protected const val VSTEP = 18
        private val BLOCK_ELEMENTS =
            setOf(
                "html",
                "body",
                "article",
                "section",
                "nav",
                "aside",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "hgroup",
                "header",
                "footer",
                "address",
                "p",
                "hr",
                "pre",
                "blockquote",
                "ol",
                "ul",
                "menu",
                "li",
                "dl",
                "dt",
                "dd",
                "figure",
                "figcaption",
                "main",
                "div",
                "table",
                "form",
                "fieldset",
                "legend",
                "details",
                "summary",
            )
    }
}

class AnonymousBlockBoxLayout(
    private val nodes: List<Node>,
    override val parent: Layout?,
    private val previous: Layout?,
) : BlockLayout(nodes.first(), parent, previous) {
    override fun layout(frameWidth: Int) {
        x = parent?.x ?: 0
        width = parent?.width ?: (frameWidth - x)
        y =
            if (previous != null) {
                previous.y + previous.height
            } else {
                parent?.y ?: 0
            }

        newLine(nodes.first())
        nodes.forEach { recurse(it) }

        children.forEach { it.layout(width) }
        height = children.sumOf { it.height }
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "AnonymousBlockBox(nodes=$nodes)"
}

class DocumentLayout(
    override val node: Node,
) : BlockLayout(node, null, null) {
    override fun layout(frameWidth: Int) {
        children.clear()
        val child = BlockLayout(node, this, null)
        children.add(child)

        width = frameWidth - 2 * HSTEP
        x = HSTEP
        y = VSTEP
        children.forEach { child ->
            child.layout(width)
        }
        height = children.sumOf { it.height }
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "Document(x=$x, y=$y, width=$width, height=$height)"
}

enum class LayoutMode {
    INLINE,
    BLOCK,
}

fun paintTree(
    layoutObject: Layout,
    displayList: MutableList<DrawCommand>,
) {
    displayList.addAll(layoutObject.paint())
    layoutObject.children.forEach { paintTree(it, displayList) }
}

fun printTree(
    layoutObject: Layout,
    indent: String = "",
) {
    println("$indent$layoutObject")
    layoutObject.children.forEach { printTree(it, "$indent  ") }
}

fun Layout.treeToList(): List<Layout> {
    val result = mutableListOf<Layout>()
    result.add(this)
    children.forEach { result.addAll(it.treeToList()) }
    return result
}
