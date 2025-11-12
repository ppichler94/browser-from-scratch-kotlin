package layout

import DrawCommand
import DrawLine
import DrawRect
import DrawText
import Element
import Node
import Text
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics

class InputLayout(
    override val node: Node,
    override val parent: Layout,
    private val previous: Layout?,
) : Layout() {
    private val logger = KotlinLogging.logger {}
    lateinit var font: Font
        private set
    lateinit var metrics: LineMetrics
        private set

    override fun layout(frameWidth: Int) {
        font = getFont(node.style)
        width = INPUT_WIDTH
        if (previous != null) {
            val space = font.getStringBounds(" ", fontRenderContext).width.toInt()
            x = previous.x + previous.width + space
            y = previous.y
        } else {
            x = parent.x
            y = parent.y
        }

        metrics = font.getLineMetrics("1", fontRenderContext)
        height = metrics.height.toInt()
    }

    override fun paint(): List<DrawCommand> =
        buildList {
            val bgcolor = node.style["background-color"] ?: "transparent"
            if (bgcolor != "transparent") {
                add(DrawRect(y, x, y + height, x + width, bgcolor))
            }

            val text = text()

            if (node.isFocused) {
                val cx = x + font.getStringBounds(text, fontRenderContext).width.toInt()
                add(DrawLine(cx, y, cx, y + height, "black", 1))
            }

            val color = node.style["color"] ?: "black"
            add(DrawText(y, x, text, font, color))
        }

    private fun text(): String =
        if (node is Element && node.tag == "input") {
            node.attributes["value"] ?: ""
        } else if (node is Element && node.tag == "button") {
            if (node.children.size == 1 && node.children[0] is Text) {
                (node.children[0] as Text).content
            } else {
                logger.warn { "Ignoring HTML contents inside button" }
                ""
            }
        } else {
            ""
        }

    override fun toString(): String = "Input(node=$node, text=${text()} x=$x, y=$y, width=$width, height=$height)"

    companion object {
        private val fontRenderContext = FontRenderContext(null, true, true)
        val INPUT_WIDTH = 200
    }
}
