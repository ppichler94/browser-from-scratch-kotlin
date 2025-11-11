package layout

import DrawCommand
import Node
import java.awt.font.LineMetrics

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

        val maxAscent = children.maxOfOrNull { it.metrics().ascent } ?: 0f
        val baseline = y + (1.25f * maxAscent).toInt()
        children.forEach { it.y = baseline - it.metrics().ascent.toInt() }
        val maxDescent = children.maxOfOrNull { it.metrics().descent } ?: 0f

        height = (1.25f * (maxAscent + maxDescent)).toInt()
    }

    override fun paint(): List<DrawCommand> = listOf()

    override fun toString(): String = "Line(node=$node, x=$x, y=$y, width=$width, height=$height)"

    private fun Layout.metrics(): LineMetrics =
        when (this) {
            is TextLayout -> metrics
            is InputLayout -> metrics
            else -> throw IllegalStateException("LineLayout can't contain ${this::class.simpleName}")
        }
}
