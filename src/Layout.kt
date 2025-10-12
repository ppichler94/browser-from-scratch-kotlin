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

class Layout(
    node: Node,
    val width: Int,
) {
    val displayList = mutableListOf<DisplayElement>()
    var cursorX = HSTEP
    var cursorY = VSTEP
    var size = 12
    var bold = false
    var italic = false
    val line = mutableListOf<LineElement>()
    val contentHeight get() = cursorY

    init {
        recuse(node)
        flush()
    }

    fun recuse(node: Node) {
        if (node is Text) {
            text(node.content)
        } else if (node is Element) {
            if (node.tag == "script" || node.tag == "style") {
                return
            }
            openTag(node)
            node.children.forEach { recuse(it) }
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
        val font = Font("Times", style, size)
        content.split("\\s+".toRegex()).forEach { word(it, font) }
    }

    fun word(
        word: String,
        font: Font,
    ) {
        val width = font.getStringBounds(word, FontRenderContext(null, true, true)).width.toInt()
        if (cursorX + width > this.width - HSTEP) {
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
        line.forEach { (x, text, font) ->
            val ascent = font.getLineMetrics(text, FontRenderContext(null, true, true)).ascent
            val y = baseline - ascent.toInt()
            displayList.add(DisplayElement(x, y, text, font))
        }
        val maxDescent = metrics.maxOfOrNull { it.descent } ?: 0f
        cursorY = baseline + (1.25f * maxDescent).toInt()
        cursorX = HSTEP
        line.clear()
    }

    fun openTag(node: Element) {
        when (node.tag) {
            "i" -> italic = true
            "b" -> bold = true
            "small" -> size -= 2
            "big" -> size += 4
            "br" -> flush()
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
        }
    }

    companion object {
        private const val HSTEP = 13
        private const val VSTEP = 18
    }
}
