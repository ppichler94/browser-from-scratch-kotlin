import java.awt.Color
import java.awt.Font
import java.awt.font.FontRenderContext

data class DisplayElement(
    val x: Int,
    val y: Int,
    val text: String,
    val font: Font,
)

data class LineElement(
    val x: Int,
    val text: String,
    val font: Font,
)

open class BlockLayout(
    private val node: Node,
    private val parent: BlockLayout?,
    private val previous: BlockLayout?,
    val children: MutableList<BlockLayout> = mutableListOf(),
) {
    private val displayList = mutableListOf<DisplayElement>()
    var cursorX = 0
    var cursorY = 0
    var size = 12
    var bold = false
    var italic = false
    var preformatted = false
    val line = mutableListOf<LineElement>()
    val contentHeight get() = cursorY
    protected var x: Int = 0
    protected var y: Int = 0
    protected var width: Int = 0
    var height: Int = 0
        protected set

    open fun layout(frameWidth: Int) {
        if (node is Element && node.tag == "head") {
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
                var previous: BlockLayout? = null
                node.children.forEach {
                    val next = BlockLayout(it, this, previous)
                    children.add(next)
                    previous = next
                }
            }
            LayoutMode.INLINE -> {
                cursorX = 0
                cursorY = 0
                bold = false
                italic = false
                line.clear()
                recurse(node)
                flush()
            }
        }

        children.forEach { it.layout(width) }

        height =
            when (mode) {
                LayoutMode.BLOCK -> children.sumOf { it.height }
                LayoutMode.INLINE -> contentHeight
            }
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
            text(node.content)
        } else if (node is Element) {
            openTag(node)
            node.children.forEach { recurse(it) }
            closeTag(node)
        }
    }

    fun text(content: String) {
        var style = 0
        if (bold) {
            style += Font.BOLD
        }
        if (italic) {
            style += Font.ITALIC
        }
        if (preformatted) {
            flush()
            val font = Font(Font.MONOSPACED, style, size)
            preformatted(content, font)
        } else {
            val font = Font("Times", style, size)
            content.split("\\s+".toRegex()).forEach { word(it, font) }
        }
    }

    fun word(
        word: String,
        font: Font,
    ) {
        val width = font.getStringBounds(word, FontRenderContext(null, true, true)).width.toInt()
        if (cursorX + width > this.width) {
            flush()
        }
        line.add(LineElement(cursorX, word, font))
        cursorX += width + font.getStringBounds(" ", FontRenderContext(null, true, true)).width.toInt()
    }

    fun flush() {
        if (line.isEmpty()) {
            return
        }
        val metrics = line.map { it.font.getLineMetrics(it.text, FontRenderContext(null, true, true)) }
        val maxAscent = metrics.maxOfOrNull { it.ascent } ?: 0f
        val baseline = cursorY + (1.25f * maxAscent).toInt()
        line.forEach { (relX, text, font) ->
            val ascent = font.getLineMetrics(text, FontRenderContext(null, true, true)).ascent
            val y = this.y + baseline - ascent.toInt()
            displayList.add(DisplayElement(x + relX, y, text, font))
        }
        val maxDescent = metrics.maxOfOrNull { it.descent } ?: 0f
        cursorY = baseline + (1.25f * maxDescent).toInt()
        cursorX = 0
        line.clear()
    }

    fun preformatted(
        text: String,
        font: Font,
    ) {
        cursorX = 0
        text.split("\n").forEach {
            displayList.add(DisplayElement(cursorX + x, cursorY + y, it, font))
            cursorX = 0
            cursorY += font.getLineMetrics(it, FontRenderContext(null, true, true)).height.toInt()
        }
    }

    fun openTag(node: Element) {
        when (node.tag) {
            "i" -> italic = true
            "b" -> bold = true
            "small" -> size -= 2
            "big" -> size += 4
            "br" -> flush()
            "pre" -> preformatted = true
        }
    }

    fun closeTag(node: Element) {
        when (node.tag) {
            "i" -> italic = false
            "b" -> bold = false
            "small" -> size += 2
            "big" -> size -= 4
            "p" -> {
                flush()
                cursorY += VSTEP
            }
            "pre" -> preformatted = false
        }
    }

    open fun paint(): List<DrawCommand> =
        buildList {
            val bgcolor = if (node is Element) node.style["background-color"] ?: "transparent" else "transparent"
            if (bgcolor != "transparent") {
                val x2 = x + width
                val y2 = y + height
                val bgColorCode = if (bgcolor.startsWith("#")) bgcolor else colorCode(bgcolor)
                add(DrawRect(y, x, y2, x2, Color.decode(bgColorCode)))
            }
            if (node is Element && node.tag == "li") {
                add(DrawRect(y + 6, x + 2, y + 14, x + 8, Color.LIGHT_GRAY))
            }
            if (layoutMode() == LayoutMode.INLINE) {
                val offset = if (node is Element && node.tag == "li") 10 else 0
                addAll(displayList.map { DrawText(it.y, it.x + offset, it.text, it.font) })
            }
        }

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

class DocumentLayout(
    private val node: Node,
) : BlockLayout(node, null, null) {
    override fun layout(frameWidth: Int) {
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
}

enum class LayoutMode {
    INLINE,
    BLOCK,
}

fun paintTree(
    layoutObject: BlockLayout,
    displayList: MutableList<DrawCommand>,
) {
    displayList.addAll(layoutObject.paint())
    layoutObject.children.forEach { paintTree(it, displayList) }
}
