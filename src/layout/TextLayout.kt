package layout

import DrawCommand
import DrawText
import Node
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics

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

    override fun paint(): List<DrawCommand> = listOf(DrawText(y, x, word, font, node.style["color"] ?: "black"))

    val metrics: LineMetrics get() = font.getLineMetrics(word, fontRenderContext)
    lateinit var font: Font
        private set

    override fun toString(): String = "Text(node=$node, word='$word', x=$x, y=$y, width=$width, height=$height)"

    companion object {
        private val fontRenderContext = FontRenderContext(null, true, true)
    }
}
