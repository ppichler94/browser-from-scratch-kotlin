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
    val metrics: LineMetrics get() = font.getLineMetrics(word, fontRenderContext)
    lateinit var font: Font
        private set

    override fun layout(frameWidth: Int) {
        font = getFont(node.style)

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

    override fun toString(): String = "Text(node=$node, word='$word', x=$x, y=$y, width=$width, height=$height)"

    companion object {
        private val fontRenderContext = FontRenderContext(null, true, true)
    }
}
