package layout

import DrawCommand
import Node

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
